import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Draws the Ryan Mk1 suit as a 64x64 player-skin-layout image (yellow Ryan faceplate on a red/gold
 * Iron Man suit), to be fed into SkinSplitter.
 *
 * Usage: java tools/RyanSkinGen.java <out.png>
 *
 * Every face is written as a character map; see PALETTE for the letters. Front-face columns run from
 * the wearer's right to left (viewer's left to right), side faces run back to front on the right side
 * and front to back on the left side, matching the vanilla skin UV layout.
 */
public class RyanSkinGen {
    private static final Map<Character, Integer> PALETTE = Map.ofEntries(
            Map.entry('R', 0xFFC62A22), Map.entry('l', 0xFFE0473A), Map.entry('d', 0xFF8C1A15),
            Map.entry('Y', 0xFFF2BE22), Map.entry('L', 0xFFFFD95A), Map.entry('y', 0xFFC9951A),
            Map.entry('G', 0xFF5A5C63), Map.entry('g', 0xFF868990), Map.entry('k', 0xFF3C3D42),
            Map.entry('K', 0xFF1E1A1A), Map.entry('W', 0xFFFFFFFF), Map.entry('w', 0xFFE6E1DA),
            Map.entry('a', 0xFFE8FCFF), Map.entry('b', 0xFF9EE8FF), Map.entry('c', 0xFF4FC3EF));

    private static final BufferedImage SKIN = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

    public static void main(String[] args) throws IOException {
        drawHead();
        drawBody();
        drawArm(40, 16); // right arm
        drawArm(32, 48); // left arm
        drawLeg(0, 16);  // right leg
        drawLeg(16, 48); // left leg
        ImageIO.write(SKIN, "png", new File(args[0]));
        System.out.println("wrote " + args[0]);
    }

    private static void drawHead() {
        face(8, 0, // top: row 0 touches the back, row 7 the faceplate
                "dRRRRRRd",
                "RRRllRRR",
                "RRRllRRR",
                "RRRllRRR",
                "RRRllRRR",
                "RRRllRRR",
                "RRRllRRR",
                "RRRllRRR");
        face(16, 0, rows(8, "dddddddd"));
        String[] side = {
                "dRRRRRRR",
                "dRRddRRR",
                "dRdRRdRR",
                "dRdRldRR",
                "dRRddRRR",
                "dRRRRRRR",
                "dRRRRRRR",
                "dddddddd"};
        face(0, 8, side);
        face(16, 8, mirror(side));
        face(8, 8, // Ryan faceplate
                "yYYYYYYy",
                "yKKYYKKy",
                "yYYYYYYy",
                "yYKYYKYy",
                "yYYKKYYy",
                "yYWWWWYy",
                "yYwWWwYy",
                "yyyyyyyy");
        face(24, 8,
                "RRRRRRRR",
                "RlRRRRlR",
                "RRRRRRRR",
                "RRRRRRRR",
                "dddddddd",
                "RRRRRRRR",
                "RRRRRRRR",
                "dddddddd");
    }

    private static void drawBody() {
        face(20, 16,
                "RRRRRRRR",
                "RRGGGGRR",
                "RRGGGGRR",
                "RRRRRRRR");
        face(28, 16, rows(4, "dddddddd"));
        String[] side = {
                "RRRR", "RRRR", "RlRR", "RRRR", "RRRR", "GGGG",
                "RRRR", "RRRR", "RRRR", "RRRR", "RRRR", "dddd"};
        face(16, 20, side);
        face(28, 20, mirror(side));
        face(20, 20, // chest: arc reactor, gold abs, belt
                "RRGGGGRR",
                "GRRggRRG",
                "GRgabgRG",
                "GRgbcgRG",
                "GRRggRRG",
                "RGYYYYGR",
                "RGYLLYGR",
                "RGyyyyGR",
                "RGYLLYGR",
                "RRGYYGRR",
                "dRRGGRRd",
                "ddRRRRdd");
        face(32, 20, // back: thruster vent
                "RRRRRRRR",
                "RlRRRRlR",
                "RRGGGGRR",
                "RGkkkkGR",
                "RGkbbkGR",
                "RGkkkkGR",
                "RRGGGGRR",
                "RRYYYYRR",
                "RRYYYYRR",
                "RRRGGRRR",
                "dRRRRRRd",
                "dddddddd");
    }

    private static void drawArm(int u, int v) {
        face(u + 4, v, "lRRR", "RRRR", "RRRR", "RRRR");
        face(u + 8, v, "kkkk", "kabk", "kbck", "kkkk"); // palm repulsor
        String[] side = {
                "lRRR", "RRRR", "dddd", "GGGG", "YLYY", "YYYY",
                "GGGG", "RRRR", "RRRR", "YYYY", "RRRR", "dddd"};
        for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, side);
    }

    private static void drawLeg(int u, int v) {
        face(u + 4, v, rows(4, "RRRR"));
        face(u + 8, v, "GGGG", "GbcG", "GcbG", "GGGG"); // boot thruster
        String[] front = {
                "RRRR", "RRRR", "YYYY", "YLYY", "YYYY", "GGGG",
                "YRRY", "YRRY", "YddY", "YYYY", "GGGG", "RRRR"};
        String[] back = {
                "RRRR", "RRRR", "YYYY", "YYYY", "YYYY", "GGGG",
                "YYYY", "YYYY", "yyyy", "YYYY", "GGGG", "RRRR"};
        face(u, v + 4, front);
        face(u + 4, v + 4, front);
        face(u + 8, v + 4, front);
        face(u + 12, v + 4, back);
    }

    private static void face(int x, int y, String... rows) {
        for (int j = 0; j < rows.length; j++) {
            for (int i = 0; i < rows[j].length(); i++) {
                Integer c = PALETTE.get(rows[j].charAt(i));
                if (c == null) throw new IllegalArgumentException("unknown colour '" + rows[j].charAt(i) + "'");
                SKIN.setRGB(x + i, y + j, c);
            }
        }
    }

    private static String[] rows(int n, String row) {
        String[] out = new String[n];
        java.util.Arrays.fill(out, row);
        return out;
    }

    private static String[] mirror(String[] rows) {
        String[] out = new String[rows.length];
        for (int j = 0; j < rows.length; j++) out[j] = new StringBuilder(rows[j]).reverse().toString();
        return out;
    }
}
