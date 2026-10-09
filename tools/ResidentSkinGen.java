import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the village residents as 64x64 classic (4px arm) player skins, one per job.
 *
 * Usage: java tools/ResidentSkinGen.java <out dir>
 *
 * Writes farmer, rancher, cook, architect, teacher, doctor, blacksmith, merchant, musician and soldier.png
 * (the guard uses a downloaded stormtrooper skin instead). Each resident is a shared head (skin tone, hair
 * style, beard, glasses) plus job clothing; hats, coats and aprons go on the outer layers so they stand out.
 * Colours are letters looked up in the current palette; '.' leaves a pixel transparent.
 */
public class ResidentSkinGen {
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
        for (String job : new String[]{"farmer", "rancher", "cook", "architect", "teacher", "doctor",
                "blacksmith", "merchant", "musician", "soldier"}) {
            skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            pal = new HashMap<>();
            pal.put('W', 0xFFFFFFFF);
            pal.put('K', 0xFF1E1A1A);
            switch (job) {
                case "farmer" -> farmer();
                case "rancher" -> rancher();
                case "cook" -> cook();
                case "architect" -> architect();
                case "teacher" -> teacher();
                case "doctor" -> doctor();
                case "blacksmith" -> blacksmith();
                case "merchant" -> merchant();
                case "musician" -> musician();
                case "soldier" -> soldier();
                default -> throw new IllegalStateException(job);
            }
            File out = new File(dir, job + ".png");
            ImageIO.write(skin, "png", out);
            System.out.println("wrote " + out);
        }
    }

    // ---------------------------------------------------------------- jobs

    /** Straw hat, red plaid shirt with rolled sleeves, denim overalls, work boots. */
    static void farmer() {
        tone(0xFFD9A273, 0xFF6B4A2F, 0xFF3F7A3A);
        hair(0xFF6B4A2F);
        colors("R", 0xFFC0392B, "r", 0xFF8E2A20, "D", 0xFF3B5B92, "d", 0xFF2B4370,
                "Y", 0xFFE8C66A, "y", 0xFFBF9B45, "B", 0xFF5A3A22, "G", 0xFFD9B54A);
        head("short", "stubble", false);
        Pattern plaid = (f, i, j) -> (i % 4 == 1 || j % 4 == 1) ? 'r' : 'R';
        wrap(BODY, plaid);
        top(BODY, 'R');
        face(BODY, Face.FRONT,
                "RDrrrrDR",
                "RDRRRRDR",
                "rDrrrrDr",
                "RGDDDDGR",
                "RRDDDDRR",
                "rrDddDrr",
                "RRDDDDRR",
                "RRDDDDRR",
                "DDDDDDDD",
                "DDDDDDDD",
                "DDDddDDD",
                "DDDddDDD");
        face(BODY, Face.BACK,
                "RDRRRRDR",
                "rrDrrDrr",
                "RRRDDRRR",
                "RRRDDRRR",
                "RRDrrDRR",
                "rDrrrrDr",
                "RDRRRRDR",
                "RDRRRRDR",
                "DDDDDDDD",
                "DDDDDDDD",
                "DDDDDDDD",
                "DDDDDDDD");
        for (Face f : new Face[]{Face.RIGHT, Face.LEFT}) rows(BODY, f, 8, 12, 'D');
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrap(arm, plaid);
            top(arm, 'R');
            wrapRows(arm, "......RSSSSS");
            bottom(arm, 'S');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, "DDDDDDDDDdBB");
            top(leg, 'D');
            bottom(leg, 'B');
        }
        // Straw hat on the hat layer: woven crown, red band, brim edge.
        top(HAT, (f, i, j) -> (i + j) % 3 == 0 ? 'y' : 'Y');
        ring(HAT, "Yry");
    }

    /** Cowgirl: tan hat, red bandana, leather vest over a white shirt, jeans, tall boots. */
    static void rancher() {
        tone(0xFFE0B48A, 0xFFC9A24A, 0xFF4A7FB5);
        hair(0xFFE8C46A);
        colors("L", 0xFF8B5A2B, "l", 0xFF6B4320, "C", 0xFFF0EDE4, "c", 0xFFD3CFC3, "N", 0xFFC23B2E,
                "J", 0xFF4A6FA5, "j", 0xFF35527D, "B", 0xFF6B3E1F, "k", 0xFF4A2A14, "G", 0xFFD9B54A,
                "T", 0xFFA0703F, "t", 0xFF7A5230);
        head("long", "none", false);
        wrapRows(BODY, "LLLLLLLLlJJJ");
        top(BODY, 'L');
        face(BODY, Face.FRONT,
                "LNNNNNNL",
                "LLCNNCLL",
                "LLCCNCLL",
                "LLCCCCLL",
                "LLCCCCLL",
                "LLCcCCLL",
                "LLCCCCLL",
                "LLCCCCLL",
                "lllGGlll",
                "JJJJJJJJ",
                "JJJjjJJJ",
                "JJJjjJJJ");
        face(BODY, Face.BACK,
                "NNNNNNNN",
                "LNNNNNNL",
                "LLLNNLLL",
                "LLLLLLLL",
                "LlLLLLlL",
                "LLLLLLLL",
                "LLLLLLLL",
                "LLLLLLLL",
                "llllllll",
                "JJJJJJJJ",
                "JJJJJJJJ",
                "JJJJJJJJ");
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrapRows(arm, "CCCCCCCCCcSS");
            top(arm, 'C');
            bottom(arm, 'S');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, "JJJJJJJBBBBk");
            face(leg, Face.FRONT, rowsOf(12, "BBBB", 0, "JJJJ", 1, "JJJJ", 2, "JJJJ", 3, "JJJJ", 4, "JJJJ", 5, "JJJJ",
                    6, "JJJJ", 7, "BtBB", 11, "kkkk"));
            top(leg, 'J');
            bottom(leg, 'k');
        }
        // Cowboy hat: creased crown, dark band, brim.
        top(HAT, (f, i, j) -> i == 3 || i == 4 ? 't' : 'T');
        ring(HAT, "TtT");
    }

    /** Chef: tall pleated toque, white double-breasted jacket, red neckerchief, check trousers. */
    static void cook() {
        tone(0xFFF2C9A0, 0xFF2B2420, 0xFF5A3A22);
        hair(0xFF2B2420);
        colors("C", 0xFFF7F6F2, "c", 0xFFD8D6CF, "N", 0xFFD03A2F, "n", 0xFFA02A22,
                "P", 0xFF2E2E33, "p", 0xFFBFBFC4, "B", 0xFF1E1E22);
        head("short", "mustache", false);
        wrap(BODY, (f, i, j) -> j == 11 ? 'c' : 'C');
        top(BODY, 'C');
        face(BODY, Face.FRONT,
                "cNNNNNNc",
                "CCNNNNCC",
                "CCCnNCCC",
                "CCKCCKCC",
                "CCCCCcCC",
                "CCKCCKCC",
                "CCCCCcCC",
                "CCKCCKCC",
                "CCCCCcCC",
                "CCKCCKCC",
                "CCCCCcCC",
                "cccccccc");
        face(BODY, Face.BACK, rowsOf(12, "CCCCCCCC", 0, "NNNNNNNN", 11, "cccccccc"));
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrapRows(arm, "CCCCCCCCCcSS");
            top(arm, 'C');
            bottom(arm, 'S');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrap(leg, (f, i, j) -> j >= 10 ? 'B' : ((i / 2 + j / 2) % 2 == 0 ? 'P' : 'p'));
            top(leg, 'P');
            bottom(leg, 'B');
        }
        // Toque: white crown with pleats, puffed out on the hat layer over a white base.
        top(HEAD, 'C');
        top(HAT, (f, i, j) -> (i == 0 || j == 0 || i == 7 || j == 7) ? 'c' : 'C');
        wrap(HAT, (f, i, j) -> j > 2 ? '.' : (j == 2 ? 'c' : (i % 2 == 0 ? 'C' : 'c')));
    }

    /** Architect: yellow hard hat, hi-vis vest with reflective stripes over a blue shirt, khakis. */
    static void architect() {
        tone(0xFF8D5A3B, 0xFF1E1714, 0xFF3A2416);
        hair(0xFF1E1714);
        colors("Y", 0xFFF2C230, "y", 0xFFC99A1E, "O", 0xFFF07A1E, "o", 0xFFC95E10, "X", 0xFFD8DCE0,
                "C", 0xFF5A86C2, "c", 0xFF41679E, "T", 0xFFC2A878, "t", 0xFF9C8558, "B", 0xFF6B4A2B);
        head("long", "none", false);
        wrap(BODY, (f, i, j) -> j >= 10 ? 'T' : (j == 5 || j == 8 ? 'X' : 'O'));
        top(BODY, 'O');
        face(BODY, Face.FRONT,
                "OOCCCCOO",
                "OOOccOOO",
                "OOOCCOOO",
                "OoOCCOoO",
                "OoOCCOoO",
                "XXXCCXXX",
                "OOOCCOOO",
                "OOOCCOOO",
                "XXXCCXXX",
                "OOOCCOOO",
                "tttttttt",
                "TTTTTTTT");
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrapRows(arm, "CCCCCcSSSSSS");
            top(arm, 'C');
            bottom(arm, 'S');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, "TTTTTTTTTBBB");
            top(leg, 'T');
            bottom(leg, 'B');
        }
        // Hard hat: ridge down the middle, brim at the bottom row.
        top(HAT, (f, i, j) -> i == 3 || i == 4 ? 'y' : 'Y');
        ring(HAT, "YYy");
    }

    /** Teacher: glasses, maroon sweater vest over a light blue shirt and navy tie, grey slacks. */
    static void teacher() {
        tone(0xFFF2C9A0, 0xFF7A5230, 0xFF4A7FB5);
        hair(0xFF7A5230);
        colors("V", 0xFF8C2F39, "v", 0xFF6B222B, "C", 0xFFBFD6EE, "c", 0xFF9DB8D6, "T", 0xFF223A6B,
                "G", 0xFF5E6168, "g", 0xFF484A50, "B", 0xFF4A2F1C);
        head("short", "none", true);
        wrapRows(BODY, "VVVVVVVVVVvB");
        top(BODY, 'V');
        face(BODY, Face.FRONT,
                "CCCTTCCC",
                "VCCTTCCV",
                "VVCTTCVV",
                "VVVTTVVV",
                "VVVVVVVV",
                "VvVVVVvV",
                "VVVVVVVV",
                "VvVVVVvV",
                "VVVVVVVV",
                "VVVVVVVV",
                "vvvvvvvv",
                "BBBGBBBB");
        face(BODY, Face.BACK, rowsOf(12, "VVVVVVVV", 0, "CCCCCCCC", 10, "vvvvvvvv", 11, "BBBBBBBB"));
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrapRows(arm, "CCCCCCCCCCcS");
            top(arm, 'C');
            bottom(arm, 'S');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, "GGGGGGGGGGBB");
            face(leg, Face.FRONT, "GGGG", "GGgG", "GGgG", "GGgG", "GGgG", "GGgG", "GGgG", "GGgG", "GGgG", "GGGG", "BBBB", "BBBB");
            top(leg, 'G');
            bottom(leg, 'B');
        }
    }

    /** Doctor: white lab coat (outer layers) with stethoscope and badge over teal scrubs. */
    static void doctor() {
        tone(0xFFE8BC94, 0xFF2B1E17, 0xFF3A2416);
        hair(0xFF2B1E17);
        colors("C", 0xFFF7F8FA, "c", 0xFFD5D9E0, "U", 0xFF3FA7A0, "u", 0xFF2E827C, "X", 0xFF2B2D33,
                "x", 0xFFBFC5CC, "R", 0xFFD0453A, "p", 0xFF5A86C2, "g", 0xFF9AA0A8);
        head("ponytail", "none", false);
        wrapRows(BODY, "UUUUUUUUUUUu");
        top(BODY, 'U');
        face(BODY, Face.FRONT,
                "UUuUUuUU",
                "UUUuuUUU",
                "UUUUUUUU",
                "UUUUUUUU",
                "UUUUUUUU",
                "UUUXXUUU",
                "UUUxxUUU",
                "UUUUUUUU",
                "UUUUUUUU",
                "UUUUUUUU",
                "UUUUUUUU",
                "uuuuuuuu");
        // Lab coat: open front, stethoscope tubes down the lapels, badge and pen pocket.
        wrap(JACKET, (f, i, j) -> j == 11 ? 'c' : 'C');
        top(JACKET, 'C');
        face(JACKET, Face.FRONT,
                "CCX..XCC",
                "CCX..XCC",
                "CcX..XcC",
                "RxX..XpC",
                "CcX..XcC",
                "CcC..CcC",
                "CcC..CcC",
                "CcC..CcC",
                "CcC..CcC",
                "CcC..CcC",
                "CcC..CcC",
                "ccc..ccc");
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrapRows(arm, "UUUUSSSSSSSS");
            top(arm, 'U');
            bottom(arm, 'S');
        }
        for (Box sleeve : new Box[]{R_SLEEVE, L_SLEEVE}) {
            wrapRows(sleeve, "CCCCCCCCCCc.");
            top(sleeve, 'C');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, "UUUUUUUUUUWg");
            top(leg, 'U');
            bottom(leg, 'g');
        }
        // Coat skirt over the thighs, open at the front.
        for (Box pants : new Box[]{R_PANTS, L_PANTS}) wrapRows(pants, "CCCCCc......");
        face(R_PANTS, Face.FRONT, "CC..", "CC..", "CC..", "CC..", "CC..", "cc..");
        face(L_PANTS, Face.FRONT, "..CC", "..CC", "..CC", "..CC", "..CC", "..cc");
        clear(R_PANTS, Face.LEFT);
        clear(L_PANTS, Face.RIGHT);
    }

    /** Blacksmith: bald with a full beard, goggles, leather apron, bare forearms, heavy gloves and boots. */
    static void blacksmith() {
        tone(0xFF6B4226, 0xFF1A1412, 0xFF2A1A10);
        hair(0xFF1A1412);
        colors("A", 0xFF7A4E2A, "a", 0xFF573619, "T", 0xFF4F5157, "t", 0xFF3D3F44, "G", 0xFF3D2A1A,
                "P", 0xFF4A3A2E, "B", 0xFF2E2420, "R", 0xFF9AA0A6, "Q", 0xFFF0A040, "q", 0xFF2A2C30);
        head("bald", "beard", false);
        wrapRows(BODY, "TTTTTTTTTaPP");
        top(BODY, 'T');
        face(BODY, Face.FRONT,
                "TAATTAAT",
                "TATTTTAT",
                "TAAAAAAT",
                "TAAAAAAT",
                "TARAARAT",
                "TAAAAAAT",
                "TAaaaaAT",
                "TAaAAaAT",
                "TAaaaaAT",
                "aAAAAAAa",
                "PAAAAAAP",
                "PAAAAAAP");
        face(BODY, Face.BACK,
                "TTTTTTTT",
                "TATTTTAT",
                "TTATTATT",
                "TTTAATTT",
                "TTATTATT",
                "TATTTTAT",
                "TTTTTTTT",
                "TTTTTTTT",
                "TTTTTTTT",
                "aaaaaaaa",
                "PPPPPPPP",
                "PPPPPPPP");
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrapRows(arm, "TTSSSSSSaGGG");
            top(arm, 'T');
            bottom(arm, 'G');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, "PPPPPPPPPBBB");
            top(leg, 'P');
            bottom(leg, 'B');
        }
        // Apron hangs down over the front of the legs.
        face(R_PANTS, Face.FRONT, "AAAA", "AAAA", "AAAA", "AAAA", "AAAA", "AAAA", "aaaa");
        face(L_PANTS, Face.FRONT, "AAAA", "AAAA", "AAAA", "AAAA", "AAAA", "AAAA", "aaaa");
        // Goggles pushed up on the forehead.
        ring(HAT, "..q");
        face(HAT, Face.FRONT, "........", "........", "qQQqqQQq");
    }

    /** Merchant: red fez, burgundy vest with gold trim, satchel strap, teal sash, cream sleeves. */
    static void merchant() {
        tone(0xFFC68B59, 0xFF1E1714, 0xFF3A2416);
        hair(0xFF1E1714);
        colors("V", 0xFF7A1F3D, "v", 0xFF5A1530, "G", 0xFFD9B54A, "C", 0xFFEDE3C8, "c", 0xFFCFC2A3,
                "N", 0xFF2E8B83, "n", 0xFF206860, "P", 0xFF5A4632, "p", 0xFF45362A, "B", 0xFF3A2A1E,
                "L", 0xFF8B5A2B, "F", 0xFFB3262E, "f", 0xFF8A1C22);
        head("short", "mustache", false);
        wrapRows(BODY, "VVVVVVVVVNNP");
        top(BODY, 'V');
        face(BODY, Face.FRONT,
                "VVGCCGVL",
                "VVGCCGLV",
                "VVGCCLVV",
                "VVGCLGVV",
                "VVGLCGVV",
                "VvLCCGvV",
                "VLGCCGVV",
                "LVGCCGVV",
                "GGGCCGGG",
                "NNNNNNNN",
                "NnGGNNnN",
                "PPPPPPPP");
        face(BODY, Face.BACK,
                "LVVVVVVV",
                "VLVVVVVV",
                "VVLVVVVV",
                "VVVLVVVV",
                "VVVVLVVV",
                "VvVVVLvV",
                "VVVVVVLV",
                "VVVVVVVL",
                "GGGGGGGG",
                "NNNNNNNN",
                "NnNNNNnN",
                "PPPPPPPP");
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrapRows(arm, "CCCCCCCCcCGS");
            top(arm, 'C');
            bottom(arm, 'S');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, "PPPPPPPPpPBB");
            top(leg, 'P');
            bottom(leg, 'B');
        }
        // Fez: flat red crown with a black tassel falling down the right side.
        top(HAT, 'F');
        ring(HAT, "FFf");
        face(HAT, Face.RIGHT, "FFFFKFFF", "FFFFKFFF", "fffKffff", "...K....");
    }

    /** Musician: red beret, Breton striped shirt, guitar strap, black trousers. */
    static void musician() {
        tone(0xFFF2D0B0, 0xFFB5502E, 0xFF3F7A3A);
        hair(0xFFB5502E);
        colors("N", 0xFF24345E, "C", 0xFFF4F4F4, "R", 0xFFC0392B, "r", 0xFF962C22, "P", 0xFF26262B,
                "p", 0xFF3A3A40, "L", 0xFF7A4A24, "G", 0xFFD9B54A, "B", 0xFF1A1A1E);
        head("bob", "none", false);
        wrap(BODY, (f, i, j) -> j == 11 ? 'P' : (j % 2 == 0 ? 'C' : 'N'));
        top(BODY, 'C');
        // Strap from the left shoulder down to the right hip, front and back.
        int[] fr = region(BODY, Face.FRONT), bk = region(BODY, Face.BACK);
        for (int j = 0; j < 11; j++) {
            int i = 7 - Math.min(7, j * 7 / 10);
            skin.setRGB(fr[0] + i, fr[1] + j, pal.get('L'));
            skin.setRGB(bk[0] + 7 - i, bk[1] + j, pal.get('L'));
        }
        skin.setRGB(fr[0] + 6, fr[1] + 1, pal.get('G'));
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrap(arm, (f, i, j) -> j >= 10 ? 'S' : (j % 2 == 0 ? 'C' : 'N'));
            top(arm, 'C');
            bottom(arm, 'S');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrapRows(leg, "PPPPPPPPPpBB");
            top(leg, 'P');
            bottom(leg, 'B');
        }
        // Beret: slouched crown with a little stalk mark in the middle.
        top(HAT, (f, i, j) -> (i == 3 || i == 4) && (j == 3 || j == 4) ? 'r' : 'R');
        ring(HAT, "r");
    }

    /** Soldier: camo uniform, olive helmet, plate carrier with pouches, gloves and boots. */
    static void soldier() {
        tone(0xFFA86F45, 0xFF2B2420, 0xFF3A2416);
        hair(0xFF2B2420);
        colors("O", 0xFF5B6B3A, "o", 0xFF45512C, "Q", 0xFF7A8650, "D", 0xFF3A4226, "T", 0xFF8B7A55,
                "t", 0xFF6E6043, "G", 0xFF2E302C, "B", 0xFF2E2A24, "V", 0xFF4E5A34, "v", 0xFF3C4528);
        head("short", "none", false);
        Pattern camo = (f, i, j) -> camo(f, i, j);
        wrap(BODY, camo);
        top(BODY, 'O');
        face(BODY, Face.FRONT,
                "OQVVVVOD",
                "QVVVVVVO",
                "OVVVVVVQ",
                "DVvVVvVO",
                "OVVVVVVO",
                "QVVVVVVD",
                "OVTTTTVO",
                "DVTtTtVQ",
                "OVTTTTVO",
                "QvvvvvvO",
                "ttttTttt",
                "OQOODQOD");
        face(BODY, Face.BACK,
                "OVVVVVVQ",
                "QVVVVVVO",
                "OVVvvVVD",
                "DVVVVVVO",
                "OVVVVVVQ",
                "QVvVVvVO",
                "OVVVVVVD",
                "DVVVVVVO",
                "OVVVVVVQ",
                "QvvvvvvO",
                "tttttttt",
                "ODQOQODO");
        for (Box arm : new Box[]{R_ARM, L_ARM}) {
            wrap(arm, camo);
            top(arm, 'O');
            wrapRows(arm, "..........GG");
            bottom(arm, 'G');
        }
        for (Box leg : new Box[]{R_LEG, L_LEG}) {
            wrap(leg, camo);
            top(leg, 'O');
            wrapRows(leg, ".........BBB");
            face(leg, Face.FRONT, rowsOf(6, "....", 4, "oooo", 5, "oVVo"));
            bottom(leg, 'B');
        }
        // Helmet with a camo cover and a rim; chin strap on the base face sides.
        top(HAT, camo);
        wrap(HAT, (f, i, j) -> j > 2 ? '.' : (j == 2 ? 'o' : camo(f, i, j)));
        for (Face f : new Face[]{Face.RIGHT, Face.LEFT}) {
            int[] r = region(HEAD, f);
            int col = f == Face.RIGHT ? r[0] + 6 : r[0] + 1;
            for (int j = 3; j < 8; j++) skin.setRGB(col, r[1] + j, pal.get('t'));
        }
    }

    /** Chunky woodland camo: a fixed, blotchy mix of light, base and dark olive. */
    static char camo(Face f, int i, int j) {
        int h = ((i / 2) * 73856093) ^ ((j / 2) * 19349663) ^ (f.ordinal() * 83492791);
        int m = Math.floorMod(h, 7);
        return m < 2 ? 'Q' : (m < 4 ? 'D' : 'O');
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
