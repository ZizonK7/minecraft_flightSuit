import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Splits a 64x64 player-skin-layout image into the four per-slot armor textures used by
 * SuitArmorModel (same UV layout as the skin, everything outside the slot's region made
 * transparent), and draws placeholder item icons.
 *
 * Usage: java tools/SkinSplitter.java <skin.png> <assets/flightsuit/textures dir> <suit id>
 *
 * Leg boxes are split at the knee: leggings keep the top face + upper 8 side rows, boots keep the
 * bottom face + lower 4 side rows, so the two slots render on the same leg cube without z-fighting.
 */
public class SkinSplitter {
    // (u, v) of every 4x12x4 leg box in the skin layout: right leg, right pants, left leg, left pants.
    private static final int[][] LEG_BOXES = {{0, 16}, {0, 32}, {16, 48}, {0, 48}};

    public static void main(String[] args) throws IOException {
        BufferedImage skin = ImageIO.read(new File(args[0]));
        if (skin.getWidth() != 64 || skin.getHeight() != 64) {
            throw new IllegalArgumentException("expected a 64x64 skin, got " + skin.getWidth() + "x" + skin.getHeight());
        }
        File root = new File(args[1]);
        String id = args[2];
        File armorDir = new File(root, "models/armor");
        File itemDir = new File(root, "item");
        armorDir.mkdirs();
        itemDir.mkdirs();

        BufferedImage helmet = blank(64, 64);
        copy(skin, helmet, 0, 0, 64, 16);

        BufferedImage chest = blank(64, 64);
        copy(skin, chest, 16, 16, 24, 32); // body + jacket
        copy(skin, chest, 40, 16, 16, 32); // right arm + sleeve
        copy(skin, chest, 32, 48, 32, 16); // left arm + sleeve

        BufferedImage legs = blank(64, 64);
        BufferedImage boots = blank(64, 64);
        for (int[] box : LEG_BOXES) {
            int u = box[0], v = box[1];
            copy(skin, legs, u + 4, v, 4, 4);       // top face
            copy(skin, legs, u, v + 4, 16, 8);      // upper side rows
            copy(skin, boots, u + 8, v, 4, 4);      // bottom face
            copy(skin, boots, u, v + 12, 16, 4);    // lower side rows
        }

        write(helmet, new File(armorDir, id + "_helmet.png"));
        write(chest, new File(armorDir, id + "_chestplate.png"));
        write(legs, new File(armorDir, id + "_leggings.png"));
        write(boots, new File(armorDir, id + "_boots.png"));

        writeIcons(skin, itemDir, id);
    }

    private static void writeIcons(BufferedImage skin, File itemDir, String id) throws IOException {
        // Helmet: face front (base + hat overlay), 2x.
        BufferedImage face = blank(8, 8);
        over(skin, face, 8, 8, 8, 8, 0, 0);
        over(skin, face, 40, 8, 8, 8, 0, 0);
        write(scale(face, 2), new File(itemDir, id + "_helmet.png"));

        // Chestplate: right arm | body | left arm front faces (+ overlays), 12 tall.
        BufferedImage chest = blank(16, 16);
        over(skin, chest, 44, 20, 4, 12, 0, 2);
        over(skin, chest, 44, 36, 4, 12, 0, 2);
        over(skin, chest, 20, 20, 8, 12, 4, 2);
        over(skin, chest, 20, 36, 8, 12, 4, 2);
        over(skin, chest, 36, 52, 4, 12, 12, 2);
        over(skin, chest, 52, 52, 4, 12, 12, 2);
        write(chest, new File(itemDir, id + "_chestplate.png"));

        // Leggings: upper 8 rows of both leg fronts, 2x.
        BufferedImage legs = blank(8, 8);
        over(skin, legs, 4, 20, 4, 8, 0, 0);
        over(skin, legs, 4, 36, 4, 8, 0, 0);
        over(skin, legs, 20, 52, 4, 8, 4, 0);
        over(skin, legs, 4, 52, 4, 8, 4, 0);
        write(scale(legs, 2), new File(itemDir, id + "_leggings.png"));

        // Boots: lower 4 rows of both leg fronts, 2x, sitting at the bottom of the icon.
        BufferedImage bootsSmall = blank(8, 4);
        over(skin, bootsSmall, 4, 28, 4, 4, 0, 0);
        over(skin, bootsSmall, 4, 44, 4, 4, 0, 0);
        over(skin, bootsSmall, 20, 60, 4, 4, 4, 0);
        over(skin, bootsSmall, 4, 60, 4, 4, 4, 0);
        BufferedImage boots = blank(16, 16);
        over(scale(bootsSmall, 2), boots, 0, 0, 16, 8, 0, 6);
        write(boots, new File(itemDir, id + "_boots.png"));

        write(drawCapsule(), new File(itemDir, id + "_capsule.png"));
        write(drawArcReactor(), new File(itemDir, "arc_reactor.png"));
        write(drawEnergyCell(), new File(itemDir, "energy_cell.png"));
    }

    // Hoi-Poi-style pill: red left half, gold right half, dark outline, white glint.
    private static BufferedImage drawCapsule() {
        BufferedImage img = blank(16, 16);
        int outline = 0xFF3A1A12, red = 0xFFC8312A, redDark = 0xFF8E1F1A, gold = 0xFFE8B83A, goldDark = 0xFFB0841E;
        for (int y = 4; y <= 11; y++) {
            for (int x = 1; x <= 14; x++) {
                double cx = x < 5 ? 5 : (x > 10 ? 10 : x);
                double dx = x - cx, dy = y - 7.5;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > 4.0) continue;
                int c = d > 3.2 ? outline : (x <= 7 ? (y > 8 ? redDark : red) : (y > 8 ? goldDark : gold));
                img.setRGB(x, y, c);
            }
        }
        img.setRGB(4, 5, 0xFFFFFFFF);
        img.setRGB(5, 5, 0xFFFFE0E0);
        img.setRGB(8, 7, 0xFF66E6FF); // center seam light
        img.setRGB(8, 8, 0xFF66E6FF);
        return img;
    }

    private static BufferedImage drawArcReactor() {
        BufferedImage img = blank(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                int c;
                if (d > 7.2) continue;
                else if (d > 6.0) c = 0xFF5A5A62;
                else if (d > 4.8) c = ((x + y) % 2 == 0) ? 0xFF9AA0A8 : 0xFF7C828A;
                else if (d > 3.6) c = 0xFF39C6F0;
                else if (d > 2.0) c = 0xFF9BEBFF;
                else c = 0xFFFFFFFF;
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    private static BufferedImage drawEnergyCell() {
        BufferedImage img = blank(16, 16);
        int body = 0xFF4A4F57, edge = 0xFF2A2D33, glow = 0xFF5FE3FF, cap = 0xFFB8BEC6;
        for (int y = 3; y <= 14; y++) {
            for (int x = 4; x <= 11; x++) {
                img.setRGB(x, y, (x == 4 || x == 11 || y == 3 || y == 14) ? edge : body);
            }
        }
        for (int x = 6; x <= 9; x++) {
            img.setRGB(x, 1, cap);
            img.setRGB(x, 2, cap);
        }
        int[][] bolt = {{8, 5}, {7, 6}, {7, 7}, {6, 8}, {7, 8}, {8, 8}, {9, 8}, {8, 9}, {8, 10}, {7, 11}, {9, 7}};
        for (int[] p : bolt) img.setRGB(p[0], p[1], glow);
        return img;
    }

    private static BufferedImage blank(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    private static void copy(BufferedImage src, BufferedImage dst, int x, int y, int w, int h) {
        for (int j = y; j < y + h; j++) {
            for (int i = x; i < x + w; i++) {
                dst.setRGB(i, j, src.getRGB(i, j));
            }
        }
    }

    /** Alpha-over a w*h region of src at (sx, sy) onto dst at (dx, dy). Fully transparent pixels are skipped. */
    private static void over(BufferedImage src, BufferedImage dst, int sx, int sy, int w, int h, int dx, int dy) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int argb = src.getRGB(sx + i, sy + j);
                if ((argb >>> 24) == 0) continue;
                dst.setRGB(dx + i, dy + j, argb);
            }
        }
    }

    private static BufferedImage scale(BufferedImage src, int factor) {
        BufferedImage out = blank(src.getWidth() * factor, src.getHeight() * factor);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                out.setRGB(x, y, src.getRGB(x / factor, y / factor));
            }
        }
        return out;
    }

    private static void write(BufferedImage img, File file) throws IOException {
        ImageIO.write(img, "png", file);
        System.out.println("wrote " + file);
    }
}
