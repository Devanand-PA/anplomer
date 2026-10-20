package com.example.anplomer;

/**
 * Persistence boundary, kept as an interface so the pure-Java game never
 * touches {@code android.content.SharedPreferences}. The Android shell
 * (GameView) supplies the real implementation; tests supply memory or files.
 */
public interface SaveStore {

    /** @return the previously stored blob, or null when nothing is saved. */
    String load();

    /** Persist the blob (synchronously; saves are rare). */
    void store(String blob);
}
