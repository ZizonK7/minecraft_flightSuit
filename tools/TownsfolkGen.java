import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the townsfolk of the Three Kingdoms fortresses and Hero City (after the M16 test: "make them real
 * towns"): 64x64 skins - fort farmer, merchant, blacksmith, cook, elder, child; city office worker, scientist,
 * police officer, shopkeeper, citizen, child.
 *
 * Usage: java tools/TownsfolkGen.java <assets/flightsuit/textures dir>
 *
 * Skin painting helpers are copied from ThanosGen (colours are letters looked up in the current palette).
 */
public class TownsfolkGen {
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
        File dir = new File(new File(args[0]), "entity/townsfolk");
        dir.mkdirs();
        String[] names = {"fort_farmer", "fort_merchant", "fort_smith", "fort_cook", "fort_elder", "fort_child",
                "city_worker", "city_scientist", "city_police", "city_shopkeeper", "city_citizen", "city_child",
                "dbz_citizen", "dbz_shopkeeper", "dbz_child", "namekian"};
        if (args.length > 1) {
            // Only the skins named (M17: the new ones, without redrawing the rest).
            names = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        for (String name : names) {
            skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            pal = new HashMap<>();
            pal.put('W', 0xFFFFFFFF);
            pal.put('K', 0xFF141216);
            switch (name) {
                case "fort_farmer" -> fortFarmer();
                case "fort_merchant" -> fortMerchant();
                case "fort_smith" -> fortSmith();
                case "fort_cook" -> fortCook();
                case "fort_elder" -> fortElder();
                case "fort_child" -> fortChild();
                case "city_worker" -> cityWorker();
                case "city_scientist" -> cityScientist();
                case "city_police" -> cityPolice();
                case "city_shopkeeper" -> cityShopkeeper();
                case "city_citizen" -> cityCitizen();
                case "city_child" -> cityChild();
                case "dbz_citizen" -> dbzCitizen();
                case "dbz_shopkeeper" -> dbzShopkeeper();
                case "dbz_child" -> dbzChild();
                case "namekian" -> namekian();
                default -> throw new IllegalStateException(name);
            }
            ImageIO.write(skin, "png", new File(dir, name + ".png"));
        }
        System.out.println("wrote townsfolk skins");
    }

    static final int TONE_EAST = 0xFFE8C4A0, TONE_TAN = 0xFFC99A72, TONE_DARK = 0xFF8A5A3C, TONE_FAIR = 0xFFF0D2B8;

    /** A plain tunic or shirt: colour A, its fold B on the edges, a belt row. */
    static void tunic(char a, char b, int beltRow, char belt) {
        wrap(BODY, (f, i, j) -> j == beltRow ? belt : (f == Face.FRONT && (i == 0 || i == 7)) ? b : a);
        top(BODY, a);
    }

    // ---------------------------------------------------------------- Three Kingdoms townsfolk

    /** Farmer: wide straw hat, brown hemp tunic with a rope belt, rolled trousers, bare shins and straw sandals. */
    static void fortFarmer() {
        tone(TONE_TAN, 0xFF2A1A10, 0xFF2A1A10);
        hair(0xFF1A1410);
        colors("A", 0xFF8A6A44, "B", 0xFF6A4E30, "R", 0xFFC8B070, "Y", 0xFFD8B860, "y", 0xFFA8883A, "P", 0xFF5A4630);
        head("short", "stubble", false);
        top(HAT, (f, i, j) -> (i + j) % 3 == 0 ? 'y' : 'Y');
        ring(HAT, "YY");
        tunic('A', 'B', 7, 'R');
        limbs("AAAAASSSSSSS", "PPPPPPSSSSyy");
    }

    /** Merchant: deep blue robe with gold trim down the front, a small black cap, a neat mustache. */
    static void fortMerchant() {
        tone(TONE_EAST, 0xFF1A1410, 0xFF1A1410);
        hair(0xFF141012);
        colors("A", 0xFF2A3A7A, "B", 0xFF1A2650, "G", 0xFFC8A040, "C", 0xFF1A1A1E);
        head("short", "mustache", false);
        top(HAT, 'C');
        ring(HAT, "C");
        wrap(BODY, (f, i, j) -> f == Face.FRONT && (i == 3 || i == 4) ? 'G' : j == 6 ? 'B' : 'A');
        top(BODY, 'A');
        limbs("AAAAAAAAAGSS", "AAAAAAAABBCC");
    }

    /** Blacksmith: red headband, bare strong arms, a leather apron over a dark shirt, heavy boots. */
    static void fortSmith() {
        tone(TONE_TAN, 0xFF1A1410, 0xFF1A1410);
        hair(0xFF141012);
        colors("D", 0xFF3A3A40, "L", 0xFF6A4424, "l", 0xFF4A2E18, "R", 0xFFB02A20, "O", 0xFF2A2420);
        head("short", "beard", false);
        ring(HAT, ".R");
        wrap(BODY, (f, i, j) -> f == Face.FRONT && i >= 1 && i <= 6 && j >= 2 ? (j % 4 == 0 ? 'l' : 'L') : 'D');
        top(BODY, 'D');
        limbs("DDSSSSSSSSSS", "DDDDDDDDOOOO");
    }

    /** Cook: white headscarf, a white apron over a beige jacket, rolled sleeves. */
    static void fortCook() {
        tone(TONE_EAST, 0xFF2A1A10, 0xFF2A1A10);
        hair(0xFF1A1410);
        colors("A", 0xFFC8B490, "B", 0xFF9A8A6A, "w", 0xFFE8E8E0, "P", 0xFF5A5040);
        head("short", "none", false);
        top(HAT, 'w');
        ring(HAT, "w");
        wrap(BODY, (f, i, j) -> f == Face.FRONT && i >= 1 && i <= 6 && j >= 3 ? 'w' : 'A');
        top(BODY, 'A');
        limbs("AAAASSSSSSSS", "PPPPPPPPPPBB");
    }

    /** Elder: grey hair in a topknot, long grey beard, a long sage-green robe with a dark sash, a walking-stick hand. */
    static void fortElder() {
        tone(TONE_EAST, 0xFF9A9A98, 0xFF2A1A10);
        hair(0xFFB8B8B4);
        colors("A", 0xFF6A7A5A, "B", 0xFF4A5640, "X", 0xFF2A2A30);
        head("bald", "beard", false);
        top(HAT, (f, i, j) -> i >= 3 && i <= 4 && j >= 3 && j <= 4 ? 'H' : '.');
        wrap(BODY, (f, i, j) -> j == 6 ? 'X' : f == Face.FRONT && (i == 3 || i == 4) ? 'B' : 'A');
        top(BODY, 'A');
        limbs("AAAAAAAAAASS", "AAAAAAAAAABB");
        face(JACKET, Face.FRONT, rowsOf(12, "........", 8, "AAAAAAAA", 9, "AAAAAAAA", 10, "AAAAAAAA", 11, "BBBBBBBB"));
    }

    /** A child: two little hair buns, a light red jacket, short trousers. */
    static void fortChild() {
        tone(TONE_EAST, 0xFF1A1410, 0xFF1A1410);
        hair(0xFF141012);
        colors("A", 0xFFD86A5A, "B", 0xFFB04A3A, "P", 0xFF4A5A7A);
        head("short", "none", false);
        top(HAT, (f, i, j) -> (i == 1 || i == 6) && j >= 2 && j <= 4 ? 'H' : '.');
        tunic('A', 'B', 8, 'B');
        limbs("AAAAASSSSSSS", "PPPPPSSSSSSK");
    }

    // ---------------------------------------------------------------- Hero City citizens

    /** Office worker: dark grey suit, white shirt and a red tie, black shoes, neat hair. */
    static void cityWorker() {
        tone(TONE_FAIR, 0xFF3A2A1A, 0xFF3A5A8A);
        hair(0xFF4A3420);
        colors("A", 0xFF3A3E46, "w", 0xFFF0F0F0, "R", 0xFFB01E24, "P", 0xFF2A2E36);
        head("short", "none", false);
        wrap(BODY, (f, i, j) -> f == Face.FRONT && i >= 3 && i <= 4 ? (j >= 1 && j <= 8 ? (i == 3 && j < 8 ? 'R' : 'w') : 'w')
                : f == Face.FRONT && (i == 2 || i == 5) && j < 3 ? 'w' : 'A');
        top(BODY, 'A');
        limbs("AAAAAAAAAAwS", "PPPPPPPPPPKK");
    }

    /** Scientist: a long white lab coat open over a blue shirt, glasses, a pen in the pocket. */
    static void cityScientist() {
        tone(TONE_EAST, 0xFF1A1410, 0xFF1A1410);
        hair(0xFF1A1410);
        colors("w", 0xFFF2F2EE, "g", 0xFFC8C8C4, "C", 0xFF4A7AB8, "P", 0xFF3A3A44, "b", 0xFF2A4AA8);
        head("bob", "none", true);
        wrap(BODY, (f, i, j) -> f == Face.FRONT && i >= 3 && i <= 4 ? 'C' : f == Face.FRONT && i == 1 && j == 3 ? 'b' : (i + j) % 7 == 0 ? 'g' : 'w');
        top(BODY, 'w');
        limbs("wwwwwwwwwwgS", "PPPPPPPPPPKK");
        face(JACKET, Face.FRONT, rowsOf(12, "........", 9, "www..www", 10, "www..www", 11, "ggg..ggg"));
    }

    /** Police: navy uniform with a gold badge, a peaked cap, a black belt, dark trousers. */
    static void cityPolice() {
        tone(TONE_TAN, 0xFF1A1410, 0xFF1A1410);
        hair(0xFF1A1410);
        colors("N", 0xFF1E2A50, "n", 0xFF141C38, "G", 0xFFE0B040, "X", 0xFF0E0E10);
        head("short", "none", false);
        top(HAT, 'N');
        ring(HAT, "NNn");
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, "NNNGGNNN", 1, "NNNNNNNN", 2, "nnnnnnnn"));
        wrap(BODY, (f, i, j) -> j == 7 ? 'X' : f == Face.FRONT && i == 2 && j == 2 ? 'G' : 'N');
        top(BODY, 'N');
        limbs("NNNNNNNNNNNS", "nnnnnnnnnnXX");
    }

    /** Shopkeeper: a green apron over a striped shirt, rolled sleeves, a friendly face. */
    static void cityShopkeeper() {
        tone(TONE_FAIR, 0xFF5A3A20, 0xFF3A6A3A);
        hair(0xFF7A4A24);
        colors("A", 0xFF2E8A4A, "a", 0xFF1E6A36, "w", 0xFFE8E8E8, "r", 0xFFC85A5A, "P", 0xFF4A4A56);
        head("ponytail", "none", false);
        wrap(BODY, (f, i, j) -> f == Face.FRONT && i >= 1 && i <= 6 && j >= 2 ? (j == 2 ? 'a' : 'A') : j % 2 == 0 ? 'w' : 'r');
        top(BODY, 'w');
        limbs("wrwrSSSSSSSS", "PPPPPPPPPPKK");
    }

    /** Citizen: a red hoodie with the hood down, jeans, white trainers. */
    static void cityCitizen() {
        tone(TONE_DARK, 0xFF1A1410, 0xFF1A1410);
        hair(0xFF141012);
        colors("A", 0xFFC0302A, "a", 0xFF902420, "J", 0xFF3A5A8A, "j", 0xFF2A4470);
        head("short", "none", false);
        wrap(BODY, (f, i, j) -> f == Face.FRONT && j >= 7 && j <= 9 && i >= 2 && i <= 5 ? 'a' : f == Face.BACK && j < 3 ? 'a' : 'A');
        top(BODY, 'a');
        limbs("AAAAAAAAAAaS", "JJJJJjJJJJWW");
    }

    /** A city kid: yellow t-shirt, blue shorts, sneakers. */
    static void cityChild() {
        tone(TONE_FAIR, 0xFF6A4A2A, 0xFF3A5A8A);
        hair(0xFF8A5A2A);
        colors("A", 0xFFF0C83A, "B", 0xFFD0A82A, "P", 0xFF3A6AB0);
        head("short", "none", false);
        tunic('A', 'B', 11, 'A');
        limbs("AAASSSSSSSSS", "PPPPPSSSSSWW");
    }

    // ---------------------------------------------------------------- Dragon Ball Earth's West City, Namek (M17)

    /** West City citizen: lavender hair, a blue Capsule Corp jacket (white CC patch on the back), brown trousers. */
    static void dbzCitizen() {
        tone(TONE_FAIR, 0xFF3A3A6A, 0xFF2A2A4A);
        hair(0xFF8A7AD8);
        colors("A", 0xFF2A5AB0, "a", 0xFF1E4488, "w", 0xFFF0F0F0, "P", 0xFF6A4A2E, "p", 0xFF4A321E);
        head("short", "none", false);
        wrap(BODY, (f, i, j) -> f == Face.BACK && j >= 2 && j <= 4 && i >= 2 && i <= 5 ? ((i + j) % 2 == 0 ? 'w' : 'A')
                : f == Face.FRONT && (i == 3 || i == 4) ? 'a' : j == 11 ? 'a' : 'A');
        top(BODY, 'A');
        limbs("AAAAAAAAAAaS", "PPPPPPPPPPpK");
    }

    /** West City shopkeeper: an orange shirt, a yellow cap, a white apron. */
    static void dbzShopkeeper() {
        tone(TONE_TAN, 0xFF1A1410, 0xFF1A1410);
        hair(0xFF1A1410);
        colors("A", 0xFFE8742A, "w", 0xFFF2F2EE, "g", 0xFFC8C8C4, "Y", 0xFFF0C83A, "y", 0xFFC8A02A, "P", 0xFF2E3A5A);
        head("short", "mustache", false);
        top(HAT, 'Y');
        ring(HAT, "YYy");
        wrap(BODY, (f, i, j) -> f == Face.FRONT && i >= 1 && i <= 6 && j >= 2 ? (j == 2 ? 'g' : 'w') : 'A');
        top(BODY, 'A');
        limbs("AAAASSSSSSSS", "PPPPPPPPPPKK");
    }

    /** A West City kid: a little orange training gi with a blue sash, black hair sticking up. */
    static void dbzChild() {
        tone(TONE_FAIR, 0xFF1A1410, 0xFF1A1410);
        hair(0xFF141012);
        colors("A", 0xFFF07A1E, "B", 0xFFC85E14, "N", 0xFF2A4AB0, "n", 0xFF1E3480);
        head("short", "none", false);
        top(HAT, (f, i, j) -> (i + j) % 3 == 0 && j < 4 ? 'H' : '.');
        wrap(BODY, (f, i, j) -> j == 8 ? 'N' : f == Face.FRONT && i == 3 + (j < 6 ? 0 : 1) && j < 8 ? 'B' : 'A');
        top(BODY, 'A');
        limbs("AAAASSSSSSSS", "AAAAAAAAANnn");
    }

    /** A Namekian (after Dende): green skin with pink forearms, antennae, a white robe with a purple sash. */
    static void namekian() {
        tone(0xFF6AB04A, 0xFF3A6A2A, 0xFF141216);
        hair(0xFF5A9A3E);
        colors("w", 0xFFF2F0EA, "g", 0xFFC8C6BE, "V", 0xFF6A3A9A, "v", 0xFF4A2A70, "k", 0xFFE89AA8, "B", 0xFF4A3424);
        head("bald", "none", false);
        // Antennae: two dark stalks on the forehead, drawn on the hat layer.
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, "..s..s..", 1, "..M..M.."));
        top(HAT, (f, i, j) -> (i == 2 || i == 5) && j >= 5 ? 'M' : '.');
        wrap(BODY, (f, i, j) -> j == 7 ? 'V' : j == 8 ? 'v' : f == Face.FRONT && (i == 3 || i == 4) && j < 7 ? 'g' : 'w');
        top(BODY, 'w');
        limbs("wwwwSSSkkkSS", "wwwwwwwwwwBB");
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
