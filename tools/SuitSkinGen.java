import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Draws a suit as a 64x64 player-skin-layout image, to be fed into SkinSplitter.
 *   mk1: yellow Ryan faceplate on a red/gold Iron Man suit
 *   mk2: Choonsik in a grey bear onesie (hood rim on the hat layer)
 *
 * Usage: java tools/SuitSkinGen.java <mk1|mk2> <out.png>
 *
 * Every face is written as a character map; see the palettes for the letters ('.' = transparent).
 * Front-face columns run from the wearer's right to left (viewer's left to right), side faces run back
 * to front on the right side and front to back on the left side, matching the vanilla skin UV layout.
 */
public class SuitSkinGen {
    private static final Map<Character, Integer> MK1 = Map.ofEntries(
            Map.entry('R', 0xFFC62A22), Map.entry('l', 0xFFE0473A), Map.entry('d', 0xFF8C1A15),
            Map.entry('Y', 0xFFF2BE22), Map.entry('L', 0xFFFFD95A), Map.entry('y', 0xFFC9951A),
            Map.entry('G', 0xFF5A5C63), Map.entry('g', 0xFF868990), Map.entry('k', 0xFF3C3D42),
            Map.entry('K', 0xFF1E1A1A), Map.entry('W', 0xFFFFFFFF), Map.entry('w', 0xFFE6E1DA),
            Map.entry('a', 0xFFE8FCFF), Map.entry('b', 0xFF9EE8FF), Map.entry('c', 0xFF4FC3EF));

    private static final Map<Character, Integer> MK2 = Map.ofEntries(
            Map.entry('G', 0xFFA8A8AD), Map.entry('g', 0xFFC6C6CB), Map.entry('h', 0xFFDADADF),
            Map.entry('k', 0xFF85858C), Map.entry('n', 0xFF64646B),
            Map.entry('P', 0xFFFFE0AE), Map.entry('p', 0xFFF5CB92), Map.entry('C', 0xFFF08A80),
            Map.entry('K', 0xFF1E1A1A), Map.entry('W', 0xFFFFFFFF), Map.entry('w', 0xFFEFEBE2),
            Map.entry('T', 0xFFEC7A84));

    private static final BufferedImage SKIN = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    private static Map<Character, Integer> palette;

    public static void main(String[] args) throws IOException {
        switch (args[0]) {
            case "mk1" -> {
                palette = MK1;
                drawMk1();
            }
            case "mk2" -> {
                palette = MK2;
                drawMk2();
            }
            default -> throw new IllegalArgumentException("unknown suit " + args[0]);
        }
        ImageIO.write(SKIN, "png", new File(args[1]));
        System.out.println("wrote " + args[1]);
    }

    // ---- Mark 1: Ryan Iron Man ----

    private static void drawMk1() {
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

        face(20, 16,
                "RRRRRRRR",
                "RRGGGGRR",
                "RRGGGGRR",
                "RRRRRRRR");
        face(28, 16, rows(4, "dddddddd"));
        String[] bodySide = {
                "RRRR", "RRRR", "RlRR", "RRRR", "RRRR", "GGGG",
                "RRRR", "RRRR", "RRRR", "RRRR", "RRRR", "dddd"};
        face(16, 20, bodySide);
        face(28, 20, mirror(bodySide));
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

        String[] armSide = {
                "lRRR", "RRRR", "dddd", "GGGG", "YLYY", "YYYY",
                "GGGG", "RRRR", "RRRR", "YYYY", "RRRR", "dddd"};
        for (int[] uv : new int[][]{{40, 16}, {32, 48}}) {
            int u = uv[0], v = uv[1];
            face(u + 4, v, "lRRR", "RRRR", "RRRR", "RRRR");
            face(u + 8, v, "kkkk", "kabk", "kbck", "kkkk"); // palm repulsor
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, armSide);
        }

        String[] legFront = {
                "RRRR", "RRRR", "YYYY", "YLYY", "YYYY", "GGGG",
                "YRRY", "YRRY", "YddY", "YYYY", "GGGG", "RRRR"};
        String[] legBack = {
                "RRRR", "RRRR", "YYYY", "YYYY", "YYYY", "GGGG",
                "YYYY", "YYYY", "yyyy", "YYYY", "GGGG", "RRRR"};
        for (int[] uv : new int[][]{{0, 16}, {16, 48}}) {
            int u = uv[0], v = uv[1];
            face(u + 4, v, rows(4, "RRRR"));
            face(u + 8, v, "GGGG", "GbcG", "GcbG", "GGGG"); // boot thruster
            face(u, v + 4, legFront);
            face(u + 4, v + 4, legFront);
            face(u + 8, v + 4, legFront);
            face(u + 12, v + 4, legBack);
        }
    }

    // ---- Mark 2: Choonsik bear onesie ----

    private static void drawMk2() {
        // Head base: Choonsik's face in front, plain hood elsewhere (the hat layer adds the rim).
        face(8, 0, rows(8, "GGGGGGGG"));
        face(16, 0, rows(8, "kkkkkkkk"));
        face(0, 8, rows(8, "GGGGGGGG"));
        face(16, 8, rows(8, "GGGGGGGG"));
        face(24, 8, rows(8, "GGGGGGGG"));
        face(8, 8,
                "GGGGGGGG",
                "GpPPPPpG",
                "GPKPPKPG",
                "GCPPPPCG",
                "GCPWWPCG",
                "GPPTTPPG",
                "GpPPPPpG",
                "GGGGGGGG");

        // Hat layer: the hood itself, with the little bear's face on the brow.
        face(40, 0, // top: row 7 is the brow
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGhhhhGG",
                "GhhhhhhG");
        face(48, 0, rows(8, "kkkkkkkk"));
        String[] hoodSide = {
                "GGGGGGGk",
                "GGGGGGGk",
                "GGGGGGGk",
                "GGGGGGGk",
                "GGGGGGGk",
                "GGGGGGGk",
                "kGGGGGGk",
                "kkkkkkkk"};
        face(32, 8, hoodSide);
        face(48, 8, mirror(hoodSide));
        face(40, 8, // front rim with a face window
                "kGnhhnGk",
                "k......k",
                "k......k",
                "k......k",
                "k......k",
                "k......k",
                "k......k",
                "kkkkkkkk");
        face(56, 8,
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "kkkkkkkk");

        face(20, 16, rows(4, "GGGGGGGG"));
        face(28, 16, rows(4, "kkkkkkkk"));
        String[] bodySide = {
                "GGGG", "GGGG", "GGGG", "GGGG", "GGGG", "GGGG",
                "GGGG", "GGGG", "GGGG", "GGGG", "GGGG", "kkkk"};
        face(16, 20, bodySide);
        face(28, 20, mirror(bodySide));
        face(20, 20, // white belly patch
                "GGGGGGGG",
                "GGGGGGGG",
                "GGwwwwGG",
                "GwWWWWwG",
                "GWWWWWWG",
                "GWWWWWWG",
                "GWWWWWWG",
                "GWWWWWWG",
                "GwWWWWwG",
                "GGwwwwGG",
                "GGGGGGGG",
                "kGGGGGGk");
        face(32, 20, // back, with a stubby tail
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGGGGGG",
                "GGGhhGGG",
                "GGGhhGGG",
                "GGGGGGGG",
                "kkkkkkkk");

        String[] armSide = {
                "GGGG", "GGGG", "GGGG", "GGGG", "GGGG", "GGGG",
                "GGGG", "GGGG", "GGGG", "GGGG", "gggg", "kgkg"};
        for (int[] uv : new int[][]{{40, 16}, {32, 48}}) {
            int u = uv[0], v = uv[1];
            face(u + 4, v, "GGGG", "GGGG", "GGGG", "GGGG");
            face(u + 8, v, "gggg", "gkkg", "gkkg", "gggg"); // paw pad
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, armSide);
        }

        // Right leg's inner side is column 3 of its front, left leg's is column 0.
        String[] legFront = {
                "GGGG", "GGGk", "GGGk", "GGGk", "GGGk", "GGGk",
                "GGGk", "GGGk", "GGGk", "GGGG", "gggg", "kgkg"};
        String[] legSide = {
                "GGGG", "GGGG", "GGGG", "GGGG", "GGGG", "GGGG",
                "GGGG", "GGGG", "GGGG", "GGGG", "gggg", "gggg"};
        String[] sole = {"gkgk", "gggg", "gnng", "gnng"};
        face(4, 16, rows(4, "GGGG"));
        face(8, 16, sole);
        face(0, 20, legSide);
        face(4, 20, legFront);
        face(8, 20, legSide);
        face(12, 20, legSide);
        face(20, 48, rows(4, "GGGG"));
        face(24, 48, sole);
        face(16, 52, legSide);
        face(20, 52, mirror(legFront));
        face(24, 52, legSide);
        face(28, 52, legSide);
    }

    private static void face(int x, int y, String... rows) {
        for (int j = 0; j < rows.length; j++) {
            for (int i = 0; i < rows[j].length(); i++) {
                char ch = rows[j].charAt(i);
                if (ch == '.') continue;
                Integer c = palette.get(ch);
                if (c == null) throw new IllegalArgumentException("unknown colour '" + ch + "'");
                SKIN.setRGB(x + i, y + j, c);
            }
        }
    }

    private static String[] rows(int n, String row) {
        String[] out = new String[n];
        Arrays.fill(out, row);
        return out;
    }

    private static String[] mirror(String[] rows) {
        String[] out = new String[rows.length];
        for (int j = 0; j < rows.length; j++) out[j] = new StringBuilder(rows[j]).reverse().toString();
        return out;
    }
}
