import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Space travel art (DESIGN.md 4-16, M15): the Capsule Corp spaceship (model boxes + texture) and the launch pad.
 *
 * Usage: java tools/SpaceGen.java <assets/flightsuit/textures dir>
 *   writes entity/spaceship.png, block/launch_pad_{top,side}.png and prints the model's box lines for
 *   client/SpaceshipModel.java (paste them over the old ones when the layout changes).
 *
 * Model space: 16 px = 1 block, y points down, the feet stand on y = 0, front is -Z.
 */
public class SpaceGen {
    enum Style { HULL, RING, LEG, FOOT, WINDOW, DOME, CAP, ANTENNA, BELLY }

    record Box(String name, int x, int y, int z, int w, int h, int d, Style style) {
    }

    static final int TEX_W = 256, TEX_H = 256;
    static BufferedImage tex;

    public static void main(String[] args) throws IOException {
        File root = new File(args[0]);
        new File(root, "entity").mkdirs();
        new File(root, "block").mkdirs();
        List<Box> boxes = new ArrayList<>();
        boxes.add(new Box("ring", -18, -21, -18, 36, 3, 36, Style.RING));
        boxes.add(new Box("hull_low", -16, -18, -16, 32, 6, 32, Style.HULL));
        boxes.add(new Box("hull_high", -15, -29, -15, 30, 8, 30, Style.HULL));
        boxes.add(new Box("belly", -12, -12, -12, 24, 3, 24, Style.BELLY));
        boxes.add(new Box("dome", -10, -34, -10, 20, 5, 20, Style.DOME));
        boxes.add(new Box("cap", -5, -36, -5, 10, 2, 10, Style.CAP));
        boxes.add(new Box("window", -7, -27, -16, 14, 5, 1, Style.WINDOW));
        boxes.add(new Box("antenna", 0, -41, 0, 1, 5, 1, Style.ANTENNA));
        int[][] corners = {{-17, -17}, {14, -17}, {-17, 14}, {14, 14}};
        for (int i = 0; i < 4; i++) {
            boxes.add(new Box("leg_" + i, corners[i][0] + 1, -9, corners[i][1] + 1, 2, 8, 2, Style.LEG));
            boxes.add(new Box("foot_" + i, corners[i][0], -1, corners[i][1], 4, 1, 4, Style.FOOT));
        }

        tex = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        int u = 0, v = 0, shelf = 0;
        StringBuilder lines = new StringBuilder();
        // Same-sized boxes share one texture spot.
        java.util.Map<String, int[]> placed = new java.util.HashMap<>();
        for (Box b : boxes) {
            String key = b.w + "x" + b.h + "x" + b.d + b.style;
            int[] at = placed.get(key);
            if (at == null) {
                int fw = 2 * (b.w + b.d), fh = b.d + b.h;
                if (u + fw > TEX_W) {
                    u = 0;
                    v += shelf;
                    shelf = 0;
                }
                at = new int[]{u, v};
                placed.put(key, at);
                paint(b, at[0], at[1]);
                u += fw;
                shelf = Math.max(shelf, fh);
            }
            lines.append(String.format("                .texOffs(%d, %d).addBox(%.1fF, %.1fF, %.1fF, %.1fF, %.1fF, %.1fF) // %s%n",
                    at[0], at[1], (float) b.x, (float) b.y, (float) b.z, (float) b.w, (float) b.h, (float) b.d, b.name));
        }
        if (v + shelf > TEX_H) {
            throw new IllegalStateException("texture too small");
        }
        ImageIO.write(tex, "png", new File(root, "entity/spaceship.png"));
        System.out.print(lines);
        ImageIO.write(padTop(), "png", new File(root, "block/launch_pad_top.png"));
        ImageIO.write(padSide(), "png", new File(root, "block/launch_pad_side.png"));
        System.out.println("wrote spaceship + launch pad textures");
    }

    // ---------------------------------------------------------------- ship painting

    static final int WHITE = 0xFFF2F1EC, WHITE_SHADE = 0xFFD8D8D2, PANEL = 0xFFBFC1BC, YELLOW = 0xFFF2C230,
            YELLOW_SHADE = 0xFFC8961E, BLUE = 0xFF2A5AB0, NAVY = 0xFF1A2E5A, GLASS = 0xFF20406A, GLASS_HI = 0xFF6FA8DC,
            GREY = 0xFF8C93A0, DARK = 0xFF3A3C44, RED = 0xFFD03A2A;

    static void paint(Box b, int u, int v) {
        int w = b.w, h = b.h, d = b.d;
        face(u + d, v, w, d, b, "top");
        face(u + d + w, v, w, d, b, "bottom");
        face(u, v + d, d, h, b, "side");
        face(u + d, v + d, w, h, b, "front");
        face(u + d + w, v + d, d, h, b, "side");
        face(u + 2 * d + w, v + d, w, h, b, "back");
    }

    static void face(int x0, int y0, int fw, int fh, Box b, String which) {
        for (int j = 0; j < fh; j++) {
            for (int i = 0; i < fw; i++) {
                tex.setRGB(x0 + i, y0 + j, colour(b, which, i, j, fw, fh));
            }
        }
    }

    static int colour(Box b, String which, int i, int j, int fw, int fh) {
        boolean side = which.equals("side") || which.equals("front") || which.equals("back");
        switch (b.style) {
            case HULL -> {
                if (which.equals("bottom")) return WHITE_SHADE;
                if (which.equals("top")) return (i % 10 == 0 || j % 10 == 0) ? PANEL : WHITE;
                // Capsule Corp mark on the lower hull's four sides: a blue disc with a white C.
                if (b.name.equals("hull_low") && side) {
                    double cx = fw / 2.0 - 0.5, cy = fh / 2.0 - 0.5;
                    double dx = (i - cx) / 3.2, dy = (j - cy) / 2.6;
                    double r = Math.sqrt(dx * dx + dy * dy);
                    if (r <= 1.0) {
                        boolean c = r > 0.45 && r < 0.85 && !(dx > 0.25 && Math.abs(dy) < 0.45);
                        return c ? 0xFFFFFFFF : BLUE;
                    }
                    if (j == fh - 1) return PANEL;
                }
                if (b.name.equals("hull_high") && side && (j == 2 || j == 3) && i % 6 < 3) return NAVY;
                return i % 8 == 0 ? PANEL : (side ? WHITE_SHADE : WHITE);
            }
            case RING -> {
                if (which.equals("top")) return (i + j) % 6 == 0 ? YELLOW_SHADE : YELLOW;
                if (which.equals("bottom")) return YELLOW_SHADE;
                return i % 4 == 0 ? RED : (j == 1 ? YELLOW : YELLOW_SHADE);
            }
            case BELLY -> {
                double dx = i - fw / 2.0, dy = j - fh / 2.0;
                if (which.equals("bottom") && dx * dx + dy * dy < 36) return (int) (dx * dx + dy * dy) < 9 ? 0xFFFF9A3A : DARK;
                return which.equals("bottom") ? GREY : PANEL;
            }
            case DOME -> {
                if (which.equals("top")) return (i - fw / 2) * (i - fw / 2) + (j - fw / 2) * (j - fw / 2) < 20 ? WHITE : WHITE_SHADE;
                return j == 2 ? BLUE : (side ? WHITE_SHADE : WHITE);
            }
            case CAP -> {
                return which.equals("top") ? GREY : DARK;
            }
            case WINDOW -> {
                return (i + j) % 7 == 0 || i == j ? GLASS_HI : GLASS;
            }
            case ANTENNA -> {
                return j == 0 ? RED : GREY;
            }
            case LEG -> {
                return j % 3 == 0 ? DARK : GREY;
            }
            case FOOT -> {
                return DARK;
            }
        }
        return 0xFFFF00FF;
    }

    // ---------------------------------------------------------------- launch pad

    static BufferedImage padTop() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double dx = x - 7.5, dy = y - 7.5;
                double r = Math.sqrt(dx * dx + dy * dy);
                int c = (x + y) % 5 == 0 ? 0xFF4A4D56 : 0xFF3E414A;
                if (r > 5.6 && r < 7.0) c = YELLOW;
                if (r < 1.6) c = 0xFF9FF4FF;
                if (x == 0 || y == 0 || x == 15 || y == 15) c = 0xFF22242A;
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    static BufferedImage padSide() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = (x * 5 + y * 3) % 13 == 0 ? 0xFF4A4D56 : 0xFF3E414A;
                if (y >= 11 && y <= 13) c = ((x + y) / 2) % 2 == 0 ? YELLOW : 0xFF18181C;
                if (y == 0 || y == 15) c = 0xFF22242A;
                if (y == 3 && x >= 3 && x <= 12) c = x % 3 == 0 ? 0xFF9FF4FF : 0xFF2A5A6A;
                img.setRGB(x, y, c);
            }
        }
        return img;
    }
}
