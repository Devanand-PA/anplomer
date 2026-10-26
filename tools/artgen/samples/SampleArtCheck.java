// Compiled by tools/artgen/selftest.py against the generated sample art and
// the android.graphics stubs.
//
// Touching count()/part(i) forces every generated path builder to run, so the
// stub Path's non-finite guard validates the baked coordinates. The geometry
// assertions then rebuild each part as a java.awt Path2D (same M/L/Q/C/Z
// language) to prove the winding rules, holes and fitted bounds are right
// without needing to look at a picture.
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import android.graphics.Path;

import com.example.anplomer.ArtPlayer;
import com.example.anplomer.ArtShapes;

public final class SampleArtCheck {

    public static void main(String[] args) {
        int shapesSegs = 0;
        boolean donutEvenOdd = false;
        for (int i = 0; i < ArtShapes.count(); i++) {
            ArtShapes.Part p = ArtShapes.part(i);
            require(!p.name.isEmpty(), "ArtShapes part " + i + " has no name");
            require(p.color != 0 || p.strokeColor != 0,
                    "ArtShapes part " + p.name + " draws nothing");
            shapesSegs += p.path.segments.size();
            if (p.name.equals("donut")) {
                donutEvenOdd = p.path.getFillType() == Path.FillType.EVEN_ODD;
            }
        }
        require(shapesSegs > 0, "ArtShapes has no segments");
        require(donutEvenOdd, "ArtShapes#donut should be even-odd");

        int playerSegs = 0;
        Path2D.Float all = new Path2D.Float(Path2D.WIND_NON_ZERO);
        Path2D.Float body = null;
        for (int i = 0; i < ArtPlayer.count(); i++) {
            ArtPlayer.Part p = ArtPlayer.part(i);
            require(!p.name.isEmpty(), "ArtPlayer part " + i + " has no name");
            require(p.color != 0 || p.strokeColor != 0,
                    "ArtPlayer part " + p.name + " draws nothing");
            playerSegs += p.path.segments.size();
            Path2D.Float shape = toPath2D(p.path.segments.toArray(new float[0][]), false);
            all.append(shape, false);
            if (p.name.equals("body")) {
                body = shape;
            }
        }
        require(playerSegs > 0, "ArtPlayer has no segments");

        // --- geometry: even-odd hole in the compound donut path ---
        ArtShapes.Part donutPart = null;
        for (int i = 0; i < ArtShapes.count(); i++) {
            if (ArtShapes.part(i).name.equals("donut")) {
                donutPart = ArtShapes.part(i);
            }
        }
        require(donutPart != null, "missing donut part");
        Path2D.Float donut = toPath2D(donutPart.path.segments.toArray(new float[0][]), true);
        require(!donut.contains(26.0, 78.0), "even-odd hole should not be filled");
        require(donut.contains(26.0, 73.5), "donut ring should be filled");
        require(!donut.contains(26.0, 68.0), "outside the donut should be empty");

        // --- geometry: fitted player frame ---
        Rectangle2D b = all.getBounds2D();
        require(Math.abs(b.getMaxY()) < 0.02,
                "feet should sit on y=0, got " + b.getMaxY());
        require(Math.abs(b.getCenterX()) < 0.02,
                "art should be centred on x=0, got " + b.getCenterX());
        require(Math.abs(b.getHeight() - ArtPlayer.HEIGHT) < 0.02,
                "HEIGHT should match the ink box: " + b.getHeight());
        require(body != null && body.contains(0.0, -16.5),
                "body should cover (0,-16.5) in the fitted frame");
        // The silhouette must have real gaps (two separate legs), i.e. a
        // scanline near the feet crosses in and out more than once. This
        // catches a path that collapsed into its bounding box.
        int spans = spans(all, -5.0);
        require(spans >= 2, "expected separate legs, found " + spans + " filled span(s)");
        require(spans(all, -20.0) == 1, "torso should be one solid span");

        // Per-part API: names + joint pivots (top-centre of each part's ink
        // box), which the name-driven limb animation rotates around.
        int legIdx = -1;
        int shoeIdx = -1;
        for (int i = 0; i < ArtPlayer.count(); i++) {
            if (ArtPlayer.partName(i).equals("leg-left")) {
                legIdx = i;
            }
            if (ArtPlayer.partName(i).equals("shoe-left")) {
                shoeIdx = i;
            }
        }
        require(legIdx >= 0, "leg-left part should exist");
        require(ArtPlayer.partPivotY(legIdx) < -9f,
                "leg pivot should sit at the hip, not the shoe");
        require(ArtPlayer.partPivotY(legIdx) > -12f,
                "leg pivot should not float above the leg");
        require(shoeIdx >= 0
                && ArtPlayer.partPivotY(shoeIdx) > ArtPlayer.partPivotY(legIdx),
                "shoe pivot should be lower than the leg pivot");
        require(Math.abs(ArtPlayer.partPivotX(legIdx) + 1.5f) < 2f,
                "leg pivot should be roughly centred on the leg");

        System.out.println("ArtShapes parts=" + ArtShapes.count()
                + " segs=" + shapesSegs + " evenOddDonut=" + donutEvenOdd
                + " holeEmpty=" + !donut.contains(26.0, 78.0));
        System.out.println("ArtPlayer parts=" + ArtPlayer.count()
                + " segs=" + playerSegs
                + " extents=" + ArtPlayer.WIDTH + "x" + ArtPlayer.HEIGHT
                + " bounds=" + b.getMinX() + "," + b.getMinY()
                + ".." + b.getMaxX() + "," + b.getMaxY());
    }

    private static Path2D.Float toPath2D(float[][] segs, boolean evenOdd) {
        Path2D.Float p = new Path2D.Float(
                evenOdd ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
        for (float[] s : segs) {
            switch ((char) s[0]) {
                case 'M': p.moveTo(s[1], s[2]); break;
                case 'L': p.lineTo(s[1], s[2]); break;
                case 'Q': p.quadTo(s[1], s[2], s[3], s[4]); break;
                case 'C': p.curveTo(s[1], s[2], s[3], s[4], s[5], s[6]); break;
                case 'Z': p.closePath(); break;
                default: throw new IllegalStateException("unexpected segment " + (char) s[0]);
            }
        }
        return p;
    }

    /** Counts how many separate filled intervals the shape covers on one scanline. */
    private static int spans(Path2D.Float shape, double y) {
        Rectangle2D b = shape.getBounds2D();
        int steps = 800;
        int spans = 0;
        boolean wasIn = false;
        for (int i = 0; i <= steps; i++) {
            double x = b.getMinX() - 1 + (b.getWidth() + 2) * i / steps;
            boolean in = shape.contains(x, y);
            if (in && !wasIn) {
                spans++;
            }
            wasIn = in;
        }
        return spans;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }
}
