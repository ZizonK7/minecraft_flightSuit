import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws Batman's crew (DESIGN.md 4-14, M14): the three thieves' 64x64 classic player skins, the items they leave
 * or drop (bat mark, batarang, grapple, smoke bomb) and the security sensor block.
 *
 * Usage: java tools/ThiefGen.java <assets/flightsuit/textures dir>
 *   writes entity/thief/{batman,catwoman,robin}.png, item/{bat_mark,batarang,grapple,smoke_bomb}.png,
 *   block/security_sensor_{side,side_on,top}.png
 *
 * Skin painting helpers are copied from HeroSkinGen (colours are letters looked up in the current palette).
 */
public class ThiefGen {
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
        File entity = new File(root, "entity/thief");
        File item = new File(root, "item");
        File block = new File(root, "block");
        entity.mkdirs();
        item.mkdirs();
        block.mkdirs();
        for (String name : new String[]{"batman", "catwoman", "robin"}) {
            skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            pal = new HashMap<>();
            pal.put('W', 0xFFFFFFFF);
            pal.put('K', 0xFF141216);
            switch (name) {
                case "batman" -> batman();
                case "catwoman" -> catwoman();
                case "robin" -> robin();
                default -> throw new IllegalStateException(name);
            }
            write(skin, new File(entity, name + ".png"));
        }
        write(icon(BAT_MARK, Map.of('Y', 0xFFF2C230, 'y', 0xFFC8961E, 'K', 0xFF141216)), new File(item, "bat_mark.png"));
        write(icon(BATARANG, Map.of('K', 0xFF1C1C22, 'G', 0xFF4A4C56, 'g', 0xFF7A7E8A)), new File(item, "batarang.png"));
        write(icon(GRAPPLE, Map.of('K', 0xFF1C1C22, 'G', 0xFF3A3C44, 'V', 0xFFC8CDD8, 'v', 0xFF8C93A0, 'Y', 0xFFF2C230)), new File(item, "grapple.png"));
        write(icon(SMOKE_BOMB, Map.of('K', 0xFF18181C, 'G', 0xFF3A3C44, 'g', 0xFF5A5C66, 'w', 0xFFB8BCC4, 'W', 0xFFE4E6EA, 'R', 0xFFD03A2A)), new File(item, "smoke_bomb.png"));
        write(sensorSide(false), new File(block, "security_sensor_side.png"));
        write(sensorSide(true), new File(block, "security_sensor_side_on.png"));
        write(sensorTop(), new File(block, "security_sensor_top.png"));
    }

    static void write(BufferedImage img, File out) throws IOException {
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    // ---------------------------------------------------------------- skins

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

    /** Batman: grey suit, black bat on the chest, yellow utility belt, black cowl with ears and a long black cape. */
    static void batman() {
        tone(0xFFE0B48A, 0xFF141216, 0xFFFFFFFF);
        hair(0xFF141216);
        colors("G", 0xFF4A4C56, "g", 0xFF3A3C44, "Y", 0xFFE8B830, "y", 0xFFB88A1E, "C", 0xFF18181E, "c", 0xFF26262E);
        head("short", "none", false);
        wrap(BODY, (f, i, j) -> f == Face.BACK ? (j == 9 ? 'Y' : (i % 3 == 0 ? 'c' : 'C')) : (j == 9 ? (i % 2 == 0 ? 'Y' : 'y') : 'G'));
        top(BODY, 'C');
        face(BODY, Face.FRONT, "CGGGGGGC", "GGGGGGGG", "GKGKKGKG", "KKKKKKKK", "GKKKKKKG", "GGGKKGGG", "GGGGGGGG", "gGGGGGGg",
                "GGGGGGGG", "YyYYYYyY", "gggggggg", "gggggggg");
        limbs("CCCCGGGGCCCC", "GGGGGGGgCCCC");
        // Cowl over the head: black down to the nose, the mouth and chin show, two ears on top, white eyes.
        top(HAT, 'C');
        face(HAT, Face.FRONT, "C......C", "CCCCCCCC", "CCCCCCCC", "CCCCCCCC", "CWWCCWWC", "CCCssCCC", "........", "........");
        face(HAT, Face.RIGHT, rowsOf(8, "CCCCCCCC", 6, "CCCCC...", 7, "CCCC...."));
        face(HAT, Face.LEFT, rowsOf(8, "CCCCCCCC", 6, "...CCCCC", 7, "....CCCC"));
        face(HAT, Face.BACK, rowsOf(8, "CCCCCCCC"));
        // Cape down the back, over the jacket layer.
        face(JACKET, Face.BACK, rowsOf(12, "CCCCCCCC", 11, "cCcCcCcC"));
        face(JACKET, Face.RIGHT, rowsOf(12, "C...", 11, "c..."));
        face(JACKET, Face.LEFT, rowsOf(12, "...C", 11, "...c"));
    }

    /** Catwoman: black catsuit with a zip, short black hair, cat-ear cowl, goggles pushed up, silver claws. */
    static void catwoman() {
        tone(0xFFF0C8A8, 0xFF1E1A1A, 0xFF4AA84A);
        hair(0xFF1E1A1A);
        colors("D", 0xFF18181E, "d", 0xFF2E2E38, "V", 0xFFC8CDD8, "R", 0xFFB01E30, "O", 0xFF7A7E8A);
        head("bob", "none", false);
        face(HEAD, Face.FRONT, rowsOf(8, "........", 6, "HSSRRSSH"));
        wrap(BODY, (f, i, j) -> (i + j) % 6 == 0 ? 'd' : 'D');
        top(BODY, 'D');
        face(BODY, Face.FRONT, rowsOf(12, "DDDdDDDD", 0, "DDdddDDD", 1, "DDDVdDDD", 9, "dddddddd"));
        limbs("DDDDdDDDDDDV", "DDDDdDDDDDDD");
        top(HAT, 'D');
        face(HAT, Face.FRONT, "DD....DD", "DDDDDDDD", "D......D", "D......D", "D......D", "........", "........", "........");
        face(HAT, Face.RIGHT, rowsOf(8, "DDDDDDDD", 4, "DDDD....", 5, "DDD.....", 6, "........", 7, "........"));
        face(HAT, Face.LEFT, rowsOf(8, "DDDDDDDD", 4, "....DDDD", 5, ".....DDD", 6, "........", 7, "........"));
        face(HAT, Face.BACK, rowsOf(8, "DDDDDDDD", 6, "........", 7, "........"));
    }

    /** Robin: red tunic with the R, green sleeves and trunks, black mask, yellow cape lining, black boots. */
    static void robin() {
        tone(0xFFE8BC92, 0xFF1E1A16, 0xFFFFFFFF);
        hair(0xFF1E1A16);
        colors("R", 0xFFC0242A, "r", 0xFF8A1A1E, "N", 0xFF2E7A3A, "n", 0xFF1E5A28, "Y", 0xFFF2C230, "y", 0xFFC8961E, "L", 0xFF141216);
        head("short", "none", false);
        face(HEAD, Face.FRONT, rowsOf(8, "........", 3, "HKKSSKKH", 4, "SKWKKWKS"));
        wrap(BODY, (f, i, j) -> f == Face.BACK ? (j < 1 ? 'R' : (i % 3 == 0 ? 'y' : 'Y')) : (j == 9 ? 'Y' : (j > 9 ? 'N' : 'R')));
        top(BODY, 'R');
        face(BODY, Face.FRONT, "RRRRRRRR", "rRRRRRRr", "RKKKRRRR", "RKYKRRRR", "RKKKRRRR", "RKYKRRRR", "RKRKRRRR", "RRRRRRRR",
                "RRRRRRRR", "YYYYYYYY", "NNNNNNNN", "nNNNNNNn");
        limbs("RRRNNNNNNNnn", "NNNNSSSSLLLL");
    }

    // ---------------------------------------------------------------- items and block

    static final String[] BAT_MARK = {
            "................",
            "................",
            "....yYYYYYYy....",
            "..yYYYYYYYYYYy..",
            ".yYYYYKYYKYYYYy.",
            ".YYYYYKKKKYYYYY.",
            "yYKKYKKKKKKYKKYy",
            "YKKKKKKKKKKKKKKY",
            "YKKKKKKKKKKKKKKY",
            "yYKKKKKKKKKKKKYy",
            ".YYKYKKYYKKYKYY.",
            ".yYYYYYYYYYYYYy.",
            "..yYYYYYYYYYYy..",
            "....yYYYYYYy....",
            "................",
            "................"};
    static final String[] BATARANG = {
            "................",
            "................",
            "................",
            "................",
            "......g..g......",
            "......GggG......",
            "K....GGGGGG....K",
            "KK..GGGGGGGG..KK",
            "KGGGGGGGGGGGGGGK",
            ".KGGGGKGGKGGGGK.",
            "..KKGK.KK.KGKK..",
            "....K......K....",
            "................",
            "................",
            "................",
            "................"};
    static final String[] GRAPPLE = {
            "..............v.",
            ".............vVv",
            "............vVv.",
            "...........vVv..",
            "..........VVv...",
            ".........GGV....",
            "........GGGG....",
            ".......GGGGG....",
            "......GGGGG.....",
            ".....GGGGGY.....",
            "....KGGGGY......",
            "...KKKGG........",
            "..KKKK.G........",
            "..KKK...........",
            "...K............",
            "................"};
    static final String[] SMOKE_BOMB = {
            "..........w.W...",
            "........W..w....",
            ".........ww.....",
            "........RR......",
            ".......GGGG.....",
            ".....KKKKKKK....",
            "....KKgKKKKKK...",
            "...KKgKKKKKKKK..",
            "...KgKKKKKKKKK..",
            "...KKKKKKKKKKK..",
            "...KKKKKKKKKKK..",
            "...KKKKKKKKKKK..",
            "....KKKKKKKKK...",
            ".....KKKKKKK....",
            "................",
            "................"};

    static BufferedImage icon(String[] rows, Map<Character, Integer> colours) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                char c = rows[y].charAt(x);
                if (c != '.') {
                    img.setRGB(x, y, colours.get(c));
                }
            }
        }
        return img;
    }

    /** Dark steel plate with rivets, a lens in the middle: dim when unpowered, glowing red when armed. */
    static BufferedImage sensorSide(boolean armed) {
        BufferedImage img = sensorPlate();
        int[][] lens = {{6, 6}, {7, 6}, {8, 6}, {9, 6}, {5, 7}, {6, 7}, {7, 7}, {8, 7}, {9, 7}, {10, 7}, {5, 8}, {6, 8}, {7, 8}, {8, 8},
                {9, 8}, {10, 8}, {6, 9}, {7, 9}, {8, 9}, {9, 9}};
        for (int[] p : lens) {
            img.setRGB(p[0], p[1], armed ? 0xFFE0302A : 0xFF3A2224);
        }
        for (int[] p : new int[][]{{7, 7}, {8, 7}, {7, 8}}) {
            img.setRGB(p[0], p[1], armed ? 0xFFFFA090 : 0xFF5A3A3C);
        }
        for (int x = 4; x <= 11; x++) {
            img.setRGB(x, 5, 0xFF1A1C22);
            img.setRGB(x, 10, 0xFF1A1C22);
        }
        return img;
    }

    static BufferedImage sensorTop() {
        BufferedImage img = sensorPlate();
        for (int x = 3; x <= 12; x++) {
            for (int y = 3; y <= 12; y++) {
                if ((x + y) % 3 == 0) {
                    img.setRGB(x, y, 0xFF1A1C22);
                }
            }
        }
        return img;
    }

    static BufferedImage sensorPlate() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
                int c = edge ? 0xFF22242A : ((x * 7 + y * 13) % 11 == 0 ? 0xFF3E414A : 0xFF353840);
                img.setRGB(x, y, c);
            }
        }
        for (int[] r : new int[][]{{2, 2}, {13, 2}, {2, 13}, {13, 13}}) {
            img.setRGB(r[0], r[1], 0xFF8C93A0);
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
