import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the Hero City people as 64x64 classic (4px arm) player skins (DESIGN.md 4-13, M13): Captain, Iron Man,
 * Thor, Hulk, Spider-Man, Black Widow, Hawkeye and the S.H.I.E.L.D. agents.
 *
 * Usage: java tools/HeroSkinGen.java <out dir>   (src/main/resources/assets/flightsuit/textures/entity/hero)
 *
 * Same painting helpers as ResidentSkinGen (copied): colours are letters looked up in the current palette.
 */
public class HeroSkinGen {
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
        File dir = new File(args[0]);
        dir.mkdirs();
        for (String name : new String[]{"captain", "iron_man", "thor", "hulk", "spider_man", "black_widow", "hawkeye", "agent"}) {
            skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            pal = new HashMap<>();
            pal.put('W', 0xFFFFFFFF);
            pal.put('K', 0xFF1E1A1A);
            switch (name) {
                case "captain" -> captain();
                case "iron_man" -> ironMan();
                case "thor" -> thor();
                case "hulk" -> hulk();
                case "spider_man" -> spiderMan();
                case "black_widow" -> blackWidow();
                case "hawkeye" -> hawkeye();
                case "agent" -> agent();
                default -> throw new IllegalStateException(name);
            }
            File out = new File(dir, name + ".png");
            ImageIO.write(skin, "png", out);
            System.out.println("wrote " + out);
        }
    }

    static void limbs(String armRows, String legRows) {
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrapRows(arm, armRows);
            top(arm, armRows.charAt(0));
            bottom(arm, armRows.charAt(armRows.length() - 1));
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, legRows);
            top(leg, legRows.charAt(0));
            bottom(leg, legRows.charAt(legRows.length() - 1));
        }
    }

    /** Captain: blue scale suit, white star, red-and-white stripes at the waist, winged helmet with the A. */
    static void captain() {
        tone(0xFFE0B48A, 0xFF6B4A2F, 0xFF3060A0);
        hair(0xFFC9A04A);
        colors("B", 0xFF2A4A9A, "b", 0xFF1F3672, "R", 0xFFB8302A, "L", 0xFF6B4A2F, "G", 0xFFD9B54A);
        head("short", "none", false);
        wrap(BODY, (f, i, j) -> j < 7 ? ((i + j) % 2 == 0 ? 'B' : 'b') : (j < 9 ? (j % 2 == 0 ? 'R' : 'W') : (j == 9 ? 'L' : 'B')));
        top(BODY, 'B');
        face(BODY, Face.FRONT, "BBBBBBBB", "BBBWWBBB", "BWWWWWWB", "BBWWWWBB", "BBWBBWBB", "BBBBBBBB", "bBBBBBBb",
                "RRRRRRRR", "WWWWWWWW", "LLLGGLLL", "BBBBBBBB", "BBBBBBBB");
        limbs("BBBBBBBBRRRR", "BBBBBBBBRRRR");
        // Helmet: blue cowl leaving the face, white A, little white wings at the sides.
        top(HAT, 'B');
        face(HAT, Face.FRONT, "BBBWWBBB", "BBWBBWBB", "B.WWWW.B", "B......B", "B......B", "B......B", "........", "........");
        face(HAT, Face.RIGHT, "BBBBBBBB", "BBWWWBBB", "BBBWWBBB", "BBBBBBBB", "BBBBBB..", "BBBBB...", "........", "........");
        face(HAT, Face.LEFT, mirror(new String[]{"BBBBBBBB", "BBWWWBBB", "BBBWWBBB", "BBBBBBBB", "BBBBBB..", "BBBBB...", "........", "........"}));
        face(HAT, Face.BACK, rowsOf(8, "BBBBBBBB", 6, "........", 7, "........"));
    }

    /** Iron Man: red armour, gold faceplate and forearms, a glowing arc reactor. */
    static void ironMan() {
        tone(0xFFE0B48A, 0xFF2B2420, 0xFF3A2416);
        hair(0xFF2B2420);
        colors("R", 0xFFB01E1E, "r", 0xFF7E1414, "G", 0xFFE0AE3A, "g", 0xFFA97E22, "C", 0xFF9FF4FF, "c", 0xFF3FB8D8, "D", 0xFF3A3A40);
        head("short", "none", false);
        wrap(BODY, (f, i, j) -> j == 9 ? 'r' : (f == Face.FRONT && (i == 0 || i == 7) ? 'r' : 'R'));
        top(BODY, 'R');
        face(BODY, Face.FRONT, "rRRRRRRr", "RRgGGgRR", "RRRccRRR", "RRcCCcRR", "RRRccRRR", "RRRRRRRR", "rRGRRGRr", "RRGRRGRR",
                "RRRRRRRR", "rrrrrrrr", "RRRRRRRR", "RRRRRRRR");
        limbs("RRRRrGGGGGgG", "RRRRGGGGRRRR");
        // Helmet over the whole head: red, gold faceplate with the cyan eye slits.
        top(HAT, 'R');
        face(HAT, Face.FRONT, "RRRRRRRR", "RGGGGGGR", "RGGGGGGR", "RGCCGCCR".replace("GCCGCC", "CCGGCC"), "RGGGGGGR", "RGGggGGR", "RGGGGGGR", "RRGGGGRR");
        face(HAT, Face.RIGHT, rowsOf(8, "RRRRRRRR", 3, "RRRRRRrr", 4, "RRRRRRrr"));
        face(HAT, Face.LEFT, rowsOf(8, "RRRRRRRR", 3, "rrRRRRRR", 4, "rrRRRRRR"));
        face(HAT, Face.BACK, rowsOf(8, "RRRRRRRR"));
        bottom(HAT, 'R');
    }

    /** Thor: long blond hair and beard, silver discs on dark armour, red cape down the back. */
    static void thor() {
        tone(0xFFE8BC92, 0xFF8A6A30, 0xFF3070B0);
        hair(0xFFE0C060);
        colors("A", 0xFF2A2C34, "V", 0xFFC8CDD8, "v", 0xFF8C93A0, "C", 0xFFB8302A, "c", 0xFF8A2420, "L", 0xFF4A3424, "M", 0xFF6E6043);
        head("long", "beard", false);
        wrap(BODY, (f, i, j) -> f == Face.BACK ? (j < 2 ? 'A' : (i % 4 == 0 ? 'c' : 'C')) : (j == 9 ? 'L' : 'A'));
        top(BODY, 'A');
        face(BODY, Face.FRONT, "AAAAAAAA", "AVVAAVVA", "AvVAAvVA", "AAAAAAAA", "AVVAAVVA", "AvVAAvVA", "AAAAAAAA", "AAAAAAAA",
                "AAAAAAAA", "LLLVVLLL", "AAAAAAAA", "AAAAAAAA");
        limbs("AAAVVAAAvVVV", "AAAAAAAAALLL");
        face(BODY, Face.BACK, rowsOf(12, "CCCCCCCC", 0, "AAAAAAAA"));
    }

    /** Hulk: green, bare-chested, torn purple trousers. */
    static void hulk() {
        tone(0xFF5E9A3A, 0xFF1E2A14, 0xFF2A3A1A);
        hair(0xFF1E1A16);
        colors("G", 0xFF5E9A3A, "g", 0xFF4A7E2E, "P", 0xFF5A2E7A, "p", 0xFF3E1E56);
        head("short", "none", false);
        face(HEAD, Face.FRONT, rowsOf(8, "........", 3, "HbbSSbbH", 6, "SSMMMMSS"));
        wrap(BODY, (f, i, j) -> j >= 10 ? 'P' : ((i + j) % 5 == 0 ? 'g' : 'G'));
        top(BODY, 'G');
        face(BODY, Face.FRONT, rowsOf(12, "........", 2, "GgGGGGgG", 3, "GGgGGgGG", 5, "GGGggGGG", 7, "GGGggGGG"));
        limbs("GGGGGgGGGGGG", "PPPPPPpPGGGG");
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            face(leg, Face.FRONT, rowsOf(12, "....", 7, "PpPp", 8, "pGpG"));
        }
    }

    /** Spider-Man: red with black web lines, big white eyes, blue sides, a black spider on the chest. */
    static void spiderMan() {
        tone(0xFFC02028, 0xFF1A1418, 0xFFFFFFFF);
        hair(0xFFC02028);
        colors("R", 0xFFC02028, "r", 0xFF801418, "U", 0xFF2A4AB0, "u", 0xFF1E3480);
        Pattern web = (f, i, j) -> (i == 3 || i == 4 || j % 3 == 0) ? 'r' : 'R';
        wrap(HEAD, web);
        top(HEAD, 'R');
        bottom(HEAD, 'R');
        face(HEAD, Face.FRONT, "RrRrrRrR", "rRRrrRRr", "RKKRRKKR", "KWWKKWWK", "KWWWWWWK".replace("WWWW", "WKKW"), "rKKRRKKr", "RRrRRrRR", "rRRrrRRr");
        wrap(BODY, (f, i, j) -> f == Face.FRONT || f == Face.BACK ? (i == 0 || i == 7 ? 'U' : web.at(f, i, j)) : 'U');
        top(BODY, 'R');
        face(BODY, Face.FRONT, rowsOf(12, "........", 2, ".R.KK.R.", 3, "..KKKK..", 4, ".R.KK.R.", 5, "..K..K.."));
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrap(arm, (f, i, j) -> j >= 9 ? web.at(f, i, j) : (f == Face.FRONT || j < 3 ? web.at(f, i, j) : 'U'));
            top(arm, 'R');
            bottom(arm, 'R');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrap(leg, (f, i, j) -> j >= 8 ? web.at(f, i, j) : (f == Face.FRONT ? 'U' : 'u'));
            top(leg, 'U');
            bottom(leg, 'R');
        }
    }

    /** Black Widow: long red hair, black catsuit, the red hourglass buckle, silver gauntlets. */
    static void blackWidow() {
        tone(0xFFF0C8A8, 0xFF8A3A20, 0xFF3A6A5A);
        hair(0xFFB8402A);
        colors("D", 0xFF1A1A22, "d", 0xFF2C2C36, "R", 0xFFD02828, "V", 0xFFB8BEC8);
        head("long", "none", false);
        wrap(BODY, (f, i, j) -> j == 9 ? 'd' : 'D');
        top(BODY, 'D');
        face(BODY, Face.FRONT, rowsOf(12, "DDDDDDDD", 0, "DdDDDDdD", 9, "dddRRddd", 10, "DDDRRDDD"));
        limbs("DDDDDDDVVVDD", "DDDDDDDDDddd");
    }

    /** Hawkeye: short blond hair, purple-and-black tactical suit, quiver strap across the chest. */
    static void hawkeye() {
        tone(0xFFE8BC92, 0xFF8A6A30, 0xFF3A5A7A);
        hair(0xFFC9A04A);
        colors("P", 0xFF4A2A6A, "p", 0xFF2E1A44, "D", 0xFF1E1E24, "L", 0xFF6B4A2F);
        head("short", "stubble", false);
        wrap(BODY, (f, i, j) -> j == 9 ? 'D' : (i < 2 || i > 5 ? 'D' : 'P'));
        top(BODY, 'P');
        face(BODY, Face.FRONT, rowsOf(12, "........", 1, "L.......", 2, ".L......", 3, "..L.....", 4, "...L....", 5, "....L...", 6, ".....L.."));
        face(BODY, Face.BACK, rowsOf(12, "........", 1, ".LLL....", 2, ".LWL....", 3, ".LLL....", 4, ".LLL....", 5, ".LLL...."));
        limbs("PPPPDDDDDDLL", "DDDDDDDDDppp");
    }

    /** S.H.I.E.L.D. agent: black suit, white shirt, dark tie, sunglasses. */
    static void agent() {
        tone(0xFFD8AA7E, 0xFF2B2420, 0xFF1A1A1A);
        hair(0xFF2B2420);
        colors("D", 0xFF1C1E24, "d", 0xFF2A2C34, "T", 0xFF101216);
        head("short", "none", true);
        wrap(BODY, (f, i, j) -> 'D');
        top(BODY, 'D');
        face(BODY, Face.FRONT, rowsOf(12, "DDDDDDDD", 0, "DDWTTWDD", 1, "DDWTTWDD", 2, "DDDTTDDD", 3, "DDDTTDDD", 4, "DDDTDDDD"));
        limbs("DDDDDDDDDDWS", "DDDDDDDDDDTT");
    }

    // ---------------------------------------------------------------- shared head

    static void tone(int skinTone, int brow, int eyes) {
        pal.put('S', skinTone);
        pal.put('s', shade(skinTone, 0.86));
        pal.put('M', shade(skinTone, 0.68));
        pal.put('b', brow);
        pal.put('e', eyes);
    }

    static void hair(int c) {
        pal.put('H', c);
        pal.put('h', shade(c, 0.8));
    }

    /**
     * Styles: short, long, bob, ponytail, bald. Facial: none, stubble, mustache, beard.
     * Side templates run back (col 0) to face (col 7) and are mirrored for the left side.
     */
    static void head(String style, String facial, boolean glasses) {
        boolean framed = style.equals("long") || style.equals("bob");
        String[] front = new String[8];
        String edge = framed ? "H" : "S";
        front[0] = style.equals("bald") ? "SSSSSSSS" : "HHHHHHHH";
        front[1] = style.equals("bald") ? "SSSSSSSS" : (framed ? "HHHHHHHH" : "HHHhhHHH");
        front[2] = style.equals("bald") ? "SSSSSSSS" : (framed ? "HHSSSSHH" : "HSSSSSSH");
        front[3] = edge + "bbSSbb" + edge;
        front[4] = glasses ? "KWeKKeWK" : edge + "WeSSeW" + edge;
        front[5] = edge + "SSssSS" + edge;
        front[6] = edge + "SSMMSS" + edge;
        front[7] = edge + "SSSSSS" + edge;
        if (style.equals("bob")) {
            front[6] = "S" + front[6].substring(1, 7) + "S";
            front[7] = "SSSSSSSS";
        }
        String[] side;
        String[] back;
        switch (style) {
            case "long" -> {
                side = new String[]{"HHHHHHHH", "HHHHHHHH", "HHhHHHHH", "HHHHHHHH", "HHHHhHHH", "HhHHHHHH", "HHHHHHHH", "hHHHHHHh"};
                back = new String[]{"HHHHHHHH", "HHHHHHHH", "HhHHHHhH", "HHHHHHHH", "HHHhHHHH", "HHHHHHHH", "HhHHHHhH", "hhhhhhhh"};
            }
            case "bob" -> {
                side = new String[]{"HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHH", "SSSSSSSS", "SSSSSSSS"};
                back = new String[]{"HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHhHHHH", "HHHHHHHH", "hhhhhhhh", "SSSSSSSS", "SSSSSSSS"};
            }
            case "bald" -> {
                side = new String[]{"SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "HSSSSSSS", "HHSsSSSS", "HHSsSSSS", "HSSSSSSS", "SSSSSSSS"};
                back = new String[]{"SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "SSSSSSSS", "SSSSSSSS"};
            }
            default -> { // short, ponytail
                side = new String[]{"HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHSSSS", "HHHsSSSS", "HHHsSSSS", "HHSSSSSS", "HSSSSSSS"};
                back = style.equals("ponytail")
                        ? new String[]{"HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHhhHHH", "HHHhhHHH", "HHHhhHHH", "SSShhSSS", "SSShhSSS"}
                        : new String[]{"HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "SSSSSSSS"};
            }
        }
        side = side.clone();
        switch (facial) {
            case "stubble" -> {
                front[6] = front[6].substring(0, 1) + "ssMMss" + front[6].charAt(7);
                front[7] = front[7].substring(0, 1) + "ssssss" + front[7].charAt(7);
            }
            case "mustache" -> front[6] = front[6].substring(0, 1) + "SHHHHS" + front[6].charAt(7);
            case "beard" -> {
                front[5] = front[5].substring(0, 1) + "SSssSS" + front[5].charAt(7);
                front[6] = "HHHMMHHH";
                front[7] = "HHHHHHHH";
                for (int j = 4; j < 8; j++) side[j] = side[j].substring(0, 5) + "HHH";
            }
            default -> {
            }
        }
        if (glasses) side[4] = side[4].substring(0, 4) + "KKKK";
        face(HEAD, Face.FRONT, front);
        face(HEAD, Face.RIGHT, side);
        face(HEAD, Face.LEFT, mirror(side));
        face(HEAD, Face.BACK, back);
        top(HEAD, style.equals("bald") ? 'S' : 'H');
        bottom(HEAD, 's');
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
