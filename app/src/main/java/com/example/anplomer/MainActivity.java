package com.example.anplomer;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Fullscreen landscape shell hosting the {@link GameView}.
 */
public final class MainActivity extends Activity {

    private GameView gameView;
    private SoundManager sound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        sound = new SoundManager(this);
        gameView = new GameView(this, sound);
        setContentView(gameView);
        hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Critical: clears GameView.hostPaused. Without this the first
        // backgrounding froze the game permanently — the loop kept drawing a
        // stale frame and never called update() again.
        if (gameView != null) {
            gameView.onHostResume();
        }
        hideSystemUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            // Freeze the simulation and drop held input while not visible. The
            // PAUSED overlay state is applied on the loop thread
            // (pendingForcePause in GameView), so Game is only ever mutated
            // from GameView.run.
            gameView.onHostPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sound != null) {
            sound.release();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Back is handled by the game (pause toggle) rather than closing the app.
        if (keyCode == KeyEvent.KEYCODE_BACK && gameView != null) {
            return gameView.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            decor.setSystemUiVisibility(0);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
