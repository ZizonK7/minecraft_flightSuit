import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the Three Kingdoms raiders as 64x64 classic (4px arm) player skins (DESIGN.md 4-11, M10): a foot
 * soldier per kingdom (lamellar in the kingdom colour) and the named generals.
 *
 * Usage: java tools/FactionSkinGen.java <out dir>   (src/main/resources/assets/flightsuit/textures/entity/kingdom)
 *
 * Same painting helpers as ResidentSkinGen (copied, so each tool runs on its own): colours are letters looked
 * up in the current palette; '.' leaves a pixel transparent.
 */
public class FactionSkinGen {
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
        for (String name : new String[]{"soldier_wei", "soldier_shu", "soldier_wu", "guan_yu", "zhang_fei", "xiahou_dun", "gan_ning"}) {
            skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            pal = new HashMap<>();
            pal.put('W', 0xFFFFFFFF);
            pal.put('K', 0xFF1E1A1A);
            switch (name) {
                case "soldier_wei" -> soldier(0xFF2F4F8F, 0xFF1F3466, 0xFF6F8FCF);
                case "soldier_shu" -> soldier(0xFF2F7A3A, 0xFF1F5426, 0xFF6FB86F);
                case "soldier_wu" -> soldier(0xFFA8322A, 0xFF74201B, 0xFFE0705F);
                case "guan_yu" -> guanYu();
                case "zhang_fei" -> zhangFei();
                case "xiahou_dun" -> xiahouDun();
                case "gan_ning" -> ganNing();
                default -> throw new IllegalStateException(name);
            }
            File out = new File(dir, name + ".png");
            ImageIO.write(skin, "png", out);
            System.out.println("wrote " + out);
        }
    }

    // ---------------------------------------------------------------- shared armour pieces

    /** Rows of small plates (찰갑): a light edge on each plate, staggered every other row. */
    static char lamellar(Face f, int i, int j) {
        int shift = (j / 2) % 2 == 0 ? 0 : 1;
        if (j % 2 == 1) return 'l';
        return (i + shift) % 2 == 0 ? 'P' : 'p';
    }

    /** Lamellar body over a dark tunic, a gold-buckled belt, kingdom-colour sleeves, wrapped trousers, boots. */
    static void armour(String bodyFront, String bodyBack) {
        Pattern plates = FactionSkinGen::lamellar;
        wrap(BODY, plates);
        top(BODY, 'P');
        if (bodyFront != null) face(BODY, Face.FRONT, bodyFront.split("/"));
        if (bodyBack != null) face(BODY, Face.BACK, bodyBack.split("/"));
        for (Face f : SIDES) rows(BODY, f, 9, 10, 'B');
        face(BODY, Face.FRONT, rowsOf(12, "........", 9, "BBBGGBBB"));
        for (Face f : SIDES) rows(BODY, f, 10, 12, 'T');
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrapRows(arm, "PPPllTTTTMMS");
            top(arm, 'P');
            bottom(arm, 'S');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, "TTTTTwwwwwKK");
            top(leg, 'T');
            bottom(leg, 'K');
        }
    }

    static void soldier(int colour, int dark, int light) {
        tone(0xFFC89A6E, 0xFF2B2420, 0xFF2A1A10);
        hair(0xFF201A16);
        colors("P", colour, "p", dark, "l", light, "B", 0xFF4A3424, "G", 0xFFD9B54A, "T", 0xFF3A332C, "M", 0xFF6E6043,
                "w", 0xFFBDB29A, "R", 0xFFC0392B, "s", 0xFFA07850);
        head("short", "none", false);
        armour(null, null);
        // Helmet: kingdom-colour bowl with a metal rim and a red tassel on top.
        top(HAT, (f, i, j) -> (i == 3 || i == 4) && (j == 3 || j == 4) ? 'R' : 'P');
        wrap(HAT, (f, i, j) -> j > 2 ? '.' : (j == 2 ? 'w' : (j == 0 ? 'p' : 'P')));
        for (Face f : new Face[]{Face.RIGHT, Face.LEFT}) {
            int[] r = region(HEAD, f);
            for (int j = 3; j < 8; j++) skin.setRGB(f == Face.RIGHT ? r[0] + 6 : r[0] + 1, r[1] + j, pal.get('M'));
        }
    }

    /** 관우: red face, the famous long black beard, green robe with gold trim over armour, green headscarf. */
    static void guanYu() {
        tone(0xFFB8463C, 0xFF1A1414, 0xFF1A0E0A);
        hair(0xFF141010);
        colors("P", 0xFF2F7A3A, "p", 0xFF1F5426, "l", 0xFF5FA05F, "B", 0xFF4A3424, "G", 0xFFD9B54A, "T", 0xFF22402A,
                "M", 0xFF6E6043, "w", 0xFF2F7A3A, "H", 0xFF141010);
        head("long", "beard", false);
        // The beard falls to the chest.
        armour("PGHHHHGP/PGHHHHGP/PGHHHHGP/PGlHHlGP/PGPHHPGP/PGPPPPGP/PGPPPPGP/PGPPPPGP/PGPPPPGP/........", null);
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrapRows(arm, "PPPPGPPPGlSS");
        }
        // Green headscarf on the hat layer with a gold band.
        top(HAT, 'P');
        wrap(HAT, (f, i, j) -> j > 2 ? '.' : (j == 2 ? 'G' : 'P'));
        // Long robe tails over the legs.
        for (Box pants : new Box[]{R_PANTS, L_PANTS}) {
            wrapRows(pants, "PPPPPPPPGG..");
        }
    }

    /** 장비: wild bristling beard, glaring eyes, black armour with steel studs, black headband. */
    static void zhangFei() {
        tone(0xFF9C6A44, 0xFF100C0C, 0xFF0A0606);
        hair(0xFF100C0C);
        colors("P", 0xFF2A2A2E, "p", 0xFF16161A, "l", 0xFF8A8F99, "B", 0xFF5A3A22, "G", 0xFFB0B5BF, "T", 0xFF1E1E22,
                "M", 0xFF6E6043, "w", 0xFF6A6F78, "R", 0xFF8E2A20);
        head("short", "beard", false);
        // Bulging eyes: whites around the pupils, brows knotted.
        face(HEAD, Face.FRONT, rowsOf(8, "........", 3, "HbbSSbbH", 4, "HWeSSeWH"));
        // Beard bristles out at the sides.
        face(HEAD, Face.RIGHT, rowsOf(8, "........", 5, "....HHHH", 6, "...HHHHH", 7, "...HHHHH"));
        face(HEAD, Face.LEFT, rowsOf(8, "........", 5, "HHHH....", 6, "HHHHH...", 7, "HHHHH..."));
        armour("PlPPPPlP/PPlPPlPP/PPPRRPPP/PPRPPRPP/PPPRRPPP/PlPPPPlP/PPlPPlPP/PlPPPPlP/PPPPPPPP/........", null);
        top(HAT, '.');
        wrap(HAT, (f, i, j) -> j == 1 ? 'R' : '.');
    }

    /** 하후돈: the eyepatch, Wei-blue armour with gold edging, a crested helmet. */
    static void xiahouDun() {
        tone(0xFFC89A6E, 0xFF2B2420, 0xFF2A1A10);
        hair(0xFF241C18);
        colors("P", 0xFF2F4F8F, "p", 0xFF1F3466, "l", 0xFFD9B54A, "B", 0xFF3A2A1E, "G", 0xFFD9B54A, "T", 0xFF1F2A44,
                "M", 0xFF6E6043, "w", 0xFF9AA3B5);
        head("short", "mustache", false);
        // Eyepatch over his left eye (viewer's right), strap across the face.
        face(HEAD, Face.FRONT, rowsOf(8, "........", 3, "KbbSSKKK", 4, "SWeSSKKS"));
        armour("PGPPPPGP/PGPllPGP/PGPPPPGP/PGPllPGP/PGPPPPGP/PGPPPPGP/PGPPPPGP/PGPPPPGP/PGPPPPGP/........", null);
        top(HAT, (f, i, j) -> i == 3 || i == 4 ? 'G' : 'P');
        wrap(HAT, (f, i, j) -> j > 2 ? '.' : (j == 2 ? 'G' : 'P'));
    }

    /** 감녕: the river pirate - bare tattooed arms, red vest, bells on the belt, feathered headband. */
    static void ganNing() {
        tone(0xFFB07A50, 0xFF2B2420, 0xFF2A1A10);
        hair(0xFF3A2418);
        colors("P", 0xFFA8322A, "p", 0xFF74201B, "l", 0xFFE0705F, "B", 0xFF4A3424, "G", 0xFFE8C66A, "T", 0xFF3A2E24,
                "M", 0xFF6E6043, "w", 0xFFBDB29A, "I", 0xFF2E4F6E, "F", 0xFFF2F2F2);
        head("ponytail", "stubble", false);
        armour("PPSSSSPP/PPSSSSPP/PPSSSSPP/PPPSSPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP/PPPPPPPP/........", null);
        face(BODY, Face.FRONT, rowsOf(12, "........", 9, "BGBGBGBG"));
        // Bare arms with river-blue tattoos.
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrap(arm, (f, i, j) -> j < 2 ? 'P' : ((i + j) % 3 == 0 && j < 9 ? 'I' : 'S'));
            wrapRows(arm, "PP........MM");
        }
        top(HAT, '.');
        wrap(HAT, (f, i, j) -> j == 1 ? 'P' : (j == 0 && f == Face.RIGHT && i < 2 ? 'F' : '.'));
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
