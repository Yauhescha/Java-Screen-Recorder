package com.example.screenrecorder;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Single-owner webcam capture service.
 *
 * One FFmpeg process owns the DirectShow camera and produces 640x360 BGR24 frames.
 * The same frames are used for the on-screen live preview and, through a local TCP
 * bridge, by the recording FFmpeg process. This avoids opening the same webcam twice,
 * which many Windows camera drivers do not support.
 */
final class WebcamPreviewService implements AutoCloseable {
    static final int WIDTH = 640;
    static final int HEIGHT = 360;
    static final int FPS = 15;
    private static final int FRAME_BYTES = WIDTH * HEIGHT * 3;

    private volatile Process process;
    private volatile Thread frameThread;
    private volatile Thread errorThread;
    private volatile Thread healthThread;
    private volatile Thread bridgeThread;
    private volatile ServerSocket bridgeServer;
    private volatile String activeDeviceName;
    private volatile boolean bridgeClientConnected;
    private volatile Socket bridgeClient;
    private volatile Consumer<BufferedImage> frameConsumer;
    private volatile Consumer<String> logConsumer;
    private volatile Consumer<String> failureConsumer;
    private volatile CountDownLatch firstFrameLatch = new CountDownLatch(1);
    private final ArrayBlockingQueue<byte[]> bridgeFrames = new ArrayBlockingQueue<>(3);

    synchronized void start(String ffmpegPath, WebcamDevice device,
                            Consumer<BufferedImage> onFrame, Consumer<String> log,
                            Consumer<String> onFailure) {
        if (device == null) return;

        frameConsumer = onFrame;
        logConsumer = log;
        failureConsumer = onFailure;

        if (isRunningFor(device)) {
            return;
        }

        stop();
        frameConsumer = onFrame;
        logConsumer = log;
        failureConsumer = onFailure;
        activeDeviceName = device.name();
        firstFrameLatch = new CountDownLatch(1);
        bridgeFrames.clear();

        try {
            startBridgeServer();

            Process p = new ProcessBuilder(
                    ffmpegPath,
                    "-hide_banner", "-loglevel", "warning",
                    "-thread_queue_size", "128",
                    "-f", "dshow",
                    "-i", "video=" + device.name(),
                    "-vf", "scale=" + WIDTH + ":" + HEIGHT + ":force_original_aspect_ratio=increase," +
                            "crop=" + WIDTH + ":" + HEIGHT + ",fps=" + FPS,
                    "-pix_fmt", "bgr24",
                    "-f", "rawvideo",
                    "pipe:1")
                    .redirectErrorStream(false)
                    .start();
            process = p;

            AtomicBoolean gotFrame = new AtomicBoolean(false);
            AtomicLong lastFrameNanos = new AtomicLong(System.nanoTime());
            Deque<String> recentErrors = new ArrayDeque<>();

            errorThread = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (!line.isBlank()) {
                            synchronized (recentErrors) {
                                recentErrors.addLast(line.trim());
                                while (recentErrors.size() > 6) recentErrors.removeFirst();
                            }
                            Consumer<String> logger = logConsumer;
                            if (logger != null) logger.accept("Webcam source: " + line);
                        }
                    }
                } catch (Exception ignored) {
                }
            }, "webcam-source-log");
            errorThread.setDaemon(true);
            errorThread.start();

            frameThread = new Thread(() -> readFrames(p, gotFrame, lastFrameNanos), "webcam-source-frames");
            frameThread.setDaemon(true);
            frameThread.start();

            healthThread = new Thread(() -> {
                AtomicBoolean failureReported = new AtomicBoolean(false);
                try {
                    if (!firstFrameLatch.await(5, TimeUnit.SECONDS) && p == process) {
                        String detail;
                        synchronized (recentErrors) { detail = String.join(" | ", recentErrors); }
                        if (detail.isBlank()) detail = "No camera frames were received within 5 seconds.";
                        reportFailure(detail + " Close Windows Camera/Teams/Discord or other apps that may be using the camera, then retry.", failureReported);
                        p.destroy();
                    }

                    while (p == process && p.isAlive()) {
                        if (gotFrame.get() && System.nanoTime() - lastFrameNanos.get() > TimeUnit.SECONDS.toNanos(3)) {
                            reportFailure("No webcam frames were received for 3 seconds. The camera was likely disconnected.", failureReported);
                            disconnectBridgeClient();
                            p.destroy();
                            break;
                        }
                        Thread.sleep(500);
                    }
                    int exit = p.waitFor();
                    if (p != process) return; // normal stop() cleared ownership before destroying FFmpeg
                    disconnectBridgeClient(); // signal EOF to the recording FFmpeg so video can continue without webcam
                    String detail;
                    synchronized (recentErrors) { detail = String.join(" | ", recentErrors); }
                    String message = gotFrame.get()
                            ? "Webcam disconnected or stopped during recording (source exit " + exit + ")."
                            : "FFmpeg closed the camera before a frame was received (exit " + exit + ").";
                    if (!detail.isBlank()) message += " " + detail;
                    reportFailure(message, failureReported);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, "webcam-source-health");
            healthThread.setDaemon(true);
            healthThread.start();
        } catch (Exception e) {
            String message = "Could not start webcam: " + e.getMessage();
            Consumer<String> logger = logConsumer;
            if (logger != null) logger.accept(message);
            Consumer<String> failure = failureConsumer;
            if (failure != null) failure.accept(message);
            stop();
        }
    }

    synchronized boolean isRunningFor(WebcamDevice device) {
        Process p = process;
        return device != null && p != null && p.isAlive() && device.name().equals(activeDeviceName);
    }

    String bridgeUrl() {
        ServerSocket server = bridgeServer;
        if (server == null || server.isClosed()) return null;
        return "tcp://127.0.0.1:" + server.getLocalPort();
    }

    boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
        return firstFrameLatch.await(timeout, unit);
    }

    private void startBridgeServer() throws Exception {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        bridgeServer = server;
        bridgeThread = new Thread(() -> runBridge(server), "webcam-recording-bridge");
        bridgeThread.setDaemon(true);
        bridgeThread.start();
    }

    private void runBridge(ServerSocket server) {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                bridgeClient = client;
                bridgeClientConnected = true;
                client.setTcpNoDelay(true);
                bridgeFrames.clear();
                Consumer<String> logger = logConsumer;
                if (logger != null) logger.accept("Webcam bridge connected for recording.");
                try (OutputStream out = client.getOutputStream()) {
                    while (!client.isClosed() && !server.isClosed()) {
                        byte[] frame = bridgeFrames.take();
                        out.write(frame);
                        out.flush();
                    }
                } finally {
                    if (bridgeClient == client) bridgeClient = null;
                    bridgeClientConnected = false;
                    bridgeFrames.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (!server.isClosed()) {
                    Consumer<String> logger = logConsumer;
                    if (logger != null) logger.accept("Webcam bridge client closed: " + e.getMessage());
                }
            }
        }
    }

    private void readFrames(Process p, AtomicBoolean gotFrame, AtomicLong lastFrameNanos) {
        byte[] frame = new byte[FRAME_BYTES];
        try (InputStream in = p.getInputStream()) {
            while (p.isAlive()) {
                int offset = 0;
                while (offset < frame.length) {
                    int n = in.read(frame, offset, frame.length - offset);
                    if (n < 0) return;
                    offset += n;
                }

                gotFrame.set(true);
                lastFrameNanos.set(System.nanoTime());
                firstFrameLatch.countDown();

                // Only clone a raw frame when a recorder is actually connected/consuming.
                if (bridgeClientConnected && bridgeServer != null && !bridgeServer.isClosed()) {
                    byte[] copy = frame.clone();
                    if (!bridgeFrames.offer(copy)) {
                        bridgeFrames.poll();
                        bridgeFrames.offer(copy);
                    }
                }

                Consumer<BufferedImage> consumer = frameConsumer;
                if (consumer != null) {
                    BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
                    byte[] dst = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                    System.arraycopy(frame, 0, dst, 0, frame.length);
                    consumer.accept(image);
                }
            }
        } catch (Exception e) {
            Consumer<String> logger = logConsumer;
            if (p == process && p.isAlive() && logger != null) {
                logger.accept("Webcam source stopped: " + e.getMessage());
            }
        }
    }


    private void reportFailure(String message, AtomicBoolean reported) {
        if (!reported.compareAndSet(false, true)) return;
        Consumer<String> logger = logConsumer;
        if (logger != null) logger.accept("Webcam unavailable: " + message);
        Consumer<String> failure = failureConsumer;
        if (failure != null) failure.accept(message);
    }

    private void disconnectBridgeClient() {
        Socket client = bridgeClient;
        bridgeClient = null;
        bridgeClientConnected = false;
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
        }
        bridgeFrames.clear();
    }

    synchronized void stop() {
        ServerSocket server = bridgeServer;
        bridgeServer = null;
        if (server != null) {
            try { server.close(); } catch (Exception ignored) {}
        }
        disconnectBridgeClient();

        Process p = process;
        process = null;
        if (p != null) {
            try { p.getInputStream().close(); } catch (Exception ignored) {}
            try { p.getErrorStream().close(); } catch (Exception ignored) {}
            p.destroy();
            try {
                if (!p.waitFor(600, TimeUnit.MILLISECONDS)) p.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
        }

        interrupt(frameThread);
        interrupt(errorThread);
        interrupt(healthThread);
        interrupt(bridgeThread);
        frameThread = null;
        errorThread = null;
        healthThread = null;
        bridgeThread = null;
        activeDeviceName = null;
        firstFrameLatch = new CountDownLatch(1);
    }

    private static void interrupt(Thread thread) {
        if (thread != null) thread.interrupt();
    }

    @Override public void close() { stop(); }
}
