package com.example.screenrecorder;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Windows WASAPI loopback capture for the current default playback endpoint.
 * Produces signed 16-bit little-endian stereo PCM at 48 kHz.
 *
 * Important synchronization detail: capture starts before FFmpeg/gdigrab and
 * PCM is buffered. When FFmpeg reports the wall-clock timestamp of its first
 * video frame, the sender trims only the audio pre-roll that precedes that
 * frame. This avoids both "audio leads video" and "the first second of audio is
 * missing" problems.
 */
final class WasapiLoopbackPcmSource extends TcpPcmSource {
    private static final int RATE = 48_000;
    private static final int CHANNELS = 2;
    private static final int BYTES_PER_FRAME = 4;

    private static final int COINIT_MULTITHREADED = 0;
    private static final int CLSCTX_INPROC_SERVER = 0x1;
    private static final int E_RENDER = 0;
    private static final int E_MULTIMEDIA = 1;
    private static final int AUDCLNT_SHAREMODE_SHARED = 0;
    private static final int AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000;
    private static final int AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM = 0x80000000;
    private static final int AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY = 0x08000000;
    private static final int AUDCLNT_BUFFERFLAGS_SILENT = 0x00000002;

    private static final Guid CLSID_MMDEVICE_ENUMERATOR = Guid.of("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private static final Guid IID_IMMDEVICE_ENUMERATOR = Guid.of("A95664D2-9614-4F35-A746-DE8DB63617E6");
    private static final Guid IID_IAUDIO_CLIENT = Guid.of("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2");
    private static final Guid IID_IAUDIO_CAPTURE_CLIENT = Guid.of("C8ADBD64-E71E-48A0-A4DE-185C395CD317");
    private final PcmLevelTracker levelTracker;

    WasapiLoopbackPcmSource(AudioLevelListener levels) {
        AudioLevelListener safe = levels != null ? levels : AudioLevelListener.NONE;
        this.levelTracker = new PcmLevelTracker(safe::onSystemLevel);
    }

    @Override public int sampleRate() { return RATE; }
    @Override public int channels() { return CHANNELS; }

    @Override
    public void start(CaptureTimeline timeline, Consumer<String> log) {
        running = true;
        pcmBuffer = new TimedPcmBuffer(RATE, CHANNELS);
        startSender(timeline, log, "System audio");

        captureWorker = new Thread(() -> capture(log), "wasapi-loopback-capture");
        captureWorker.setDaemon(true);
        captureWorker.start();
    }

    private void capture(Consumer<String> log) {
        ComObject enumerator = null;
        ComObject device = null;
        ComObject audioClient = null;
        ComObject captureClient = null;
        boolean comInitialized = false;
        boolean clientStarted = false;

        try {
            int initHr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, COINIT_MULTITHREADED);
            if (failed(initHr) && initHr != 0x80010106) {
                throw new IllegalStateException(hr("CoInitializeEx", initHr));
            }
            comInitialized = !failed(initHr);

            PointerByReference pp = new PointerByReference();
            check("CoCreateInstance(MMDeviceEnumerator)", Ole32.INSTANCE.CoCreateInstance(
                    CLSID_MMDEVICE_ENUMERATOR.getPointer(), Pointer.NULL, CLSCTX_INPROC_SERVER,
                    IID_IMMDEVICE_ENUMERATOR.getPointer(), pp));
            enumerator = new ComObject(pp.getValue());

            PointerByReference deviceRef = new PointerByReference();
            check("IMMDeviceEnumerator.GetDefaultAudioEndpoint",
                    enumerator.invokeInt(4, E_RENDER, E_MULTIMEDIA, deviceRef));
            device = new ComObject(deviceRef.getValue());

            PointerByReference clientRef = new PointerByReference();
            check("IMMDevice.Activate(IAudioClient)",
                    device.invokeInt(3, IID_IAUDIO_CLIENT.getPointer(), CLSCTX_INPROC_SERVER,
                            Pointer.NULL, clientRef));
            audioClient = new ComObject(clientRef.getValue());

            Memory format = pcm16Stereo48k();
            int flags = AUDCLNT_STREAMFLAGS_LOOPBACK
                    | AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM
                    | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY;

            // 100 ms engine buffer. This is not an output delay: samples are copied
            // into our own pre-roll queue immediately as WASAPI makes them available.
            check("IAudioClient.Initialize",
                    audioClient.invokeInt(3, AUDCLNT_SHAREMODE_SHARED, flags,
                            1_000_000L, 0L, format, Pointer.NULL));

            PointerByReference captureRef = new PointerByReference();
            check("IAudioClient.GetService(IAudioCaptureClient)",
                    audioClient.invokeInt(14, IID_IAUDIO_CAPTURE_CLIENT.getPointer(), captureRef));
            captureClient = new ComObject(captureRef.getValue());

            check("IAudioClient.Start", audioClient.invokeInt(10));
            clientStarted = true;

            long captureStartEpochNs = CaptureTimeline.epochNanosNow();
            pcmBuffer.markCaptureStarted(captureStartEpochNs);
            log.accept("System audio armed before video start: Windows default playback via WASAPI loopback (48 kHz stereo)");

            byte[] silence10ms = new byte[RATE / 100 * BYTES_PER_FRAME];
            long lastWriteNs = System.nanoTime();

            while (running) {
                IntByReference nextFrames = new IntByReference();
                int sizeHr = captureClient.invokeInt(5, nextFrames);
                if (failed(sizeHr)) {
                    throw new IllegalStateException(hr("IAudioCaptureClient.GetNextPacketSize", sizeHr));
                }

                boolean wrote = false;
                while (nextFrames.getValue() > 0 && running) {
                    PointerByReference dataRef = new PointerByReference();
                    IntByReference framesRef = new IntByReference();
                    IntByReference flagsRef = new IntByReference();
                    LongByReference devicePos = new LongByReference();
                    LongByReference qpcPos = new LongByReference();

                    check("IAudioCaptureClient.GetBuffer",
                            captureClient.invokeInt(3, dataRef, framesRef, flagsRef, devicePos, qpcPos));

                    int frames = framesRef.getValue();
                    int bytes = frames * BYTES_PER_FRAME;
                    if (frames > 0) {
                        if ((flagsRef.getValue() & AUDCLNT_BUFFERFLAGS_SILENT) != 0
                                || dataRef.getValue() == null) {
                            pcmBuffer.appendSilenceFrames(frames);
                            levelTracker.silence();
                        } else {
                            byte[] pcm = dataRef.getValue().getByteArray(0, bytes);
                            levelTracker.accept(pcm, bytes);
                            pcmBuffer.append(pcm, bytes);
                        }
                    }

                    check("IAudioCaptureClient.ReleaseBuffer", captureClient.invokeInt(4, frames));
                    wrote = true;
                    lastWriteNs = System.nanoTime();

                    check("IAudioCaptureClient.GetNextPacketSize", captureClient.invokeInt(5, nextFrames));
                }

                // Keep the exact v2 behavior that already worked on the user's machine:
                // some loopback endpoints stop emitting packets while completely silent.
                if (!wrote && System.nanoTime() - lastWriteNs >= 10_000_000L) {
                    pcmBuffer.append(silence10ms, silence10ms.length);
                    levelTracker.silence();
                    lastWriteNs = System.nanoTime();
                }

                Thread.sleep(5);
            }
        } catch (Exception e) {
            pcmBuffer.markStartupFailure(e);
            if (running) log.accept("System audio capture error: " + e.getMessage());
        } finally {
            running = false;
            if (clientStarted && audioClient != null) {
                try { audioClient.invokeInt(11); } catch (Exception ignored) {}
            }
            release(captureClient);
            release(audioClient);
            release(device);
            release(enumerator);
            if (comInitialized) Ole32.INSTANCE.CoUninitialize();
        }
    }

    @Override
    public void stop() {
        running = false;
        closeServer();
        interruptWorkers();
        levelTracker.reset();
    }

    private static void release(ComObject o) {
        if (o != null) {
            try { o.invokeInt(2); } catch (Exception ignored) {}
        }
    }

    private static boolean failed(int hr) { return hr < 0; }

    private static void check(String op, int hr) {
        if (failed(hr)) throw new IllegalStateException(hr(op, hr));
    }

    private static String hr(String op, int hr) {
        return op + " failed, HRESULT=0x" + String.format("%08X", hr);
    }

    private interface Ole32 extends StdCallLibrary {
        Ole32 INSTANCE = Native.load("ole32", Ole32.class);
        int CoInitializeEx(Pointer pvReserved, int dwCoInit);
        void CoUninitialize();
        int CoCreateInstance(Pointer rclsid, Pointer pUnkOuter, int dwClsContext,
                             Pointer riid, PointerByReference ppv);
    }

    private static final class ComObject {
        private final Pointer pointer;

        private ComObject(Pointer pointer) {
            if (pointer == null) throw new IllegalArgumentException("Null COM pointer");
            this.pointer = pointer;
        }

        int invokeInt(int vtableIndex, Object... rest) {
            Pointer vtable = pointer.getPointer(0);
            Pointer fnPtr = vtable.getPointer((long) vtableIndex * Native.POINTER_SIZE);
            Function fn = Function.getFunction(fnPtr, Function.ALT_CONVENTION);
            Object[] args = new Object[rest.length + 1];
            args[0] = pointer;
            System.arraycopy(rest, 0, args, 1, rest.length);
            return fn.invokeInt(args);
        }
    }

    @Structure.FieldOrder({"data1", "data2", "data3", "data4"})
    public static class Guid extends Structure {
        public int data1;
        public short data2;
        public short data3;
        public byte[] data4 = new byte[8];

        static Guid of(String text) {
            UUID u = UUID.fromString(text);
            long msb = u.getMostSignificantBits();
            long lsb = u.getLeastSignificantBits();
            Guid g = new Guid();
            g.data1 = (int) (msb >>> 32);
            g.data2 = (short) (msb >>> 16);
            g.data3 = (short) msb;
            for (int i = 0; i < 8; i++) g.data4[i] = (byte) (lsb >>> (56 - i * 8));
            g.write();
            return g;
        }
    }

    private static Memory pcm16Stereo48k() {
        // WAVEFORMATEX is an 18-byte packed multimedia structure on Windows.
        Memory m = new Memory(18);
        m.clear();
        m.setShort(0, (short) 1); // WAVE_FORMAT_PCM
        m.setShort(2, (short) CHANNELS);
        m.setInt(4, RATE);
        m.setInt(8, RATE * BYTES_PER_FRAME);
        m.setShort(12, (short) BYTES_PER_FRAME);
        m.setShort(14, (short) 16);
        m.setShort(16, (short) 0);
        return m;
    }
}
