import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the Thanos saga (DESIGN.md 4-16, M16): 64x64 skins for the Chitauri, the Black Order (Ebony Maw,
 * Proxima Midnight, Corvus Glaive, Cull Obsidian), Thanos and Red Skull; icons for the six Infinity Stones and
 * the Infinity Gauntlet (empty and full).
 *
 * Usage: java tools/ThanosGen.java <assets/flightsuit/textures dir>
 *
 * Skin painting helpers are copied from HeroSkinGen (colours are letters looked up in the current palette).
 */
public class ThanosGen {
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
        File root = new File(args[0]);
        File dir = new File(root, "entity/thanos");
        File item = new File(root, "item");
        dir.mkdirs();
        item.mkdirs();
        for (String name : new String[]{"chitauri", "ebony_maw", "proxima_midnight", "corvus_glaive", "cull_obsidian", "thanos", "red_skull"}) {
            skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            pal = new HashMap<>();
            pal.put('W', 0xFFFFFFFF);
            pal.put('K', 0xFF141216);
            switch (name) {
                case "chitauri" -> chitauri();
                case "ebony_maw" -> ebonyMaw();
                case "proxima_midnight" -> proxima();
                case "corvus_glaive" -> corvus();
                case "cull_obsidian" -> cull();
                case "thanos" -> thanos();
                case "red_skull" -> redSkull();
                default -> throw new IllegalStateException(name);
            }
            ImageIO.write(skin, "png", new File(dir, name + ".png"));
        }
        String[] stones = {"space", "mind", "reality", "power", "time", "soul"};
        int[] colours = {0xFF2E6CFF, 0xFFF2D21E, 0xFFE0202A, 0xFF9A3AE8, 0xFF2EC85A, 0xFFF28A1E};
        for (int i = 0; i < stones.length; i++) {
            ImageIO.write(stone(colours[i]), "png", new File(item, stones[i] + "_stone.png"));
        }
        ImageIO.write(gauntlet(null), "png", new File(item, "infinity_gauntlet.png"));
        ImageIO.write(gauntlet(colours), "png", new File(item, "infinity_gauntlet_full.png"));
        System.out.println("wrote thanos saga textures");
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

    /** Chitauri: grey-green hide, bronze armour plates, glowing blue eyes, dark mouthpiece. */
    static void chitauri() {
        tone(0xFF6A7462, 0xFF3A3E36, 0xFF5FE3FF);
        hair(0xFF4A5244);
        colors("G", 0xFF6A7462, "g", 0xFF4A5244, "B", 0xFF8A6A3A, "b", 0xFF5A4424, "E", 0xFF5FE3FF);
        Pattern hide = (f, i, j) -> (i + j * 2) % 5 == 0 ? 'g' : 'G';
        wrap(HEAD, hide);
        top(HEAD, 'B');
        bottom(HEAD, 'g');
        face(HEAD, Face.FRONT, "BBBBBBBB", "BbBBBBbB", "GGGGGGGG", "GEEGGEEG", "GGGggGGG", "gKKKKKKg", "GKbbbbKG", "GGgggGGG");
        wrap(BODY, (f, i, j) -> j < 8 ? ((i + j) % 4 == 0 ? 'b' : 'B') : hide.at(f, i, j));
        top(BODY, 'B');
        limbs("BBBGGGGgGGGg", "GGGGBBBBgggg");
    }

    /** Ebony Maw: grey skin, no nose, slicked back hair, long black robes with a high collar. */
    static void ebonyMaw() {
        tone(0xFFA8A8A0, 0xFF3A3A3A, 0xFFE8E8D8);
        hair(0xFF7A7A72);
        colors("R", 0xFF1E1A22, "r", 0xFF2E2834, "V", 0xFFB8BEC8);
        head("short", "none", false);
        face(HEAD, Face.FRONT, rowsOf(8, "........", 5, "SSSSSSSS", 6, "SSKKKKSS"));
        wrap(BODY, (f, i, j) -> j % 4 == 0 ? 'r' : 'R');
        top(BODY, 'R');
        face(BODY, Face.FRONT, rowsOf(12, "RRRRRRRR", 0, "VRRRRRRV", 1, "RVRRRRVR"));
        limbs("RRRRRRRRRRSS", "RRRRRRRRRRRR");
        face(JACKET, Face.BACK, rowsOf(12, "rRRRRRRr"));
    }

    /** Proxima Midnight: blue-grey skin, horned black crest, dark armour, glowing purple eyes. */
    static void proxima() {
        tone(0xFF7A8AA8, 0xFF1E1E2A, 0xFFC87AF2);
        hair(0xFF141216);
        colors("D", 0xFF1E1E2A, "d", 0xFF34344A, "P", 0xFF6A3AA8);
        head("ponytail", "none", false);
        top(HAT, (f, i, j) -> i == 1 || i == 6 ? 'H' : '.');
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, ".H....H."));
        wrap(BODY, (f, i, j) -> (i + j) % 5 == 0 ? 'd' : 'D');
        top(BODY, 'D');
        face(BODY, Face.FRONT, rowsOf(12, "DDDDDDDD", 2, "DPDDDDPD", 9, "dddPPddd"));
        limbs("DDDDdDDDDDDD", "DDDDDDdDDDDD");
    }

    /** Corvus Glaive: pale grey, gaunt, black-and-gold armour, long dark hair. */
    static void corvus() {
        tone(0xFF9A9A92, 0xFF141216, 0xFFF2D21E);
        hair(0xFF141216);
        colors("D", 0xFF1E1A1E, "Y", 0xFFB8902A, "y", 0xFF7A5E1A);
        head("long", "none", false);
        wrap(BODY, (f, i, j) -> j % 3 == 0 ? 'y' : 'D');
        top(BODY, 'D');
        face(BODY, Face.FRONT, rowsOf(12, "DDYYYYDD", 1, "DYDDDDYD", 9, "YYYYYYYY"));
        limbs("YYDDDDDDDDYY", "DDDDDDDDYYYY");
    }

    /** Cull Obsidian: huge, grey-brown armoured hide, a heavy jaw, bare head with ridges. */
    static void cull() {
        tone(0xFF7A6A5A, 0xFF3A2E24, 0xFFF28A1E);
        hair(0xFF5A4A3A);
        colors("A", 0xFF4A4A52, "a", 0xFF34343A, "B", 0xFF7A6A5A);
        head("bald", "none", false);
        face(HEAD, Face.FRONT, rowsOf(8, "SSSSSSSS", 0, "ShShhShS", 6, "SKKKKKKS", 7, "sssssss" + "s"));
        wrap(BODY, (f, i, j) -> (i + j) % 3 == 0 ? 'a' : 'A');
        top(BODY, 'A');
        limbs("AAAAaBBBBBBB", "AAAAAAaaAAAA");
    }

    /** Thanos: purple skin with the ridged chin, gold-and-blue armour and helmet crest, the gauntlet on the left. */
    static void thanos() {
        tone(0xFF8A6A9A, 0xFF4A3A54, 0xFF2E2A6A);
        hair(0xFF6A4A7A);
        colors("U", 0xFF2A3E8A, "u", 0xFF1E2A5A, "Y", 0xFFE8B830, "y", 0xFFB88A1E, "M", 0xFF6A4A7A, "R", 0xFFE0202A, "P", 0xFF9A3AE8, "G", 0xFF2EC85A, "B", 0xFF2E6CFF, "O", 0xFFF28A1E);
        head("bald", "none", false);
        face(HEAD, Face.FRONT, rowsOf(8, "SSSSSSSS", 3, "SbbSSbbS", 4, "SWeSSeWS", 6, "SMSMMSMS", 7, "MSMSSMSM"));
        top(HAT, 'Y');
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, "YYYYYYYY", 1, "Y......Y"));
        face(HAT, Face.RIGHT, rowsOf(8, "........", 0, "YYYYYYYY", 1, "YY......", 2, "Y......."));
        face(HAT, Face.LEFT, rowsOf(8, "........", 0, "YYYYYYYY", 1, "......YY", 2, ".......Y"));
        face(HAT, Face.BACK, rowsOf(8, "........", 0, "YYYYYYYY", 1, "YYYYYYYY"));
        wrap(BODY, (f, i, j) -> j < 9 ? (j < 2 ? 'Y' : 'U') : 'u');
        top(BODY, 'Y');
        face(BODY, Face.FRONT, rowsOf(12, "UUUUUUUU", 0, "YYYYYYYY", 1, "YyYYYYyY", 3, "UYUUUUYU", 8, "YYYYYYYY", 9, "uuuuuuuu"));
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, "uuuuuuuYYYYY");
            top(leg, 'u');
            bottom(leg, 'Y');
        }
        wrapRows(R_ARM, "YYYSSSSSSSYY");
        top(R_ARM, 'Y');
        bottom(R_ARM, 'Y');
        // The Infinity Gauntlet: gold with the six stones on the knuckles.
        wrap(L_ARM, (f, i, j) -> j < 3 ? 'Y' : (j < 7 ? 'S' : (j == 9 && f == Face.FRONT ? "BGRP".charAt(i) : (j == 10 && f == Face.FRONT && i < 2 ? "OY".charAt(i) : 'Y'))));
        top(L_ARM, 'Y');
        bottom(L_ARM, 'Y');
    }

    /** Red Skull: a red skull face, black coat, the stone keeper on Vormir. */
    static void redSkull() {
        tone(0xFFC0302A, 0xFF5A0E0A, 0xFF141216);
        hair(0xFFC0302A);
        colors("D", 0xFF1E1A1E, "d", 0xFF2E2A2E, "G", 0xFF4A4A52);
        head("bald", "none", false);
        face(HEAD, Face.FRONT, "SSSSSSSS", "SSSSSSSS", "SsSSSSsS", "SKKSSKKS", "SKKSSKKS", "SSSKKSSS", "SsWWWWsS", "SSssssSS");
        face(HEAD, Face.BACK, rowsOf(8, "SSSSSSSS"));
        wrap(BODY, (f, i, j) -> j % 4 == 0 ? 'd' : 'D');
        top(BODY, 'D');
        face(JACKET, Face.BACK, rowsOf(12, "DDDDDDDD", 11, "dDdDdDdD"));
        limbs("DDDDDDDDDDGG", "DDDDDDDDDDDD");
    }

    // ---------------------------------------------------------------- items

    /** A faceted gem: darker rim, bright core, a white glint. */
    static BufferedImage stone(int colour) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y < 14; y++) {
            for (int x = 2; x < 14; x++) {
                double dx = x - 7.5, dy = y - 7.5;
                if (Math.abs(dx) + Math.abs(dy) > 7.0) continue;
                double r = Math.abs(dx) + Math.abs(dy);
                img.setRGB(x, y, r > 5.5 ? shade(colour, 0.6) : (r < 2.5 ? brighten(colour) : colour));
            }
        }
        img.setRGB(6, 5, 0xFFFFFFFF);
        img.setRGB(5, 6, 0xFFFFFFFF);
        return img;
    }

    static int brighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 70), g = Math.min(255, ((c >> 8) & 255) + 70), b = Math.min(255, (c & 255) + 70);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The gauntlet: a gold glove; with the stones, their colours on the knuckles and the back of the hand. */
    static BufferedImage gauntlet(int[] stones) {
        String[] rows = {
                "................",
                "....Y.Y.Y.......",
                "...YyYyYyY......",
                "...YyYyYyY.Y....",
                "...YyYyYyYYy....",
                "...YYYYYYYYy....",
                "...Y1Y2Y3YYY....",
                "...YYYYYYYY.....",
                "...YYY5YYYY.....",
                "...YY4Y6YYY.....",
                "...YYYYYYYY.....",
                "....yYYYYy......",
                "....YYYYYY......",
                "....yyyyyy......",
                "................",
                "................"};
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                char c = rows[y].charAt(x);
                int argb = switch (c) {
                    case 'Y' -> 0xFFE8B830;
                    case 'y' -> 0xFFB88A1E;
                    case '.' -> 0;
                    default -> stones == null ? 0xFF7A5E1A : stones[c - '1'];
                };
                if (argb != 0) img.setRGB(x, y, argb);
            }
        }
        return img;
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
