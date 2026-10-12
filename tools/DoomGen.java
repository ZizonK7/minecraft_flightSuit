import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the Doom saga (DESIGN.md 4-17): Battleworld's people as 64x64 player skins on the Dragon Ball fighters' body
 * (textures/entity/dbz/<id>.png - Doom, God Emperor Doom, the Doombots, the Latverian witches, the Sentinels,
 * Magneto, Namor and Talokan's warriors, the Marvel Zombies and their Captain, the Fantastic Four of Earth-828),
 * the multiverse gate (an animated sheet, block/multiverse_gate.png + .mcmeta), and the items: Reed's bridge key,
 * the world core, and Doom's mask (the 3D model's texture, item/doom_mask.png, 32x32).
 *
 * Usage: java tools/DoomGen.java <assets/flightsuit/textures dir> [skins|gate|items|preview|all]
 *   preview: docs/reference/doom_preview.png (front and back of everyone; the textures dir must be the repo's)
 *
 * The skin painting helpers are DbzSkinGen's (colours are letters looked up in the current palette).
 */
public class DoomGen {
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

    static final List<String> CAST = List.of("doctor_doom", "doom_god_emperor", "doombot", "latverian_witch", "sentinel", "magneto",
            "namor", "talokan_warrior", "marvel_zombie", "zombie_captain", "mister_fantastic", "invisible_woman", "human_torch", "the_thing");

    interface Pattern {
        char at(Face f, int i, int j);
    }

    static BufferedImage skin;
    static Map<Character, Integer> pal;

    public static void main(String[] args) throws IOException {
        File root = new File(args[0]);
        String mode = args.length > 1 ? args[1] : "all";
        File dir = new File(root, "entity/dbz");
        dir.mkdirs();
        if (mode.equals("all") || mode.equals("skins")) {
            for (String name : CAST) {
                skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                pal = new HashMap<>();
                pal.put('W', 0xFFFFFFFF);
                pal.put('K', 0xFF141216);
                draw(name);
                File out = new File(dir, name + ".png");
                ImageIO.write(skin, "png", out);
                System.out.println("wrote " + out);
            }
        }
        if (mode.equals("all") || mode.equals("gate")) {
            File block = new File(root, "block");
            block.mkdirs();
            ImageIO.write(gate(), "png", new File(block, "multiverse_gate.png"));
            Files.writeString(new File(block, "multiverse_gate.png.mcmeta").toPath(),
                    "{\n  \"animation\": {\n    \"frametime\": 2\n  }\n}\n", StandardCharsets.UTF_8);
            System.out.println("wrote gate");
        }
        if (mode.equals("all") || mode.equals("items")) {
            File item = new File(root, "item");
            item.mkdirs();
            ImageIO.write(bridgeKey(), "png", new File(item, "bridge_key.png"));
            ImageIO.write(worldCore(), "png", new File(item, "world_core.png"));
            ImageIO.write(doomMask(), "png", new File(item, "doom_mask.png"));
            System.out.println("wrote items");
        }
        if (mode.equals("all") || mode.equals("preview")) {
            preview(dir, new File(root, "../../../../../../docs/reference/doom_preview.png"));
        }
    }

    static void draw(String name) {
        switch (name) {
            case "doctor_doom" -> doom(false, false);
            case "doom_god_emperor" -> doom(true, false);
            case "doombot" -> doom(false, true);
            case "latverian_witch" -> witch();
            case "sentinel" -> sentinel();
            case "magneto" -> magneto();
            case "namor" -> namor();
            case "talokan_warrior" -> talokan();
            case "marvel_zombie" -> zombie();
            case "zombie_captain" -> zombieCaptain();
            case "mister_fantastic" -> fantastic(0xFF5A3A26, true, false);
            case "invisible_woman" -> fantastic(0xFFF0D27A, false, true);
            case "human_torch" -> torch();
            case "the_thing" -> thing();
            default -> throw new IllegalArgumentException(name);
        }
    }

    // ---------------------------------------------------------------- Doom and his

    /**
     * Doom: the riveted iron mask (eye slits, the grille of a mouth), a green hood over it on the hat layer, the green
     * tunic with its belt and round buckle, iron arms and legs, the cloak down the back. God Emperor: a gold mask and
     * white robes trimmed gold. A Doombot: duller, the seams showing, red eyes.
     */
    static void doom(boolean god, boolean bot) {
        int iron = god ? 0xFFE6BE48 : bot ? 0xFF7E848C : 0xFF9AA2AC;
        int cloth = god ? 0xFFEDE8DA : bot ? 0xFF3A5A3C : 0xFF2E6B34;
        int hood = god ? 0xFF2E6B34 : bot ? 0xFF324E34 : 0xFF2A5E2E;
        colors("A", iron, "a", shade(iron, 0.78), "m", shade(iron, 0.6), "G", cloth, "g", shade(cloth, 0.78), "h", hood, "H", shade(hood, 1.15),
                "Y", god ? 0xFF2E6B34 : 0xFFC9A23A, "y", god ? 0xFF1E4A22 : 0xFF8A6A1E, "E", bot ? 0xFFFF3A2A : god ? 0xFF7CFFA0 : 0xFF1A1C20,
                "L", 0xFF5A3A22);
        // The mask.
        face(HEAD, Face.FRONT,
                "AAAAAAAA",
                "AaAAAAaA",
                "AmEAAEmA",
                "AAAaaAAA",
                "AAAaaAAA",
                "AammmmaA",
                "AAmamaAA",
                "aAAAAAAa");
        wrap(HEAD, (f, i, j) -> f == Face.FRONT ? '.' : (i + j) % 5 == 0 ? 'a' : 'A');
        face(HEAD, Face.RIGHT, rowsOf(8, "AAAAAAAA", 3, "AAAAAAma", 7, "aaaaaaaa"));
        face(HEAD, Face.LEFT, rowsOf(8, "AAAAAAAA", 3, "amAAAAAA", 7, "aaaaaaaa"));
        top(HEAD, 'A');
        bottom(HEAD, 'a');
        if (bot) {
            // The seam round a Doombot's face.
            face(HEAD, Face.RIGHT, rowsOf(8, "........", 0, "m.......", 7, "mmmmmmmm"));
        }
        // The hood: over the top, down the sides and back, framing the face.
        top(HAT, (f, i, j) -> (i + j) % 4 == 0 ? 'H' : 'h');
        face(HAT, Face.FRONT, "hhhhhhhh", "hH....Hh", "h......h", "h......h", "h......h", "h......h", "........", "........");
        face(HAT, Face.RIGHT, rowsOf(8, "hhhhhhhh", 1, "hhHhhhhh", 4, "hhhhhhhH", 7, "hhhhhh.."));
        face(HAT, Face.LEFT, mirror(rowsOf(8, "hhhhhhhh", 1, "hhHhhhhh", 4, "hhhhhhhH", 7, "hhhhhh..")));
        face(HAT, Face.BACK, rowsOf(8, "hhhhhhhh", 2, "hhHhhHhh", 6, "hHhhhhHh"));
        // The tunic: green over iron, a belt with a round buckle.
        wrap(BODY, (f, i, j) -> j == 8 ? 'L' : j < 8 ? (f == Face.FRONT && j == 0 && (i == 0 || i == 7) ? 'A' : 'G') : (i + j) % 3 == 0 ? 'g' : 'G');
        face(BODY, Face.FRONT, rowsOf(12, "GGGGGGGG", 0, "AGGGGGGA", 1, "AGgGGgGA", 4, "GGgGGgGG", 8, "LLLYYLLL", 9, "GGGyyGGG",
                10, "GgGGGGgG", 11, "gGGGGGGg"));
        if (god) {
            face(BODY, Face.FRONT, rowsOf(12, "........", 2, "...YY...", 3, "..YyyY..", 4, "...YY..."));
        }
        top(BODY, 'A');
        bottom(BODY, 'G');
        // The cloak, from the shoulders down the back (and folded over the shoulders).
        face(JACKET, Face.BACK, rowsOf(12, "hhhhhhhh", 0, "HhhhhhhH", 4, "hHhhhhHh", 8, "hhHhhHhh", 11, "hhhhhhhh"));
        face(JACKET, Face.RIGHT, rowsOf(12, "...h", 0, "hhhh", 1, "..hh"));
        face(JACKET, Face.LEFT, rowsOf(12, "h...", 0, "hhhh", 1, "hh.."));
        top(JACKET, (f, i, j) -> j >= 2 ? 'h' : '.');
        // Iron arms with green short sleeves, iron gauntlets; iron legs with green-cloth trunks at the top.
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrap(arm, (f, i, j) -> j < 3 ? 'G' : j == 7 ? 'm' : j >= 8 ? ((i + j) % 4 == 0 ? 'a' : 'A') : (i + j) % 5 == 0 ? 'a' : 'A');
            top(arm, 'G');
            bottom(arm, 'a');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrap(leg, (f, i, j) -> j < 2 ? 'G' : j == 6 ? 'm' : j >= 9 ? 'a' : (i * 3 + j) % 7 == 0 ? 'a' : 'A');
            top(leg, 'G');
            bottom(leg, 'm');
        }
        if (god) {
            // God Emperor's robes fall to the knees.
            for (Box pants : new Box[]{R_PANTS, L_PANTS}) {
                wrap(pants, (f, i, j) -> j < 6 ? (j == 5 ? 'Y' : 'G') : '.');
            }
        }
    }

    /** The witches behind the throne: black-green robes and veils, white masks, a violet sigil on the brow. */
    static void witch() {
        colors("R", 0xFF1C2A20, "r", 0xFF121A14, "V", 0xFF9A3AC8, "w", 0xFFE8E4DA, "x", 0xFFB8B2A6, "G", 0xFF3A7A44);
        face(HEAD, Face.FRONT,
                "wwwwwwww",
                "wwwVVwww",
                "wwwwwwww",
                "wKKwwKKw",
                "wwwxxwww",
                "wwwwwwww",
                "xwwKKwwx",
                "xxwwwwxx");
        wrap(HEAD, (f, i, j) -> f == Face.FRONT ? '.' : 'R');
        top(HEAD, 'R');
        bottom(HEAD, 'r');
        top(HAT, (f, i, j) -> 'R');
        face(HAT, Face.FRONT, "RRRRRRRR", "RR....RR", "R......R", "R......R", "R......R", "R......R", "R......R", "........");
        face(HAT, Face.RIGHT, rowsOf(8, "RRRRRRRR"));
        face(HAT, Face.LEFT, rowsOf(8, "RRRRRRRR"));
        face(HAT, Face.BACK, rowsOf(8, "RRRRRRRR", 3, "RrRRRRrR"));
        wrap(BODY, (f, i, j) -> j == 6 ? 'G' : (i + j) % 6 == 0 ? 'r' : 'R');
        face(BODY, Face.FRONT, rowsOf(12, "RRRRRRRR", 1, "RRRVVRRR", 2, "RRVRRVRR", 3, "RRRVVRRR", 6, "GGGGGGGG", 11, "rRRRRRRr"));
        top(BODY, 'R');
        bottom(BODY, 'R');
        wrap(JACKET, (f, i, j) -> j > 6 ? (i + j) % 3 == 0 ? 'r' : 'R' : '.');
        limbs("RRRRRRRRRRww", "RRRRRRRRRRRr");
        for (Box pants : new Box[]{R_PANTS, L_PANTS}) {
            wrap(pants, (f, i, j) -> (i + j) % 4 == 0 ? 'r' : 'R');
        }
    }

    /** A Sentinel: purple armour, a magenta face plate with the burning eyes, ribbed joints. */
    static void sentinel() {
        colors("P", 0xFF6A3A92, "p", 0xFF4A2468, "M", 0xFFC04A8A, "m", 0xFF8A2E62, "E", 0xFFFFC040, "B", 0xFF3A3A44);
        face(HEAD, Face.FRONT,
                "PPPPPPPP",
                "PMMMMMMP",
                "PMEEMEEM",
                "PMmMMMmP",
                "PMMMMMMP",
                "PMBBBBMP",
                "PMMMMMMP",
                "pPPPPPPp");
        wrap(HEAD, (f, i, j) -> f == Face.FRONT ? '.' : j == 7 ? 'p' : 'P');
        top(HEAD, (f, i, j) -> i == 3 || i == 4 ? 'M' : 'P');
        bottom(HEAD, 'p');
        // A crest of plates on the hat layer.
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, "PP....PP"));
        top(HAT, (f, i, j) -> i == 0 || i == 7 ? 'P' : '.');
        wrap(BODY, (f, i, j) -> (j == 4 || j == 8) ? 'p' : f == Face.FRONT && j < 4 && i > 1 && i < 6 ? 'M' : 'P');
        face(BODY, Face.FRONT, rowsOf(12, "PPPPPPPP", 1, "PPMMMMPP", 2, "PMMEEMMP", 3, "PPMMMMPP", 9, "BBBBBBBB"));
        top(BODY, 'P');
        bottom(BODY, 'p');
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrap(arm, (f, i, j) -> j % 4 == 3 ? 'B' : j < 3 ? 'M' : 'P');
            top(arm, 'M');
            bottom(arm, 'p');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrap(leg, (f, i, j) -> j == 5 ? 'B' : j > 8 ? 'M' : 'P');
            top(leg, 'P');
            bottom(leg, 'p');
        }
    }

    // ---------------------------------------------------------------- the Barons

    /** Magneto: the red helmet (open over the face, a crest), red suit, purple gloves, boots and cape. */
    static void magneto() {
        tone(0xFFE8C4A0, 0xFFC8C8C8, 0xFF4A6AA0);
        colors("R", 0xFFB0202A, "r", 0xFF7A121A, "V", 0xFF6A2A8A, "v", 0xFF4A1A62, "Y", 0xFFE0B440);
        face(HEAD, Face.FRONT,
                "RRRRRRRR",
                "RrRRRRrR",
                "RSSSSSSR",
                "RbbSSbbR",
                "RWeSSeWR",
                "rSSssSSr",
                "rSSMMSSr",
                "RSSSSSSR");
        wrap(HEAD, (f, i, j) -> f == Face.FRONT ? '.' : j < 6 ? 'R' : 'r');
        face(HEAD, Face.RIGHT, rowsOf(8, "RRRRRRRR", 3, "RRRRRRrS", 4, "RRRRRrSS", 5, "rrrrrrSS", 6, "rrrrrrrS", 7, "rrrrrrrr"));
        face(HEAD, Face.LEFT, mirror(rowsOf(8, "RRRRRRRR", 3, "RRRRRRrS", 4, "RRRRRrSS", 5, "rrrrrrSS", 6, "rrrrrrrS", 7, "rrrrrrrr")));
        top(HEAD, (f, i, j) -> i == 3 || i == 4 ? 'r' : 'R');
        bottom(HEAD, 's');
        // The crest down the middle and the wings of the helmet at the temples.
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, "...RR...", 1, "...rr..."));
        top(HAT, (f, i, j) -> (i == 3 || i == 4) && j < 6 ? 'R' : '.');
        face(HAT, Face.RIGHT, rowsOf(8, "........", 2, "......RR", 3, ".....RR."));
        face(HAT, Face.LEFT, rowsOf(8, "........", 2, "RR......", 3, ".RR....."));
        wrap(BODY, (f, i, j) -> j == 9 ? 'V' : (i + j) % 7 == 0 ? 'r' : 'R');
        face(BODY, Face.FRONT, rowsOf(12, "RRRRRRRR", 0, "RRRrrRRR", 1, "RRRRRRRR", 9, "VVVYYVVV"));
        top(BODY, 'R');
        bottom(BODY, 'R');
        // The cape: purple down the back, clasped at the shoulders.
        face(JACKET, Face.BACK, rowsOf(12, "VVVVVVVV", 0, "vVVVVVVv", 6, "VvVVVVvV", 11, "vvvvvvvv"));
        face(JACKET, Face.FRONT, rowsOf(12, "........", 0, "Y......Y"));
        top(JACKET, (f, i, j) -> j >= 2 ? 'V' : '.');
        limbs("RRRRRRVVVVVV", "RRRRRRVVVVVV");
    }

    /** Namor: bare-chested, a widow's peak of black hair, green scaled trunks, a jade collar, wings at the ankles. */
    static void namor() {
        tone(0xFFC8946A, 0xFF141216, 0xFF2A2A2E);
        hair(0xFF141216);
        colors("G", 0xFF1E7A4A, "g", 0xFF125A34, "J", 0xFF3ADBA0, "Y", 0xFFE0B440, "w", 0xFFE8E8E0);
        head("short", "none", false);
        face(HEAD, Face.FRONT, rowsOf(8, "........", 0, "HHHHHHHH", 1, "HHHhHHHH", 2, "HSSHHSSH", 3, "SbbSSbbS"));
        // The pointed ears.
        face(HAT, Face.RIGHT, rowsOf(8, "........", 3, ".....SS.", 4, "......S."));
        face(HAT, Face.LEFT, rowsOf(8, "........", 3, ".SS.....", 4, ".S......"));
        wrap(BODY, (f, i, j) -> j >= 10 ? ((i + j) % 2 == 0 ? 'G' : 'g') : 'S');
        face(BODY, Face.FRONT, rowsOf(12, "SSSSSSSS", 0, "JYJYYJYJ", 1, "SJJJJJJS", 4, "SSsSSsSS", 6, "SSSsSSSS", 10, "GGGGGGGG", 11, "gGgGgGgG"));
        top(BODY, 'S');
        bottom(BODY, 'G');
        limbs("SSSSSSSSSYSS", "ggSSSSSSSSSS");
        // Wings at the ankles (outer layer).
        for (Box pants : new Box[]{R_PANTS, L_PANTS}) {
            paint(pants, Face.RIGHT, (f, i, j) -> j == 9 && i < 3 || j == 10 && i < 2 ? 'w' : '.');
            paint(pants, Face.LEFT, (f, i, j) -> j == 9 && i > 0 || j == 10 && i > 1 ? 'w' : '.');
        }
    }

    /** Talokan's warriors: blue skin, a jade breathing mask, shell plates and a feather band. */
    static void talokan() {
        tone(0xFF4A8AB8, 0xFF1E3A5A, 0xFFE8E0C0);
        hair(0xFF141E2A);
        colors("J", 0xFF2EC08A, "j", 0xFF1A8A5E, "T", 0xFFD8C8A0, "t", 0xFFA89870, "F", 0xFFE04A3A, "Y", 0xFFE0B440);
        head("short", "none", false);
        face(HEAD, Face.FRONT, rowsOf(8, "........", 5, "SJJJJJJS", 6, "SJjjjjJS", 7, "SSJJJJSS"));
        face(HAT, Face.FRONT, rowsOf(8, "........", 0, "FYFYFYFY"));
        face(HAT, Face.RIGHT, rowsOf(8, "........", 0, "FYFYFYFY"));
        face(HAT, Face.LEFT, rowsOf(8, "........", 0, "FYFYFYFY"));
        face(HAT, Face.BACK, rowsOf(8, "........", 0, "FYFYFYFY", 1, "...FF..."));
        wrap(BODY, (f, i, j) -> j >= 9 ? 'T' : f == Face.FRONT && j > 1 && j < 7 && i > 0 && i < 7 ? ((i + j) % 2 == 0 ? 'T' : 't') : 'S');
        top(BODY, 'S');
        bottom(BODY, 'T');
        limbs("SSSSSSSSSJSS", "TTTSSSSSSSSS");
    }

    /** A Marvel Zombie: grey-green rotting skin, torn bits of an old costume, bone showing, red eyes. */
    static void zombie() {
        tone(0xFF7A8E6A, 0xFF3A4430, 0xFFE02A2A);
        hair(0xFF2A2420);
        colors("C", 0xFF2A4AA0, "c", 0xFF1A2E66, "R", 0xFFA02A2A, "B", 0xFFE8E0C8, "D", 0xFF4A2A22);
        head("short", "none", false);
        face(HEAD, Face.FRONT, rowsOf(8, "........", 4, "SWeSSeWS", 6, "SDBBBBDS", 7, "SsDDDDsS"));
        wrap(BODY, (f, i, j) -> (i * 7 + j * 5) % 9 == 0 ? 'S' : (i * 3 + j) % 11 == 0 ? 'B' : j < 7 ? 'C' : (i + j) % 2 == 0 ? 'R' : 'c');
        top(BODY, 'C');
        bottom(BODY, 'c');
        limbs("CCCSSSSBSSSS", "cccccSSccDDD");
    }

    /** The Zombie Captain: a rotted Captain America - the winged helmet with its A, the star, stripes, torn blue. */
    static void zombieCaptain() {
        tone(0xFF7A8E6A, 0xFF3A4430, 0xFFE02A2A);
        colors("C", 0xFF2A4AA0, "c", 0xFF1A2E66, "R", 0xFFB02A2A, "B", 0xFFE8E0C8, "D", 0xFF4A2A22, "w", 0xFFF0F0F0);
        face(HEAD, Face.FRONT,
                "CCCwwCCC",
                "CCwCCwCC",
                "CCCCCCCC",
                "CbbCCbbC",
                "CWeSSeWC",
                "SSSssSSS",
                "SDBBBBDS",
                "SsDDDDsS");
        wrap(HEAD, (f, i, j) -> f == Face.FRONT ? '.' : j < 5 ? 'C' : 'S');
        face(HEAD, Face.RIGHT, rowsOf(8, "CCCCCCCC", 2, "CCCCCwwC", 5, "SSSSSSSS"));
        face(HEAD, Face.LEFT, mirror(rowsOf(8, "CCCCCCCC", 2, "CCCCCwwC", 5, "SSSSSSSS")));
        top(HEAD, 'C');
        bottom(HEAD, 's');
        wrap(BODY, (f, i, j) -> j >= 6 && j <= 9 && f == Face.FRONT ? ((i % 2 == 0) ? 'R' : 'w') : (i * 7 + j * 5) % 10 == 0 ? 'S' : 'C');
        face(BODY, Face.FRONT, rowsOf(12, "........", 1, "...ww...", 2, "..wwww..", 3, "...ww...", 4, "..w..w..", 10, "cccccccc", 11, "cSccDccc"));
        top(BODY, 'C');
        bottom(BODY, 'c');
        limbs("CCCCSSSBRRRR", "cccccccRRRRR");
    }

    // ---------------------------------------------------------------- the Fantastic Four (Earth-828)

    /** Reed or Sue: the blue suit with the white "4" in its circle on the chest, black belt and boots. */
    static void fantastic(int hairColour, boolean reed, boolean sue) {
        tone(0xFFF0C8A0, shade(hairColour, 0.7), reed ? 0xFF6A4A2E : 0xFF3A6AB0);
        hair(hairColour);
        colors("B", 0xFF2E6AC8, "b", 0xFF1E4A90, "w", 0xFFF2F2F2, "k", 0xFF1E1E26, "g", 0xFFB8B8B8);
        head(sue ? "bob" : "short", "none", false);
        if (reed) {
            // Grey at the temples.
            face(HEAD, Face.RIGHT, rowsOf(8, "........", 3, "....gg..", 4, "....g..."));
            face(HEAD, Face.LEFT, rowsOf(8, "........", 3, "..gg....", 4, "...g...."));
        }
        if (sue) {
            face(HAT, Face.BACK, rowsOf(8, "........", 6, "HHHHHHHH", 7, "hHHHHHHh"));
        }
        wrap(BODY, (f, i, j) -> j == 9 ? 'k' : (i + j) % 9 == 0 ? 'b' : 'B');
        // The "4": a bold glyph over the heart of the chest, white trim at the collar.
        face(BODY, Face.FRONT, rowsOf(12, "BBBBBBBB", 0, "BkwwwwkB", 1, "BBBwBwBB", 2, "BBwBBwBB", 3, "BwwwwwwB", 4, "BBBBBwBB",
                5, "BBBBBwBB", 9, "kkkkkkkk"));
        top(BODY, 'B');
        bottom(BODY, 'B');
        limbs("BBBBBBBBBkkk", "BBBBBBBBkkkk");
    }

    /** Johnny, flamed on: fire all over - red, orange, yellow licking upwards - white-hot eyes. */
    static void torch() {
        colors("R", 0xFFE0401A, "O", 0xFFFF8A1E, "Y", 0xFFFFD040, "y", 0xFFFFF0A0);
        Pattern flame = (f, i, j) -> {
            int n = (i * 5 + j * 3 + (f.ordinal() * 7)) % 9;
            return n < 2 ? 'Y' : n < 4 ? 'R' : n < 7 ? 'O' : (j % 3 == 0 ? 'y' : 'O');
        };
        for (Box b : new Box[]{HEAD, BODY, R_ARM, L_ARM, R_LEG, L_LEG}) {
            wrap(b, flame);
            top(b, flame);
            paint(b, Face.BOTTOM, flame);
        }
        face(HEAD, Face.FRONT, rowsOf(8, "........", 4, ".yWOOWy.", 6, "..ROOR.."));
        // Flickers of flame on the outer layer.
        for (Box b : new Box[]{HAT, JACKET, R_SLEEVE, L_SLEEVE, R_PANTS, L_PANTS}) {
            wrap(b, (f, i, j) -> (i * 7 + j * 11 + f.ordinal()) % 6 == 0 ? (j % 2 == 0 ? 'Y' : 'O') : '.');
        }
        top(HAT, (f, i, j) -> (i + j) % 3 == 0 ? 'Y' : (i * j) % 4 == 1 ? 'O' : '.');
    }

    /** Ben Grimm: orange rock in cracked plates, a heavy brow over blue eyes, the blue trunks with the black belt. */
    static void thing() {
        colors("O", 0xFFD07A32, "o", 0xFFA85A22, "c", 0xFF5A3218, "E", 0xFF4A8AE0, "B", 0xFF2E6AC8, "k", 0xFF1E1E26);
        Pattern rock = (f, i, j) -> (i % 4 == 0 && j % 2 == 0) || (j % 4 == 0 && (i + j / 4) % 2 == 0) ? 'c' : (i + j) % 3 == 0 ? 'o' : 'O';
        for (Box b : new Box[]{HEAD, BODY, R_ARM, L_ARM, R_LEG, L_LEG}) {
            wrap(b, rock);
            top(b, rock);
            paint(b, Face.BOTTOM, rock);
        }
        face(HEAD, Face.FRONT,
                "OcOOcOOc",
                "cOOcOOcO",
                "ccoooocc",
                "oOEoEOOo",
                "OcOooOcO",
                "OOcOOcOO",
                "occccccO",
                "OOcOOcOO");
        face(BODY, Face.FRONT, rowsOf(12, "........", 9, "kkkkkkkk", 10, "BBBBBBBB", 11, "BBBBBBBB"));
        for (Face f : SIDES) {
            if (f != Face.FRONT) {
                paint(BODY, f, (ff, i, j) -> j == 9 ? 'k' : j > 9 ? 'B' : '.');
            }
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            paint(leg, Face.FRONT, (f, i, j) -> j < 2 ? 'B' : '.');
            for (Face f : SIDES) {
                paint(leg, f, (ff, i, j) -> j < 2 ? 'B' : '.');
            }
        }
    }

    // ---------------------------------------------------------------- the gate and the items

    /**
     * The multiverse gate: 32 frames of a slow green-and-violet whirl with bright threads running through it (other
     * worlds glimpsed), mostly see-through.
     */
    static BufferedImage gate() {
        int frames = 32;
        BufferedImage img = new BufferedImage(16, 16 * frames, BufferedImage.TYPE_INT_ARGB);
        for (int n = 0; n < frames; n++) {
            double t = n * Math.PI * 2.0D / frames;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    double dx = x - 7.5, dy = y - 7.5;
                    double r = Math.sqrt(dx * dx + dy * dy);
                    double a = Math.atan2(dy, dx);
                    double swirl = Math.sin(a * 3.0D + r * 0.7D - t * 2.0D);
                    double threads = Math.sin(a * 7.0D - r * 1.3D + t * 3.0D);
                    double mix = 0.5D + 0.5D * swirl;
                    int g = (int) (90 + 140 * mix);
                    int rr = (int) (40 + 110 * (1.0D - mix));
                    int b = (int) (120 + 90 * (1.0D - mix));
                    int alpha = 170 + (int) (40 * swirl);
                    if (threads > 0.92D) {
                        rr = 230;
                        g = 255;
                        b = 240;
                        alpha = 235;
                    }
                    rr = Math.max(0, Math.min(255, rr));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));
                    img.setRGB(x, n * 16 + y, (alpha << 24) | (rr << 16) | (g << 8) | b);
                }
            }
        }
        return img;
    }

    /** Reed's bridge key: a white-and-blue rod with the "4" on its grip and a green crystal at the tip. */
    static BufferedImage bridgeKey() {
        String[] rows = {
                "............GG..",
                "...........GgHG.",
                "..........GgHHG.",
                "...........GgG..",
                "..........ww....",
                ".........wBw....",
                "........wBBw....",
                ".......wBwB.....",
                "......wBBw......",
                ".....wBwBw......",
                "....wBBBw.......",
                "...wkkkw........",
                "..wBBBw.........",
                "..wBkw..........",
                "...ww...........",
                "................"};
        return icon(rows, Map.of('G', 0xFF2EB85A, 'g', 0xFF7CFFA0, 'H', 0xFFE8FFF0, 'w', 0xFFF2F2F2, 'B', 0xFF2E6AC8, 'k', 0xFF1E1E26));
    }

    /** A world core: a small world in a green glow, a ring of light round it. */
    static BufferedImage worldCore() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = x - 7.5, dy = y - 7.5;
                double r = Math.sqrt(dx * dx + dy * dy);
                int c = 0;
                if (r <= 5.2) {
                    boolean land = Math.sin(dx * 1.3 + dy * 0.6) + Math.cos(dy * 1.1 - dx * 0.4) > 0.6;
                    c = land ? 0xFF3AA858 : 0xFF2A5AC0;
                    if (dx + dy < -4.5) {
                        c = shade(c, 1.35) | 0xFF000000;
                    } else if (dx + dy > 4.5) {
                        c = shade(c, 0.7);
                    }
                } else if (r <= 6.2) {
                    c = 0xC07CFFA0;
                }
                // The ring, tilted.
                double ring = Math.abs(dy - dx * 0.35D);
                if (ring < 0.7D && r > 5.0D && r < 7.6D) {
                    c = 0xFFE8FFF0;
                }
                if (c != 0) {
                    img.setRGB(x, y, c);
                }
            }
        }
        return img;
    }

    /**
     * Doom's mask for its 3D model (models/item/doom_mask.json), 32x32: top-left the mask's face (16x16: riveted iron,
     * eye slits, the nose ridge, the grille), top-right plain iron for its edges, bottom-right the hood's green
     * cloth with folds, bottom-left the hood's darker inside.
     */
    static BufferedImage doomMask() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        int iron = 0xFF9AA2AC, dark = 0xFF6A7078, light = 0xFFC8CED6, slit = 0xFF141216;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = (x * 3 + y * 5) % 13 == 0 ? light : iron;
                if (x == 0 || x == 15 || y == 15) {
                    c = dark;
                }
                // Eye slits.
                if (y >= 5 && y <= 6 && (x >= 3 && x <= 6 || x >= 9 && x <= 12)) {
                    c = slit;
                }
                // The brow and the nose ridge.
                if (y == 4 && x >= 2 && x <= 13) {
                    c = dark;
                }
                if (x >= 7 && x <= 8 && y >= 5 && y <= 9) {
                    c = x == 7 ? light : iron;
                }
                // The grille of the mouth.
                if (y >= 11 && y <= 12 && x >= 4 && x <= 11) {
                    c = x % 2 == 0 ? slit : dark;
                }
                // Rivets down the cheeks.
                if ((x == 2 || x == 13) && (y == 8 || y == 11 || y == 14)) {
                    c = light;
                }
                img.setRGB(x, y, c);
                img.setRGB(16 + x, y, (x + y) % 5 == 0 ? dark : iron);
                int cloth = (x + y * 2) % 7 == 0 ? 0xFF3A7A40 : (x * 2 + y) % 9 == 0 ? 0xFF1E4A22 : 0xFF2A5E2E;
                img.setRGB(16 + x, 16 + y, cloth);
                img.setRGB(x, 16 + y, (x + y) % 6 == 0 ? 0xFF1A3A1C : 0xFF214A24);
            }
        }
        return img;
    }

    // ---------------------------------------------------------------- preview

    static void preview(File dir, File out) throws IOException {
        int scale = 4;
        int cell = 18;
        int columns = 7;
        int rows = (CAST.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(columns * cell * 2 * scale, rows * 34 * scale, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = sheet.createGraphics();
        g.setColor(new java.awt.Color(0x2A2E33));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int n = 0; n < CAST.size(); n++) {
            BufferedImage s = ImageIO.read(new File(dir, CAST.get(n) + ".png"));
            int ox = (n % columns) * cell * 2 * scale;
            int oy = (n / columns) * 34 * scale;
            for (int view = 0; view < 2; view++) {
                g.drawImage(flat(s, view == 1), ox + view * cell * scale + scale, oy + scale, 16 * scale, 32 * scale, null);
            }
        }
        g.dispose();
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
        System.out.println("wrote " + out);
    }

    static BufferedImage flat(BufferedImage s, boolean back) {
        BufferedImage img = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        Face f = back ? Face.BACK : Face.FRONT;
        Box[][] layers = {{HEAD, BODY, R_ARM, L_ARM, R_LEG, L_LEG}, {HAT, JACKET, R_SLEEVE, L_SLEEVE, R_PANTS, L_PANTS}};
        int[][] at = {{4, 0}, {4, 8}, {0, 8}, {12, 8}, {4, 20}, {8, 20}};
        for (Box[] layer : layers) {
            for (int k = 0; k < layer.length; k++) {
                int[] r = region(layer[k], f);
                int x0 = at[k][0];
                if (back && k >= 2) {
                    x0 = at[k % 2 == 0 ? k + 1 : k - 1][0];
                }
                for (int j = 0; j < r[3]; j++) {
                    for (int i = 0; i < r[2]; i++) {
                        int argb = s.getRGB(r[0] + i, r[1] + j);
                        if ((argb >>> 24) != 0) {
                            img.setRGB(x0 + i, at[k][1] + j, argb);
                        }
                    }
                }
            }
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

    // ---------------------------------------------------------------- shared head (DbzSkinGen's)

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
            case "bob" -> {
                side = new String[]{"HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHH", "SSSSSSSS", "SSSSSSSS"};
                back = new String[]{"HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHhHHHH", "HHHHHHHH", "hhhhhhhh", "SSSSSSSS", "SSSSSSSS"};
            }
            case "bald" -> {
                side = new String[]{"SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "HSSSSSSS", "HHSsSSSS", "HHSsSSSS", "HSSSSSSS", "SSSSSSSS"};
                back = new String[]{"SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "SSSSSSSS", "SSSSSSSS"};
            }
            default -> {
                side = new String[]{"HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHSSSS", "HHHsSSSS", "HHHsSSSS", "HHSSSSSS", "HSSSSSSS"};
                back = new String[]{"HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "SSSSSSSS"};
            }
        }
        if (facial.equals("stubble")) {
            front[6] = front[6].substring(0, 1) + "ssMMss" + front[6].charAt(7);
        }
        face(HEAD, Face.FRONT, front);
        face(HEAD, Face.RIGHT, side);
        face(HEAD, Face.LEFT, mirror(side));
        face(HEAD, Face.BACK, back);
        top(HEAD, style.equals("bald") ? 'S' : 'H');
        bottom(HEAD, 's');
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

    // ---------------------------------------------------------------- painting helpers (DbzSkinGen's)

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

    static void wrap(Box b, Pattern p) {
        for (Face f : SIDES) paint(b, f, p);
    }

    static void wrapRows(Box b, String rowColors) {
        wrap(b, (f, i, j) -> j < rowColors.length() ? rowColors.charAt(j) : '.');
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
        int r = Math.min(255, (int) (((c >> 16) & 255) * s)), g = Math.min(255, (int) (((c >> 8) & 255) * s)), b = Math.min(255, (int) ((c & 255) * s));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
