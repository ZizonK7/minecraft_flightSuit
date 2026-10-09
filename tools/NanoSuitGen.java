import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the Nanotech Mark 50 (DESIGN.md 4-16 최종전 보상, 4-4 Mark 50) as a 64x64 skin-layout image for
 * SkinSplitter: deep red nano plates with a faint hex grain, gold faceplate, forearms and shins, black joints,
 * the triangular blue arc reactor.
 *
 * Usage: java tools/NanoSuitGen.java <out.png>
 *   then: java tools/SkinSplitter.java <out.png> <a scratch textures dir> nano_mk50   (copy the nano_mk50_* files)
 */
public class NanoSuitGen {
    enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    record Box(int u, int v, int w, int h, int d) {
    }

    static final Box HEAD = new Box(0, 0, 8, 8, 8), HAT = new Box(32, 0, 8, 8, 8);
    static final Box BODY = new Box(16, 16, 8, 12, 4), JACKET = new Box(16, 32, 8, 12, 4);
    static final Box R_ARM = new Box(40, 16, 4, 12, 4), R_SLEEVE = new Box(40, 32, 4, 12, 4);
    static final Box L_ARM = new Box(32, 48, 4, 12, 4), L_SLEEVE = new Box(48, 48, 4, 12, 4);
    static final Box R_LEG = new Box(0, 16, 4, 12, 4), R_PANTS = new Box(0, 32, 4, 12, 4);
    static final Box L_LEG = new Box(16, 48, 4, 12, 4), L_PANTS = new Box(0, 48, 4, 12, 4);
    static final Face[] SIDES = {Face.RIGHT, Face.FRONT, Face.LEFT, Face.BACK};

    interface Pattern {
        char at(Face f, int i, int j);
    }

    static BufferedImage skin;
    static Map<Character, Integer> pal;

    public static void main(String[] args) throws IOException {
        skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        pal = new HashMap<>();
        colors("R", 0xFFA8141A, "r", 0xFF7A0E12, "h", 0xFFC22A2A, "G", 0xFFE8B830, "g", 0xFFB0841E, "K", 0xFF18161A,
                "k", 0xFF2E2A30, "C", 0xFF9FF4FF, "c", 0xFF3FB8D8, "W", 0xFFFFFFFF);
        Pattern plate = (f, i, j) -> ((i + 2 * j) % 7 == 0 || (2 * i - j + 14) % 9 == 0) ? 'h' : ((i + j) % 11 == 0 ? 'r' : 'R');
        // Helmet: red shell, gold faceplate with the eye slits.
        wrap(HEAD, plate);
        top(HEAD, plate);
        bottom(HEAD, 'r');
        face(HEAD, Face.FRONT, "RRRRRRRR", "RGGGGGGR", "RGGGGGGR", "RGCCGCCR".replace("GCCGCC", "CCGGCC"), "RGGGGGGR", "RGGggGGR", "RGgGGgGR", "RRGGGGRR");
        face(HEAD, Face.RIGHT, rowsOf(8, "RRRRRRRR", 3, "RRRRRRrr", 4, "RRRRrrKK", 5, "RRRRRRrr"));
        face(HEAD, Face.LEFT, rowsOf(8, "RRRRRRRR", 3, "rrRRRRRR", 4, "KKrrRRRR", 5, "rrRRRRRR"));
        // Body: red plates, black waist, the triangle reactor.
        wrap(BODY, (f, i, j) -> j == 9 ? 'K' : (j == 10 ? 'g' : plate.at(f, i, j)));
        top(BODY, 'R');
        bottom(BODY, 'K');
        face(BODY, Face.FRONT, "rRRRRRRr", "RRgGGgRR", "RRRCCRRR", "RRCCCCRR", "RRRccRRR", "RRRRRRRR", "rRGRRGRr", "RRGRRGRR",
                "RhRRRRhR", "KKKKKKKK", "gggGGggg", "RRRRRRRR");
        // Arms: red upper arm, black elbow, gold forearm, red glove with a palm repulsor.
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrap(arm, (f, i, j) -> j < 4 ? plate.at(f, i, j) : (j == 4 ? 'K' : (j < 9 ? (f == Face.FRONT && j == 6 ? 'g' : 'G') : (j == 11 ? 'K' : 'R'))));
            top(arm, 'R');
            bottom(arm, 'C');
        }
        // Legs: red thigh, black knee, gold shin, red boot with the thruster ring under it.
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrap(leg, (f, i, j) -> j < 5 ? plate.at(f, i, j) : (j == 5 ? 'K' : (j < 9 ? 'G' : (j == 11 ? 'r' : 'R'))));
            top(leg, 'R');
            bottom(leg, 'c');
        }
        File out = new File(args[0]);
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(skin, "png", out);
        System.out.println("wrote " + out);
    }

    // ---------------------------------------------------------------- painting helpers

    static void colors(Object... kv) {
        for (int k = 0; k < kv.length; k += 2) pal.put(((String) kv[k]).charAt(0), (Integer) kv[k + 1]);
    }

    static int[] region(Box b, Face f) {
        int u = b.u, v = b.v, w = b.w, h = b.h, d = b.d;
        return switch (f) {
            case TOP -> new int[]{u + d, v, w, d};
            case BOTTOM -> new int[]{u + d + w, v, w, d};
            case RIGHT -> new int[]{u, v + d, d, h};
            case FRONT -> new int[]{u + d, v + d, w, h};
            case LEFT -> new int[]{u + d + w, v + d, d, h};
            case BACK -> new int[]{u + 2 * d + w, v + d, w, h};
        };
    }

    static void put(int x, int y, char c) {
        if (c == '.') return;
        Integer argb = pal.get(c);
        if (argb == null) throw new IllegalArgumentException("unknown colour '" + c + "'");
        skin.setRGB(x, y, argb);
    }

    static void face(Box b, Face f, String... rows) {
        int[] r = region(b, f);
        for (int j = 0; j < rows.length; j++) {
            for (int i = 0; i < rows[j].length(); i++) put(r[0] + i, r[1] + j, rows[j].charAt(i));
        }
    }

    static void paint(Box b, Face f, Pattern p) {
        int[] r = region(b, f);
        for (int j = 0; j < r[3]; j++) for (int i = 0; i < r[2]; i++) put(r[0] + i, r[1] + j, p.at(f, i, j));
    }

    static void clear(Box b, Face f) {
        int[] r = region(b, f);
        for (int j = 0; j < r[3]; j++) for (int i = 0; i < r[2]; i++) skin.setRGB(r[0] + i, r[1] + j, 0);
    }

    /** Paints the four side faces. */
    static void wrap(Box b, Pattern p) {
        for (Face f : SIDES) paint(b, f, p);
    }

    /** Paints the four side faces one colour per row (rowColors.charAt(j)). */
    static void wrapRows(Box b, String rowColors) {
        wrap(b, (f, i, j) -> j < rowColors.length() ? rowColors.charAt(j) : '.');
    }

    /** Like wrapRows, but only the top rows: a hat band or brim. */
    static void ring(Box b, String rowColors) {
        wrapRows(b, rowColors);
    }

    static void rows(Box b, Face f, int from, int to, char c) {
        paint(b, f, (ff, i, j) -> j >= from && j < to ? c : '.');
    }

    static void top(Box b, char c) {
        paint(b, Face.TOP, (f, i, j) -> c);
    }

    static void top(Box b, Pattern p) {
        paint(b, Face.TOP, p);
    }

    static void bottom(Box b, char c) {
        paint(b, Face.BOTTOM, (f, i, j) -> c);
    }

    /** n rows of `fill`, with (index, row) overrides. */
    static String[] rowsOf(int n, String fill, Object... overrides) {
        String[] out = new String[n];
        java.util.Arrays.fill(out, fill);
        for (int k = 0; k < overrides.length; k += 2) out[(Integer) overrides[k]] = (String) overrides[k + 1];
        return out;
    }

    static String[] mirror(String[] rows) {
        String[] out = new String[rows.length];
        for (int j = 0; j < rows.length; j++) out[j] = new StringBuilder(rows[j]).reverse().toString();
        return out;
    }

    static int shade(int c, double s) {
        int r = (int) (((c >> 16) & 255) * s), g = (int) (((c >> 8) & 255) * s), b = (int) ((c & 255) * s);
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
