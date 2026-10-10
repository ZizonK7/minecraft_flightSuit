import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the Dragon Ball Earth people (DESIGN.md 4-16, M15) as 64x64 classic player skins: Goku, Bulma, Raditz,
 * Nappa, Vegeta and the Saibamen; plus the scouter, senzu bean and dragon radar icons.
 *
 * Usage: java tools/DbzSkinGen.java <assets/flightsuit/textures dir>
 *   writes entity/dbz/<id>.png and item/{scouter,senzu_bean}.png
 *
 * Skin painting helpers are copied from HeroSkinGen (colours are letters looked up in the current palette).
 */
public class DbzSkinGen {
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
        File dir = new File(root, "entity/dbz");
        File item = new File(root, "item");
        dir.mkdirs();
        item.mkdirs();
        java.util.List<String> names = new java.util.ArrayList<>(java.util.List.of("goku", "bulma", "raditz", "nappa", "vegeta", "saibaman"));
        names.addAll(java.util.List.of(SAGA));
        for (String name : names) {
            skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            pal = new HashMap<>();
            pal.put('W', 0xFFFFFFFF);
            pal.put('K', 0xFF141216);
            switch (name) {
                case "goku" -> goku();
                case "bulma" -> bulma();
                case "raditz" -> raditz();
                case "nappa" -> nappa();
                case "vegeta" -> vegeta();
                case "saibaman" -> saibaman();
                default -> sagaSkin(name);
            }
            File out = new File(dir, name + ".png");
            ImageIO.write(skin, "png", out);
            System.out.println("wrote " + out);
        }
        ImageIO.write(icon(SCOUTER, Map.of('G', 0xFF3ADB5A, 'g', 0xFF1E8A34, 'V', 0xFFC8CDD8, 'v', 0xFF8C93A0, 'R', 0xFFD03A2A)), "png", new File(item, "scouter.png"));
        ImageIO.write(icon(SENZU, Map.of('G', 0xFF6FBF3A, 'g', 0xFF3E8A24, 'h', 0xFFB8E68A)), "png", new File(item, "senzu_bean.png"));
        for (int stars = 1; stars <= 7; stars++) {
            ImageIO.write(dragonBall(stars), "png", new File(item, "dragon_ball_" + stars + ".png"));
        }
        ImageIO.write(icon(RADAR, Map.of('W', 0xFFF2F1EC, 'w', 0xFFC8C8C2, 'G', 0xFF2E7A3A, 'g', 0xFF56C068, 'Y', 0xFFF2C230, 'K', 0xFF1E1E24)), "png", new File(item, "dragon_radar.png"));
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

    /** Spiky black hair standing up out of the hat layer. */
    static void spikes(char c) {
        top(HAT, (f, i, j) -> (i + j) % 3 == 0 ? c : '.');
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, c + "." + c + c + "." + c + c + ".", 1, "." + c + "......" ));
        face(HAT, Face.RIGHT, rowsOf(8, "........", 0, c + "" + c + "." + c + c + "." + c + c, 1, c + "" + c + c + c + "...."));
        face(HAT, Face.LEFT, rowsOf(8, "........", 0, c + "" + c + "." + c + c + "." + c + c, 1, "...." + c + c + c + c));
        face(HAT, Face.BACK, rowsOf(8, "........", 0, c + "" + c + c + "." + c + c + "." + c, 1, c + "" + c + c + c + c + c + c + c, 2, "." + c + "." + c + c + "." + c + "."));
    }

    /** Goku: orange gi with the blue belt and wristbands, blue undershirt at the neck, wild black hair. */
    static void goku() {
        tone(0xFFF0C8A0, 0xFF141216, 0xFF141216);
        hair(0xFF141216);
        colors("O", 0xFFF07A1E, "o", 0xFFC85A10, "B", 0xFF2A4AB0, "b", 0xFF1E3480, "Y", 0xFFF2C230);
        head("short", "none", false);
        spikes('H');
        wrap(BODY, (f, i, j) -> j == 9 ? 'B' : 'O');
        top(BODY, 'O');
        face(BODY, Face.FRONT, "OBBBBBBO", "OOBBBBOO", "OOOBBOOO", "OOOOOOOO", "OOOOOOOO", "oOOOOOOo", "OOOOOOOO", "OOOOOOOO",
                "OOOOOOOO", "BBBBBBBB", "OOOOOOOO", "oOOOOOOo");
        face(BODY, Face.BACK, rowsOf(12, "OOOOOOOO", 2, "OOWWWWOO", 3, "OOWOOWOO", 4, "OOWWWWOO", 9, "BBBBBBBB"));
        limbs("OOOOSSSSBBSS", "OOOOOOOOBBBB");
    }

    /** Bulma: teal-blue hair in a bob, pink Capsule Corp dress, white boots. */
    static void bulma() {
        tone(0xFFF4D2B4, 0xFF2A7A8A, 0xFF2A6AB0);
        hair(0xFF36A8B8);
        colors("P", 0xFFE87AA8, "p", 0xFFC0587E, "C", 0xFF2A4AB0);
        head("bob", "none", false);
        wrap(BODY, (f, i, j) -> j < 1 ? 'S' : 'P');
        top(BODY, 'P');
        face(BODY, Face.FRONT, rowsOf(12, "PPPPPPPP", 0, "PSSSSSSP", 3, "PPCCCPPP", 4, "PPCPPPPP", 5, "PPCCCPPP", 11, "pppppppp"));
        limbs("PPSSSSSSSSSS", "SSSSSSSWWWWW");
    }

    /** Saiyan battle armour: dark bodysuit, armour over the chest with shoulder flaps, white gloves and boots. */
    static void saiyanArmour(int suit, int plate, int plateShade) {
        colors("D", suit, "A", plate, "a", plateShade);
        wrap(BODY, (f, i, j) -> j < 9 ? (j == 0 && (i == 0 || i == 7) ? 'a' : 'A') : 'D');
        top(BODY, 'A');
        face(BODY, Face.FRONT, rowsOf(12, "AAAAAAAA", 0, "aAAAAAAa", 7, "aAAaaAAa", 8, "AaaaaaaA", 9, "DDDDDDDD"));
        limbs("aaaDDDDDWWWW", "DDDDDDDWWWWW");
    }

    /** Raditz: very long wild black hair down his back, brown-and-black Saiyan armour, a green scouter. */
    static void raditz() {
        tone(0xFFE8BC92, 0xFF141216, 0xFF141216);
        hair(0xFF141216);
        colors("G", 0xFF3ADB5A);
        head("long", "none", false);
        spikes('H');
        face(HEAD, Face.LEFT, rowsOf(8, "HHHHHHHH", 4, "HHHHHGGH"));
        saiyanArmour(0xFF1E1A1E, 0xFF6A4A2A, 0xFF4A3420);
        face(JACKET, Face.BACK, rowsOf(12, "HHHHHHHH", 10, ".HHHHHH.", 11, "..HHHH.."));
    }

    /** Nappa: bald with a black moustache, big, brown-and-white armour. */
    static void nappa() {
        tone(0xFFE8BC92, 0xFF141216, 0xFF141216);
        hair(0xFF141216);
        colors("G", 0xFF3ADB5A);
        head("bald", "mustache", false);
        face(HEAD, Face.BACK, rowsOf(8, "SSSSSSSS", 7, "ssssssss"));
        face(HEAD, Face.RIGHT, rowsOf(8, "SSSSSSSS"));
        face(HEAD, Face.LEFT, rowsOf(8, "SSSSSSSS", 4, "SSSSSGGS"));
        saiyanArmour(0xFF2A2A34, 0xFFE8E4D8, 0xFF8A6A3A);
    }

    /** Vegeta: tall flame-shaped hair, blue bodysuit, white armour with yellow shoulder pads. */
    static void vegeta() {
        tone(0xFFF0C8A0, 0xFF141216, 0xFF141216);
        hair(0xFF141216);
        head("short", "none", false);
        face(HEAD, Face.FRONT, rowsOf(8, "........", 2, "HSSSSSSH", 3, "HbbSSbbH"));
        top(HAT, 'H');
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, "HHHHHHHH", 1, "H.HHHH.H"));
        face(HAT, Face.RIGHT, rowsOf(8, "........", 0, "HHHHHHHH", 1, "HHHHH..."));
        face(HAT, Face.LEFT, rowsOf(8, "........", 0, "HHHHHHHH", 1, "...HHHHH"));
        face(HAT, Face.BACK, rowsOf(8, "........", 0, "HHHHHHHH", 1, "HHHHHHHH"));
        saiyanArmour(0xFF2A3E8A, 0xFFF2F1EC, 0xFFE8B830);
    }

    /** Saibaman: green skin with darker veins, bald knobbly head, big black eyes. */
    static void saibaman() {
        tone(0xFF5AAA3A, 0xFF2E5A1E, 0xFF141216);
        hair(0xFF4A8A2E);
        colors("G", 0xFF5AAA3A, "g", 0xFF3A7A24, "V", 0xFF2E5A1E);
        Pattern veins = (f, i, j) -> (i * 3 + j * 5) % 11 == 0 ? 'V' : ((i + j) % 4 == 0 ? 'g' : 'G');
        wrap(HEAD, veins);
        top(HEAD, veins);
        bottom(HEAD, 'g');
        face(HEAD, Face.FRONT, "GgGGGGgG", "gGVGGVGg", "GGGGGGGG", "GKKGGKKG", "GKKGGKKG", "GGGggGGG", "GVKKKKVG", "GGGGGGGG");
        wrap(BODY, veins);
        top(BODY, 'G');
        for (Box arm : new Box[]{R_ARM, L_ARM, R_LEG, L_LEG}) {
            wrap(arm, veins);
            top(arm, 'G');
            bottom(arm, 'g');
        }
    }

    // ---------------------------------------------------------------- chapters 2-4 (after the M16 test)

    /** Ginyu Force / Frieza Force armour: a dark bodysuit under white plates, the given shoulder colour. */
    static void forceArmour(int suit, int shoulder) {
        saiyanArmour(suit, 0xFFF0EEE6, shoulder);
    }

    /** Namekian head: green skin, two antennae on top, a bald dome. */
    static void namekian(int skin) {
        tone(skin, shade(skin, 0.7), 0xFF141216);
        hair(shade(skin, 0.8));
        head("bald", "none", false);
        top(HAT, (f, i, j) -> (i == 2 || i == 5) && j == 3 ? 'h' : '.');
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, "..h..h.."));
    }

    /** Dende: a young Namekian, a white robe with a purple sash. */
    static void dende() {
        namekian(0xFF6ABF5A);
        colors("w", 0xFFF2F1EC, "P", 0xFF7A3AA8);
        wrap(BODY, (f, i, j) -> j == 7 ? 'P' : 'w');
        top(BODY, 'w');
        limbs("wwwwSSSSSSSS", "wwwwwwwwwwKK");
    }

    /** A Frieza Force soldier: a blue-skinned alien in the white armour with gold shoulders, a scouter. */
    static void friezaSoldier() {
        tone(0xFF6A8AC8, 0xFF2A3A6A, 0xFF141216);
        hair(0xFF4A6AA8);
        colors("G", 0xFF3ADB5A);
        head("bald", "none", false);
        face(HEAD, Face.LEFT, rowsOf(8, "SSSSSSSS", 4, "SSSSSGGS"));
        forceArmour(0xFF2A2A44, 0xFFC8A040);
    }

    /** Dodoria: big, pink, covered in spikes, in the white armour. */
    static void dodoria() {
        tone(0xFFE88AA8, 0xFFB05A7A, 0xFF141216);
        hair(0xFFC86A8A);
        head("bald", "none", false);
        top(HAT, (f, i, j) -> (i + j) % 3 == 0 ? 'h' : '.');
        face(HAT, Face.RIGHT, rowsOf(8, "........", 1, ".h..h..h", 3, "h..h..h."));
        face(HAT, Face.LEFT, rowsOf(8, "........", 1, "h..h..h.", 3, ".h..h..h"));
        forceArmour(0xFF3A2A3A, 0xFF8A6A3A);
    }

    /** Zarbon: pale blue-green skin, a long dark green braid, white armour with blue shoulders, a cape. */
    static void zarbon() {
        tone(0xFF9AD0C0, 0xFF1E4A3A, 0xFF1E4A3A);
        hair(0xFF1E5A3A);
        head("ponytail", "none", false);
        forceArmour(0xFF1E2A5A, 0xFF4A6AC8);
        colors("C", 0xFF3A5AB8);
        face(JACKET, Face.BACK, rowsOf(12, "CCCCCCCC"));
    }

    /** Guldo: short, green, four eyes, purple Ginyu armour. */
    static void guldo() {
        tone(0xFF7ABF5A, 0xFF3A6A2A, 0xFF141216);
        hair(0xFF5A9A3A);
        head("bald", "none", false);
        face(HEAD, Face.FRONT, "SSSSSSSS", "SWeSSeWS", "SSSSSSSS", "SWeSSeWS", "SSSSSSSS", "SSssSSSS", "SSMMMMSS", "SSSSSSSS");
        forceArmour(0xFF1E1A1E, 0xFF8A5AB0);
    }

    /** Recoome: huge, pale, a shock of orange hair, purple Ginyu armour. */
    static void recoome() {
        tone(0xFFF0D4C0, 0xFFC8501E, 0xFF141216);
        hair(0xFFE0601E);
        head("short", "none", false);
        spikes('H');
        forceArmour(0xFF1E1A1E, 0xFF8A5AB0);
    }

    /** Burter: tall, blue-skinned, bald, purple Ginyu armour. */
    static void burter() {
        tone(0xFF5A7AD8, 0xFF2A3A8A, 0xFFE02A2A);
        hair(0xFF4A6AC8);
        head("bald", "none", false);
        forceArmour(0xFF1E1A1E, 0xFF8A5AB0);
    }

    /** Jeice: red skin, long white hair, purple Ginyu armour. */
    static void jeice() {
        tone(0xFFD0503A, 0xFFE8E8E0, 0xFF141216);
        hair(0xFFF0F0EA);
        head("bob", "none", false);
        forceArmour(0xFF1E1A1E, 0xFF8A5AB0);
    }

    /** Captain Ginyu: purple skin, two black horns, his own purple-trimmed armour. */
    static void ginyu() {
        tone(0xFF8A5AB8, 0xFF141216, 0xFF141216);
        hair(0xFF141216);
        head("bald", "none", false);
        top(HAT, (f, i, j) -> (i == 1 || i == 6) && j <= 2 ? 'H' : '.');
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, ".H....H.", 1, ".H....H."));
        forceArmour(0xFF1E1A1E, 0xFF6A3A9A);
    }

    /** Frieza (final form): white, purple domes on head, shoulders, chest and forearms, red eyes. */
    static void frieza() {
        tone(0xFFF4F2F4, 0xFF8A3A9A, 0xFFC82A3A);
        hair(0xFFE8E6EA);
        colors("P", 0xFF8A3AAA, "p", 0xFF6A2A8A, "w", 0xFFF4F2F4, "g", 0xFFC8C4CC);
        head("bald", "none", false);
        top(HEAD, (f, i, j) -> i >= 2 && i <= 5 && j >= 2 && j <= 5 ? 'P' : 'S');
        wrap(BODY, (f, i, j) -> f == Face.FRONT && j >= 2 && j <= 5 && i >= 2 && i <= 5 ? 'P' : (j % 6 == 5 ? 'g' : 'w'));
        top(BODY, 'P');
        limbs("PPwwwPPPwwww", "wwwwwwgwwwww");
    }

    /** Trunks: lavender hair, the blue Capsule Corp jacket over black, grey trousers, yellow boots. */
    static void trunks() {
        tone(0xFFF0C8A0, 0xFF8A6AB8, 0xFF2A4AB0);
        hair(0xFFB89AD8);
        colors("B", 0xFF2A4AB0, "b", 0xFF1E3480, "K", 0xFF141216, "G", 0xFF7A7A82, "Y", 0xFFE8C030);
        head("bob", "none", false);
        wrap(BODY, (f, i, j) -> f == Face.FRONT && i >= 3 && i <= 4 ? 'K' : 'B');
        top(BODY, 'B');
        face(BODY, Face.BACK, rowsOf(12, "BBBBBBBB", 3, "BBbbbbBB", 4, "BBbBBbBB"));
        limbs("BBBBBBBBBBKS", "GGGGGGGGYYYY");
    }

    /** Android 17: shoulder-length black hair, an orange scarf, black shirt, jeans. */
    static void android17() {
        tone(0xFFF0D0B4, 0xFF141216, 0xFF3A5A8A);
        hair(0xFF141216);
        colors("O", 0xFFE8701E, "K", 0xFF1E1E22, "J", 0xFF3A5A8A);
        head("bob", "none", false);
        wrap(BODY, (f, i, j) -> j <= 1 ? 'O' : 'K');
        top(BODY, 'O');
        limbs("KKKKKKKKKKSS", "JJJJJJJJJJKK");
    }

    /** Android 18: a blonde bob, a denim vest over black-and-white stripes, a blue skirt. */
    static void android18() {
        tone(0xFFF4D6BC, 0xFFC8A040, 0xFF2A6AB0);
        hair(0xFFF2D06A);
        colors("D", 0xFF3A6AB0, "w", 0xFFF0F0F0, "K", 0xFF1E1E22);
        head("bob", "none", false);
        wrap(BODY, (f, i, j) -> f == Face.FRONT && i >= 2 && i <= 5 ? (j % 2 == 0 ? 'w' : 'K') : 'D');
        top(BODY, 'D');
        limbs("DDwKwKwKwKSS", "DDDDSSSSSSKK");
    }

    /** A Cell Junior: little, blue-skinned, a green spotted carapace. */
    static void cellJr() {
        tone(0xFF6A9AD8, 0xFF2A4A8A, 0xFF141216);
        hair(0xFF4A8A3A);
        colors("G", 0xFF6ABF5A, "g", 0xFF1E3A1E);
        head("bald", "none", false);
        top(HEAD, (f, i, j) -> (i + j) % 3 == 0 ? 'g' : 'G');
        wrap(BODY, (f, i, j) -> (i * 3 + j) % 5 == 0 ? 'g' : 'G');
        top(BODY, 'G');
        limbs("GGGGgGGGGgGG", "GGGgGGGGgGKK");
    }

    /** Perfect Cell: a green body with black spots, a pale purple face, black on the arms and legs. */
    static void cell() {
        tone(0xFFD8C8E0, 0xFF8A3A9A, 0xFFC82A3A);
        hair(0xFF4A8A3A);
        colors("G", 0xFF6ABF5A, "g", 0xFF141216, "K", 0xFF1E1E22);
        head("bald", "none", false);
        top(HEAD, (f, i, j) -> (i + j) % 4 == 0 ? 'g' : 'G');
        top(HAT, (f, i, j) -> (i == 0 || i == 7) && j <= 3 ? 'G' : '.');
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, "G......G", 1, "GG....GG"));
        wrap(BODY, (f, i, j) -> (i * 5 + j * 3) % 7 == 0 ? 'g' : 'G');
        top(BODY, 'G');
        limbs("GGGgGGKKKKKK", "GGgGGGKKKKKK");
    }

    /** Majin Buu: big and pink, an antenna on his head, a black vest, white trousers, a gold belt. */
    static void majinBuu() {
        tone(0xFFF0A0C0, 0xFF141216, 0xFF141216);
        hair(0xFFE08AAA);
        colors("V", 0xFF2A2236, "w", 0xFFF2F1EC, "Y", 0xFFE8C030);
        head("bald", "none", false);
        top(HAT, (f, i, j) -> i >= 3 && i <= 4 && j >= 3 && j <= 4 ? 'h' : '.');
        wrap(BODY, (f, i, j) -> j == 9 ? 'Y' : j > 9 ? 'w' : (f == Face.FRONT && i >= 2 && i <= 5 ? 'S' : 'V'));
        top(BODY, 'V');
        limbs("SSSSSSSSSSYY", "wwwwwwwwwwYY");
    }

    /** Kid Buu: small, pink and lean, bare-chested, white trousers, a gold belt. */
    static void kidBuu() {
        tone(0xFFE88AB0, 0xFF141216, 0xFF141216);
        hair(0xFFC86A90);
        colors("w", 0xFFF2F1EC, "Y", 0xFFE8C030);
        head("bald", "none", false);
        top(HAT, (f, i, j) -> i >= 3 && i <= 4 && j >= 2 && j <= 5 ? 'h' : '.');
        wrap(BODY, (f, i, j) -> j == 10 ? 'Y' : j > 10 ? 'w' : 'S');
        top(BODY, 'S');
        limbs("SSSSSSSSSSYY", "wwwwwwwwwwYY");
    }

    /** Draws one of the chapter 2-4 characters by id (the generator's main list and the new-skins harness). */
    static void sagaSkin(String name) {
        switch (name) {
            case "dende" -> dende();
            case "frieza_soldier" -> friezaSoldier();
            case "dodoria" -> dodoria();
            case "zarbon" -> zarbon();
            case "guldo" -> guldo();
            case "recoome" -> recoome();
            case "burter" -> burter();
            case "jeice" -> jeice();
            case "ginyu" -> ginyu();
            case "frieza" -> frieza();
            case "trunks" -> trunks();
            case "android_17" -> android17();
            case "android_18" -> android18();
            case "cell_jr" -> cellJr();
            case "cell" -> cell();
            case "majin_buu" -> majinBuu();
            case "kid_buu" -> kidBuu();
            default -> throw new IllegalStateException(name);
        }
    }

    static final String[] SAGA = {"dende", "frieza_soldier", "dodoria", "zarbon", "guldo", "recoome", "burter", "jeice", "ginyu",
            "frieza", "trunks", "android_17", "android_18", "cell_jr", "cell", "majin_buu", "kid_buu"};

    // ---------------------------------------------------------------- items

    static final String[] SCOUTER = {
            "................",
            "................",
            "....vvvvvv......",
            "...vVVVVVVv.....",
            "..vVvvvvvvVv....",
            "..vV......Vv....",
            "..vV......VvRR..",
            "..vV.GGGGGGGGGg.",
            "..vV.GGGGGGGGGg.",
            "..vV.GGGGGGGGGg.",
            "..vV.gGGGGGGGgg.",
            "..vVvvvvv.......",
            "...vVVVVv.......",
            "....vvvv........",
            "................",
            "................"};
    static final String[] SENZU = {
            "................",
            "................",
            "................",
            "................",
            "......gggg......",
            ".....gGGGGg.....",
            "....gGhhGGGg....",
            "....gGhGGGGg....",
            "....gGGGGGGg....",
            "....gGGGGGGg....",
            ".....gGGGGg.....",
            "......gggg......",
            "................",
            "................",
            "................",
            "................"};

    static final String[] RADAR = {
            "................",
            ".......KK.......",
            ".....wWWWWw.....",
            "....wWWWWWWw....",
            "...wWGGGGGGWw...",
            "..wWGgGGgGGGWw..",
            "..wWGGGGGGGGWw..",
            "..wWGGgYGgGGWw..",
            "..wWGGgGGGGGWw..",
            "..wWGGGGGgGGWw..",
            "..wWGgGGGGGGWw..",
            "...wWGGGGGGWw...",
            "....wWWWWWWw....",
            ".....wwwwww.....",
            "................",
            "................"};

    /** An orange crystal ball with 1-7 red stars, a white highlight top-left. */
    static BufferedImage dragonBall(int stars) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = x - 7.5, dy = y - 7.5;
                double r = Math.sqrt(dx * dx + dy * dy);
                if (r > 7.2) continue;
                int c = r > 6.3 ? 0xFFC8661E : (dx + dy < -4 ? 0xFFFFB04A : 0xFFF28A2A);
                if (Math.abs(dx + 3.5) < 1.1 && Math.abs(dy + 3.5) < 1.1) c = 0xFFFFF4D8;
                img.setRGB(x, y, c);
            }
        }
        int[][][] layouts = {
                {{7, 7}},
                {{5, 7}, {9, 8}},
                {{5, 6}, {9, 6}, {7, 10}},
                {{5, 5}, {9, 5}, {5, 9}, {9, 9}},
                {{4, 5}, {10, 5}, {7, 7}, {4, 10}, {10, 10}},
                {{4, 5}, {7, 4}, {10, 5}, {4, 9}, {7, 10}, {10, 9}},
                {{4, 5}, {7, 4}, {10, 5}, {7, 7}, {4, 9}, {7, 10}, {10, 9}}};
        for (int[] star : layouts[stars - 1]) {
            img.setRGB(star[0], star[1], 0xFFD0202A);
            img.setRGB(star[0] + 1, star[1], 0xFFD0202A);
            img.setRGB(star[0], star[1] + 1, 0xFFD0202A);
            img.setRGB(star[0] + 1, star[1] + 1, 0xFF9A1018);
        }
        return img;
    }

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
