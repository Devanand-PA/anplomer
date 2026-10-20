package android.graphics;

public class Typeface {

    public static final Typeface DEFAULT = new Typeface("sans-serif");
    public static final Typeface DEFAULT_BOLD = new Typeface("sans-serif-bold");

    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;
    public static final Typeface SANS_SERIF = DEFAULT;
    public static final Typeface SERIF = new Typeface("serif");
    public static final Typeface MONOSPACE = new Typeface("monospace");

    private final String name;

    public Typeface(String name) { this.name = name; }

    public static Typeface create(String family, int style) { return new Typeface(family + "-" + style); }
    public static Typeface create(Typeface family, int style) { return new Typeface(family.name + "-" + style); }
    public static Typeface defaultFromStyle(int style) { return DEFAULT; }

    public String toString() { return name; }
}
