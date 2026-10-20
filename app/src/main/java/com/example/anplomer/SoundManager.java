package com.example.anplomer;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;
import android.util.SparseIntArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Synthesises every sound effect as a tiny WAV file at startup and plays them
 * through {@link SoundPool} — no audio assets are shipped with the APK.
 * Any failure degrades to silent mode instead of crashing the game.
 */
public final class SoundManager implements SoundSink {

    private static final String TAG = "AnplomerSound";
    private static final int SAMPLE_RATE = 44100;

    private volatile SoundPool pool;
    private final SparseIntArray ids = new SparseIntArray();
    private volatile boolean enabled = true;

    public SoundManager(Context context) {
        try {
            build(context);
        } catch (Throwable t) {
            Log.w(TAG, "audio unavailable: " + t);
            release();
        }
    }

    private void build(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), "sfx");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        cacheDir = dir;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();

        define(SoundSink.JUMP, synth(0.16, (t, n) -> {
            double f = 520 + 520 * t / n;
            return Math.sin(6.283185 * f * t) * 0.7 + Math.sin(12.566 * f * t) * 0.15;
        }, 0.30));
        define(SoundSink.COIN, concat(
                synth(0.07, (t, n) -> Math.sin(6.283185 * 988 * t) * 0.9, 0.34),
                synth(0.14, (t, n) -> Math.sin(6.283185 * 1319 * t) * 0.9, 0.34)));
        define(SoundSink.STOMP, synth(0.20, (t, n) -> {
            double f = 420 - 300 * (t / n);
            double body = Math.sin(6.283185 * f * t);
            double noise = (Math.random() * 2 - 1) * 0.6;
            return body * 0.7 + noise * (1 - t / n);
        }, 0.42));
        define(SoundSink.DIE, synth(0.55, (t, n) -> {
            double f = 700 * Math.pow(0.16, t / n);
            double saw = 2 * ((f * t) % 1.0) - 1;
            double wobble = 1 + 0.04 * Math.sin(6.283185 * 22 * t);
            return saw * wobble;
        }, 0.34));
        define(SoundSink.HURT, synth(0.4, (t, n) -> {
            double f = 520 * Math.pow(0.25, t / n);
            return Math.sin(6.283185 * f * t);
        }, 0.34));
        define(SoundSink.CLEAR, arpeggio(0.34, new double[]{523.25, 659.25, 783.99, 1046.5}, 0.34));
        define(SoundSink.WIN, arpeggio(0.44, new double[]{523.25, 659.25, 783.99, 1046.5, 1318.5, 1568.0}, 0.34));
        define(SoundSink.GAME_OVER, arpeggio(0.9, new double[]{392.0, 349.23, 293.66, 196.0}, 0.34));
        define(SoundSink.SELECT, synth(0.07, (t, n) -> Math.sin(6.283185 * 760 * t) * 0.9, 0.30));
        define(SoundSink.PAUSE, synth(0.09, (t, n) -> Math.sin(6.283185 * 430 * t) * 0.9, 0.30));
        define(SoundSink.CHECKPOINT, synth(0.32, (t, n) -> {
            double f = 660 + 330 * Math.min(1, t / 0.12);
            return Math.sin(6.283185 * f * t) * 0.9;
        }, 0.34));
    }

    private interface Voice {
        double sample(double t, double total);
    }

    private void define(String name, byte[] wav) throws IOException {
        File f = new File(cacheDir, name + ".wav");
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(wav);
        }
        int id = pool.load(f.getAbsolutePath(), 1);
        ids.put(name.hashCode(), id);
    }

    private File cacheDir;

    // ---- tiny synth ---------------------------------------------------

    private static byte[] synth(double seconds, Voice voice, double attack) {
        int n = (int) (SAMPLE_RATE * seconds);
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double env = 1.0;
            double a = attack * seconds;
            if (t < a) {
                env = t / a;
            } else {
                double k = (t - a) / Math.max(1e-6, seconds - a);
                env = Math.pow(1.0 - k, 1.7);
            }
            double v = voice.sample(t, seconds);
            // gentle low-pass to take the edge off the digital waveform
            if (i > 0) {
                v = raw[i - 1] * 0.35 + v * 0.65;
            }
            raw[i] = v * env;
        }
        return toWav(raw);
    }

    private static byte[] arpeggio(double seconds, double[] notes, double gain) {
        int n = (int) (SAMPLE_RATE * seconds);
        double[] raw = new double[n];
        double noteDur = seconds / notes.length;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            int idx = Math.min(notes.length - 1, (int) (t / noteDur));
            double local = t - idx * noteDur;
            double f = notes[idx];
            double env = Math.pow(1.0 - Math.min(1, local / noteDur), 1.4);
            double v = (Math.sin(6.283185 * f * local) * 0.75
                    + Math.sin(6.283185 * 2 * f * local) * 0.2) * env;
            raw[i] = v * gain;
        }
        return toWav(raw);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] toWav(double[] samples) {
        int dataLen = samples.length * 2;
        byte[] out = new byte[44 + dataLen];
        int p = 0;
        p = writeAscii(out, p, "RIFF");
        p = writeIntLE(out, p, 36 + dataLen);
        p = writeAscii(out, p, "WAVE");
        p = writeAscii(out, p, "fmt ");
        p = writeIntLE(out, p, 16);
        p = writeShortLE(out, p, 1);             // PCM
        p = writeShortLE(out, p, 1);             // mono
        p = writeIntLE(out, p, SAMPLE_RATE);
        p = writeIntLE(out, p, SAMPLE_RATE * 2); // byte rate
        p = writeShortLE(out, p, 2);             // block align
        p = writeShortLE(out, p, 16);            // bits
        p = writeAscii(out, p, "data");
        p = writeIntLE(out, p, dataLen);
        for (double s : samples) {
            double v = Math.max(-1, Math.min(1, s));
            int iv = (int) (v * 32000);
            out[p++] = (byte) (iv & 0xFF);
            out[p++] = (byte) ((iv >> 8) & 0xFF);
        }
        return out;
    }

    private static int writeAscii(byte[] b, int p, String s) {
        for (int i = 0; i < s.length(); i++) {
            b[p++] = (byte) s.charAt(i);
        }
        return p;
    }

    private static int writeIntLE(byte[] b, int p, int v) {
        b[p++] = (byte) (v & 0xFF);
        b[p++] = (byte) ((v >> 8) & 0xFF);
        b[p++] = (byte) ((v >> 16) & 0xFF);
        b[p++] = (byte) ((v >> 24) & 0xFF);
        return p;
    }

    private static int writeShortLE(byte[] b, int p, int v) {
        b[p++] = (byte) (v & 0xFF);
        b[p++] = (byte) ((v >> 8) & 0xFF);
        return p;
    }

    // ---- SoundSink ----------------------------------------------------

    @Override
    public void play(String name) {
        SoundPool p = pool;              // read once: release() may null it
        if (!enabled || p == null) {
            return;
        }
        int id = ids.get(name.hashCode(), 0);
        if (id != 0) {
            try {
                p.play(id, 0.75f, 0.75f, 1, 0, 1f);
            } catch (Throwable t) {
                // released concurrently with playback - stay silent
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public synchronized void release() {
        if (pool != null) {
            pool.release();
            pool = null;
        }
        ids.clear();
    }
}
