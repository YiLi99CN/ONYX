package EY.ONYX.PE.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pure-Java Real-Time PCM Audio Synthesizer for EY.ONYX.PE:
 * Generates crisp, clearly audible, non-harsh chimes when toggling modules or opening/closing panels.
 */
public class OnyxSoftSoundEngine {

    private static final int SAMPLE_RATE = 44100;
    private static final ExecutorService SOUND_POOL = Executors.newSingleThreadExecutor();
    private static volatile boolean soundEnabled = true;

    public static void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
    }

    public static boolean isSoundEnabled() {
        return soundEnabled;
    }

    public static void playToggleSound(final boolean enabled) {
        if (!soundEnabled) return;
        SOUND_POOL.execute(() -> {
            try {
                if (enabled) {
                    playSoftTwoToneChime(440.00f, 659.25f, 130, 0.82f);
                } else {
                    playSoftTwoToneChime(523.25f, 349.23f, 125, 0.74f);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static void playSoftTwoToneChime(float freq1, float freq2, int durationMs, float volume) {
        int totalSamples = (SAMPLE_RATE * durationMs) / 1000;
        short[] pcm = new short[totalSamples];
        int splitSample = (int) (totalSamples * 0.40f);
        int attackSamples = Math.max(1, (SAMPLE_RATE * 6) / 1000);

        double phase = 0.0;
        for (int i = 0; i < totalSamples; i++) {
            float t = (float) i / totalSamples;
            float freq;
            if (i < splitSample) {
                freq = freq1;
            } else {
                float blend = Math.min(1.0f, (i - splitSample) / (float) Math.max(1, totalSamples * 0.10f));
                freq = freq1 + (freq2 - freq1) * blend;
            }

            phase += (2.0 * Math.PI * freq) / SAMPLE_RATE;

            float env;
            if (i < attackSamples) {
                env = 0.5f * (1.0f - (float) Math.cos(Math.PI * i / attackSamples));
            } else {
                float decayProgress = (float) (i - attackSamples) / Math.max(1, totalSamples - attackSamples);
                env = (float) Math.exp(-3.1f * decayProgress) * (1.0f - decayProgress * 0.15f);
            }

            double fundamental = Math.sin(phase);
            double subWarmth = 0.30 * Math.sin(phase * 0.5);
            double crispHarmonic = 0.16 * Math.sin(phase * 2.0) * (1.0f - t * 0.6f);
            double raw = (fundamental + subWarmth + crispHarmonic) / 1.46;

            float sampleVal = (float) (raw * env * volume);
            int clamped = Math.round(sampleVal * 32767.0f);
            if (clamped > 32767) clamped = 32767;
            if (clamped < -32768) clamped = -32768;
            pcm[i] = (short) clamped;
        }

        AudioTrack track = null;
        try {
            int bufferBytes = totalSamples * 2;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(bufferBytes)
                        .build();
            } else {
                track = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferBytes,
                        AudioTrack.MODE_STATIC
                );
            }
            track.write(pcm, 0, totalSamples);
            track.play();
            Thread.sleep(durationMs + 25L);
        } catch (Throwable ignored) {
        } finally {
            if (track != null) {
                try {
                    track.stop();
                } catch (Throwable ignored) {}
                try {
                    track.release();
                } catch (Throwable ignored) {}
            }
        }
    }
}
