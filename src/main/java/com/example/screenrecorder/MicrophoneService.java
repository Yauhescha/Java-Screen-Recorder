package com.example.screenrecorder;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;

public final class MicrophoneService {
    public static final String DEFAULT_ID = "__DEFAULT__";

    public List<MicrophoneDevice> list() {
        List<MicrophoneDevice> result = new ArrayList<>();
        result.add(new MicrophoneDevice(DEFAULT_ID, "Default Windows microphone"));

        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            boolean hasTargetDataLine = false;
            for (Line.Info lineInfo : mixer.getTargetLineInfo()) {
                if (TargetDataLine.class.isAssignableFrom(lineInfo.getLineClass())) {
                    hasTargetDataLine = true;
                    break;
                }
            }
            if (hasTargetDataLine) {
                String id = info.getName() + "\u0000" + info.getVendor() + "\u0000" + info.getVersion();
                String label = info.getName();
                if (info.getDescription() != null && !info.getDescription().isBlank()) {
                    label += " — " + info.getDescription();
                }
                result.add(new MicrophoneDevice(id, label));
            }
        }
        return result;
    }

    public Mixer findMixer(MicrophoneDevice device) {
        if (device == null || DEFAULT_ID.equals(device.id())) return null;
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            String id = info.getName() + "\u0000" + info.getVendor() + "\u0000" + info.getVersion();
            if (id.equals(device.id())) return AudioSystem.getMixer(info);
        }
        throw new IllegalArgumentException("Microphone is no longer available: " + device.label());
    }
}
