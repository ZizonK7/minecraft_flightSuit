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
 *   mk3: Phantom (MapleStory) as armour - white plate, gold trim, royal blue crown and boots, amethyst core,
 *        domino-mask faceplate; epaulettes and coat tails stand out on the outer layers
 *   mk4: Hero of Twilight as armour - green cap with a gold brim and drooping tail, triforce chest plate,
 *        chainmail, leather bracers and boots; the Hylian shield and sword hilt ride on the jacket layer's back
 *   mk0: Scrap Mark 0 (M18) - the cave suit: grey iron plate, copper rivets and pipes, a round boiler door on the
 *        chest with a coal glow, a slit visor, a chimney stub on the back
 *   mk6: Cannon Shooter (M18) - white spiky hair under a red bandana, green eyes, a black-and-brown gunner's
 *        jacket with brass buckles, a bandolier of shells, striped trousers, brown boots
 *   mk7: Bishop (M18) - long ash-blonde hair under a white mitre with a gold cross, white-and-teal vestments,
 *        gold trim, a blue gem at the throat, white gloves, dark stockings and white shoes
 *
 * Usage: java tools/SuitSkinGen.java <mk0|mk1|mk2|mk3|mk4|mk6|mk7> <out.png>
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

    private static final Map<Character, Integer> MK3 = Map.ofEntries(
            Map.entry('W', 0xFFF3F4F8), Map.entry('w', 0xFFC9CCD8),
            Map.entry('Y', 0xFFE9B42A), Map.entry('y', 0xFFB0811C),
            Map.entry('B', 0xFF2238B8), Map.entry('b', 0xFF152276),
            Map.entry('P', 0xFF7A3BE0), Map.entry('p', 0xFF4E1FA6), Map.entry('q', 0xFFC9A6FF),
            Map.entry('E', 0xFFE6D4FF), Map.entry('K', 0xFF1F2130), Map.entry('k', 0xFF4A4D5C));

    private static final Map<Character, Integer> MK4 = Map.ofEntries(
            Map.entry('G', 0xFF4F8A3C), Map.entry('g', 0xFF6FAE52), Map.entry('n', 0xFF33612B),
            Map.entry('H', 0xFFE3C278), Map.entry('h', 0xFFB8954F),
            Map.entry('S', 0xFFF2C1A5), Map.entry('s', 0xFFDDA68A), Map.entry('E', 0xFF2F6FD6), Map.entry('W', 0xFFF4F2EA),
            Map.entry('L', 0xFF7A5230), Map.entry('l', 0xFF553620), Map.entry('T', 0xFFC9A877), Map.entry('t', 0xFFA88A5C),
            Map.entry('Y', 0xFFE6B53A), Map.entry('y', 0xFFA8801F),
            Map.entry('M', 0xFF9298A0), Map.entry('m', 0xFF62676F),
            Map.entry('B', 0xFF2F62C8), Map.entry('A', 0xFFCDD3DC), Map.entry('a', 0xFF8E96A3), Map.entry('R', 0xFFC3383A),
            Map.entry('P', 0xFF4A3DA8), Map.entry('p', 0xFF2D2672));

    private static final Map<Character, Integer> MK0 = Map.ofEntries(
            Map.entry('G', 0xFF6E7074), Map.entry('g', 0xFF8C8F94), Map.entry('d', 0xFF4A4C50), Map.entry('k', 0xFF2E3034),
            Map.entry('C', 0xFFC07A3C), Map.entry('c', 0xFFE09A58), Map.entry('b', 0xFF8A5226),
            Map.entry('F', 0xFFFF9A2A), Map.entry('f', 0xFFFFD36A), Map.entry('K', 0xFF161618),
            Map.entry('W', 0xFFD8DADC), Map.entry('L', 0xFF5A3A22), Map.entry('l', 0xFF3E2816));

    private static final Map<Character, Integer> MK6 = Map.ofEntries(
            Map.entry('W', 0xFFF2F2F2), Map.entry('w', 0xFFC8C8CC), Map.entry('R', 0xFFC8312A), Map.entry('r', 0xFF8E1F1A),
            Map.entry('S', 0xFFE8B98A), Map.entry('s', 0xFFC8976A), Map.entry('E', 0xFF3FB65A), Map.entry('K', 0xFF1C1A1C),
            Map.entry('B', 0xFF4A3426), Map.entry('b', 0xFF2F2018), Map.entry('J', 0xFF2A2A2E), Map.entry('j', 0xFF3E3E44),
            Map.entry('Y', 0xFFE2B341), Map.entry('y', 0xFFA87E22), Map.entry('T', 0xFF6B4A32), Map.entry('t', 0xFF4E3524),
            Map.entry('P', 0xFF8A6A4A), Map.entry('p', 0xFF5E4631), Map.entry('H', 0xFF2E2D34), Map.entry('O', 0xFFD9A05A));

    private static final Map<Character, Integer> MK7 = Map.ofEntries(
            Map.entry('W', 0xFFF7F6F0), Map.entry('w', 0xFFD9D6CC), Map.entry('T', 0xFF2E8A7E), Map.entry('t', 0xFF1F5F57),
            Map.entry('Y', 0xFFE2B341), Map.entry('y', 0xFFA87E22), Map.entry('H', 0xFFC9B48E), Map.entry('h', 0xFFA8926C),
            Map.entry('S', 0xFFF6DCC2), Map.entry('s', 0xFFDDB89A), Map.entry('E', 0xFFD9A52E), Map.entry('K', 0xFF2A2024),
            Map.entry('B', 0xFF4FB6E8), Map.entry('D', 0xFF34323A), Map.entry('d', 0xFF26242A), Map.entry('G', 0xFFEDEDF2));

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
            case "mk3" -> {
                palette = MK3;
                drawMk3();
            }
            case "mk4" -> {
                palette = MK4;
                drawMk4();
            }
            case "mk0" -> {
                palette = MK0;
                drawMk0();
            }
            case "mk6" -> {
                palette = MK6;
                drawMk6();
            }
            case "mk7" -> {
                palette = MK7;
                drawMk7();
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

    // ---- Mark 3: Phantom ----

    private static void drawMk3() {
        // Helmet: royal blue crown with a gold band (Phantom's hat), white plate, domino-mask faceplate.
        face(8, 0, // top: gold crest running back to front
                "bBBYYBBb",
                "BBBYYBBB",
                "BBBYYBBB",
                "BBBYYBBB",
                "BBBYYBBB",
                "BBBYYBBB",
                "BBBYYBBB",
                "bBBYYBBb");
        face(16, 0, rows(8, "kkkkkkkk"));
        String[] side = { // amethyst ear disc in a gold ring
                "BBBBBBBB",
                "YYYYYYYY",
                "wWWWWWWW",
                "wWWYYWWW",
                "wWYqPYWW",
                "wWYPpYWW",
                "wWWYYWWW",
                "wwwwwwww"};
        face(0, 8, side);
        face(16, 8, mirror(side));
        face(8, 8,
                "BBBBBBBB",
                "YYYYYYYY",
                "WWWYYWWW",
                "WKKKKKKW",
                "KEEKKEEK",
                "WWWWWWWW",
                "YWWkkWWY",
                "yYWWWWYy");
        face(24, 8,
                "BBBBBBBB",
                "YYYYYYYY",
                "WWWYYWWW",
                "WWWYYWWW",
                "WWWWWWWW",
                "wWWWWWWw",
                "WWWWWWWW",
                "wwwwwwww");
        // Hat layer: the crown and band stand out a little, like a hat.
        face(40, 0, rows(8, "BBBBBBBB"));
        for (int x : new int[]{32, 40, 48, 56}) face(x, 8, "BBBBBBBB", "YYYYYYYY");

        // Torso: amethyst core in a gold ring, cravat knot at the collar, plated abs, blue sash with a gem buckle.
        face(20, 16, "wWWBBWWw", "WWWBBWWW", "WWWWWWWW", "wWWWWWWw");
        face(28, 16, rows(4, "kkkkkkkk"));
        String[] bodySide = {
                "WWWW", "WWWW", "WWWW", "WWWW", "WWWW", "wwww",
                "WWWW", "WWWW", "BBBB", "BBBB", "WWWW", "wwww"};
        face(16, 20, bodySide);
        face(28, 20, mirror(bodySide));
        face(20, 20,
                "wWWBBWWw",
                "WWYYYYWW",
                "WYYqPYYW",
                "WYYPpYYW",
                "WWYYYYWW",
                "WwWWWWwW",
                "WWwWWwWW",
                "WwWWWWwW",
                "BBYYYYBB",
                "BBYPpYBB",
                "WWWwwWWW",
                "wWWwwWWw");
        face(32, 20, // back: gold V, twin amethyst thrusters
                "wWWWWWWw",
                "WYWWWWYW",
                "WWYWWYWW",
                "WWWYYWWW",
                "WkkWWkkW",
                "WkPWWPkW",
                "WkkWWkkW",
                "WWWYYWWW",
                "BBBBBBBB",
                "BBBBBBBB",
                "WWWWWWWW",
                "wwwwwwww");

        // Arms: gold epaulettes (stand out on the sleeve layer), white plate, gold cuffs, amethyst repulsors.
        String[] armSide = {
                "YYYY", "YYYY", "yYyY", "WWWW", "WWWW", "wwww",
                "WWWW", "WWWW", "YYYY", "WWWW", "WWWW", "wwww"};
        String[] epaulette = {"YYYY", "YYYY", "yYyY"};
        String[] epauletteTop = {"YYYY", "YqPY", "YPpY", "YYYY"};
        for (int[] uv : new int[][]{{40, 16, 40, 32}, {32, 48, 48, 48}}) {
            int u = uv[0], v = uv[1], su = uv[2], sv = uv[3];
            face(u + 4, v, epauletteTop);
            face(u + 8, v, "kkkk", "kqPk", "kPpk", "kkkk");
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, armSide);
            face(su + 4, sv, epauletteTop);
            for (int i = 0; i < 4; i++) face(su + 4 * i, sv + 4, epaulette);
        }

        // Legs: white plate, gold knee caps, royal blue boots with amethyst sole thrusters.
        String[] legFront = {
                "WWWW", "WWWW", "WwWW", "WWWW", "wwww", "WYYW",
                "WWWW", "WWWW", "YYYY", "BBBB", "BBBB", "kkkk"};
        String[] legBack = {
                "WWWW", "WWWW", "WWWW", "WWWW", "wwww", "WWWW",
                "WWWW", "WWWW", "YYYY", "BBBB", "BBBB", "kkkk"};
        // Coat tails on the pants layer: back and outer side only, upper half.
        String[] tail = {"YYYY", "YWWY", "YWWY", "YWWY", "YWWY", "YWWY", "YyyY"};
        int[][] legs = {{0, 16, 0, 32, 0}, {16, 48, 0, 48, 8}}; // base uv, pants uv, outer-side column
        for (int[] l : legs) {
            int u = l[0], v = l[1];
            face(u + 4, v, rows(4, "WWWW"));
            face(u + 8, v, "kkkk", "kPPk", "kPPk", "kkkk");
            face(u, v + 4, legFront);
            face(u + 4, v + 4, legFront);
            face(u + 8, v + 4, legFront);
            face(u + 12, v + 4, legBack);
            face(l[2] + 12, l[3] + 4, tail);
            face(l[2] + l[4], l[3] + 4, tail);
        }
    }

    // ---- Mark 4: Hero of Twilight ----

    private static void drawMk4() {
        // Helmet: the hero's green cap with a gold brim band, blonde fringe and side locks, pointed ears.
        face(8, 0, // top: row 0 touches the back, row 7 the brim
                "nGGGGGGn",
                "GGGgGGGG",
                "GGgGGGGG",
                "GGGGGGgG",
                "GGGGGGGG",
                "GgGGGGGG",
                "GGGGGGGG",
                "yYYYYYYy");
        face(16, 0, rows(8, "nnnnnnnn"));
        String[] side = { // back to front on the right side
                "GGGGGGGG",
                "YYYYYYYY",
                "GGGGGhHH",
                "GGGGhHHH",
                "GGGGhSHH",
                "GGGGhHHH",
                "nGGGGhHh",
                "nnGGGhhh"};
        face(0, 8, side);
        face(16, 8, mirror(side));
        face(8, 8,
                "GGGGGGGG",
                "YYYYYYYY",
                "HHhHHhHH",
                "HsSSSSsH",
                "HWESSEWH",
                "hSSSSSSh",
                "hSSssSSh",
                "hhSSSShh");
        face(24, 8,
                "GGGGGGGG",
                "YYYYYYYY",
                "GGgGGGGG",
                "GGGGGgGG",
                "GgGGGGGG",
                "GGGGgGGG",
                "nGGGGGGn",
                "nnGGGGnn");
        // Hat layer: the cap's long tail droops down the back, fringe and side locks stand out, ear tips poke out.
        face(40, 0, "GGGGGGGG", "GGGGGGGG", "nGGGGGGn");
        face(56, 8,
                "GGGGGGGG",
                "nGGGGGGn",
                ".nGGGGn.",
                "..GGGG..",
                "..nGGn..",
                "...GG...",
                "...nn...");
        String[] hatSide = {
                "GGGGGGGG",
                "........",
                "......HH",
                "..sS...H",
                "...SS..H",
                ".......H",
                ".......h"};
        face(32, 8, hatSide);
        face(48, 8, mirror(hatSide));
        face(40, 8, "GGGGGGGG", "........", "HH....HH", "H......H");

        // Torso: chainmail collar, green plate with a gold triforce, leather belt; the sword baldric on the back.
        face(20, 16, "GGGGGGGG", "GGGGGGGG", "GMMMMMMG", "nMMMMMMn"); // row 0 = back
        face(28, 16, rows(4, "nnnnnnnn"));
        String[] bodySide = {
                "GGGG", "GGGG", "GgGG", "GGGG", "GGgG", "GGGG",
                "GGGG", "nGGn", "LLLL", "nGGn", "GGGG", "nnnn"};
        face(16, 20, bodySide);
        face(28, 20, mirror(bodySide));
        face(20, 20,
                "nMMMMMMn",
                "GnMmmMnG",
                "GGnMMnGG",
                "GgGGYGGG",
                "GgGYyYGG",
                "GGYYYYYG",
                "GgGGGGGn",
                "GGGGGGGn",
                "lLLYYLLl",
                "nGGGGGGn",
                "GgGGGGgG",
                "nnGnnGnn");
        face(32, 20, // back: baldric from the left shoulder (sword hilt) down to the right hip
                "GlLGGGGG",
                "GGlLGGGG",
                "GGGlLGGG",
                "GGGGlLGG",
                "GGGGGlLG",
                "GGGGGGlL",
                "GGGGGGGl",
                "nGGGGGGn",
                "lLLLLLLl",
                "nGGGGGGn",
                "GGGGGGGG",
                "nnGnnGnn");
        // Jacket layer: belt and tunic skirt flare out; the Hylian shield on the back with the sword hilt above it.
        face(20, 32, ".....pP.");
        face(20, 36,
                "........", "........", "........", "........", "........", "........",
                "........", "........",
                "lLLYYLLl",
                "nGGGGGGn",
                "GgGGGGgG",
                "nnGnnGnn");
        face(32, 36,
                ".Pp.....",
                "aAAAAAAa",
                "ABBYYBBA",
                "ABYYYYBA",
                "ABBBBBBA",
                "ARBBBBRA", // the red bird: wings swept up, body below
                "ABRBBRBA",
                "ABBRRBBA",
                "ABRRRRBA",
                "aABRRBAa",
                ".aABBAa.",
                "..aAAa..");
        String[] skirtSide = {"....", "....", "....", "....", "....", "....", "....", "....", "LLLL", "nGGn", "GGGG", "nnnn"};
        face(16, 36, skirtSide);
        face(28, 36, skirtSide);

        // Arms: green sleeves under plated shoulders, chainmail elbows, leather bracers with gold studs, gloves.
        String[] arm = {
                "GGGG", "GgGG", "GGGG", "nnnn", "MMMM", "mMmM",
                "LLLL", "LYLL", "LLLL", "lLLl", "llll", "llll"};
        String[] pauldron = {"GGGG", "GgGG", "yYYy", "....", "....", "....", "LLLL", "LYYL", "lLLl"};
        for (int[] uv : new int[][]{{40, 16, 40, 32}, {32, 48, 48, 48}}) {
            int u = uv[0], v = uv[1], su = uv[2], sv = uv[3];
            face(u + 4, v, rows(4, "GGGG"));
            face(u + 8, v, rows(4, "llll"));
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, arm);
            face(su + 4, sv, "GGGG", "GgGG", "GGgG", "GGGG");
            for (int i = 0; i < 4; i++) face(su + 4 * i, sv + 4, pauldron);
        }

        // Legs: tan plate trousers with silver knee guards, brown leather boots with a folded cuff.
        String[] leg = {
                "TTTT", "TTTT", "tTTt", "TTTT", "aAAa", "TaaT",
                "TTTT", "tTTt", "lLLl", "LLLL", "LLLL", "llll"};
        String[] cuff = {"....", "....", "....", "....", "....", "....", "....", "....", "LTTL"};
        for (int[] l : new int[][]{{0, 16, 0, 32}, {16, 48, 0, 48}}) {
            int u = l[0], v = l[1];
            face(u + 4, v, rows(4, "TTTT"));
            face(u + 8, v, rows(4, "llll"));
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, leg);
            for (int i = 0; i < 4; i++) face(l[2] + 4 * i, l[3] + 4, cuff);
        }
    }

    // ---- Mark 0: the scrap suit (M18) ----

    private static void drawMk0() {
        // Helmet: a riveted iron bucket with a slit visor and a copper seam, a bolt on each side.
        face(8, 0,
                "dGGGGGGd",
                "GgGGGGgG",
                "GGGCCGGG",
                "GGCCCCGG",
                "GGCCCCGG",
                "GGGCCGGG",
                "GgGGGGgG",
                "dGGGGGGd");
        face(16, 0, rows(8, "dddddddd"));
        String[] side = {
                "dGGGGGGd",
                "GGGGGGGG",
                "GGcCGGGG",
                "GCCCCGGG",
                "GCCCCGGG",
                "GGcCGGGG",
                "GGGGGGGG",
                "dddddddd"};
        face(0, 8, side);
        face(16, 8, mirror(side));
        face(8, 8, // front: the visor slit glows faintly, a copper mouth grille
                "dGGGGGGd",
                "GgGGGGgG",
                "GGGGGGGG",
                "GKffffKG",
                "GGGGGGGG",
                "GGCbCbCG",
                "GGCbCbCG",
                "dGGGGGGd");
        face(24, 8,
                "dGGGGGGd",
                "GgGGGGgG",
                "GGGGGGGG",
                "GGdGGdGG",
                "GGdGGdGG",
                "GGGGGGGG",
                "GgGGGGgG",
                "dGGGGGGd");
        // Hat layer: copper rivets and a pipe along the top.
        face(40, 0, "........", "..CCCC..", "........");
        face(40, 8, "g......g", "........", "........", "........", "........", "........", "........", "g......g");
        face(32, 8, ".......g", "........", "........", "........", "........", "........", "........", ".......g");
        face(48, 8, "g.......", "........", "........", "........", "........", "........", "........", "g.......");

        // Torso: iron plate, the round boiler door with a coal glow in the middle, copper pipes down the sides.
        face(20, 16, "dGGGGGGd", "GGGCCGGG", "GGGCCGGG", "dGGGGGGd");
        face(28, 16, rows(4, "dddddddd"));
        String[] bodySide = {"CGGd", "CGGd", "CGGd", "CGGd", "CGGd", "CGGd", "CGGd", "CGGd", "CGGd", "CGGd", "dddd", "dddd"};
        face(16, 20, bodySide);
        face(28, 20, mirror(bodySide));
        face(20, 20,
                "dGgGGgGd",
                "GGGGGGGG",
                "GGCCCCGG",
                "GCbFFbCG",
                "GCFffFCG",
                "GCFffFCG",
                "GCbFFbCG",
                "GGCCCCGG",
                "GGGGGGGG",
                "dGGGGGGd",
                "LLLLLLLL",
                "llllllll");
        face(32, 20, // back: the chimney stub between the shoulders, a copper tank
                "dGgGGgGd",
                "GGGkkGGG",
                "GGGkkGGG",
                "GCCCCCCG",
                "GCccccCG",
                "GCccccCG",
                "GCccccCG",
                "GCCCCCCG",
                "GGGGGGGG",
                "dGGGGGGd",
                "LLLLLLLL",
                "llllllll");
        // Jacket layer: the chimney stands out of the back, the belt buckle.
        face(32, 36, "...kk...", "...KK...", "........", "........", "........", "........", "........", "........", "........", "........", "........", "........");
        face(20, 36, "........", "........", "........", "........", "........", "........", "........", "........", "........", "........", "...CC...", "........");

        // Arms: plate with copper elbow joints and rivets, dark gloves.
        String[] arm = {"GGGG", "GgGG", "GGGG", "dGGd", "CCCC", "cCcC", "GGGG", "GGgG", "GGGG", "dddd", "kkkk", "kkkk"};
        for (int[] uv : new int[][]{{40, 16}, {32, 48}}) {
            int u = uv[0], v = uv[1];
            face(u + 4, v, rows(4, "GGGG"));
            face(u + 8, v, rows(4, "kkkk"));
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, arm);
        }
        // Legs: plate thighs, copper knee pipes, heavy iron boots with a copper toe.
        String[] leg = {"GGGG", "GgGG", "GGGG", "dGGd", "CCCC", "GGGG", "GGGG", "dddd", "dGGd", "GGGG", "GGGG", "CCCC"};
        for (int[] l : new int[][]{{0, 16}, {16, 48}}) {
            int u = l[0], v = l[1];
            face(u + 4, v, rows(4, "GGGG"));
            face(u + 8, v, rows(4, "dddd"));
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, leg);
        }
    }

    // ---- Mark 6: Cannon Shooter (M18) ----

    private static void drawMk6() {
        // Helmet: spiky white hair with a red bandana knotted at the back, green eyes, an earring.
        face(8, 0,
                "wWWWWWWw",
                "WWwWWWwW",
                "WWWWWWWW",
                "WwWWWWWW",
                "WWWWWwWW",
                "WWWWWWWW",
                "RRRRRRRR",
                "RrRRRRrR");
        face(16, 0, rows(8, "KKKKKKKK"));
        String[] side = {
                "WWwWWWWW",
                "WWWWWwWW",
                "RRRRRRRR",
                "rRRRRRRR",
                "SSSSsSSS",
                "SSSSSSSS",
                "SYSSSSSS",
                "SSSsSSSS"};
        face(0, 8, side);
        face(16, 8, mirror(side));
        face(8, 8,
                "WWwWWwWW",
                "wWWWWWWw",
                "RRRRRRRR",
                "RrRRRRrR",
                "SEKSSKES",
                "SSSSSSSS",
                "SsSKKSsS",
                "SSSSSSSS");
        face(24, 8,
                "WWWwWWWW",
                "WwWWWWwW",
                "RRRRRRRR",
                "RRRrrRRR",
                "SSRrrRSS",
                "SSSrrSSS",
                "SSSSSSSS",
                "SSSSSSSS");
        // Hat layer: hair spikes stand up and out, the bandana's tails hang down the back.
        face(40, 0, "..WWWW..", ".WWWWWW.", "WWWWWWWW");
        face(40, 8, "W.WWWW.W", "WWWWWWWW", "........", "........", "........", "........", "........", "........");
        face(32, 8, "WWW.....", "WWWWW...", "........", "........", "........", "........", "........", "........");
        face(48, 8, ".....WWW", "...WWWWW", "........", "........", "........", "........", "........", "........");
        face(56, 8, "WW....WW", "WWWWWWWW", "........", "...rr...", "..r..r..", "..r..r..", ".r....r.", "........");

        // Torso: black jacket open over a brown shirt, a brass-buckled bandolier of shells across the chest.
        face(20, 16, "JJJJJJJJ", "JJSSSSJJ", "JJSSSSJJ", "JJJJJJJJ");
        face(28, 16, rows(4, "bbbbbbbb"));
        String[] bodySide = {"JJJJ", "JjJJ", "JJJJ", "JJjJ", "JJJJ", "JJJJ", "JjJJ", "JJJJ", "TTTT", "YYYY", "TTTT", "tttt"};
        face(16, 20, bodySide);
        face(28, 20, mirror(bodySide));
        face(20, 20,
                "JJRRRRJJ",
                "JJTTTTJJ",
                "JOTTTTJJ",
                "JJOTTTJJ",
                "JJJOTTJJ",
                "JJJJOTJJ",
                "JJJJJOJJ",
                "JjJJJJjJ",
                "TTTTTTTT",
                "YYTYYTYY",
                "TTTTTTTT",
                "tttttttt");
        face(32, 20, // back: the bandolier crosses, a brass plate
                "JJRRRRJJ",
                "JJJJJJJJ",
                "JJJJJOJJ",
                "JJJJOJJJ",
                "JJJOJJJJ",
                "JJOJJJJJ",
                "JOJJJJJJ",
                "JjJJJJjJ",
                "TTTTTTTT",
                "YYYYYYYY",
                "TTTTTTTT",
                "tttttttt");
        // Jacket layer: the collar flares, the belt pouch.
        face(20, 36, "JJ....JJ", "........", "........", "........", "........", "........", "........", "........", "........", "...PPP..", "...ppp..", "........");
        face(32, 36, "JJ....JJ", "........", "........", "........", "........", "........", "........", "........", "........", "........", "........", "........");
        String[] skirtSide = {"J...", "....", "....", "....", "....", "....", "....", "....", "....", "....", "....", "...."};
        face(16, 36, skirtSide);
        face(28, 36, mirror(skirtSide));

        // Arms: black sleeves rolled above brown leather bracers with brass studs, skin at the elbow, gloves.
        String[] arm = {"JJJJ", "JjJJ", "JJJJ", "JJjJ", "JJJJ", "SSSS", "SSsS", "TTTT", "TYTT", "TTTT", "tttt", "PPPP"};
        for (int[] uv : new int[][]{{40, 16}, {32, 48}}) {
            int u = uv[0], v = uv[1];
            face(u + 4, v, rows(4, "JJJJ"));
            face(u + 8, v, rows(4, "PPPP"));
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, arm);
        }
        // Legs: striped brown trousers, brown boots with a buckle.
        String[] leg = {"BbBb", "bBbB", "BbBb", "bBbB", "BbBb", "bBbB", "BbBb", "bBbB", "TTTT", "TYTT", "TTTT", "tttt"};
        for (int[] l : new int[][]{{0, 16}, {16, 48}}) {
            int u = l[0], v = l[1];
            face(u + 4, v, rows(4, "BbBb"));
            face(u + 8, v, rows(4, "tttt"));
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, leg);
        }
    }

    // ---- Mark 7: Bishop (M18) ----

    private static void drawMk7() {
        // Helmet: long ash-blonde hair under a white mitre with a gold cross and a blue gem, amber eyes.
        face(8, 0,
                "wWWWWWWw",
                "WWWYYWWW",
                "WWWYYWWW",
                "WYYYYYYW",
                "WWWYYWWW",
                "WWWYYWWW",
                "WWWWWWWW",
                "HHHHHHHH");
        face(16, 0, rows(8, "HHHHHHHH"));
        String[] side = {
                "WWWWWWWW",
                "WWWWWWWW",
                "HHHHHHHH",
                "HhHHHHHH",
                "HHHHSSSS",
                "HHHHSSSS",
                "HHHhSSSS",
                "HHHHSSSS"};
        face(0, 8, side);
        face(16, 8, mirror(side));
        face(8, 8,
                "WWWYYWWW",
                "WWWBBWWW",
                "HHHHHHHH",
                "HhHHHHhH",
                "SEKSSKES",
                "SSSSSSSS",
                "SsSSSSsS",
                "SSSsSSSS");
        face(24, 8,
                "WWWWWWWW",
                "WWWWWWWW",
                "HHHHHHHH",
                "HHhHHhHH",
                "HHHHHHHH",
                "HhHHHHhH",
                "HHHHHHHH",
                "HHHHHhHH");
        // Hat layer: the mitre rises above the head (gold band), the hair falls long down the back and sides.
        face(40, 0, "........", "..WWWW..", "..WWWW..");
        face(40, 8, "..WYYW..", "..WWWW..", "........", "........", "........", "........", "........", "........");
        face(32, 8, "....WW..", "....WW..", "........", "........", "....HH..", "....HH..", "....HH..", "....HH..");
        face(48, 8, "..WW....", "..WW....", "........", "........", "..HH....", "..HH....", "..HH....", "..HH....");
        face(56, 8, "..WWWW..", "..WWWW..", "HHHHHHHH", "HHHHHHHH", "HhHHHHhH", "HHHHHHHH", "HHHhHHHH", "HHHHHHHH");

        // Torso: white vestments with a teal stole and gold trim, a blue gem at the throat, a gold belt.
        face(20, 16, "WWWWWWWW", "WWWTTWWW", "WWWTTWWW", "WWWWWWWW");
        face(28, 16, rows(4, "wwwwwwww"));
        String[] bodySide = {"WWWW", "WwWW", "WWWW", "WWWW", "WwWW", "WWWW", "WWWW", "WWWW", "YYYY", "WWWW", "WWWW", "wwww"};
        face(16, 20, bodySide);
        face(28, 20, mirror(bodySide));
        face(20, 20,
                "WWWBBWWW",
                "WTTWWTTW",
                "WTtWWtTW",
                "WTTWWTTW",
                "WTtWWtTW",
                "WTTWWTTW",
                "WTYWWYTW",
                "WWWWWWWW",
                "YYYEEYYY",
                "WWWWWWWW",
                "WWWWWWWW",
                "wwwwwwww");
        face(32, 20, // back: the stole continues, a gold cross
                "WWWWWWWW",
                "WWWYYWWW",
                "WWYYYYWW",
                "WWWYYWWW",
                "WWWYYWWW",
                "WWWWWWWW",
                "WwWWWWwW",
                "WWWWWWWW",
                "YYYYYYYY",
                "WWWWWWWW",
                "WWWWWWWW",
                "wwwwwwww");
        // Jacket layer: the cape's shoulders stand out and the skirt flares.
        face(20, 36, "TT....TT", "........", "........", "........", "........", "........", "........", "........", "........", "WWWWWWWW", "wWWWWWWw", "wwwwwwww");
        face(32, 36, "TTTTTTTT", "TTTTTTTT", "tTTTTTTt", "tTTTTTTt", "tTTTTTTt", ".tTTTTt.", ".tTTTTt.", "..tTTt..", "........", "WWWWWWWW", "wWWWWWWw", "wwwwwwww");
        String[] skirtSide = {"T...", "....", "....", "....", "....", "....", "....", "....", "....", "WWWW", "WWWW", "wwww"};
        face(16, 36, skirtSide);
        face(28, 36, mirror(skirtSide));

        // Arms: white sleeves with teal cuffs and gold bands, white gloves.
        String[] arm = {"WWWW", "WwWW", "WWWW", "WWWW", "WwWW", "WWWW", "WWWW", "YYYY", "TTTT", "TtTT", "GGGG", "GGGG"};
        for (int[] uv : new int[][]{{40, 16}, {32, 48}}) {
            int u = uv[0], v = uv[1];
            face(u + 4, v, rows(4, "WWWW"));
            face(u + 8, v, rows(4, "GGGG"));
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, arm);
        }
        // Legs: the white skirt over dark stockings, white shoes with a gold buckle.
        String[] leg = {"WWWW", "WWWW", "WWWW", "wwww", "DDDD", "DdDD", "DDDD", "DDdD", "DDDD", "GGGG", "GYGG", "GGGG"};
        for (int[] l : new int[][]{{0, 16}, {16, 48}}) {
            int u = l[0], v = l[1];
            face(u + 4, v, rows(4, "WWWW"));
            face(u + 8, v, rows(4, "GGGG"));
            for (int i = 0; i < 4; i++) face(u + 4 * i, v + 4, leg);
        }
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
