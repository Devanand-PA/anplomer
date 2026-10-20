package com.example.anplomer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * SurfaceView hosting the fixed-timestep game loop. Input events are translated
 * into the headless-friendly {@link InputController} API.
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final float STEP = 1f / 60f;
    private static final String TAG = "AnplomerView";

    private final Game game;
    private final Renderer renderer = new Renderer();
    private final FrameDumper frameDumper;

    private Thread loop;
    private volatile boolean running;
    private boolean surfaceReady;

    /**
     * Surface size requested by the UI thread. Applied by the loop thread so
     * that all mutation of {@link Game} happens on one thread.
     */
    private volatile int pendingW, pendingH;

    /** True while the host activity is not visible: the sim is frozen. */
    private volatile boolean hostPaused;

    /**
     * Set by the UI thread in onHostPause; applied to {@link Game} on the loop
     * thread so the PAUSED overlay state is never written from off-loop.
     */
    private volatile boolean pendingForcePause;

    public GameView(Context context, SoundManager sound) {
        super(context);
        game = new Game(sound, new PrefsSaveStore(context));
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setFormat(PixelFormat.RGBA_8888);
        setFocusable(true);
        setFocusableInTouchMode(true);
        frameDumper = new FrameDumper(context, game);
    }

    public Game game() {
        return game;
    }

    // ---- surface lifecycle -------------------------------------------

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        startLoop();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (width > 0 && height > 0) {
            // Handed to the loop thread; never resize Game from here.
            pendingW = width;
            pendingH = height;
        }
    }

    /** Freeze the simulation while the hosting activity is not visible. */
    public void onHostPause() {
        hostPaused = true;
        pendingForcePause = true;
        game.input.reset();
    }

    /** Called by MainActivity.onResume; without it the game stays frozen. */
    public void onHostResume() {
        hostPaused = false;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        stopLoop();
    }

    private void startLoop() {
        if (loop != null && loop.isAlive()) {
            return;
        }
        running = true;
        loop = new Thread(this, "anplomer-loop");
        loop.start();
    }

    private void stopLoop() {
        running = false;
        Thread t = loop;
        if (t != null) {
            try {
                t.join(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!t.isAlive()) {
                loop = null;
            }
            // If the thread refused to die we keep the reference: startLoop
            // must never create a second writer for the same Game.
        }
        game.input.reset();
    }

    // ---- game loop ----------------------------------------------------

    @Override
    public void run() {
        long last = SystemClock.elapsedRealtimeNanos();
        float accumulator = 0f;

        while (running) {
            if (!surfaceReady) {
                sleepQuietly(16);
                continue;
            }
            Canvas canvas = getHolder().lockCanvas();
            if (canvas == null) {
                sleepQuietly(16);
                continue;
            }
            try {
                long now = SystemClock.elapsedRealtimeNanos();
                float dt = (now - last) / 1_000_000_000f;
                last = now;

                // Surface size changes are applied here, on the loop thread.
                int pw = pendingW, ph = pendingH;
                if (pw > 0 && ph > 0) {
                    pendingW = 0;
                    pendingH = 0;
                    game.resize(pw, ph);
                } else if (game.screenWidth <= 0f) {
                    game.resize(canvas.getWidth(), canvas.getHeight());
                }

                // Backgrounding is applied on the loop thread too: the UI
                // thread only raises the flag, keeping all Game mutation here.
                if (pendingForcePause) {
                    pendingForcePause = false;
                    game.forcePause();
                    game.input.reset();
                }

                if (hostPaused) {
                    accumulator = 0f;       // drop time spent in the background
                } else {
                    if (dt > 0.25f) {
                        dt = 0.25f;   // never spiral after a long stall
                    }
                    accumulator += dt;
                    int steps = 0;
                    while (accumulator >= STEP && steps < 4) {
                        game.update(STEP);
                        accumulator -= STEP;
                        steps++;
                    }
                    if (steps == 4) {
                        accumulator = 0f;
                    }
                }

                renderer.draw(game, canvas, canvas.getWidth(), canvas.getHeight());

                // Debug-only frame capture; inert unless the sentinel file
                // exists. Needed because screencap freezes this loop, so
                // remotely grabbed frames cannot show motion artifacts.
                if (frameDumper != null) {
                    frameDumper.maybeDump(canvas.getWidth(), canvas.getHeight());
                }
            } catch (Throwable t) {
                // A frame must never kill the loop: keep rendering and log it.
                Log.e(TAG, "frame failed", t);
                sleepQuietly(50);
            } finally {
                try {
                    getHolder().unlockCanvasAndPost(canvas);
                } catch (IllegalStateException ignored) {
                    // surface vanished between lock and post — safe to ignore
                }
            }
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- input --------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = event.getActionIndex();
                game.input.pointerDown(event.getPointerId(i), event.getX(i), event.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    game.input.pointerMove(event.getPointerId(i), event.getX(i), event.getY(i));
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP: {
                int i = event.getActionIndex();
                game.input.pointerUp(event.getPointerId(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                game.input.pointersCancel();
                break;
            }
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mapKey(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mapKeyUp(keyCode, event)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean mapKey(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                game.input.key(InputController.KEY_LEFT, true);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                game.input.key(InputController.KEY_RIGHT, true);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_SPACE:
                game.input.key(InputController.KEY_JUMP, true);
                game.input.key(InputController.KEY_CONFIRM, true);
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                game.input.key(InputController.KEY_CONFIRM, true);
                return true;
            case KeyEvent.KEYCODE_P:
                game.input.key(InputController.KEY_PAUSE, true);
                return true;
            case KeyEvent.KEYCODE_R:
                game.input.key(InputController.KEY_RESTART, true);
                return true;
            case KeyEvent.KEYCODE_M:
                game.input.key(InputController.KEY_MUTE, true);
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK:
                game.input.key(InputController.KEY_BACK, true);
                return true;
            default:
                return false;
        }
    }

    private boolean mapKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                game.input.key(InputController.KEY_LEFT, false);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                game.input.key(InputController.KEY_RIGHT, false);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_SPACE:
                game.input.key(InputController.KEY_JUMP, false);
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                game.input.key(InputController.KEY_CONFIRM, false);
                return true;
            default:
                return false;
        }
    }

    /**
     * The real {@link SaveStore}: a tiny SharedPreferences blob. Lives here so
     * the pure-Java game never touches android.content.
     */
    private static final class PrefsSaveStore implements SaveStore {

        private static final String PREFS = "anplomer";
        private static final String KEY = "save";

        private final android.content.SharedPreferences prefs;

        PrefsSaveStore(Context context) {
            prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        @Override
        public String load() {
            try {
                return prefs.getString(KEY, null);
            } catch (RuntimeException e) {
                return null;   // a broken store must never keep the game from starting
            }
        }

        @Override
        public void store(String blob) {
            try {
                prefs.edit().putString(KEY, blob).apply();
            } catch (RuntimeException ignored) {
                // losing one save write is survivable; a crash is not
            }
        }
    }
}
