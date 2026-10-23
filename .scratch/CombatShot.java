import com.example.anplomer.*;

public final class CombatShot {
    static final class Mute implements SoundSink {
        public void play(String name) { }
        public void setEnabled(boolean enabled) { }
        public boolean isEnabled() { return true; }
    }

    public static void main(String[] a) throws Exception {
        Game g = new Game(new Mute());
        Renderer r = new Renderer();
        g.resize(2400, 1080);
        g.loadLevel(0);
        g.state = Game.State.PLAYING;
        g.update(1f / 60f);
        g.setSecondaryWeapon(Weapons.BOW);
        g.player.swingStyle = SwordStyle.GROUND_RUN;
        g.player.swingTimer = Constants.SWORD_TIME * 0.55f;
        g.player.vx = Constants.RUN_SPEED;
        Projectile arrow = new Projectile(Projectile.ARROW,
                g.player.centerX() + 170f, g.player.centerY() - 10f, 1);
        arrow.vx = Constants.ARROW_SPEED;
        arrow.vy = 60f;
        g.projectiles.add(arrow);
        g.input.key(InputController.KEY_RIGHT, true);
        Class<?> rc = null;
        for (Class<?> inner : Class.forName("com.example.anplomer.RendererSmoke").getDeclaredClasses()) {
            if (inner.getSimpleName().equals("RasterCanvas")) {
                rc = inner;
            }
        }
        Object canvas = rc.getDeclaredConstructor(int.class, int.class).newInstance(2400, 1080);
        r.draw(g, (android.graphics.Canvas) canvas, 2400f, 1080f);
        java.lang.reflect.Method save = rc.getDeclaredMethod("save", String.class);
        save.invoke(canvas, "shots/new/combat-zoom.png");
        System.out.println("done");
    }
}
