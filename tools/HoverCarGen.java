import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Capsule Corp hover car: box layout, texture and a preview render in one place.
 *
 * Usage: java tools/HoverCarGen.java <hover_car.png> [preview.png]
 *
 * Packs the box UVs, paints the texture and prints the CubeListBuilder lines for HoverCarModel#create, so
 * paste those back whenever the box list changes. Boxes are given as x range (+x = the car's left), height
 * above the bottom of the hover pads, and z range (front is -Z), then converted to model space (y down).
 * The preview also draws a seated player (riding offset 0.25 - 0.35) to check that legs hide under the hood.
 */
public class HoverCarGen {
    static final int TEX_W = 256, TEX_H = 128;

    enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    enum Mat {
        YELLOW(0xFFF5C842), WHITE(0xFFF4F1E8), DARK(0xFF3A3E44), CHROME(0xFFC3CAD2), RED(0xFFD8402E),
        BLUE(0xFF2E6FD8), SEAT(0xFFB8322A), GLOW(0xFF5FE3FF), LIGHT(0xFFFFF6C8), GLASS(0x668FD8F0),
        MANNEQUIN(0xFF8A8F96);

        final int color;

        Mat(int color) {
            this.color = color;
        }
    }

    /** Model-space box; boxes with the same uvKey share one texture region. */
    static final class Box {
        final String name, part, uvKey;
        final double x0, y0, z0;
        final int w, h, d;
        final Mat mat;
        int u, v;

        Box(String name, String part, String uvKey, double x0, double y0, double z0, int w, int h, int d, Mat mat) {
            this.name = name;
            this.part = part;
            this.uvKey = uvKey;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.w = w;
            this.h = h;
            this.d = d;
            this.mat = mat;
        }
    }

    static final List<Box> BOXES = new ArrayList<>();
    static final List<Box> PREVIEW_ONLY = new ArrayList<>();

    static void layout() {
        // Underside: dark belly and four glowing hover pads.
        box("belly", "body", -9, 9, 1, 3, -17, 17, Mat.DARK);
        pair("pad", "body", 4, 9, 0, 1, -15, -10, Mat.GLOW);
        pair("pad_rear", "body", 4, 9, 0, 1, 10, 15, Mat.GLOW);

        // Lower hull, rounded off with a stepped nose and tail, and a chrome running board.
        box("hull", "body", -10, 10, 3, 9, -18, 18, Mat.YELLOW);
        box("nose", "body", -10, 10, 3, 8, -20, -18, Mat.YELLOW);
        box("bumper", "body", -7, 7, 4, 7, -21, -20, Mat.CHROME);
        box("tail", "body", -10, 10, 3, 9, 18, 20, Mat.YELLOW);
        pair("running_board", "body", 10, 11, 3, 4, -10, 9, Mat.CHROME);
        pair("thruster", "body", 3, 7, 4, 8, 20, 22, Mat.CHROME);

        // Round fender pods: big headlights up front, tail lights and fins at the back.
        pair("pod_front", "body", 10, 13, 4, 10, -18, -10, Mat.YELLOW);
        pair("pod_front_top", "body", 10, 12, 10, 11, -17, -11, Mat.YELLOW);
        pair("pod_front_side", "body", 13, 14, 5, 9, -17, -11, Mat.YELLOW);
        pair("pod_front_cap", "body", 10, 12, 5, 9, -19, -18, Mat.YELLOW);
        pair("headlight", "body", 10, 12, 6, 8, -20, -19, Mat.LIGHT);
        pair("pod_rear", "body", 10, 13, 4, 10, 9, 18, Mat.YELLOW);
        pair("pod_rear_top", "body", 10, 12, 10, 11, 10, 17, Mat.YELLOW);
        pair("pod_rear_side", "body", 13, 14, 5, 9, 10, 17, Mat.YELLOW);
        pair("tail_light", "body", 10, 12, 5, 9, 18, 19, Mat.RED);
        pair("fin", "body", 11, 12, 11, 15, 12, 18, Mat.YELLOW);
        pair("fin_tip", "body", 11, 12, 15, 17, 15, 18, Mat.RED);

        // Upper body: hood, doors and rear deck around an open cockpit.
        box("hood", "body", -10, 10, 9, 12, -18, -5, Mat.WHITE);
        box("hood_top", "body", -8, 8, 12, 13, -16, -5, Mat.WHITE);
        pair("door", "body", 8, 10, 9, 12, -5, 7, Mat.WHITE);
        box("deck", "body", -10, 10, 9, 12, 7, 19, Mat.WHITE);
        box("deck_top", "body", -8, 8, 12, 13, 9, 18, Mat.WHITE);

        // Cockpit.
        box("seat", "body", -5, 5, 9, 11, -3, 5, Mat.SEAT);
        box("seat_back", "body", -5, 5, 9, 18, 5, 7, Mat.SEAT);
        box("wheel", "body", -2, 2, 13, 16, -7, -6, Mat.DARK);
        box("windshield_frame", "body", -8, 8, 18, 19, -10, -9, Mat.CHROME);
        box("antenna", "body", -8, -7, 13, 23, 16, 17, Mat.CHROME);
        box("antenna_tip", "body", -9, -6, 23, 26, 15, 18, Mat.RED);

        // Glass is drawn translucent in its own pass.
        box("windshield", "glass", -8, 8, 13, 18, -10, -9, Mat.GLASS);
        pair("side_glass", "glass", 8, 9, 13, 17, -10, -7, Mat.GLASS);

        // Seated player, for the preview only.
        previewBox(-4, 4, 10.4, 22.4, -2, 2);      // torso
        previewBox(-4, 4, 22.4, 30.4, -4, 4);      // head
        previewBox(-4, 0, 8.4, 12.4, -12, 0);      // legs, rotated forward
        previewBox(0, 4, 8.4, 12.4, -12, 0);
        previewBox(-8, -4, 13, 21, -8, -2);        // arms, reaching for the wheel
        previewBox(4, 8, 13, 21, -8, -2);
    }

    static void box(String name, String part, double xMin, double xMax, double hBot, double hTop,
                    double zMin, double zMax, Mat mat) {
        BOXES.add(make(name, part, name, xMin, xMax, hBot, hTop, zMin, zMax, mat));
    }

    /** A left/right pair mirrored around x = 0, sharing one texture region. */
    static void pair(String name, String part, double xIn, double xOut, double hBot, double hTop,
                     double zMin, double zMax, Mat mat) {
        BOXES.add(make(name + "_right", part, name, -xOut, -xIn, hBot, hTop, zMin, zMax, mat));
        BOXES.add(make(name + "_left", part, name, xIn, xOut, hBot, hTop, zMin, zMax, mat));
    }

    static void previewBox(double xMin, double xMax, double hBot, double hTop, double zMin, double zMax) {
        PREVIEW_ONLY.add(make("player", "preview", "player", xMin, xMax, hBot, hTop, zMin, zMax, Mat.MANNEQUIN));
    }

    static Box make(String name, String part, String uvKey, double xMin, double xMax, double hBot, double hTop,
                    double zMin, double zMax, Mat mat) {
        return new Box(name, part, uvKey, xMin, -hTop, zMin,
                (int) Math.round(xMax - xMin), (int) Math.round(hTop - hBot), (int) Math.round(zMax - zMin), mat);
    }

    public static void main(String[] args) throws IOException {
        layout();
        pack();
        BufferedImage tex = new BufferedImage(TEX_W, TEX_H, BufferedImage.TYPE_INT_ARGB);
        Map<String, Box> painted = new LinkedHashMap<>();
        for (Box b : BOXES) painted.putIfAbsent(b.uvKey, b);
        for (Box b : painted.values()) paint(tex, b);
        ImageIO.write(tex, "png", new File(args[0]));
        System.out.println("wrote " + args[0]);
        printModelCode();
        if (args.length > 1) {
            preview(tex, new File(args[1]));
            System.out.println("wrote " + args[1]);
        }
    }

    // ---------------------------------------------------------------- UV packing

    static void pack() {
        Map<String, Box> regions = new LinkedHashMap<>();
        for (Box b : BOXES) regions.putIfAbsent(b.uvKey, b);
        List<Box> order = new ArrayList<>(regions.values());
        order.sort(Comparator.comparingInt((Box b) -> b.d + b.h).reversed());
        int x = 0, y = 0, shelf = 0;
        for (Box b : order) {
            int rw = 2 * (b.d + b.w), rh = b.d + b.h;
            if (x + rw > TEX_W) {
                x = 0;
                y += shelf;
                shelf = 0;
            }
            if (y + rh > TEX_H) throw new IllegalStateException("texture too small for " + b.uvKey);
            b.u = x;
            b.v = y;
            x += rw;
            shelf = Math.max(shelf, rh);
        }
        for (Box b : BOXES) {
            Box owner = regions.get(b.uvKey);
            b.u = owner.u;
            b.v = owner.v;
        }
    }

    static void printModelCode() {
        for (String part : new String[]{"body", "glass"}) {
            System.out.println("// " + part);
            for (Box b : BOXES) {
                if (!b.part.equals(part)) continue;
                System.out.printf("        .texOffs(%d, %d).addBox(%sF, %sF, %sF, %d.0F, %d.0F, %d.0F) // %s%n",
                        b.u, b.v, num(b.x0), num(b.y0), num(b.z0), b.w, b.h, b.d, b.name);
            }
        }
    }

    static String num(double v) {
        return v == Math.rint(v) ? String.format("%.1f", v) : String.valueOf(v);
    }

    // ---------------------------------------------------------------- painting

    /** Texture region {x, y, w, h} of one face, using the vanilla cube UV layout. */
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

    static void paint(BufferedImage tex, Box b) {
        for (Face f : Face.values()) {
            int[] r = region(b, f);
            int base = b.mat.color;
            if (b.uvKey.equals("hull") && f == Face.TOP) base = 0xFF55595F; // cockpit floor
            for (int j = 0; j < r[3]; j++) {
                for (int i = 0; i < r[2]; i++) {
                    tex.setRGB(r[0] + i, r[1] + j, shadeEdges(base, f, i, j, r[2], r[3], b.mat));
                }
            }
            decals(tex, b, f, r);
        }
    }

    /** Cartoon panel shading: lit top edge and dark bottom edge on walls, a darker rim on lids. */
    static int shadeEdges(int c, Face f, int i, int j, int w, int h, Mat m) {
        if (m == Mat.GLASS || m == Mat.GLOW || m == Mat.LIGHT) return c;
        if (f == Face.TOP || f == Face.BOTTOM) {
            return (i == 0 || j == 0 || i == w - 1 || j == h - 1) && w > 2 && h > 2 ? scale(c, 0.9) : c;
        }
        if (h > 2 && j == 0) return scale(c, 1.08);
        if (h > 2 && j == h - 1) return scale(c, 0.82);
        return c;
    }

    static void decals(BufferedImage tex, Box b, Face f, int[] r) {
        int x = r[0], y = r[1], w = r[2], h = r[3];
        switch (b.uvKey) {
            case "hull" -> {
                if (f == Face.RIGHT || f == Face.LEFT) logo(tex, x + w / 2 - 3, y);
            }
            case "bumper" -> {
                if (f == Face.FRONT) for (int i = 1; i < w - 1; i += 2) tex.setRGB(x + i, y + 1, 0xFF4A4F57);
            }
            case "headlight" -> {
                if (f == Face.FRONT) tex.setRGB(x, y, 0xFFFFFFFF);
                else fill(tex, x, y, w, h, Mat.CHROME.color);
            }
            case "pod_front_cap" -> {
                if (f == Face.FRONT) fill(tex, x, y + 1, w, 2, Mat.CHROME.color); // headlight bezel
            }
            case "tail_light" -> {
                if (f == Face.BACK) {
                    fill(tex, x, y, w, h, 0xFFFF5A3C);
                    tex.setRGB(x, y, 0xFFFFB4A0);
                }
            }
            case "thruster" -> {
                if (f == Face.BACK) {
                    fill(tex, x, y, w, h, 0xFF2A8FA8);
                    fill(tex, x + 1, y + 1, 2, 2, 0xFFB8F4FF);
                    for (int[] p : new int[][]{{0, 0}, {3, 0}, {0, 3}, {3, 3}}) tex.setRGB(x + p[0], y + p[1], Mat.CHROME.color);
                }
            }
            case "pad", "pad_rear" -> {
                if (f != Face.BOTTOM && f != Face.TOP) fill(tex, x, y, w, h, 0xFF2A8FA8);
                if (f == Face.BOTTOM) fill(tex, x + 1, y + 1, w - 2, h - 2, 0xFFB8F4FF);
            }
            case "hood_top", "deck_top" -> {
                if (f == Face.TOP) fill(tex, x + w / 2 - 2, y, 4, h, Mat.BLUE.color);
                if (f == Face.FRONT || f == Face.BACK) fill(tex, x + w / 2 - 2, y, 4, h, Mat.BLUE.color);
            }
            case "seat_back" -> {
                if (f == Face.FRONT) {
                    for (int i = 2; i < w - 1; i += 3) fill(tex, x + i, y + 3, 1, h - 3, scale(Mat.SEAT.color, 0.8));
                    fill(tex, x, y, w, 2, scale(Mat.SEAT.color, 1.15));
                }
            }
            case "seat" -> {
                if (f == Face.TOP) for (int i = 2; i < w - 1; i += 3) fill(tex, x + i, y, 1, h, scale(Mat.SEAT.color, 0.8));
            }
            case "belly" -> {
                if (f == Face.RIGHT || f == Face.LEFT) for (int i = 4; i < w - 4; i += 4) tex.setRGB(x + i, y + 1, 0xFF1E2024);
            }
            case "windshield", "side_glass" -> {
                if (f == Face.FRONT || f == Face.RIGHT || f == Face.LEFT) {
                    for (int k = 0; k < Math.min(w, h); k++) {
                        int i = w - 3 - k, j = k;
                        if (i >= 0) tex.setRGB(x + i, y + j, 0xAAEFFBFF);
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Capsule Corp badge: a blue disc with a white C, 6x6. */
    static void logo(BufferedImage tex, int x, int y) {
        for (int j = 0; j < 6; j++) {
            for (int i = 0; i < 6; i++) {
                if (Math.hypot(i - 2.5, j - 2.5) <= 3.1) tex.setRGB(x + i, y + j, Mat.BLUE.color);
            }
        }
        for (int[] p : new int[][]{{2, 1}, {3, 1}, {4, 1}, {1, 2}, {1, 3}, {2, 4}, {3, 4}, {4, 4}}) {
            tex.setRGB(x + p[0], y + p[1], 0xFFFFFFFF);
        }
    }

    static void fill(BufferedImage img, int x, int y, int w, int h, int c) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) img.setRGB(i, j, c);
    }

    static int scale(int c, double s) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * s));
        int g = Math.min(255, (int) (((c >> 8) & 255) * s));
        int bl = Math.min(255, (int) ((c & 255) * s));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------- preview

    record Quad(double[][] pts, int argb, double depth) {
    }

    static void preview(BufferedImage tex, File out) throws IOException {
        int W = 1600, H = 1250;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x9EC9F0));
        g.fillRect(0, 0, W, H);
        render(g, tex, 400, 400, 11, 35, 22, true);
        render(g, tex, 1180, 400, 11, 215, 22, true);
        render(g, tex, 400, 800, 9, 90, 8, false);
        render(g, tex, 1180, 780, 7, 0, 89, false);
        int f = 2, ox = 20, oy = H - TEX_H * f - 20;
        for (int y = 0; y < TEX_H; y++) {
            for (int x = 0; x < TEX_W; x++) {
                int c = tex.getRGB(x, y);
                if ((c >>> 24) == 0) c = ((x / 4 + y / 4) % 2 == 0) ? 0xFFDDDDDD : 0xFFBBBBBB;
                g.setColor(new Color(c, true));
                g.fillRect(ox + x * f, oy + y * f, f, f);
            }
        }
        ImageIO.write(img, "png", out);
    }

    /** Orthographic view; yaw 0 looks at the car's front. World = (x, -y, -z) as in the renderer. */
    static void render(Graphics2D g, BufferedImage tex, int cx, int cy, double s, double yawDeg, double pitchDeg,
                       boolean withPlayer) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double[] cam = {Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)};
        double[] right = {Math.cos(yaw), 0, -Math.sin(yaw)};
        double[] up = cross(right, cam);
        if (up[1] < 0) up = new double[]{-up[0], -up[1], -up[2]};
        List<Quad> quads = new ArrayList<>();
        for (Box b : BOXES) addQuads(quads, tex, b, cam);
        if (withPlayer) for (Box b : PREVIEW_ONLY) addQuads(quads, tex, b, cam);
        quads.sort(Comparator.comparingDouble(Quad::depth));
        for (Quad q : quads) {
            int[] xs = new int[4], ys = new int[4];
            for (int i = 0; i < 4; i++) {
                xs[i] = (int) Math.round(cx + s * dot(q.pts[i], right));
                ys[i] = (int) Math.round(cy - s * dot(q.pts[i], up));
            }
            g.setColor(new Color(q.argb, true));
            g.fillPolygon(xs, ys, 4);
            if ((q.argb >>> 24) == 255) g.drawPolygon(xs, ys, 4);
        }
    }

    static void addQuads(List<Quad> out, BufferedImage tex, Box b, double[] cam) {
        double x0 = b.x0, y0 = b.y0, z0 = b.z0, x1 = x0 + b.w, y1 = y0 + b.h, z1 = z0 + b.d;
        for (Face f : Face.values()) {
            double[] n;
            double shade;
            switch (f) {
                case TOP -> { n = new double[]{0, 1, 0}; shade = 1.0; }
                case BOTTOM -> { n = new double[]{0, -1, 0}; shade = 0.5; }
                case RIGHT -> { n = new double[]{-1, 0, 0}; shade = 0.7; }
                case LEFT -> { n = new double[]{1, 0, 0}; shade = 0.7; }
                case FRONT -> { n = new double[]{0, 0, 1}; shade = 0.85; }
                default -> { n = new double[]{0, 0, -1}; shade = 0.85; }
            }
            if (dot(n, cam) <= 0) continue;
            int[] r = region(b, f);
            for (int j = 0; j < r[3]; j++) {
                for (int i = 0; i < r[2]; i++) {
                    int argb = b.mat == Mat.MANNEQUIN ? Mat.MANNEQUIN.color : tex.getRGB(r[0] + i, r[1] + j);
                    if ((argb >>> 24) == 0) continue;
                    double[][] m = switch (f) { // texel corner p0, column step, row step (model space)
                        case TOP -> new double[][]{{x0 + i, y0, z1 - j}, {1, 0, 0}, {0, 0, -1}};
                        case BOTTOM -> new double[][]{{x0 + i, y1, z0 + j}, {1, 0, 0}, {0, 0, 1}};
                        case RIGHT -> new double[][]{{x0, y0 + j, z1 - i}, {0, 0, -1}, {0, 1, 0}};
                        case FRONT -> new double[][]{{x0 + i, y0 + j, z0}, {1, 0, 0}, {0, 1, 0}};
                        case LEFT -> new double[][]{{x1, y0 + j, z0 + i}, {0, 0, 1}, {0, 1, 0}};
                        case BACK -> new double[][]{{x1 - i, y0 + j, z1}, {-1, 0, 0}, {0, 1, 0}};
                    };
                    double[] p0 = m[0], p1 = add(p0, m[1]), p2 = add(p1, m[2]), p3 = add(p0, m[2]);
                    double[][] pts = {world(p0), world(p1), world(p2), world(p3)};
                    double[] ctr = new double[3];
                    for (double[] p : pts) for (int a = 0; a < 3; a++) ctr[a] += p[a] / 4;
                    int shaded = scale(argb, shade);
                    out.add(new Quad(pts, shaded, dot(ctr, cam)));
                }
            }
        }
    }

    static double[] world(double[] p) {
        return new double[]{p[0], -p[1], -p[2]};
    }

    static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
