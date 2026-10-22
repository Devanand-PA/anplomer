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
    /** Music slider value, 0..1 (the persisted percentage divided by 100). */
    private volatile float musicVolumeSetting = 0.7f;
    private volatile int musicSampleId;
    private volatile int musicStream;

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
        // Power-up pickup: a bright three-note rise, distinct from the two-note
        // coin so the player can hear "that was not a coin".
        define(SoundSink.POWERUP, arpeggio(0.30, new double[]{659.25, 880.0, 1318.5}, 0.36));
        // Store purchase: a short two-tone "ka-ching".
        define(SoundSink.PURCHASE, concat(
                synth(0.07, (t, n) -> Math.sin(6.283185 * 784 * t) * 0.9, 0.25),
                synth(0.22, (t, n) -> {
                    double f = 1046.5 + 523.25 * Math.min(1, t / 0.05);
                    return Math.sin(6.283185 * f * t) * 0.85;
                }, 0.20)));

        // looping background music, synthesised the same way as the effects
        defineMusic(musicLoop());
    }

    /**
     * Builds a soft chiptune loop: four plucked chords (C - G - Am - F) with a
     * quiet bass note under each. Encoded as a 16-bit mono WAV so SoundPool
     * can loop it seamlessly (each note's envelope has decayed to near zero by
     * the loop point, so there is no click).
     */
    private static byte[] musicLoop() {
        final double rate = SAMPLE_RATE;
        // each chord gets 2s: 8 plucked eighth-notes of 0.25s
        final double chordLen = 2.0;
        final double noteLen = 0.25;
        double[][] chords = {
                {261.63, 329.63, 392.00},   // C
                {246.94, 293.66, 392.00},   // G
                {220.00, 261.63, 329.63},   // A minor
                {220.00, 261.63, 349.23},   // F (2nd inversion)
        };
        double[] bass = {130.81, 98.00, 110.00, 87.31};
        // arpeggio order within a chord: root, third, fifth, third, ...
        int[] pattern = {0, 1, 2, 1, 0, 1, 2, 1};
        int n = (int) (rate * chordLen * chords.length);
        double[] raw = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / rate;
            int chordIdx = (int) (t / chordLen) % chords.length;
            double local = t - (int) (t / chordLen) * chordLen;
            int noteIdx = Math.min(pattern.length - 1, (int) (local / noteLen));
            double noteT = local - noteIdx * noteLen;
            // plucked melody note: fast attack, exponential decay
            double env = Math.min(1.0, noteT / 0.006) * Math.exp(-4.5 * noteT / noteLen);
            double f = chords[chordIdx][pattern[noteIdx]];
            double v = Math.sin(6.283185 * f * noteT) * 0.16
                    + Math.sin(6.283185 * 2 * f * noteT) * 0.05;
            // sustained soft bass under the whole chord
            double benv = Math.min(1.0, local / 0.05)
                    * Math.pow(Math.max(0.0, 1.0 - local / chordLen), 0.6);
            raw[i] = v * env + Math.sin(6.283185 * bass[chordIdx] * t) * 0.14 * benv;
        }
        return toWav(raw);
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

    /** Loads the music loop; playback starts once the sample is decoded. */
    private void defineMusic(byte[] wav) throws IOException {
        File f = new File(cacheDir, "music-loop.wav");
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(wav);
        }
        final SoundPool p = pool;
        musicSampleId = p.load(f.getAbsolutePath(), 1);
        p.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (sampleId == musicSampleId && status == 0) {
                startMusic();
            }
        });
    }

    private void startMusic() {
        SoundPool p = pool;
        if (p == null || musicStream != 0) {
            return;
        }
        try {
            // loop forever at priority 0 so any effect can steal the stream
            musicStream = p.play(musicSampleId, musicVolume(), musicVolume(), 0, -1, 1f);
        } catch (Throwable t) {
            musicStream = 0;   // released concurrently — stay silent
        }
    }

    /** Effective music volume: the slider, gated by the sound on/off switch. */
    private float musicVolume() {
        return enabled ? musicVolumeSetting : 0f;
    }

    private void applyMusicVolume() {
        SoundPool p = pool;
        int s = musicStream;
        if (p == null || s == 0) {
            return;
        }
        try {
            p.setVolume(s, musicVolume(), musicVolume());
        } catch (Throwable ignored) {
            // released concurrently with the volume change — safe to ignore
        }
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
        applyMusicVolume();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setMusicVolume(float volume) {
        float v = Math.max(0f, Math.min(1f, volume));
        musicVolumeSetting = v;
        if (pool != null && musicStream == 0 && musicSampleId != 0) {
            startMusic();   // the sample may still be decoding, or was stolen
        }
        applyMusicVolume();
    }

    @Override
    public synchronized void suspendAudio() {
        SoundPool p = pool;
        if (p != null) {
            try {
                p.autoPause();   // pauses the music loop and any playing effect
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public synchronized void resumeAudio() {
        SoundPool p = pool;
        if (p != null) {
            try {
                p.autoResume();
            } catch (Throwable ignored) {
            }
        }
    }

    public synchronized void release() {
        if (pool != null) {
            pool.release();
            pool = null;
        }
        ids.clear();
        musicStream = 0;
        musicSampleId = 0;
    }
}
