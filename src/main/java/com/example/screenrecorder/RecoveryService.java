package com.example.screenrecorder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Crash-recovery support. Active recordings are written as Matroska segments in
 * a hidden recovery folder. Matroska does not depend on an MP4 moov atom, so a
 * hard application/PC crash normally leaves the already-written portion usable.
 */
public final class RecoveryService {
    public static final String RECOVERY_ROOT = ".java-screen-recorder-recovery";
    private static final String MANIFEST = "session.properties";

    public record RecoveryCandidate(Path sessionDir, Path targetOutput, List<Path> segments, Instant createdAt) {
        @Override public String toString() {
            return targetOutput.getFileName() + " (" + segments.size() + " segment(s))";
        }
    }

    Path createSession(Path outputDirectory, Path targetOutput) throws IOException {
        Path root = outputDirectory.resolve(RECOVERY_ROOT);
        Files.createDirectories(root);
        Path dir = Files.createTempDirectory(root, "session-");
        writeManifest(dir, targetOutput, "ACTIVE");
        return dir;
    }

    void updateState(Path sessionDir, Path targetOutput, String state) {
        try {
            writeManifest(sessionDir, targetOutput, state);
        } catch (IOException ignored) {
        }
    }

    private void writeManifest(Path dir, Path targetOutput, String state) throws IOException {
        Properties p = new Properties();
        p.setProperty("version", "1");
        p.setProperty("targetOutput", targetOutput.toAbsolutePath().toString());
        p.setProperty("state", state);
        p.setProperty("updatedAt", Instant.now().toString());
        Path tmp = dir.resolve(MANIFEST + ".tmp");
        try (var out = Files.newOutputStream(tmp)) {
            p.store(out, "Java Screen Recorder crash recovery");
        }
        Files.move(tmp, dir.resolve(MANIFEST), StandardCopyOption.REPLACE_EXISTING);
    }

    public List<RecoveryCandidate> scan(Path outputDirectory) {
        List<RecoveryCandidate> result = new ArrayList<>();
        Path root = outputDirectory.resolve(RECOVERY_ROOT);
        if (!Files.isDirectory(root)) return result;

        try (var dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    Path manifest = dir.resolve(MANIFEST);
                    if (!Files.isRegularFile(manifest)) return;
                    Properties p = new Properties();
                    try (var in = Files.newInputStream(manifest)) {
                        p.load(in);
                    }
                    String output = p.getProperty("targetOutput");
                    if (output == null || output.isBlank()) return;
                    List<Path> segments = listValidSegments(dir);
                    if (segments.isEmpty()) return;
                    Instant created = Files.getLastModifiedTime(manifest).toInstant();
                    result.add(new RecoveryCandidate(dir, Path.of(output), segments, created));
                } catch (Exception ignored) {
                }
            });
        } catch (IOException ignored) {
        }

        result.sort(Comparator.comparing(RecoveryCandidate::createdAt));
        return result;
    }

    public Path recover(String ffmpegPath, RecoveryCandidate candidate, Consumer<String> log) throws Exception {
        Path output = uniqueRecoveredOutput(candidate.targetOutput());
        List<Path> sourceSegments = new ArrayList<>(candidate.segments());

        // A hard crash most commonly damages only the tail of the active Matroska file. Rebuild
        // that final segment proactively so missing cues/cluster tails do not poison the whole concat.
        Path last = sourceSegments.get(sourceSegments.size() - 1);
        Path repaired = candidate.sessionDir().resolve("recovered_last.mkv");
        log.accept("Recovery: rebuilding the final MKV segment before finalization...");
        if (salvageLastSegment(ffmpegPath, last, repaired, log)) {
            sourceSegments.set(sourceSegments.size() - 1, repaired);
            log.accept("Recovery: final MKV segment rebuilt successfully.");
        } else if (sourceSegments.size() > 1) {
            sourceSegments.remove(sourceSegments.size() - 1);
            log.accept("Recovery: final MKV segment is unreadable; preserving all earlier complete segments.");
        } else {
            log.accept("Recovery: final segment could not be rebuilt; trying the original data as a last resort.");
        }

        finalizeSegmentsResilient(ffmpegPath, candidate.sessionDir(), sourceSegments, output, log);
        deleteSession(candidate.sessionDir());
        return output;
    }

    public void discard(RecoveryCandidate candidate) {
        deleteSession(candidate.sessionDir());
    }

    static List<Path> listValidSegments(Path dir) throws IOException {
        List<Path> segments = new ArrayList<>();
        try (var files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().matches("part_\\d{4}\\.mkv"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            if (Files.size(p) > 1024) segments.add(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
        return segments;
    }

    /**
     * Finalizes a recording and, if the normal remux fails, attempts to salvage a truncated/corrupt
     * final Matroska segment. Earlier completed segments are never modified.
     */
    static void finalizeSegmentsResilient(
            String ffmpegPath,
            Path workingDir,
            List<Path> segments,
            Path output,
            Consumer<String> log) throws Exception {

        try {
            remuxSegments(ffmpegPath, workingDir, segments, output, log);
            return;
        } catch (Exception first) {
            log.accept("Normal finalization failed: " + first.getMessage());
            try { Files.deleteIfExists(output); } catch (IOException ignored) {}
        }

        if (segments.isEmpty()) throw new IllegalStateException("No recoverable recording segments were found.");
        Path last = segments.get(segments.size() - 1);
        Path repaired = workingDir.resolve("repaired_last.mkv");
        log.accept("Recovery fallback: attempting to salvage the final MKV segment " + last.getFileName() + "...");

        boolean repairedUsable = salvageLastSegment(ffmpegPath, last, repaired, log);
        if (repairedUsable) {
            List<Path> repairedList = new ArrayList<>(segments);
            repairedList.set(repairedList.size() - 1, repaired);
            try {
                remuxSegments(ffmpegPath, workingDir, repairedList, output, log);
                log.accept("Damaged final MKV segment was salvaged successfully.");
                return;
            } catch (Exception second) {
                log.accept("Salvaged segment still could not be finalized: " + second.getMessage());
                try { Files.deleteIfExists(output); } catch (IOException ignored) {}
            }
        }

        if (segments.size() > 1) {
            List<Path> safeSegments = new ArrayList<>(segments.subList(0, segments.size() - 1));
            log.accept("Recovery fallback: dropping only the damaged final segment and preserving " +
                    safeSegments.size() + " earlier segment(s).");
            remuxSegments(ffmpegPath, workingDir, safeSegments, output, log);
            return;
        }

        throw new IllegalStateException("The only recovery segment is too damaged to restore automatically. " +
                "The original MKV has been kept in " + workingDir);
    }

    private static boolean salvageLastSegment(
            String ffmpegPath, Path source, Path repaired, Consumer<String> log) {
        try {
            Files.deleteIfExists(repaired);
            List<String> command = List.of(
                    ffmpegPath, "-y", "-hide_banner", "-loglevel", "warning",
                    "-fflags", "+discardcorrupt", "-err_detect", "ignore_err",
                    "-i", source.toAbsolutePath().toString(),
                    "-map", "0:v:0", "-map", "0:a?", "-c", "copy",
                    "-f", "matroska", repaired.toAbsolutePath().toString());
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) log.accept("MKV salvage: " + line);
                } catch (IOException ignored) {}
            }, "ffmpeg-mkv-salvage");
            reader.setDaemon(true);
            reader.start();
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            long size = Files.isRegularFile(repaired) ? Files.size(repaired) : 0L;
            if (size > 1024) {
                if (p.exitValue() != 0) {
                    log.accept("MKV salvage returned exit " + p.exitValue() + " but produced readable data; trying it anyway.");
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            log.accept("MKV salvage failed: " + e.getMessage());
            return false;
        }
    }

    static void remuxSegments(
            String ffmpegPath,
            Path workingDir,
            List<Path> segments,
            Path output,
            Consumer<String> log) throws Exception {

        if (segments.isEmpty()) {
            throw new IllegalStateException("No recoverable recording segments were found.");
        }
        Files.createDirectories(output.toAbsolutePath().getParent());

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-hide_banner");

        if (segments.size() == 1) {
            command.add("-i");
            command.add(segments.get(0).toAbsolutePath().toString());
        } else {
            Path listFile = workingDir.resolve("concat.txt");
            StringBuilder list = new StringBuilder();
            for (Path segment : segments) {
                String normalized = segment.toAbsolutePath().toString().replace('\\', '/').replace("'", "'\\''");
                list.append("file '").append(normalized).append("'\n");
            }
            Files.writeString(listFile, list.toString(), StandardCharsets.UTF_8);
            command.add("-f"); command.add("concat");
            command.add("-safe"); command.add("0");
            command.add("-i"); command.add(listFile.toAbsolutePath().toString());
        }

        command.add("-map"); command.add("0:v:0");
        command.add("-map"); command.add("0:a?");
        command.add("-c"); command.add("copy");
        VideoFormat format = VideoFormat.fromPath(output);
        if (format.supportsFastStart()) {
            command.add("-movflags"); command.add("+faststart");
        }
        command.add(output.toAbsolutePath().toString());

        log.accept("Finalizing recoverable recording to " + format + "...");
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) log.accept(line);
            } catch (IOException ignored) {
            }
        }, "ffmpeg-recovery-output");
        reader.setDaemon(true);
        reader.start();

        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IllegalStateException("FFmpeg timed out while finalizing the recording.");
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException("FFmpeg could not finalize the recording. Exit code: " + p.exitValue());
        }
    }

    static void deleteSession(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {
        }
        Path root = dir.getParent();
        if (root != null) {
            try (var files = Files.list(root)) {
                if (files.findAny().isEmpty()) Files.deleteIfExists(root);
            } catch (IOException ignored) {
            }
        }
    }

    private Path uniqueRecoveredOutput(Path requested) {
        if (!Files.exists(requested)) return requested;
        String fileName = requested.getFileName().toString();
        VideoFormat format = VideoFormat.fromPath(requested);
        String extension = format.extension();
        String lower = fileName.toLowerCase(java.util.Locale.ROOT);
        String base = lower.endsWith(extension)
                ? fileName.substring(0, fileName.length() - extension.length())
                : fileName;
        Path parent = requested.toAbsolutePath().getParent();
        int index = 1;
        Path candidate;
        do {
            String suffix = index == 1 ? "_recovered" : "_recovered_" + index;
            candidate = parent.resolve(base + suffix + extension);
            index++;
        } while (Files.exists(candidate));
        return candidate;
    }
}
