package com.example.screenrecorder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

abstract class TcpPcmSource implements AudioPcmSource {
    protected volatile boolean running;
    protected ServerSocket serverSocket;
    protected Thread captureWorker;
    protected Thread senderWorker;
    protected TimedPcmBuffer pcmBuffer;

    @Override
    public void prepare() throws Exception {
        if (serverSocket == null || serverSocket.isClosed()) {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        }
    }

    protected final void startSender(CaptureTimeline timeline, Consumer<String> log, String sourceName) {
        if (pcmBuffer == null) {
            pcmBuffer = new TimedPcmBuffer(sampleRate(), channels());
        }
        senderWorker = new Thread(() -> {
            try (Socket socket = serverSocket.accept();
                 BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024)) {

                long videoStart = timeline.awaitVideoStartNanos(5, TimeUnit.SECONDS);
                if (!timeline.hasExactVideoStart()) {
                    log.accept(sourceName + ": FFmpeg video timestamp was not reported; using process-launch fallback");
                }

                pcmBuffer.streamAlignedTo(videoStart, out, () -> running, log, sourceName);
            } catch (Exception e) {
                if (running) log.accept(sourceName + " sender error: " + e.getMessage());
            }
        }, sourceName.toLowerCase().replace(' ', '-') + "-sender");
        senderWorker.setDaemon(true);
        senderWorker.start();
    }

    @Override
    public void awaitReady(long timeout, TimeUnit unit) throws Exception {
        pcmBuffer.awaitCaptureStarted(timeout, unit);
    }

    @Override
    public String inputUrl() {
        if (serverSocket == null) throw new IllegalStateException("Audio source is not prepared");
        return "tcp://127.0.0.1:" + serverSocket.getLocalPort();
    }

    protected void closeServer() {
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
    }

    protected final void interruptWorkers() {
        if (captureWorker != null) captureWorker.interrupt();
        if (senderWorker != null) senderWorker.interrupt();
    }
}
