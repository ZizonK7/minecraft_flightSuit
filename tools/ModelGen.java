import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Code-built models: the box layout, the texture and a preview render of every model that isn't a plain skin.
 *
 * Usage: java tools/ModelGen.java <repo root> [hulk|hulkbuster|trunks|shenron|oozaru|monkey|weapons|parts_saiyan|parts_namekian|parts_frieza|parts_cell_buu|parts_marvel|all]
 *
 * The parts_* models (M18, DESIGN.md 5차 피드백 "인물별 입체 부품") are the Dragon Ball cast's 3D parts drawn over the
 * skinned body by client/DbzPartsLayer: two roots, "head" and "body", at the player model's pivots, each with named
 * child groups (the names are the contract with client/parts/DbzParts). See Parts.partsModel.
 *
 * Entity and suit models (hulk, hulkbuster, trunks, shenron): boxes are laid out here in model space (pixels, y
 * down, front is -Z, the usual ModelPart conventions), their UVs are packed automatically, every face is
 * painted by position (so muscles, plating seams and scales line up across boxes) and the CubeListBuilder code
 * is written straight into the model class between its "// <ModelGen:name>" markers - edit the layout here,
 * never the generated block. Previews go to docs/reference/<name>_preview.png.
 *
 * Weapons: 3D item models made of JSON elements (upright, +Y toward the tip), painted the same way. The
 * inventory icon stays the flat sprite (forge:separate_transforms); every hand/ground/frame transform is
 * worked out from vanilla's so the grip lands where a vanilla sword's grip (or a bow's) would.
 */
public class ModelGen {
    static File root;

    public static void main(String[] args) throws IOException {
        root = new File(args[0]);
        String what = args.length > 1 ? args[1] : "all";
        boolean all = what.equals("all");
        if (all || what.equals("hulk")) {
            Model m = Hulk.build();
            emit(m, "client/HulkModel.java", "textures/entity/hero/hulk_model.png");
        }
        if (all || what.equals("hulkbuster")) {
            Model m = Hulkbuster.build();
            emit(m, "client/HulkbusterModel.java", "textures/models/armor/hulkbuster_mk44.png");
            suitIcons(m, "hulkbuster_mk44");
        }
        if (all || what.equals("trunks")) {
            Model m = Trunks.build();
            emit(m, "client/TrunksSuitModel.java", "textures/models/armor/trunks_mk5.png");
            // M17: the Super Saiyan variant - the same texture with the hair (helmet boxes only) turned gold.
            write(Trunks.golden(m), asset("textures/models/armor/trunks_mk5_ssj.png"));
        }
        if (all || what.equals("oozaru")) {
            Model m = Oozaru.build();
            emit(m, "client/OozaruModel.java", "textures/entity/dbz/oozaru_vegeta.png");
        }
        if (all || what.equals("shenron")) {
            Model m = Shenron.build();
            emit(m, "client/ShenronModel.java", "textures/entity/dbz/shenron.png");
        }
        if (all || what.equals("monkey")) {
            Model m = Monkey.build();
            emit(m, "client/MonkeyModel.java", "textures/entity/monkey.png");
        }
        if (all || what.equals("weapons")) {
            Weapons.buildAll();
        }
        if (all || what.equals("parts_saiyan")) {
            emit(SaiyanParts.build(), "client/parts/SaiyanParts.java", "textures/entity/dbz/parts/saiyan.png");
        }
        if (all || what.equals("parts_namekian")) {
            emit(NamekianParts.build(), "client/parts/NamekianParts.java", "textures/entity/dbz/parts/namekian.png");
        }
        if (all || what.equals("parts_frieza")) {
            emit(FriezaParts.build(), "client/parts/FriezaParts.java", "textures/entity/dbz/parts/frieza.png");
        }
        if (all || what.equals("parts_cell_buu")) {
            emit(CellBuuParts.build(), "client/parts/CellBuuParts.java", "textures/entity/dbz/parts/cell_buu.png");
        }
        if (all || what.equals("parts_marvel")) {
            emit(MarvelParts.build(), "client/parts/MarvelParts.java", "textures/entity/dbz/parts/marvel.png");
        }
    }

    static File java(String path) {
        return new File(root, "src/main/java/com/pfkfks/flightsuit/" + path);
    }

    static File asset(String path) {
        return new File(root, "src/main/resources/assets/flightsuit/" + path);
    }

    static File reference(String name) {
        return new File(root, "docs/reference/" + name);
    }

    static void emit(Model m, String javaPath, String texPath) throws IOException {
        m.pack();
        m.paint();
        write(m.tex, asset(texPath));
        inject(java(javaPath), m.name, m.javaCode());
        write(Preview.sheet(m), reference(m.name + "_preview.png"));
        System.out.println(m.name + ": " + m.boxCount() + " boxes, texture " + m.texW + "x" + m.texH);
    }

    static void write(BufferedImage img, File file) throws IOException {
        file.getParentFile().mkdirs();
        ImageIO.write(img, "png", file);
    }

    /** Replaces everything between "// <ModelGen:name>" and "// </ModelGen:name>" in a Java file. */
    static void inject(File file, String name, String code) throws IOException {
        String src = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String open = "// <ModelGen:" + name + ">";
        String close = "// </ModelGen:" + name + ">";
        int a = src.indexOf(open);
        int b = src.indexOf(close);
        if (a < 0 || b < a) {
            throw new IllegalStateException("markers for " + name + " not found in " + file);
        }
        int lineStart = src.lastIndexOf('\n', a) + 1;
        String indent = src.substring(lineStart, a);
        StringBuilder out = new StringBuilder();
        out.append(src, 0, a + open.length()).append("\n");
        out.append(indent).append("// Generated by tools/ModelGen.java - edit the layout there, not here.\n");
        for (String line : code.split("\n")) {
            out.append(line.isEmpty() ? "" : indent + line).append("\n");
        }
        out.append(indent).append(src.substring(b));
        Files.writeString(file.toPath(), out.toString(), StandardCharsets.UTF_8);
    }

    // ================================================================ core

    enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    /**
     * One texel being painted: its face, where on the face as seen from outside (fx right, fy down, 0..1), its
     * texel index and the point it covers in its part's space (x, y, z in pixels, y down, front -Z).
     */
    static final class Px {
        Box box;
        Face face;
        double fx, fy, x, y, z;
        int i, j, fw, fh;

        /** Texels to the nearest face edge (0 = on the edge). */
        int edge() {
            return Math.min(Math.min(i, fw - 1 - i), Math.min(j, fh - 1 - j));
        }

        /** A steady per-texel random number in [0, 1). */
        double noise() {
            return hash(box.seed, face.ordinal() * 4096 + i, j);
        }

        double noise(int salt) {
            return hash(box.seed + salt * 7919, face.ordinal() * 4096 + i, j);
        }

        boolean side() {
            return face == Face.RIGHT || face == Face.LEFT || face == Face.FRONT || face == Face.BACK;
        }
    }

    interface Paint {
        int at(Px p);
    }

    static final class Box {
        final String name;
        final float x, y, z, inflate;
        final int w, h, d;
        final Paint paint;
        int u = -1, v = -1;
        boolean fixed;
        int seed;

        Box(String name, float x, float y, float z, int w, int h, int d, float inflate, Paint paint) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
            this.h = h;
            this.d = d;
            this.inflate = inflate;
            this.paint = paint;
            this.seed = name.hashCode();
        }

        int uvW() {
            return 2 * d + 2 * w;
        }

        int uvH() {
            return d + h;
        }
    }

    static final class Part {
        final String name;
        final Part parent;
        final float px, py, pz, rx, ry, rz;
        final List<Box> boxes = new ArrayList<>();
        final List<Part> children = new ArrayList<>();
        /** Applied at render time by the Java model (e.g. Hulk's wide shoulders); previews apply it too. */
        float sx, sy, sz;

        Part(String name, Part parent, float px, float py, float pz, float rx, float ry, float rz) {
            this.name = name;
            this.parent = parent;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }

        Part child(String name, float px, float py, float pz) {
            return child(name, px, py, pz, 0, 0, 0);
        }

        Part child(String name, float px, float py, float pz, float rx, float ry, float rz) {
            Part c = new Part(name, this, px, py, pz, rx, ry, rz);
            children.add(c);
            return c;
        }

        Part box(String name, float x, float y, float z, int w, int h, int d, Paint paint) {
            return box(name, x, y, z, w, h, d, 0.0F, paint);
        }

        Part box(String name, float x, float y, float z, int w, int h, int d, float inflate, Paint paint) {
            boxes.add(new Box(name, x, y, z, w, h, d, inflate, paint));
            return this;
        }

        /** The same box mirrored across x = 0 (a left limb from a right one). */
        Part mirrored(String name, float x, float y, float z, int w, int h, int d, Paint paint) {
            return box(name, -x - w, y, z, w, h, d, 0.0F, paint);
        }

        /** A box with fixed UVs into a skin laid out at the texture's top-left (not painted). */
        Part skin(String name, int u, int v, float x, float y, float z, int w, int h, int d, float inflate) {
            Box b = new Box(name, x, y, z, w, h, d, inflate, null);
            b.u = u;
            b.v = v;
            b.fixed = true;
            boxes.add(b);
            return this;
        }

        Part shift(float x, float y, float z) {
            sx = x;
            sy = y;
            sz = z;
            return this;
        }

        String var() {
            return parent == null ? "root" : name;
        }
    }

    static final class Model {
        final String name;
        final int texW, texH;
        final Part root = new Part("root", null, 0, 0, 0, 0, 0, 0);
        /** Area the packer may use (the rest holds a skin, see Part.skin). */
        int packX, packY;
        BufferedImage tex;
        BufferedImage skin;
        /** Preview views: parts to leave out (e.g. Shenron's loose pieces are drawn one at a time). */
        final List<String> previewGroups = new ArrayList<>();
        float previewScale = 8.0F;
        /** Extra Java lines placed before createLayer() in the generated block. */
        final List<String> extraJava = new ArrayList<>();

        Model(String name, int texW, int texH) {
            this.name = name;
            this.texW = texW;
            this.texH = texH;
        }

        Part part(String name, float px, float py, float pz) {
            return root.child(name, px, py, pz);
        }

        List<Part> parts() {
            List<Part> out = new ArrayList<>();
            collect(root, out);
            return out;
        }

        private void collect(Part p, List<Part> out) {
            out.add(p);
            for (Part c : p.children) {
                collect(c, out);
            }
        }

        int boxCount() {
            int n = 0;
            for (Part p : parts()) {
                n += p.boxes.size();
            }
            return n;
        }

        /** Shelf-packs every painted box's UV footprint, tallest first. */
        void pack() {
            List<Box> boxes = new ArrayList<>();
            for (Part p : parts()) {
                for (Box b : p.boxes) {
                    if (!b.fixed) {
                        boxes.add(b);
                    }
                }
            }
            boxes.sort((a, b) -> b.uvH() != a.uvH() ? b.uvH() - a.uvH() : b.uvW() - a.uvW());
            int x = packX, y = packY, rowH = 0;
            for (Box b : boxes) {
                if (x + b.uvW() > texW) {
                    x = packX;
                    y += rowH;
                    rowH = 0;
                }
                if (y + b.uvH() > texH || x + b.uvW() > texW) {
                    throw new IllegalStateException(name + ": texture too small at " + b.name);
                }
                b.u = x;
                b.v = y;
                x += b.uvW();
                rowH = Math.max(rowH, b.uvH());
            }
        }

        void paint() {
            tex = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
            if (skin != null) {
                for (int yy = 0; yy < skin.getHeight(); yy++) {
                    for (int xx = 0; xx < skin.getWidth(); xx++) {
                        tex.setRGB(xx, yy, skin.getRGB(xx, yy));
                    }
                }
            }
            for (Part p : parts()) {
                for (Box b : p.boxes) {
                    if (!b.fixed) {
                        paintBox(b);
                    }
                }
            }
        }

        void paintBox(Box b) {
            float x0 = b.x - b.inflate, y0 = b.y - b.inflate, z0 = b.z - b.inflate;
            float x1 = b.x + b.w + b.inflate, y1 = b.y + b.h + b.inflate, z1 = b.z + b.d + b.inflate;
            for (Face f : Face.values()) {
                int fw, fh, ou, ov;
                switch (f) {
                    case TOP -> { fw = b.w; fh = b.d; ou = b.u + b.d; ov = b.v; }
                    case BOTTOM -> { fw = b.w; fh = b.d; ou = b.u + b.d + b.w; ov = b.v; }
                    case RIGHT -> { fw = b.d; fh = b.h; ou = b.u; ov = b.v + b.d; }
                    case FRONT -> { fw = b.w; fh = b.h; ou = b.u + b.d; ov = b.v + b.d; }
                    case LEFT -> { fw = b.d; fh = b.h; ou = b.u + b.d + b.w; ov = b.v + b.d; }
                    default -> { fw = b.w; fh = b.h; ou = b.u + 2 * b.d + b.w; ov = b.v + b.d; }
                }
                for (int j = 0; j < fh; j++) {
                    for (int i = 0; i < fw; i++) {
                        Px p = new Px();
                        p.box = b;
                        p.face = f;
                        p.i = i;
                        p.j = j;
                        p.fw = fw;
                        p.fh = fh;
                        p.fx = (i + 0.5) / fw;
                        p.fy = (j + 0.5) / fh;
                        switch (f) {
                            case TOP -> { p.x = x0 + p.fx * (x1 - x0); p.z = z1 - p.fy * (z1 - z0); p.y = y0; }
                            case BOTTOM -> { p.x = x0 + p.fx * (x1 - x0); p.z = z1 - p.fy * (z1 - z0); p.y = y1; }
                            case RIGHT -> { p.z = z1 - p.fx * (z1 - z0); p.y = y0 + p.fy * (y1 - y0); p.x = x0; }
                            case FRONT -> { p.x = x0 + p.fx * (x1 - x0); p.y = y0 + p.fy * (y1 - y0); p.z = z0; }
                            case LEFT -> { p.z = z0 + p.fx * (z1 - z0); p.y = y0 + p.fy * (y1 - y0); p.x = x1; }
                            default -> { p.x = x1 - p.fx * (x1 - x0); p.y = y0 + p.fy * (y1 - y0); p.z = z1; }
                        }
                        tex.setRGB(ou + i, ov + j, b.paint.at(p));
                    }
                }
            }
        }

        String javaCode() {
            StringBuilder sb = new StringBuilder();
            for (String line : extraJava) {
                sb.append(line).append("\n");
            }
            List<Part> shifted = new ArrayList<>();
            for (Part p : root.children) {
                if (p.sx != 0 || p.sy != 0 || p.sz != 0) {
                    shifted.add(p);
                }
            }
            if (!shifted.isEmpty() || name.equals("hulk") || name.equals("hulkbuster") || name.equals("trunks_mk5")) {
                // Order: head, body, right arm, left arm, right leg, left leg (ShiftedHumanoidModel).
                sb.append("/** Render-time offsets of head, body, right arm, left arm, right leg, left leg (pixels). */\n");
                sb.append("public static final float[][] SHIFTS = {");
                String[] order = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
                for (int k = 0; k < order.length; k++) {
                    Part p = find(order[k]);
                    sb.append(k == 0 ? "" : ", ").append("{").append(f(p == null ? 0 : p.sx)).append(", ")
                            .append(f(p == null ? 0 : p.sy)).append(", ").append(f(p == null ? 0 : p.sz)).append("}");
                }
                sb.append("};\n\n");
            }
            sb.append("public static LayerDefinition createLayer() {\n");
            sb.append("    MeshDefinition mesh = new MeshDefinition();\n");
            sb.append("    PartDefinition root = mesh.getRoot();\n");
            for (Part p : parts()) {
                if (p == root) {
                    continue;
                }
                boolean used = !p.children.isEmpty();
                sb.append("    ").append(used ? "PartDefinition " + p.var() + " = " : "").append(p.parent.var())
                        .append(".addOrReplaceChild(\"").append(p.name).append("\", CubeListBuilder.create()");
                if (p.boxes.isEmpty()) {
                    sb.append(",");
                }
                for (int k = 0; k < p.boxes.size(); k++) {
                    Box b = p.boxes.get(k);
                    sb.append("\n            .texOffs(").append(b.u).append(", ").append(b.v).append(").addBox(")
                            .append(f(b.x)).append(", ").append(f(b.y)).append(", ").append(f(b.z)).append(", ")
                            .append(f(b.w)).append(", ").append(f(b.h)).append(", ").append(f(b.d));
                    if (b.inflate != 0) {
                        sb.append(", new CubeDeformation(").append(f(b.inflate)).append(")");
                    }
                    sb.append(")").append(k == p.boxes.size() - 1 ? "," : "").append(" // ").append(b.name);
                }
                sb.append("\n            ");
                if (p.rx == 0 && p.ry == 0 && p.rz == 0) {
                    sb.append("PartPose.offset(").append(f(p.px)).append(", ").append(f(p.py)).append(", ").append(f(p.pz)).append("));\n");
                } else {
                    sb.append("PartPose.offsetAndRotation(").append(f(p.px)).append(", ").append(f(p.py)).append(", ").append(f(p.pz))
                            .append(", ").append(f(p.rx)).append(", ").append(f(p.ry)).append(", ").append(f(p.rz)).append("));\n");
                }
            }
            sb.append("    return LayerDefinition.create(mesh, ").append(texW).append(", ").append(texH).append(");\n");
            sb.append("}\n");
            return sb.toString();
        }

        Part find(String name) {
            for (Part p : parts()) {
                if (p.name.equals(name)) {
                    return p;
                }
            }
            return null;
        }
    }

    static String f(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v).replaceAll("0+$", "");
        if (s.endsWith(".")) {
            s += "0";
        }
        if (s.equals("-0.0")) {
            s = "0.0";
        }
        return s + "F";
    }

    // ================================================================ colour helpers

    static double hash(int a, int b, int c) {
        long h = a * 73856093L ^ b * 19349663L ^ c * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    static int shade(int c, double f) {
        int a = c >>> 24;
        int r = clamp((int) Math.round(((c >> 16) & 255) * f));
        int g = clamp((int) Math.round(((c >> 8) & 255) * f));
        int b = clamp((int) Math.round((c & 255) * f));
        return a << 24 | r << 16 | g << 8 | b;
    }

    static int mix(int c1, int c2, double t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) Math.round((c1 >>> 24) * (1 - t) + (c2 >>> 24) * t);
        int r = (int) Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Slight per-texel variation so flat colours read as a material. */
    static int grain(Px p, int c, double amount) {
        return shade(c, 1.0 + (p.noise() - 0.5) * 2 * amount);
    }

    static Paint solid(int c) {
        return p -> grain(p, c, 0.04);
    }

    static boolean near(double v, double target, double half) {
        return Math.abs(v - target) < half;
    }

    // ================================================================ preview

    static final class Preview {
        static final double LX = -0.4, LY = -0.75, LZ = -0.55;

        /** Front, three-quarter, side and back views next to each other (or the preview groups, one per row). */
        static BufferedImage sheet(Model m) {
            List<BufferedImage> rows = new ArrayList<>();
            List<List<String>> groups = new ArrayList<>();
            if (m.previewGroups.isEmpty()) {
                groups.add(null);
            } else {
                for (String g : m.previewGroups) {
                    groups.add(List.of(g.split(",")));
                }
            }
            for (List<String> group : groups) {
                List<BufferedImage> views = new ArrayList<>();
                for (double[] view : new double[][]{{0, 10}, {35, 20}, {90, 5}, {180, 10}, {215, -25}}) {
                    views.add(render(m, group, view[0], view[1], m.previewScale, 0, 0));
                }
                rows.add(join(views, true));
            }
            return join(rows, false);
        }

        static BufferedImage join(List<BufferedImage> parts, boolean across) {
            int w = 0, h = 0;
            for (BufferedImage b : parts) {
                w = across ? w + b.getWidth() : Math.max(w, b.getWidth());
                h = across ? Math.max(h, b.getHeight()) : h + b.getHeight();
            }
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            int at = 0;
            for (BufferedImage b : parts) {
                for (int y = 0; y < b.getHeight(); y++) {
                    for (int x = 0; x < b.getWidth(); x++) {
                        out.setRGB(across ? at + x : x, across ? y : at + y, b.getRGB(x, y));
                    }
                }
                at += across ? b.getWidth() : b.getHeight();
            }
            return out;
        }

        /** Every visible box polygon, with MC's own vertex/UV assignment (ModelPart.Cube). */
        static List<double[][]> polygons(Model m, List<String> only) {
            List<double[][]> out = new ArrayList<>();
            walk(m, m.root, identity(), only, only == null, out);
            return out;
        }

        static void walk(Model m, Part p, double[] parent, List<String> only, boolean on, List<double[][]> out) {
            boolean include = on || only != null && only.contains(p.name);
            double[] t = mul(parent, translate(p.px + p.sx, p.py + p.sy, p.pz + p.sz));
            t = mul(t, rotZYX(p.rz, p.ry, p.rx));
            if (include) {
                for (Box b : p.boxes) {
                    cube(m, b, t, out);
                }
            }
            for (Part c : p.children) {
                walk(m, c, t, only, include, out);
            }
        }

        static void cube(Model m, Box b, double[] t, List<double[][]> out) {
            double x0 = b.x - b.inflate, y0 = b.y - b.inflate, z0 = b.z - b.inflate;
            double x1 = b.x + b.w + b.inflate, y1 = b.y + b.h + b.inflate, z1 = b.z + b.d + b.inflate;
            double[] a000 = {x0, y0, z0}, a100 = {x1, y0, z0}, a110 = {x1, y1, z0}, a010 = {x0, y1, z0};
            double[] a001 = {x0, y0, z1}, a101 = {x1, y0, z1}, a111 = {x1, y1, z1}, a011 = {x0, y1, z1};
            int u = b.u, v = b.v, w = b.w, h = b.h, d = b.d;
            double f4 = u, f5 = u + d, f6 = u + d + w, f7 = u + d + w + w, f8 = u + d + w + d, f9 = u + d + w + d + w;
            double f10 = v, f11 = v + d, f12 = v + d + h;
            poly(out, t, new double[][]{a101, a001, a000, a100}, f5, f10, f6, f11);
            poly(out, t, new double[][]{a110, a010, a011, a111}, f6, f11, f7, f10);
            poly(out, t, new double[][]{a000, a001, a011, a010}, f4, f11, f5, f12);
            poly(out, t, new double[][]{a100, a000, a010, a110}, f5, f11, f6, f12);
            poly(out, t, new double[][]{a101, a100, a110, a111}, f6, f11, f8, f12);
            poly(out, t, new double[][]{a001, a101, a111, a011}, f8, f11, f9, f12);
        }

        static void poly(List<double[][]> out, double[] t, double[][] v, double u1, double v1, double u2, double v2) {
            double[][] uv = {{u2, v1}, {u1, v1}, {u1, v2}, {u2, v2}};
            double[][] q = new double[4][];
            for (int k = 0; k < 4; k++) {
                double[] p = apply(t, v[k]);
                q[k] = new double[]{p[0], p[1], p[2], uv[k][0], uv[k][1]};
            }
            out.add(q);
        }

        static BufferedImage render(Model m, List<String> only, double yawDeg, double pitchDeg, double scale, double cx, double cy) {
            List<double[][]> polys = polygons(m, only);
            double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
            double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
            List<double[][]> view = new ArrayList<>();
            for (double[][] q : polys) {
                double[][] r = new double[4][];
                for (int k = 0; k < 4; k++) {
                    double[] p = viewPoint(q[k], yaw, pitch);
                    r[k] = new double[]{p[0], p[1], p[2], q[k][3], q[k][4]};
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                }
                view.add(r);
            }
            int pad = 6;
            int w = (int) Math.ceil((maxX - minX) * scale) + pad * 2;
            int h = (int) Math.ceil((maxY - minY) * scale) + pad * 2;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    img.setRGB(x, y, ((x / 8 + y / 8) & 1) == 0 ? 0xFF3A3D44 : 0xFF33363C);
                }
            }
            double[] depth = new double[w * h];
            java.util.Arrays.fill(depth, 1e9);
            for (int k = 0; k < view.size(); k++) {
                double[][] q = view.get(k);
                double light = lightOf(polys.get(k), yaw, pitch);
                for (int[] tri : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                    raster(m.tex, img, depth, q[tri[0]], q[tri[1]], q[tri[2]], minX, minY, scale, pad, light);
                }
            }
            return img;
        }

        static double lightOf(double[][] q, double yaw, double pitch) {
            double[] a = q[0], b = q[1], c = q[2];
            double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
            double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-9) {
                return 1;
            }
            double dot = Math.abs((nx * LX + ny * LY + nz * LZ) / len / Math.sqrt(LX * LX + LY * LY + LZ * LZ));
            return 0.55 + 0.45 * dot;
        }

        static double[] viewPoint(double[] p, double yaw, double pitch) {
            double x = p[0] * Math.cos(yaw) + p[2] * Math.sin(yaw);
            double z = -p[0] * Math.sin(yaw) + p[2] * Math.cos(yaw);
            double y = p[1];
            double y2 = y * Math.cos(pitch) - z * Math.sin(pitch);
            double z2 = y * Math.sin(pitch) + z * Math.cos(pitch);
            return new double[]{x, y2, z2};
        }

        static void raster(BufferedImage tex, BufferedImage img, double[] depth, double[] a, double[] b, double[] c,
                           double minX, double minY, double scale, int pad, double light) {
            double ax = (a[0] - minX) * scale + pad, ay = (a[1] - minY) * scale + pad;
            double bx = (b[0] - minX) * scale + pad, by = (b[1] - minY) * scale + pad;
            double cx = (c[0] - minX) * scale + pad, cy = (c[1] - minY) * scale + pad;
            double area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            if (Math.abs(area) < 1e-9) {
                return;
            }
            int x0 = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
            int x1 = (int) Math.min(img.getWidth() - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
            int y0 = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
            int y1 = (int) Math.min(img.getHeight() - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    double px = x + 0.5, py = y + 0.5;
                    double w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area;
                    double w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area;
                    double w2 = 1 - w0 - w1;
                    if (w0 < -1e-6 || w1 < -1e-6 || w2 < -1e-6) {
                        continue;
                    }
                    double z = w0 * a[2] + w1 * b[2] + w2 * c[2];
                    int idx = y * img.getWidth() + x;
                    if (z >= depth[idx]) {
                        continue;
                    }
                    double u = w0 * a[3] + w1 * b[3] + w2 * c[3];
                    double v = w0 * a[4] + w1 * b[4] + w2 * c[4];
                    int tu = Math.max(0, Math.min(tex.getWidth() - 1, (int) Math.floor(u)));
                    int tv = Math.max(0, Math.min(tex.getHeight() - 1, (int) Math.floor(v)));
                    int col = tex.getRGB(tu, tv);
                    if ((col >>> 24) < 128) {
                        continue;
                    }
                    depth[idx] = z;
                    img.setRGB(x, y, shade(col | 0xFF000000, light));
                }
            }
        }

        static double[] identity() {
            return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        }

        static double[] translate(double x, double y, double z) {
            return new double[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
        }

        /** Quaternionf.rotationZYX(z, y, x): Rz * Ry * Rx, as ModelPart.translateAndRotate uses. */
        static double[] rotZYX(double z, double y, double x) {
            return mul(mul(rot(2, z), rot(1, y)), rot(0, x));
        }

        static double[] rot(int axis, double a) {
            double c = Math.cos(a), s = Math.sin(a);
            return switch (axis) {
                case 0 -> new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
                case 1 -> new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
                default -> new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
            };
        }

        static double[] mul(double[] a, double[] b) {
            double[] r = new double[16];
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    double s = 0;
                    for (int k = 0; k < 4; k++) {
                        s += a[i * 4 + k] * b[k * 4 + j];
                    }
                    r[i * 4 + j] = s;
                }
            }
            return r;
        }

        static double[] apply(double[] t, double[] p) {
            return new double[]{
                    t[0] * p[0] + t[1] * p[1] + t[2] * p[2] + t[3],
                    t[4] * p[0] + t[5] * p[1] + t[6] * p[2] + t[7],
                    t[8] * p[0] + t[9] * p[1] + t[10] * p[2] + t[11]};
        }

        /** A 16x16 inventory icon of some parts, three-quarter front view, fitted and outlined. */
        static BufferedImage icon(Model m, List<String> parts, double yaw, double pitch) {
            return fit16(render(m, parts, yaw, pitch, 8, 0, 0));
        }

        /** Crops a render to its content, fits it into a 16x16 icon (1 pixel margin) and outlines it. */
        static BufferedImage fit16(BufferedImage big) {
            // Crop to content (anything that isn't the checker background).
            int minX = big.getWidth(), minY = big.getHeight(), maxX = -1, maxY = -1;
            for (int y = 0; y < big.getHeight(); y++) {
                for (int x = 0; x < big.getWidth(); x++) {
                    int c = big.getRGB(x, y);
                    if (c != 0xFF3A3D44 && c != 0xFF33363C) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            int size = Math.max(maxX - minX + 1, maxY - minY + 1);
            BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            double step = size / 14.0;
            int ox = minX - (int) ((size - (maxX - minX + 1)) / 2.0), oy = minY - (int) ((size - (maxY - minY + 1)) / 2.0);
            for (int y = 0; y < 14; y++) {
                for (int x = 0; x < 14; x++) {
                    // Majority sample over the cell, so thin bright bits survive.
                    int sx = ox + (int) ((x + 0.5) * step), sy = oy + (int) ((y + 0.5) * step);
                    if (sx < 0 || sy < 0 || sx >= big.getWidth() || sy >= big.getHeight()) {
                        continue;
                    }
                    int c = big.getRGB(sx, sy);
                    if (c != 0xFF3A3D44 && c != 0xFF33363C) {
                        out.setRGB(x + 1, y + 1, c);
                    }
                }
            }
            // Dark outline around the shape.
            BufferedImage outlined = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int c = out.getRGB(x, y);
                    if ((c >>> 24) != 0) {
                        outlined.setRGB(x, y, c);
                        continue;
                    }
                    boolean edge = false;
                    for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = x + n[0], ny = y + n[1];
                        if (nx >= 0 && ny >= 0 && nx < 16 && ny < 16 && (out.getRGB(nx, ny) >>> 24) != 0) {
                            edge = true;
                        }
                    }
                    if (edge) {
                        outlined.setRGB(x, y, 0xFF1A1A1E);
                    }
                }
            }
            return outlined;
        }
    }

    /** Helmet / chestplate / leggings / boots icons rendered from the suit model itself. */
    static void suitIcons(Model m, String id) throws IOException {
        String[][] pieces = {
                {"helmet", "head_piece"},
                {"chestplate", "chest_piece"},
                {"leggings", "legs_piece"},
                {"boots", "feet_piece"}};
        for (String[] piece : pieces) {
            List<String> names = new ArrayList<>();
            for (Part p : m.parts()) {
                if (p.name.startsWith(piece[1])) {
                    names.add(p.name);
                }
            }
            write(Preview.icon(m, names, 30, 15), asset("textures/item/" + id + "_" + piece[0] + ".png"));
        }
    }

    // ================================================================ humanoid suits

    /** The four armour pieces each limb is split into (SuitModel shows one per slot). */
    static final String[] PIECES = {"head_piece", "chest_piece", "legs_piece", "feet_piece"};

    /** Standard humanoid limbs, each with its four (possibly empty) piece children. */
    static Map<String, Part> humanoid(Model m) {
        Map<String, Part> limbs = new LinkedHashMap<>();
        limbs.put("head", m.part("head", 0, 0, 0));
        m.part("hat", 0, 0, 0);
        limbs.put("body", m.part("body", 0, 0, 0));
        limbs.put("right_arm", m.part("right_arm", -5, 2, 0));
        limbs.put("left_arm", m.part("left_arm", 5, 2, 0));
        limbs.put("right_leg", m.part("right_leg", -1.9F, 12, 0));
        limbs.put("left_leg", m.part("left_leg", 1.9F, 12, 0));
        return limbs;
    }

    /** Piece children named <piece>_<limb> so every name in the mesh stays unique. */
    static Part piece(Map<String, Part> limbs, String limb, int slot) {
        Part l = limbs.get(limb);
        String name = PIECES[slot] + "_" + limb;
        for (Part c : l.children) {
            if (c.name.equals(name)) {
                return c;
            }
        }
        return l.child(name, 0, 0, 0);
    }

    static void allPieces(Map<String, Part> limbs) {
        for (String limb : limbs.keySet()) {
            for (int s = 0; s < 4; s++) {
                piece(limbs, limb, s);
            }
        }
    }

    // ================================================================ Hulk

    /**
     * Vegeta as a Great Ape (M17): a huge ape in shreds of his Saiyan armour - a heavy brow over red eyes, a broad
     * muzzle with fangs, a mane, brown fur all over, a white chest plate with yellow shoulder pads still strapped on,
     * long arms with tan hands, short bowed legs, and the tail curling out behind (its own part: it sways).
     */
    static final class Oozaru {
        static final int FUR = 0xFF6B4423, FUR_DARK = 0xFF4A2E17, FUR_LIGHT = 0xFF8A5A30, SKIN = 0xFFD9A47A, SKIN_DARK = 0xFFB07A52;
        static final int EYE = 0xFFE81E1E, EYE_DARK = 0xFF8A0E0E, MOUTH = 0xFF3A1210, FANG = 0xFFF0EBDC;
        static final int ARMOUR = 0xFFF2F1EC, ARMOUR_SHADE = 0xFFC8C6BE, PAD = 0xFFE8B830, PAD_DARK = 0xFFB08420, SUIT = 0xFF2A3E8A;

        static Model build() {
            Model m = new Model("oozaru", 128, 128);
            m.extraJava.add("/** How much bigger than a player the Great Ape is drawn (its hitbox is DbzCharacter.OOZARU_VEGETA's scale). */");
            m.extraJava.add("public static final float SCALE = 4.0F;\n");
            Part head = m.part("head", 0, 0, 0).shift(0, 1.0F, -1.5F);
            m.part("hat", 0, 0, 0);
            Part body = m.part("body", 0, 0, 0);
            Part rArm = m.part("right_arm", -5, 2, 0).shift(-2.5F, 0, 0);
            Part lArm = m.part("left_arm", 5, 2, 0).shift(2.5F, 0, 0);
            Part rLeg = m.part("right_leg", -1.9F, 12, 0).shift(-1.3F, 0, 0);
            Part lLeg = m.part("left_leg", 1.9F, 12, 0).shift(1.3F, 0, 0);

            head.box("skull", -4.5F, -8, -4.5F, 9, 8, 9, Oozaru::head)
                    .box("brow", -4.5F, -7, -5.5F, 9, 2, 1, Oozaru::brow)
                    .box("muzzle", -3, -3.5F, -7.5F, 6, 4, 3, Oozaru::muzzle)
                    .box("mane", -5, -9.5F, -3, 10, 4, 8, Oozaru::fur)
                    .box("ear_right", -5.5F, -6, -1, 1, 3, 2, Oozaru::skin)
                    .mirrored("ear_left", -5.5F, -6, -1, 1, 3, 2, Oozaru::skin);

            body.box("chest", -7, -2, -4, 14, 9, 8, Oozaru::chest)
                    .box("belly", -6, 7, -3.5F, 12, 5, 7, Oozaru::belly);
            Part tail = body.child("tail", 0, 10, 3.5F, 0.5F, 0, 0);
            tail.box("tail_base", -1.5F, -1.5F, 0, 3, 3, 6, Oozaru::fur)
                    .box("tail_tip", -1, -6.5F, 5, 2, 6, 2, Oozaru::fur);

            arm(rArm, false);
            arm(lArm, true);
            leg(rLeg, false);
            leg(lLeg, true);
            return m;
        }

        static void arm(Part arm, boolean left) {
            Hulk.box(arm, left, "shoulder", -4.5F, -3, -3.5F, 7, 6, 7, Oozaru::fur);
            Hulk.box(arm, left, "pad", -5, -4, -4, 8, 3, 8, Oozaru::pad);
            Hulk.box(arm, left, "upper_arm", -4, 3, -3, 6, 6, 6, Oozaru::fur);
            Hulk.box(arm, left, "forearm", -4, 9, -3, 6, 6, 6, Oozaru::fur);
            Hulk.box(arm, left, "hand", -4, 15, -3, 6, 4, 6, Oozaru::hand);
        }

        static void leg(Part leg, boolean left) {
            Hulk.box(leg, left, "thigh", -3, 0, -3, 6, 7, 6, Oozaru::fur);
            Hulk.box(leg, left, "shin", -2.5F, 7, -2.5F, 5, 4, 5, Oozaru::fur);
            Hulk.box(leg, left, "foot", -3, 11, -4.5F, 6, 1, 8, Oozaru::hand);
        }

        static int fur(Px p) {
            // Tufts: darker streaks running down, lit from above.
            int c = (int) Math.floor(p.x * 1.3 + p.z * 0.7 + 30) % 3 == 0 ? FUR_DARK : FUR;
            if (p.noise() < 0.12) {
                c = FUR_LIGHT;
            }
            if (p.face == Face.BOTTOM) {
                c = shade(c, 0.75);
            } else if (p.side()) {
                c = shade(c, 1.06 - p.fy * 0.14);
            }
            return grain(p, c, 0.05);
        }

        static int skin(Px p) {
            return grain(p, p.face == Face.BOTTOM ? SKIN_DARK : SKIN, 0.05);
        }

        static int head(Px p) {
            if (p.face == Face.FRONT) {
                // The bare face under the fur: red eyes glaring under the brow.
                if (p.y > -4.9 && p.y < -3.6 && Math.abs(p.x) > 1.0 && Math.abs(p.x) < 3.5) {
                    return Math.abs(p.x) < 2.2 ? EYE_DARK : EYE;
                }
                if (p.y > -6 && Math.abs(p.x) < 3.8) {
                    return skin(p);
                }
            }
            return fur(p);
        }

        static int brow(Px p) {
            if (p.face == Face.FRONT || p.face == Face.BOTTOM) {
                return grain(p, Math.abs(p.x) < 1.5 ? FUR_DARK : FUR, 0.05);
            }
            return fur(p);
        }

        static int muzzle(Px p) {
            if (p.face == Face.FRONT) {
                // Nostrils up top, the open mouth and fangs below.
                if (p.y < -2.5 && Math.abs(Math.abs(p.x) - 1.0) < 0.5) {
                    return MOUTH;
                }
                if (p.y > -1.5) {
                    if ((p.y < -1.0 || p.y > -0.2) && (Math.abs(p.x) > 1.5 && Math.abs(p.x) < 2.5)) {
                        return FANG;
                    }
                    return MOUTH;
                }
            }
            return skin(p);
        }

        static int chest(Px p) {
            // The white chest plate of his armour across the front, his suit's blue at the edges.
            if (p.face == Face.FRONT) {
                if (p.y < 4.5) {
                    int c = Math.abs(p.x) < 0.5 || Math.abs(p.y - 1.5) < 0.4 ? ARMOUR_SHADE : ARMOUR;
                    return grain(p, c, 0.03);
                }
                return fur(p);
            }
            if (p.face == Face.BACK && p.y < 2) {
                return grain(p, ARMOUR_SHADE, 0.03);
            }
            if (p.face == Face.TOP) {
                return grain(p, SUIT, 0.04);
            }
            return fur(p);
        }

        static int belly(Px p) {
            return p.face == Face.FRONT ? grain(p, FUR_LIGHT, 0.06) : fur(p);
        }

        static int pad(Px p) {
            int c = p.face == Face.TOP ? PAD : p.face == Face.BOTTOM ? PAD_DARK : (p.fy > 0.6 ? PAD_DARK : PAD);
            return grain(p, c, 0.03);
        }

        static int hand(Px p) {
            if (p.face == Face.BOTTOM || p.face == Face.FRONT) {
                return skin(p);
            }
            return fur(p);
        }
    }

    static final class Hulk {
        static final int SKIN = 0xFF5E9E3C, SKIN_DARK = 0xFF3D6C27, SKIN_LIGHT = 0xFF7DBD55, SKIN_DEEP = 0xFF2E5520;
        static final int SHORTS = 0xFF5A2D82, SHORTS_DARK = 0xFF3B1C58, HAIR = 0xFF1B1E19, HAIR_LIGHT = 0xFF2C312A;
        static final int EYE_WHITE = 0xFFE4EED2, IRIS = 0xFF16340E, MOUTH = 0xFF2A140F, TEETH = 0xFFE8E4D0, NAIL = 0xFF9CC07A;

        static Model build() {
            Model m = new Model("hulk", 128, 128);
            m.extraJava.add("/** How much bigger than a player the Hulk is drawn (his hitbox is HeroType.HULK's scale). */");
            m.extraJava.add("public static final float SCALE = 1.5F;\n");
            Map<String, Part> limbs = new LinkedHashMap<>();
            Part head = m.part("head", 0, 0, 0).shift(0, 0.5F, -2.0F);
            m.part("hat", 0, 0, 0);
            Part body = m.part("body", 0, 0, 0);
            Part rArm = m.part("right_arm", -5, 2, 0).shift(-3.0F, 0, 0);
            Part lArm = m.part("left_arm", 5, 2, 0).shift(3.0F, 0, 0);
            Part rLeg = m.part("right_leg", -1.9F, 12, 0).shift(-1.4F, 0, 0);
            Part lLeg = m.part("left_leg", 1.9F, 12, 0).shift(1.4F, 0, 0);

            // Head: small for the body, pushed forward and down between the shoulders.
            head.box("skull", -4, -8, -4, 8, 8, 8, Hulk::head)
                    .box("brow", -4.5F, -6, -5, 9, 2, 1, Hulk::brow)
                    .box("jaw", -3.5F, -2.5F, -5, 7, 3, 2, Hulk::jaw)
                    .box("hair", -4.5F, -8.5F, -3.5F, 9, 3, 8, p -> hair(p))
                    .box("ear_right", -4.5F, -4.5F, -1, 1, 2, 2, Hulk::skin)
                    .mirrored("ear_left", -4.5F, -4.5F, -1, 1, 2, 2, Hulk::skin);

            // Torso: a barrel chest with huge traps up around the neck, abs, torn purple shorts.
            body.box("traps", -6, -6, -3, 12, 2, 7, Hulk::skin)
                    .box("chest", -8, -4, -5, 16, 10, 9, Hulk::chest)
                    .box("abs", -6, 6, -4, 12, 5, 7, Hulk::abs)
                    .box("waist", -6.5F, 10, -4.5F, 13, 3, 8, Hulk::shorts);

            // Arms: shoulders like boulders, forearms bigger than the upper arm, fists down past the hips.
            arm(rArm, false);
            arm(lArm, true);
            leg(rLeg, false);
            leg(lLeg, true);
            return m;
        }

        static void arm(Part arm, boolean left) {
            box(arm, left, "shoulder", -5, -6, -4, 8, 7, 8, Hulk::shoulder);
            box(arm, left, "upper_arm", -4.5F, 1, -3.5F, 7, 6, 7, Hulk::bicep);
            box(arm, left, "forearm", -5, 7, -4, 8, 6, 8, Hulk::forearm);
            box(arm, left, "fist", -4.5F, 13, -3.5F, 7, 4, 7, Hulk::fist);
        }

        static void leg(Part leg, boolean left) {
            box(leg, left, "thigh", -3, 0, -3, 6, 7, 6, Hulk::thigh);
            box(leg, left, "shin", -2.5F, 7, -2.5F, 5, 4, 5, Hulk::skin);
            box(leg, left, "foot", -3, 11, -4, 6, 1, 7, Hulk::foot);
        }

        /** Right-side boxes as given; left-side ones mirrored across the limb's pivot. */
        static void box(Part part, boolean left, String name, float x, float y, float z, int w, int h, int d, Paint paint) {
            if (left) {
                part.mirrored(name + "_left", x, y, z, w, h, d, paint);
            } else {
                part.box(name + "_right", x, y, z, w, h, d, paint);
            }
        }

        static int skin(Px p) {
            int c = grain(p, SKIN, 0.05);
            if (p.face == Face.BOTTOM) {
                c = shade(c, 0.8);
            } else if (p.side()) {
                c = shade(c, 1.06 - p.fy * 0.14); // lit from above
            }
            return c;
        }

        static int hair(Px p) {
            // Strands: darker streaks running back over the head.
            int c = (int) (p.x * 2) % 3 == 0 ? HAIR : HAIR_LIGHT;
            return grain(p, c, 0.08);
        }

        static int head(Px p) {
            double x = p.x, y = p.y;
            // Hair line: a ragged fringe across the top of the face and down the sides.
            double line = -6.2 + hash((int) Math.floor(x * 2), 3, 7) * 0.8;
            if (p.face == Face.TOP || p.face == Face.BACK || y < line || p.side() && p.face != Face.FRONT && y < -3.5) {
                return hair(p);
            }
            if (p.face == Face.FRONT) {
                // Eyes under the heavy brow: whites with a green iris looking in, a dark socket above.
                if (y > -4 && y < -3) {
                    if (x > -3 && x < -1 || x > 1 && x < 3) {
                        return Math.abs(x) < 2 ? IRIS : EYE_WHITE;
                    }
                }
                if (y > -4 && y < -3 && Math.abs(x) <= 1) {
                    return SKIN_DARK;
                }
                if (y > -3 && y < -2 && Math.abs(x) < 1.2) {
                    // Broad nose with dark nostrils.
                    return Math.abs(x) > 0.3 ? SKIN_DEEP : SKIN_LIGHT;
                }
                if (y > -2.5) {
                    return grain(p, SKIN_DARK, 0.05);
                }
            }
            return skin(p);
        }

        static int brow(Px p) {
            if (p.face == Face.FRONT || p.face == Face.BOTTOM) {
                // A scowl: the brow knits down toward the middle.
                double knit = Math.abs(p.x);
                boolean lower = p.y > -5.0 && knit < 2.5 || p.face == Face.BOTTOM;
                return grain(p, lower ? SKIN_DEEP : SKIN_DARK, 0.05);
            }
            if (p.face == Face.TOP) {
                return hair(p);
            }
            return skin(p);
        }

        static int jaw(Px p) {
            if (p.face == Face.FRONT) {
                // Gritted teeth in a snarl.
                if (p.y > -1.6 && p.y < 0.0 && Math.abs(p.x) < 2.6) {
                    if (p.y < -0.8) {
                        return (int) Math.floor(p.x + 10) % 2 == 0 ? TEETH : shade(TEETH, 0.85);
                    }
                    return MOUTH;
                }
            }
            return grain(p, SKIN, 0.05);
        }

        static int chest(Px p) {
            double x = p.x, y = p.y;
            int c = skin(p);
            if (p.face == Face.FRONT) {
                double ax = Math.abs(x);
                double pecLine = 2.2 + 0.04 * x * x;
                if (ax < 0.5 && y < 4) {
                    return shade(c, 0.62);
                }
                if (Math.abs(y - pecLine) < 0.5) {
                    return shade(c, 0.62);
                }
                if (y < pecLine && y > -3 && ax > 1.5 && ax < 6.5) {
                    return shade(c, y < -1.5 ? 1.12 : 1.0);
                }
                if (y > pecLine + 0.5) {
                    // Upper abs start below the pecs.
                    return ax < 0.5 ? shade(c, 0.75) : c;
                }
            }
            if (p.face == Face.BACK) {
                if (Math.abs(x) < 0.5) {
                    return shade(c, 0.62);
                }
                if (Math.abs(Math.abs(x) - 4.5) < 0.5 && y > 0) {
                    return shade(c, 0.85);
                }
            }
            if (p.face == Face.TOP && Math.abs(x) < 6) {
                return shade(c, 1.08);
            }
            return c;
        }

        static int abs(Px p) {
            int c = skin(p);
            if (p.face == Face.FRONT) {
                if (Math.abs(p.x) < 0.5 || Math.abs(p.y - 8.0) < 0.4 || Math.abs(p.y - 10.0) < 0.4) {
                    return shade(c, 0.62);
                }
                if (Math.abs(p.x) > 4.5) {
                    return shade(c, 0.88);
                }
            }
            if (p.face == Face.BACK && Math.abs(p.x) < 0.5) {
                return shade(c, 0.62);
            }
            return c;
        }

        static int shorts(Px p) {
            int c = grain(p, SHORTS, 0.06);
            if (p.side() && p.y < 10.8) {
                return shade(c, 0.8); // waistband
            }
            if (p.face == Face.FRONT && Math.abs(p.x) < 0.5) {
                return SHORTS_DARK;
            }
            return c;
        }

        static int shoulder(Px p) {
            int c = skin(p);
            if (p.face == Face.TOP) {
                return shade(c, 1.1);
            }
            // The deltoid's lower edge.
            if (p.side() && p.y > -0.2) {
                return shade(c, 0.86);
            }
            return c;
        }

        static int bicep(Px p) {
            int c = skin(p);
            if (p.face == Face.FRONT) {
                return shade(c, 1.08 - Math.abs(p.fx - 0.5) * 0.3);
            }
            if (p.face == Face.BACK) {
                return shade(c, 0.9);
            }
            return c;
        }

        static int forearm(Px p) {
            int c = skin(p);
            // Veins: thin dark lines winding down the outer forearm.
            if (p.side()) {
                double vein = Math.sin(p.y * 1.3 + p.box.seed % 5) * 1.2;
                double across = p.face == Face.RIGHT || p.face == Face.LEFT ? (p.z) : p.x;
                if (Math.abs(across - vein) < 0.35) {
                    return shade(c, 0.8);
                }
            }
            return c;
        }

        static int fist(Px p) {
            int c = skin(p);
            if (p.face == Face.FRONT) {
                if (p.y < 14) {
                    return shade(c, 1.12); // knuckles
                }
                double fx = p.fx * 4;
                if (fx - Math.floor(fx) < 0.18) {
                    return shade(c, 0.62); // fingers
                }
            }
            if (p.face == Face.BOTTOM) {
                return shade(c, 0.8);
            }
            return c;
        }

        static int thigh(Px p) {
            // Torn purple shorts over the thigh, ragged at the hem.
            double hem = 3.6 + hash((int) Math.floor((p.face == Face.RIGHT || p.face == Face.LEFT ? p.z : p.x) * 2), p.face.ordinal(), 11) * 1.6;
            if (p.face == Face.TOP || p.y < hem) {
                int c = grain(p, SHORTS, 0.06);
                return p.y > hem - 0.6 ? SHORTS_DARK : c;
            }
            int c = skin(p);
            if (p.face == Face.FRONT && p.y > 5.5) {
                return shade(c, 1.1); // knee
            }
            return c;
        }

        static int foot(Px p) {
            int c = skin(p);
            if (p.face == Face.FRONT) {
                double fx = p.fx * 5;
                if (fx - Math.floor(fx) < 0.2) {
                    return shade(c, 0.7);
                }
                return NAIL;
            }
            if (p.face == Face.TOP && p.z < -2.5) {
                double fx = p.fx * 5;
                return fx - Math.floor(fx) < 0.2 ? shade(c, 0.75) : c;
            }
            return c;
        }
    }

    // ================================================================ Hulkbuster

    static final class Hulkbuster {
        static final int RED = 0xFFA3191F, RED_DARK = 0xFF6B0F13, RED_LIGHT = 0xFFC8342C;
        static final int GOLD = 0xFFD2A22C, GOLD_DARK = 0xFF94701A, STEEL = 0xFF50555E, STEEL_DARK = 0xFF2B2E34;
        static final int GLOW = 0xFFBFF4FF, GLOW_CORE = 0xFFFFFFFF, GLOW_EDGE = 0xFF59C8F0;

        static Model build() {
            Model m = new Model("hulkbuster", 128, 128);
            m.extraJava.add("/** How much bigger than its wearer a full Hulkbuster is (SuitType.HULKBUSTER_MK44.size() must match). */");
            m.extraJava.add("public static final float SIZE = 1.5F;\n");
            Map<String, Part> limbs = humanoid(m);
            limbs.get("right_arm").shift(-3.0F, 0, 0);
            limbs.get("left_arm").shift(3.0F, 0, 0);
            limbs.get("right_leg").shift(-1.1F, 0, 0);
            limbs.get("left_leg").shift(1.1F, 0, 0);
            allPieces(limbs);

            // Helmet: a squat red dome sunk into the collar, gold faceplate with the eye slits.
            piece(limbs, "head", 0)
                    .box("helmet", -4.5F, -8.5F, -4.5F, 9, 9, 9, Hulkbuster::helmet)
                    .box("faceplate", -3.5F, -7, -5.5F, 7, 5, 1, Hulkbuster::faceplate)
                    .box("jaw_guard", -3.5F, -1.5F, -5.5F, 7, 2, 2, Hulkbuster::jawGuard)
                    .box("crest", -1.5F, -9.5F, -4, 3, 1, 7, Hulkbuster::trim);

            // Torso: barrel chest with the arc reactor, high collar, segmented abdomen, thruster pack.
            piece(limbs, "body", 1)
                    .box("collar", -6.5F, -6.5F, -3.5F, 13, 3, 8, Hulkbuster::collar)
                    .box("chest", -8, -4, -5.5F, 16, 10, 10, Hulkbuster::chest)
                    .box("reactor", -2, 0, -6.5F, 4, 4, 1, Hulkbuster::reactor)
                    .box("abdomen", -6, 6, -4.5F, 12, 5, 8, Hulkbuster::abdomen)
                    .box("pack", -6, -3, 4.5F, 12, 9, 3, Hulkbuster::pack)
                    .box("thruster_right", -5, 6, 5, 3, 2, 2, Hulkbuster::thruster)
                    .mirrored("thruster_left", -5, 6, 5, 3, 2, 2, Hulkbuster::thruster);
            piece(limbs, "body", 2)
                    .box("pelvis", -6.5F, 10.5F, -4, 13, 3, 8, Hulkbuster::pelvis);

            for (String side : new String[]{"right", "left"}) {
                boolean left = side.equals("left");
                Part arm = piece(limbs, side + "_arm", 1);
                hb(arm, left, "pauldron", -6, -5, -5, 10, 6, 10, Hulkbuster::pauldron);
                hb(arm, left, "upper_arm", -4, 1, -3.5F, 8, 4, 7, Hulkbuster::joint);
                hb(arm, left, "gauntlet", -5, 5, -4.5F, 9, 5, 9, Hulkbuster::gauntlet);
                hb(arm, left, "fist", -4.5F, 10, -4, 8, 5, 8, Hulkbuster::fist);
                Part thigh = piece(limbs, side + "_leg", 2);
                hb(thigh, left, "thigh", -3.5F, 0, -3.5F, 7, 6, 7, Hulkbuster::thigh);
                hb(thigh, left, "knee", -2.5F, 4, -4.5F, 5, 3, 1, Hulkbuster::trim);
                Part boot = piece(limbs, side + "_leg", 3);
                hb(boot, left, "shin", -4, 6, -4, 8, 4, 8, Hulkbuster::shin);
                hb(boot, left, "foot", -4, 10, -5.5F, 8, 2, 9, Hulkbuster::foot);
            }
            return m;
        }

        static void hb(Part part, boolean left, String name, float x, float y, float z, int w, int h, int d, Paint paint) {
            if (left) {
                part.mirrored(name + "_left", x, y, z, w, h, d, paint);
            } else {
                part.box(name + "_right", x, y, z, w, h, d, paint);
            }
        }

        /** Armour plate: seams on the edges, a few panel lines, rivets near the corners. */
        static int plate(Px p, int base) {
            int c = grain(p, base, 0.035);
            int e = p.edge();
            if (e == 0) {
                return shade(c, 0.7);
            }
            if (e == 1 && (p.i == 1 || p.i == p.fw - 2) && (p.j == 1 || p.j == p.fh - 2) && p.fw > 4 && p.fh > 4) {
                return shade(c, 1.35);
            }
            if (p.fh >= 8 && p.side() && (p.j % 4 == 3) && p.noise(3) < 0.75) {
                return shade(c, 0.82);
            }
            // Scuffs.
            if (p.noise(9) < 0.03) {
                return mix(c, STEEL, 0.6);
            }
            return c;
        }

        static int helmet(Px p) {
            if (p.face == Face.TOP && Math.abs(p.x) < 1.5) {
                return plate(p, GOLD);
            }
            return plate(p, RED);
        }

        static int faceplate(Px p) {
            if (p.face == Face.FRONT) {
                // Narrow, angry eye slits.
                if (p.y > -6.0 && p.y < -5.0 && (p.x > -3.0 && p.x < -0.6 || p.x > 0.6 && p.x < 3.0)) {
                    return Math.abs(p.x) < 1.5 ? GLOW_EDGE : GLOW;
                }
                if (p.y > -4.0 && p.y < -3.0 && Math.abs(p.x) < 0.6) {
                    return GOLD_DARK;
                }
            }
            return plate(p, GOLD);
        }

        static int jawGuard(Px p) {
            if (p.face == Face.FRONT && p.y > -1.0 && Math.abs(p.x) < 2.6 && p.i % 2 == 1) {
                return STEEL_DARK; // grille
            }
            return plate(p, GOLD);
        }

        static int trim(Px p) {
            return plate(p, GOLD);
        }

        static int collar(Px p) {
            return plate(p, p.face == Face.TOP ? STEEL : RED_DARK);
        }

        static int chest(Px p) {
            if (p.face == Face.FRONT) {
                double ax = Math.abs(p.x), y = p.y;
                // Gold frame around the reactor and down the sternum.
                if (ax < 3.5 && y > -1.0 && y < 5.0 && !(ax < 2 && y > 0 && y < 4)) {
                    return plate(p, GOLD);
                }
                if (ax > 3.0 && ax < 7.0 && Math.abs(y - (-1.5 + ax * 0.25)) < 0.5) {
                    return shade(plate(p, RED), 0.7); // pec plate seams
                }
            }
            if (p.face == Face.TOP) {
                return plate(p, Math.abs(p.x) < 4.5 ? STEEL : RED);
            }
            return plate(p, RED);
        }

        static int reactor(Px p) {
            if (p.face == Face.FRONT) {
                double r = Math.hypot(p.x, p.y - 2.0);
                if (r < 0.8) {
                    return GLOW_CORE;
                }
                if (r < 1.5) {
                    return GLOW;
                }
                if (r < 1.9) {
                    return GLOW_EDGE;
                }
                return STEEL_DARK;
            }
            return plate(p, STEEL);
        }

        static int abdomen(Px p) {
            if (p.side()) {
                int band = (int) Math.floor(p.y - 6.0);
                return plate(p, band % 2 == 0 ? STEEL : (p.face == Face.FRONT && Math.abs(p.x) < 3 ? GOLD : STEEL_DARK));
            }
            return plate(p, STEEL_DARK);
        }

        static int pack(Px p) {
            if (p.face == Face.BACK) {
                // Vents.
                if (Math.abs(p.x) > 1 && Math.abs(p.x) < 5 && p.y > -1 && p.y < 4 && p.j % 2 == 0) {
                    return STEEL_DARK;
                }
                if (Math.abs(p.x) < 1) {
                    return plate(p, RED);
                }
            }
            return plate(p, STEEL);
        }

        static int thruster(Px p) {
            if (p.face == Face.BOTTOM) {
                return p.edge() == 0 ? GLOW_EDGE : GLOW;
            }
            return plate(p, STEEL_DARK);
        }

        static int pelvis(Px p) {
            if (p.face == Face.FRONT && Math.abs(p.x) < 2.5) {
                return plate(p, RED);
            }
            return plate(p, STEEL);
        }

        static int pauldron(Px p) {
            // A gold stripe over the top, running down front and back.
            double across = p.z;
            boolean stripe = Math.abs(across) < 1.2 && (p.face == Face.TOP || p.face == Face.RIGHT || p.face == Face.LEFT);
            if (stripe) {
                return plate(p, GOLD);
            }
            if (p.face == Face.BOTTOM) {
                return plate(p, RED_DARK);
            }
            return plate(p, RED);
        }

        static int joint(Px p) {
            int c = grain(p, STEEL_DARK, 0.05);
            return p.j % 2 == 0 ? shade(c, 1.25) : c;
        }

        static int gauntlet(Px p) {
            boolean outer = p.face == Face.RIGHT && p.box.name.endsWith("right") || p.face == Face.LEFT && p.box.name.endsWith("left");
            if (outer && p.edge() > 0 && p.edge() < 3) {
                return plate(p, GOLD);
            }
            return plate(p, p.face == Face.BOTTOM ? STEEL : RED);
        }

        static int fist(Px p) {
            if (p.face == Face.FRONT) {
                if (p.y < 11.5) {
                    return plate(p, STEEL); // knuckle plate
                }
                if (p.i % 2 == 1) {
                    return STEEL_DARK; // fingers
                }
                return plate(p, RED);
            }
            if (p.face == Face.BOTTOM) {
                return plate(p, STEEL_DARK);
            }
            return plate(p, RED);
        }

        static int thigh(Px p) {
            if (p.face == Face.TOP) {
                return plate(p, STEEL_DARK);
            }
            return plate(p, RED);
        }

        static int shin(Px p) {
            if (p.face == Face.FRONT && Math.abs(p.x) < 1.2) {
                return plate(p, GOLD);
            }
            return plate(p, RED);
        }

        static int foot(Px p) {
            if (p.face == Face.BOTTOM) {
                return grain(p, 0xFF1E1F22, 0.05);
            }
            if (p.face == Face.TOP && p.z > -1) {
                return plate(p, RED);
            }
            return plate(p, STEEL);
        }
    }

    // ================================================================ Mark 5: Trunks

    static final class Trunks {
        static final int HAIR = 0xFFB7A3D9, HAIR_LIGHT = 0xFFD5C8EE, HAIR_DARK = 0xFF8C78B3;
        static final int JACKET = 0xFF3B40A4, JACKET_DARK = 0xFF282C78, JACKET_LIGHT = 0xFF5359C4;
        static final int SHEATH = 0xFF26272E, SHEATH_LIGHT = 0xFF3D3F4A, BRASS = 0xFFB48A3C, BRASS_DARK = 0xFF7C5A22;
        static final int WRAP = 0xFF8E3B22, WRAP_DARK = 0xFF5A2213, BOOT = 0xFFE3A62A, BOOT_DARK = 0xFFB07A14, SOLE = 0xFFE9E9E4;

        /** The helmet piece: its boxes are where the hair is (golden()). */
        static Part helmet;

        /**
         * Super Saiyan: the texture with every lavender texel inside the helmet's boxes (skin hair, crown, curtains,
         * locks) turned gold, keeping its light and shade.
         */
        static BufferedImage golden(Model m) {
            BufferedImage out = new BufferedImage(m.tex.getWidth(), m.tex.getHeight(), BufferedImage.TYPE_INT_ARGB);
            out.getGraphics().drawImage(m.tex, 0, 0, null);
            java.util.List<Box> hairBoxes = new java.util.ArrayList<>(helmet.boxes);
            for (Part child : helmet.children) {
                hairBoxes.addAll(child.boxes);
            }
            for (Box b : hairBoxes) {
                for (int y = b.v; y < b.v + b.uvH() && y < out.getHeight(); y++) {
                    for (int x = b.u; x < b.u + b.uvW() && x < out.getWidth(); x++) {
                        int argb = out.getRGB(x, y);
                        if ((argb >>> 24) == 0) {
                            continue;
                        }
                        float[] hsb = java.awt.Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, null);
                        float hue = hsb[0] * 360.0F;
                        if (hue < 235.0F || hue > 315.0F || hsb[1] < 0.08F || hsb[1] > 0.6F || hsb[2] < 0.3F) {
                            continue;
                        }
                        float bright = Math.min(1.0F, hsb[2] * 1.08F + 0.04F);
                        int rgb = java.awt.Color.HSBtoRGB(47.0F / 360.0F, 0.62F + (1.0F - bright) * 0.3F, bright);
                        out.setRGB(x, y, (argb & 0xFF000000) | (rgb & 0x00FFFFFF));
                    }
                }
            }
            return out;
        }

        static Model build() throws IOException {
            Model m = new Model("trunks_mk5", 128, 128);
            m.skin = ImageIO.read(reference("trunks_mk5_skin_source.png"));
            m.packX = 64;
            Map<String, Part> limbs = humanoid(m);
            allPieces(limbs);

            // Helmet: the skin's head and hair layer, then Trunks' hair as volume - crown, curtains down the
            // sides, the back, and two locks over the forehead.
            helmet = piece(limbs, "head", 0);
            helmet.skin("head", 0, 0, -4, -8, -4, 8, 8, 8, 0.75F)
                    .skin("hat", 32, 0, -4, -8, -4, 8, 8, 8, 1.0F)
                    .box("crown", -4.5F, -10, -4.5F, 9, 1, 9, Trunks::hair)
                    .box("hair_right", -5.5F, -9, -4, 1, 7, 8, Trunks::hair)
                    .mirrored("hair_left", -5.5F, -9, -4, 1, 7, 8, Trunks::hair)
                    .box("hair_back", -5, -9, 4.5F, 10, 8, 1, Trunks::hair)
                    .box("lock_right", -4, -9, -5.5F, 3, 3, 1, Trunks::hair)
                    .mirrored("lock_left", -4, -9, -5.5F, 3, 3, 1, Trunks::hair);
            // M18 feedback: the Super Saiyan 3 mane for the Dragon Fist - a taller crown swept back and hair down to
            // the waist, its own part so SuitModel shows it only then (gold in the _ssj texture like the rest).
            helmet.child("mane", 0, 0, 0)
                    .box("mane_crown", -5, -13, -3, 10, 3, 9, Trunks::hair)
                    .box("mane_top", -5, -10, 4.5F, 10, 10, 2, Trunks::hair)
                    .box("mane_mid", -4.5F, 0, 5, 9, 9, 2, Trunks::hair)
                    .box("mane_low", -3.5F, 9, 5.5F, 7, 8, 2, Trunks::hair);

            // Chest: jacket body and arms from the skin, a stand-up collar, rolled sleeve cuffs, and the sword on
            // his back - hilt over the right shoulder, sheath down to the left hip.
            Part chest = piece(limbs, "body", 1)
                    .skin("body", 16, 16, -4, 0, -2, 8, 12, 4, 0.40F)
                    .skin("jacket", 16, 32, -4, 0, -2, 8, 12, 4, 0.65F)
                    .box("collar_back", -5, -2, 2, 10, 2, 1, Trunks::collar)
                    .box("collar_right", -5, -2, -2, 1, 2, 4, Trunks::collar)
                    .mirrored("collar_left", -5, -2, -2, 1, 2, 4, Trunks::collar);
            Part sword = chest.child("sheath", 0, 4.5F, 3.4F, 0, 0, -0.55F);
            sword.box("scabbard", -1, -5, -0.5F, 2, 14, 1, Trunks::scabbard)
                    .box("chape", -1.5F, 9, -1, 3, 1, 2, Trunks::brass)
                    .box("throat", -1.5F, -6, -1, 3, 1, 2, Trunks::brass);
            // The hilt is its own part (M17): it leaves his back while the sword is in his hand (SuitModel).
            sword.child("hilt", 0, 0, 0)
                    .box("guard", -2.5F, -7, -1.5F, 5, 1, 3, Trunks::brass)
                    .box("grip", -0.5F, -12, -0.5F, 1, 5, 1, Trunks::grip)
                    .box("pommel", -1, -13, -1, 2, 1, 2, Trunks::brass);
            piece(limbs, "right_arm", 1)
                    .skin("arm", 40, 16, -3, -2, -2, 4, 12, 4, 0.40F)
                    .skin("sleeve", 40, 32, -3, -2, -2, 4, 12, 4, 0.65F)
                    .box("cuff_right", -4, 5, -3, 6, 2, 6, Trunks::cuff);
            piece(limbs, "left_arm", 1)
                    .skin("arm", 32, 48, -1, -2, -2, 4, 12, 4, 0.40F)
                    .skin("sleeve", 48, 48, -1, -2, -2, 4, 12, 4, 0.65F)
                    .box("cuff_left", -2, 5, -3, 6, 2, 6, Trunks::cuff);

            // Legs: the skin's legs split at the knee - thighs are the leggings, shins the boots - plus the
            // boots' big yellow cuffs and toes.
            piece(limbs, "right_leg", 2)
                    .skin("thigh", 0, 16, -2, 0, -2, 4, 8, 4, 0.30F)
                    .skin("thigh_pants", 0, 32, -2, 0, -2, 4, 8, 4, 0.55F);
            piece(limbs, "left_leg", 2)
                    .skin("thigh", 16, 48, -2, 0, -2, 4, 8, 4, 0.30F)
                    .skin("thigh_pants", 0, 48, -2, 0, -2, 4, 8, 4, 0.55F);
            piece(limbs, "right_leg", 3)
                    .skin("shin", 0, 24, -2, 8, -2, 4, 4, 4, 0.50F)
                    .skin("shin_pants", 0, 40, -2, 8, -2, 4, 4, 4, 0.75F)
                    .box("boot_cuff_right", -3, 7, -3, 6, 2, 6, Trunks::bootCuff)
                    .box("toe_right", -2, 10, -4, 4, 2, 2, Trunks::toe);
            piece(limbs, "left_leg", 3)
                    .skin("shin", 16, 56, -2, 8, -2, 4, 4, 4, 0.50F)
                    .skin("shin_pants", 0, 56, -2, 8, -2, 4, 4, 4, 0.75F)
                    .box("boot_cuff_left", -3, 7, -3, 6, 2, 6, Trunks::bootCuff)
                    .box("toe_left", -2, 10, -4, 4, 2, 2, Trunks::toe);
            return m;
        }

        static int hair(Px p) {
            // Strands fall straight down: stripes across, lighter on top.
            double across = p.face == Face.RIGHT || p.face == Face.LEFT ? p.z : p.x;
            int c = (int) Math.floor(across * 1.5 + 20) % 3 == 0 ? HAIR_DARK : HAIR;
            if (p.face == Face.TOP) {
                c = Math.abs(p.x) < 0.5 ? HAIR_DARK : HAIR_LIGHT; // centre parting
            }
            if (p.face == Face.BOTTOM || p.side() && p.fh > 2 && p.j == p.fh - 1 && p.noise(2) < 0.5) {
                c = HAIR_DARK; // ragged tips
            }
            return grain(p, c, 0.04);
        }

        static int collar(Px p) {
            int c = grain(p, JACKET, 0.04);
            if (p.face == Face.TOP) {
                return JACKET_LIGHT;
            }
            return p.j == p.fh - 1 ? JACKET_DARK : c;
        }

        static int cuff(Px p) {
            int c = grain(p, JACKET, 0.04);
            if (p.face == Face.TOP || p.face == Face.BOTTOM) {
                return JACKET_DARK;
            }
            return p.j == 0 ? JACKET_LIGHT : c;
        }

        static int scabbard(Px p) {
            int c = grain(p, SHEATH, 0.05);
            if (p.face == Face.BACK && p.i == 0) {
                return SHEATH_LIGHT;
            }
            return c;
        }

        static int brass(Px p) {
            int c = grain(p, BRASS, 0.06);
            return p.edge() == 0 && p.fw > 2 ? BRASS_DARK : c;
        }

        static int grip(Px p) {
            // Diamond wrap.
            return ((p.j + p.i) % 2 == 0) ? WRAP : WRAP_DARK;
        }

        static int bootCuff(Px p) {
            int c = grain(p, BOOT, 0.05);
            if (p.face == Face.TOP) {
                return BOOT_DARK;
            }
            return p.j == p.fh - 1 ? BOOT_DARK : c;
        }

        static int toe(Px p) {
            if (p.face == Face.BOTTOM) {
                return SOLE;
            }
            return p.side() && p.j == p.fh - 1 ? SOLE : grain(p, BOOT, 0.05);
        }
    }

    // ================================================================ Shenron

    static final class Shenron {
        static final int GREEN = 0xFF2F8C3B, GREEN_DARK = 0xFF1B5A25, GREEN_LIGHT = 0xFF56B85E, GREEN_DEEP = 0xFF123F1A;
        static final int BELLY = 0xFFE6D394, BELLY_DARK = 0xFFB59F5C, FIN = 0xFF8BC34A, FIN_DARK = 0xFF5E8F2E;
        static final int HORN = 0xFF8C6A3C, HORN_LIGHT = 0xFFC9A46A, EYE = 0xFFFF2A1E, EYE_HOT = 0xFFFFD2B0;
        static final int TEETH = 0xFFF2EEDC, MOUTH = 0xFF4A0E12, CLAW = 0xFFEDE6CC, WHISKER = 0xFF1F2420;

        static Model build() {
            Model m = new Model("shenron", 128, 128);
            m.previewScale = 5.0F;
            // Loose pieces the renderer places along the spine: one body segment (12 x 12 x 16, centred on the
            // origin, back up, belly down, toward the head = -Z), the head (pivot at the neck), an arm (pivot at
            // the shoulder, hanging down) and one whisker link.
            m.part("segment", 0, 0, 0)
                    .box("core", -6, -6, -8, 12, 12, 16, Shenron::scales)
                    .box("belly", -4.5F, 5.5F, -8, 9, 1, 16, Shenron::belly)
                    .box("fin", -0.5F, -9, -6, 1, 3, 9, Shenron::fin);

            Part head = m.part("head", 0, 0, 0);
            head.box("skull", -5, -6, -6, 10, 8, 10, Shenron::skull)
                    .box("snout", -3.5F, -4, -14, 7, 5, 8, Shenron::snout)
                    .box("nose", -2.5F, -5, -14, 5, 1, 3, Shenron::snout)
                    .box("upper_teeth", -3, 1, -13.5F, 6, 1, 7, Shenron::teeth)
                    .box("brow_right", -5.5F, -7, -7, 4, 2, 4, Shenron::brow)
                    .mirrored("brow_left", -5.5F, -7, -7, 4, 2, 4, Shenron::brow)
                    .box("crest", -0.5F, -8, -3, 1, 2, 7, Shenron::fin)
                    .box("frill_right", -6.5F, -5, 0, 1, 6, 5, Shenron::fin)
                    .mirrored("frill_left", -6.5F, -5, 0, 1, 6, 5, Shenron::fin);
            head.child("jaw", 0, 2, -5)
                    .box("jaw", -3.5F, 0, -8.5F, 7, 2, 9, Shenron::jaw)
                    .box("lower_teeth", -2.5F, -1, -7.5F, 5, 1, 6, Shenron::teeth);
            for (String side : new String[]{"right", "left"}) {
                float sx = side.equals("right") ? -3.5F : 3.5F;
                float tilt = side.equals("right") ? -0.35F : 0.35F;
                Part horn = head.child("horn_" + side, sx, -6, 1, -0.9F, 0, tilt);
                horn.box("horn_" + side, -1, -10, -1, 2, 10, 2, Shenron::horn);
                horn.child("tine_" + side, 0, -6, 0, 0.9F, 0, 0)
                        .box("tine_" + side, -0.5F, -4, -0.5F, 1, 4, 1, Shenron::horn);
            }

            Part arm = m.part("arm", 0, 0, 0);
            arm.box("upper", -1.5F, 0, -1.5F, 3, 7, 3, Shenron::scales);
            Part fore = arm.child("forearm", 0, 7, 0, -0.9F, 0, 0);
            fore.box("forearm", -1, 0, -1, 2, 6, 2, Shenron::scales);
            Part hand = fore.child("hand", 0, 6, 0, 0.5F, 0, 0);
            hand.box("palm", -1.5F, 0, -1.5F, 3, 1, 3, Shenron::scales)
                    .box("claw_a", -1.5F, 1, -1.5F, 1, 3, 1, Shenron::claw)
                    .box("claw_b", 0.5F, 1, -1.5F, 1, 3, 1, Shenron::claw)
                    .box("claw_c", -0.5F, 1, 0.5F, 1, 3, 1, Shenron::claw);

            m.part("whisker", 0, 0, 0)
                    .box("whisker", -0.5F, -0.5F, -8, 1, 1, 8, p -> grain(p, WHISKER, 0.1));
            m.previewGroups.add("segment");
            m.previewGroups.add("head");
            m.previewGroups.add("arm,whisker");
            return m;
        }

        /** Overlapping scales: rows of little arcs, dark on the back, lighter down the sides. */
        static int scales(Px p) {
            int row = p.j / 2;
            int col = (p.i + (row % 2) * 1) / 2;
            boolean rim = p.j % 2 == 1 && ((p.i + row) % 2 == 0);
            int base;
            switch (p.face) {
                case TOP -> base = GREEN_DARK;
                case BOTTOM -> base = BELLY;
                default -> base = mix(GREEN_DARK, GREEN, p.fy * 1.3);
            }
            if (p.face == Face.BOTTOM) {
                return grain(p, base, 0.04);
            }
            int c = grain(p, base, 0.05);
            if (rim) {
                c = shade(c, 0.72);
            } else if ((col + row) % 5 == 0) {
                c = shade(c, 1.15);
            }
            return c;
        }

        static int belly(Px p) {
            if (p.face == Face.BOTTOM || p.side()) {
                // Cross plates.
                double along = p.face == Face.FRONT || p.face == Face.BACK ? 0 : p.z;
                return (int) Math.floor(along + 20) % 2 == 0 && p.face == Face.BOTTOM ? BELLY_DARK : grain(p, BELLY, 0.04);
            }
            return grain(p, BELLY, 0.04);
        }

        static int fin(Px p) {
            int c = mix(FIN, FIN_DARK, p.fy);
            return p.i % 3 == 0 ? shade(c, 0.8) : grain(p, c, 0.05);
        }

        static int skull(Px p) {
            if (p.face == Face.FRONT || p.face == Face.RIGHT || p.face == Face.LEFT) {
                // Glowing red eyes either side of the snout, under the brow.
                boolean eye = p.y > -4.2 && p.y < -2.0 && (p.face == Face.FRONT ? Math.abs(p.x) > 3.6
                        : p.z < -3.5);
                if (eye) {
                    return p.y < -3.0 && (p.face == Face.FRONT ? Math.abs(p.x) > 4.2 : p.z < -4.6) ? EYE_HOT : EYE;
                }
            }
            if (p.face == Face.BOTTOM) {
                return grain(p, BELLY, 0.04);
            }
            return scales(p);
        }

        static int snout(Px p) {
            if (p.face == Face.FRONT && p.box.name.equals("nose")) {
                return Math.abs(p.x) > 1.2 ? GREEN_DEEP : GREEN; // nostrils
            }
            if (p.face == Face.BOTTOM) {
                return MOUTH;
            }
            if (p.face == Face.TOP) {
                return grain(p, GREEN, 0.05);
            }
            return grain(p, mix(GREEN, GREEN_LIGHT, p.fy), 0.05);
        }

        static int teeth(Px p) {
            if (p.side() && p.i % 2 == 1) {
                return shade(TEETH, 0.8);
            }
            return grain(p, TEETH, 0.03);
        }

        static int jaw(Px p) {
            if (p.face == Face.TOP) {
                return MOUTH;
            }
            if (p.face == Face.BOTTOM) {
                return grain(p, BELLY, 0.04);
            }
            return grain(p, GREEN, 0.05);
        }

        static int brow(Px p) {
            return grain(p, p.face == Face.TOP ? GREEN_LIGHT : GREEN_DARK, 0.05);
        }

        static int horn(Px p) {
            return grain(p, mix(HORN, HORN_LIGHT, 1 - p.fy), 0.06);
        }

        static int claw(Px p) {
            return grain(p, p.fy > 0.6 ? 0xFFBDB59A : CLAW, 0.04);
        }
    }

    // ================================================================ monkey (M18, the Cannon Shooter's)

    /**
     * The Cannon Shooter's monkey: a little humanoid (drawn at half size) with a big round head, round ears, a pale
     * muzzle, long arms, a long tail, in the Maple get-up - red bandana, red vest over brown fur, a blue cap with a
     * gold band and a button on top.
     */
    static final class Monkey {
        static final int FUR = 0xFF8B5A2B, FUR_DARK = 0xFF66401E, FUR_LIGHT = 0xFFA87440;
        static final int FACE = 0xFFF2D2B0, FACE_DARK = 0xFFD9B18C, EYE = 0xFF1A1410, EYE_SHINE = 0xFFFFFFFF, NOSE = 0xFF5A3A28;
        static final int RED = 0xFFC8312A, RED_DARK = 0xFF8E1F1A, BLUE = 0xFF2F5FC8, BLUE_DARK = 0xFF1F3F8A;
        static final int GOLD = 0xFFE8B83A, PAW = 0xFF3A2416;

        static Model build() {
            Model m = new Model("monkey", 128, 128);
            Part head = m.part("head", 0, 0, 0);
            m.part("hat", 0, 0, 0);
            Part body = m.part("body", 0, 0, 0);
            Part rArm = m.part("right_arm", -5, 2, 0).shift(-0.5F, 0, 0);
            Part lArm = m.part("left_arm", 5, 2, 0).shift(0.5F, 0, 0);
            Part rLeg = m.part("right_leg", -1.9F, 12, 0);
            Part lLeg = m.part("left_leg", 1.9F, 12, 0);

            head.box("skull", -4.5F, -8.5F, -4, 9, 8, 8, Monkey::head)
                    .box("muzzle", -2.5F, -3.5F, -5.5F, 5, 3, 2, Monkey::muzzle)
                    .box("ear_right", -6.5F, -6.5F, -1, 2, 3, 2, Monkey::ear)
                    .mirrored("ear_left", -6.5F, -6.5F, -1, 2, 3, 2, Monkey::ear)
                    .box("cap", -4, -10.5F, -3.5F, 8, 2, 7, Monkey::cap)
                    .box("cap_brim", -3, -9.2F, -5.5F, 6, 1, 2, Monkey::capDark)
                    .box("cap_button", -0.5F, -11.3F, -0.5F, 1, 1, 1, Monkey::gold);

            body.box("torso", -4, 0, -2.5F, 8, 11, 5, Monkey::torso)
                    .box("vest_right", -4.5F, 1, -3, 3, 7, 6, 0.1F, Monkey::vest)
                    .mirrored("vest_left", -4.5F, 1, -3, 3, 7, 6, Monkey::vest)
                    .box("belt", -4.2F, 9, -2.7F, 8, 2, 5, 0.1F, Monkey::belt);
            Part tail = body.child("tail", 0, 9, 2.5F, -0.9F, 0, 0);
            tail.box("tail_a", -1, -1, 0, 2, 2, 6, Monkey::fur)
                    .box("tail_b", -1, -5, 5, 2, 5, 2, Monkey::fur)
                    .box("tail_tip", -1.5F, -7, 4.5F, 3, 2, 3, Monkey::fur);

            arm(rArm, false);
            arm(lArm, true);
            leg(rLeg, false);
            leg(lLeg, true);
            return m;
        }

        static void arm(Part arm, boolean left) {
            Hulk.box(arm, left, "upper_arm", -3, -2, -2, 4, 7, 4, Monkey::fur);
            Hulk.box(arm, left, "forearm", -3, 5, -2, 4, 6, 4, Monkey::fur);
            Hulk.box(arm, left, "paw", -3, 11, -2, 4, 2, 4, Monkey::paw);
        }

        static void leg(Part leg, boolean left) {
            Hulk.box(leg, left, "thigh", -2, 0, -2, 4, 7, 4, Monkey::fur);
            Hulk.box(leg, left, "foot", -2, 7, -3, 4, 3, 5, Monkey::paw);
        }

        static int fur(Px p) {
            int c = p.noise() < 0.15 ? FUR_LIGHT : (int) Math.floor(p.x * 1.1 + p.y * 0.9 + 40) % 4 == 0 ? FUR_DARK : FUR;
            if (p.face == Face.BOTTOM) {
                c = shade(c, 0.78);
            } else if (p.side()) {
                c = shade(c, 1.05 - p.fy * 0.1);
            }
            return grain(p, c, 0.05);
        }

        static int head(Px p) {
            if (p.face == Face.FRONT) {
                // The pale face: round eyes with a shine, under the cap.
                boolean eye = p.y > -6.6 && p.y < -4.4 && Math.abs(Math.abs(p.x) - 2.0) < 1.1;
                if (eye) {
                    return p.y < -5.8 && Math.abs(p.x) > 1.2 && Math.abs(p.x) < 2.0 ? EYE_SHINE : EYE;
                }
                if (p.y > -7.2 && Math.abs(p.x) < 3.4) {
                    return grain(p, p.y > -1.5 ? FACE_DARK : FACE, 0.04);
                }
            }
            return fur(p);
        }

        static int muzzle(Px p) {
            if (p.face == Face.FRONT && p.y < -2.6 && Math.abs(p.x) < 1.0) {
                return NOSE;
            }
            if (p.face == Face.FRONT && p.y > -1.6 && Math.abs(p.x) < 1.6) {
                return NOSE;
            }
            return grain(p, p.face == Face.BOTTOM ? FACE_DARK : FACE, 0.04);
        }

        static int ear(Px p) {
            return p.face == Face.FRONT || p.face == Face.RIGHT || p.face == Face.LEFT ? grain(p, FACE_DARK, 0.04) : fur(p);
        }

        static int cap(Px p) {
            if (p.face == Face.BOTTOM) {
                return BLUE_DARK;
            }
            // A gold band round the lower half.
            if (p.side() && p.fy > 0.55) {
                return GOLD;
            }
            return grain(p, p.face == Face.TOP ? BLUE : (int) Math.floor(p.x + p.z + 20) % 3 == 0 ? BLUE_DARK : BLUE, 0.04);
        }

        static int capDark(Px p) {
            return grain(p, BLUE_DARK, 0.04);
        }

        static int gold(Px p) {
            return GOLD;
        }

        static int torso(Px p) {
            // A red bandana knotted at the neck, fur below.
            if (p.y < 1.6) {
                return grain(p, p.face == Face.FRONT && Math.abs(p.x) < 1.0 && p.y > 0.6 ? RED_DARK : RED, 0.04);
            }
            if (p.face == Face.FRONT && Math.abs(p.x) < 2.0) {
                return grain(p, FACE_DARK, 0.04);
            }
            return fur(p);
        }

        static int vest(Px p) {
            return grain(p, p.edge() == 0 ? RED_DARK : RED, 0.04);
        }

        static int belt(Px p) {
            return grain(p, p.face == Face.FRONT && Math.abs(p.x) < 1.0 ? GOLD : FUR_DARK, 0.04);
        }

        static int paw(Px p) {
            return grain(p, p.face == Face.BOTTOM || p.face == Face.FRONT ? PAW : FUR_DARK, 0.04);
        }
    }

    // ================================================================ Dragon Ball parts (M18)

    /**
     * Shared ground for the parts models: a texture of the given size, and the two roots the layer drives - "head"
     * (pivot at the neck, like the player model's head) and "body" (pivot at the shoulders). Add groups as children
     * of those: {@code head.child("antennae", 0, -8, 0).box(...)}. Model space is the player model's: pixels, y down,
     * the front is -Z; the head spans y -8..0, the body 0..12, x -4..4, z -2..2.
     */
    static final class Parts {
        static Model partsModel(String name, int texW, int texH) {
            Model m = new Model(name, texW, texH);
            m.part("head", 0, 0, 0);
            m.part("body", 0, 0, 0);
            m.previewScale = 10.0F;
            return m;
        }

        static Part head(Model m) {
            return m.root.children.get(0);
        }

        static Part body(Model m) {
            return m.root.children.get(1);
        }

        static int fur(Px p, int base, int dark, int light) {
            int c = p.noise() < 0.12 ? light : (int) Math.floor(p.x * 1.1 + p.y * 0.9 + 40) % 4 == 0 ? dark : base;
            if (p.face == Face.BOTTOM) {
                c = shade(c, 0.8);
            }
            return grain(p, c, 0.05);
        }
    }

    /**
     * Saiyan tails (Raditz, Vegeta, young Gohan): group "tail" under the body. A furred brown tail from the small
     * of the back, five segments nested one in the next (tail > seg2 > seg3 > seg4 > tip, each pivoted at the far end
     * of the one before, so the chain curls as one): thick at the base, thinner in the middle, a bushy tip. It hangs
     * down behind and curls back up at the end; SaiyanParts.animate() sways the segments by those names.
     */
    static final class SaiyanParts {
        static final int FUR = 0xFF6B4423, FUR_DARK = 0xFF4A2E17, FUR_LIGHT = 0xFF8A5A30;
        static final int FUR_DEEP = 0xFF33200E, FUR_TIP = 0xFF9E6E3E;
        /** Rest pose of each segment (x rotation, radians): down from the back, then curling back up to the tip. */
        static final float[] REST = {-0.95F, 0.3F, 0.4F, 0.45F, 0.45F};
        /** Length of each segment from its pivot to the next one's (the tip's box runs a little further). */
        static final float[] LENGTH = {4, 3, 3, 3, 3};

        static Model build() {
            Model m = Parts.partsModel("parts_saiyan", 64, 32);
            // Rooted half a pixel inside the small of the back so no gap opens when it swings.
            Part seg1 = Parts.body(m).child("tail", 0, 10, 1.5F, REST[0], 0, 0);
            seg1.box("tail_base", -1.5F, -1.5F, -1, 3, 3, 5, SaiyanParts::fur);
            fluff(seg1, "ruff", "tail_ruff", 1.5F, 3, -0.25F);

            Part seg2 = seg1.child("seg2", 0, 0, LENGTH[0], REST[1], 0, 0);
            seg2.box("tail_seg2", -1.5F, -1.5F, -0.5F, 3, 3, 4, -0.25F, SaiyanParts::fur);

            Part seg3 = seg2.child("seg3", 0, 0, LENGTH[1], REST[2], 0, 0);
            seg3.box("tail_seg3", -1, -1, -0.5F, 2, 2, 4, SaiyanParts::fur);
            fluff(seg3, "fluff3", "tail_fluff3", 1.5F, 2, 0);

            Part seg4 = seg3.child("seg4", 0, 0, LENGTH[2], REST[3], 0, 0);
            seg4.box("tail_seg4", -1, -1, -0.5F, 2, 2, 4, -0.1F, SaiyanParts::fur);

            // The tip: back to 3x3, a fluffed cross-section and a small nub at the very end.
            Part tip = seg4.child("tip", 0, 0, LENGTH[3], REST[4], 0, 0);
            tip.box("tail_tip", -1.5F, -1.5F, -0.5F, 3, 3, 3, -0.15F, SaiyanParts::tipFur);
            Part tuft = fluff(tip, "tuft", "tail_tuft", 1, 3, -0.2F);
            // The very end, turned with the tuft so it closes to a soft point rather than a knob.
            tuft.box("tail_tip_end", -1, -1, 1.2F, 2, 2, 1, -0.05F, SaiyanParts::tipFur);
            return m;
        }

        /**
         * A second box of the segment's thickness turned 45 degrees about its axis: its corners poke out between the
         * main box's faces, so the cross-section reads as a soft tuft of fur rather than a square rod.
         */
        static Part fluff(Part seg, String name, String boxName, float at, int size, float inflate) {
            Part f = seg.child(name, 0, 0, at, 0, 0, 0.7854F);
            float h = size / 2.0F;
            f.box(boxName, -h, -h, -h, size, size, size, inflate, name.equals("tuft") ? SaiyanParts::tipFur : SaiyanParts::fur);
            return f;
        }

        /** Tufted fur: streaks running the length of the tail, lit along the spine, dark along the underside. */
        static int fur(Px p) {
            int c = (int) Math.floor(p.x * 1.3 + p.y * 0.9 + 40) % 3 == 0 ? FUR_DARK : FUR;
            double n = p.noise();
            if (n < 0.1) {
                c = FUR_LIGHT;
            } else if (n > 0.93) {
                c = FUR_DEEP;
            }
            // Tuft ends: a few texels lit a touch brighter, scattered along the length.
            if (p.noise(3) < 0.15) {
                c = shade(c, 1.1);
            }
            if (p.face == Face.BOTTOM) {
                c = shade(c, 0.68);
            } else if (p.face == Face.TOP) {
                c = shade(c, 1.15);
            } else if (p.face == Face.RIGHT || p.face == Face.LEFT) {
                c = shade(c, 1.12 - p.fy * 0.4);
            } else {
                // End caps, tucked into the joints.
                c = shade(c, 0.85);
            }
            return grain(p, c, 0.06);
        }

        /** The bushier tip: the same fur with more light tufts and a warmer brown. */
        static int tipFur(Px p) {
            int c = fur(p);
            if (p.noise(5) < 0.22 && p.face != Face.BOTTOM) {
                c = grain(p, p.noise(7) < 0.5 ? FUR_TIP : FUR_LIGHT, 0.05);
            }
            if (p.box.name.equals("tail_tip_end") && p.face == Face.BACK) {
                c = grain(p, FUR_DARK, 0.06);
            }
            return c;
        }
    }

    /**
     * Head parts (M18): the Namekians' antennae and long pointed ears ("antennae", "ears" - Piccolo, Dende), Ginyu's
     * horns ("horns") and Dodoria's crown of head spikes ("spikes"), all groups under the head. Head space: the skull
     * is x -4..4, y -8..0, z -4..4 and the skin's hat layer sits 0.5 px outside it, so everything starts at 4.5 / -8.5.
     */
    static final class NamekianParts {
        // Namekian skin: between piccolo.png (5AAA4A) and dende.png (6ABF5A); darker at the roots, lighter at the tips.
        static final int GREEN = 0xFF60B04E, GREEN_DARK = 0xFF447A35, GREEN_LIGHT = 0xFF8AD070, GREEN_INNER = 0xFF365F2B;
        // Ginyu's horns: near-black purple with worn, lighter growth rings and a pale point.
        static final int HORN = 0xFF2A2030, HORN_DARK = 0xFF17111A, HORN_LIGHT = 0xFF4E4058, HORN_TIP = 0xFF8A7A94;
        // Dodoria's spikes: his skin pink (dodoria.png E88AA8), shadowed at the root, paler toward the points.
        static final int PINK = 0xFFE88AA8, PINK_DARK = 0xFFB0587A, PINK_LIGHT = 0xFFF6B8CC;

        static Model build() {
            Model m = Parts.partsModel("parts_namekian", 64, 64);
            Part head = Parts.head(m);
            antennae(head);
            Part ears = head.child("ears", 0, 0, 0);
            ear(ears, false);
            ear(ears, true);
            Part horns = head.child("horns", 0, 0, 0);
            horn(horns, false);
            horn(horns, true);
            spikes(head.child("spikes", 0, 0, 0));
            return m;
        }

        // ---------------------------------------------------------------- antennae

        /**
         * Two antennae from the forehead just above the brow: three thin segments each, rising up and forward, levelling
         * out above the crown and drooping at the tip. The group's pivot is at their roots so animate() can bob them.
         */
        static void antennae(Part head) {
            // Roots just above the brow (the brow is y -5..-4), on the hat layer's front; under Piccolo's turban edge.
            Part group = head.child("antennae", 0, -5.7F, -4.5F);
            antenna(group, false);
            antenna(group, true);
        }

        static void antenna(Part group, boolean left) {
            String s = left ? "l" : "r";
            float side = left ? 1 : -1;
            // Root: a slightly thicker segment leaning ~35 degrees forward and a touch outward.
            Part a = group.child("antenna_" + s, 1.5F * side, 0, 0, 0.5F, 0, 0.1F * side);
            a.box("antenna_" + s + "_root", -0.5F, -3, -0.5F, 1, 3, 1, 0.15F, p -> antennaSkin(p, 0));
            // Middle: bends on forward, nearly level with the crown.
            Part b = a.child("antenna_" + s + "_mid", 0, -3, 0, 0.55F, 0, 0);
            b.box("antenna_" + s + "_mid", -0.5F, -2, -0.5F, 1, 2, 1, p -> antennaSkin(p, 1));
            // Tip: curls down steeply, a little thinner.
            Part c = b.child("antenna_" + s + "_tip", 0, -2, 0, 0.85F, 0, 0);
            c.box("antenna_" + s + "_tip", -0.5F, -2, -0.5F, 1, 2, 1, -0.1F, p -> antennaSkin(p, 2));
        }

        /** Smooth skin: dark at the root grading to a light tip, lit from above, a faint ring where segments meet. */
        static int antennaSkin(Px p, int seg) {
            // 0 at the root of the whole antenna, 1 at the tip (segments are 3, 2, 2 px long; y runs -h..0 in each).
            double along = seg == 0 ? (-p.y) / 7.0 : seg == 1 ? (3 - p.y) / 7.0 : (5 - p.y) / 7.0;
            int c = along < 0.5 ? mix(GREEN_DARK, GREEN, along * 2) : mix(GREEN, GREEN_LIGHT, (along - 0.5) * 2);
            if (p.face == Face.TOP) {
                c = shade(c, seg == 2 ? 0.9 : 1.1);
            } else if (p.face == Face.BOTTOM) {
                c = shade(c, 0.8);
            } else if (p.face == Face.BACK) {
                c = shade(c, 0.9);
            } else if (p.face == Face.FRONT) {
                c = shade(c, 1.05);
            }
            if (seg > 0 && p.side() && p.j == p.fh - 1) {
                c = shade(c, 0.88); // the joint
            }
            return grain(p, c, 0.04);
        }

        // ---------------------------------------------------------------- ears

        /**
         * A long pointed ear: a flat base on the side of the head at eye height, then a narrower middle and a 1x1 point
         * sweeping back and up, flaring a little away from the head. Outer surface skin green, the side toward the head
         * darker (the hollow), the thin rims dark.
         */
        static void ear(Part ears, boolean left) {
            String s = left ? "l" : "r";
            float side = left ? 1 : -1;
            float x = left ? 0 : -1;
            // 0.1 px clear of the hat layer (Piccolo's turban is opaque there) so the inner face can't z-fight it.
            // The whole ear leans back a little; the base is a tall flat plate over the side of the head.
            Part base = ears.child("ear_" + s, 4.6F * side, -3, 0, 0.25F, 0, 0);
            base.box("ear_" + s + "_base", x, -2, -1.5F, 1, 4, 3, p -> earSkin(p, left, 0));
            Part mid = base.child("ear_" + s + "_mid", 0, -1.5F, 1.5F, 0.3F, 0.12F * side, 0);
            mid.box("ear_" + s + "_mid", x, -1, 0, 1, 2, 2, p -> earSkin(p, left, 1));
            Part tip = mid.child("ear_" + s + "_tip", 0, -0.5F, 2, 0.35F, 0, 0);
            tip.box("ear_" + s + "_tip", x, -0.5F, 0, 1, 1, 2, -0.05F, p -> earSkin(p, left, 2));
        }

        static int earSkin(Px p, boolean left, int seg) {
            boolean inner = left ? p.face == Face.RIGHT : p.face == Face.LEFT;
            boolean outer = left ? p.face == Face.LEFT : p.face == Face.RIGHT;
            int c = seg == 0 ? GREEN : seg == 1 ? mix(GREEN, GREEN_LIGHT, 0.35) : mix(GREEN, GREEN_LIGHT, 0.7);
            if (inner) {
                // The hollow of the ear: dark, with a slightly lighter rim.
                c = p.edge() == 0 ? mix(GREEN_INNER, GREEN_DARK, 0.5) : GREEN_INNER;
            } else if (outer) {
                // Lit toward the top; the base has a shaded fold along its lower back (the lobe).
                c = shade(c, 1.08 - p.fy * 0.18);
                boolean back = left ? p.fx > 0.5 : p.fx < 0.5; // fx runs front->back on LEFT, back->front on RIGHT
                if (seg == 0 && p.fy > 0.6 && back) {
                    c = mix(c, GREEN_DARK, 0.5);
                }
            } else if (p.face == Face.TOP) {
                c = shade(GREEN, 1.05);
            } else if (p.face == Face.BOTTOM) {
                c = shade(GREEN_DARK, 0.85);
            } else {
                c = GREEN_DARK;
            }
            return grain(p, c, 0.05);
        }

        // ---------------------------------------------------------------- horns

        /**
         * Ginyu's horns: short, thick, from the top-front corners of the skull, leaning outward and a little back, a
         * thinner upper segment bending further out to a pale point.
         */
        static void horn(Part horns, boolean left) {
            String s = left ? "l" : "r";
            float side = left ? 1 : -1;
            // Pivot a little below the skull's top so the leaning base's raised corner still meets the scalp.
            Part base = horns.child("horn_" + s, 2.5F * side, -7.8F, -1, -0.12F, 0, 0.3F * side);
            base.box("horn_" + s + "_base", -1, -3, -1, 2, 3, 2, 0.1F, p -> hornSkin(p, 0));
            Part tip = base.child("horn_" + s + "_tip", 0, -3, 0, -0.15F, 0, 0.45F * side);
            tip.box("horn_" + s + "_tip", -0.5F, -2, -0.5F, 1, 2, 1, 0.2F, p -> hornSkin(p, 1));
        }

        /** Dark horn with lighter growth rings every 2 px, a dark root ring and a pale worn point. */
        static int hornSkin(Px p, int seg) {
            double along = seg == 0 ? (-p.y) / 5.0 : (3 - p.y) / 5.0; // 0 root .. 1 point
            int c = HORN;
            if (seg == 0 && -p.y < 0.9) {
                c = HORN_DARK; // the root, in the scalp's shadow
            } else if ((int) Math.floor(-p.y + 0.3) % 2 == 1) {
                c = mix(HORN, HORN_LIGHT, 0.6); // a ring
            }
            if (along > 0.75) {
                c = mix(c, HORN_TIP, (along - 0.75) * 3.2);
            }
            if (p.face == Face.TOP) {
                c = seg == 1 ? HORN_TIP : shade(c, 1.15);
            } else if (p.face == Face.BOTTOM) {
                c = shade(c, 0.7);
            } else if (p.face == Face.BACK || p.face == Face.RIGHT) {
                c = shade(c, 0.9);
            }
            return grain(p, c, 0.05);
        }

        // ---------------------------------------------------------------- spikes

        /**
         * Dodoria: six spikes around the crown (the two down the middle taller, the sides splayed out) and three more
         * down the back of the head. Leaning spikes' pivots sit a little inside the scalp so no gap shows under them.
         */
        static void spikes(Part group) {
            spike(group, 1, 0, -7.9F, -1.8F, 0.3F, 0, 2);
            spike(group, 2, -2.6F, -7.8F, -0.3F, 0.05F, -0.4F, 1);
            spike(group, 3, 2.6F, -7.8F, -0.3F, 0.05F, 0.4F, 1);
            spike(group, 4, 0, -7.9F, 1.2F, -0.3F, 0, 2);
            spike(group, 5, -2.6F, -7.8F, 2.6F, -0.4F, -0.45F, 1);
            spike(group, 6, 2.6F, -7.8F, 2.6F, -0.4F, 0.45F, 1);
            spike(group, 7, -1.8F, -6.8F, 3.9F, -0.95F, -0.25F, 1);
            spike(group, 8, 1.8F, -6.8F, 3.9F, -0.95F, 0.25F, 1);
            spike(group, 9, 0, -5.0F, 3.9F, -1.2F, 0, 1);
        }

        /** One spike pointing up its own -Y: a three-step cone, 2.4 px wide at the root, 1.6 in the middle, a 1 px point. */
        static void spike(Part group, int n, float x, float y, float z, float rx, float rz, int tipH) {
            Part base = group.child("spike_" + n, x, y, z, rx, 0, rz);
            base.box("spike_" + n + "_base", -1, -1, -1, 2, 1, 2, 0.2F, p -> spikeSkin(p, 0, tipH));
            base.box("spike_" + n + "_mid", -0.5F, -2.2F, -0.5F, 1, 1, 1, 0.3F, p -> spikeSkin(p, 1, tipH));
            base.box("spike_" + n + "_tip", -0.5F, -2.5F - tipH, -0.5F, 1, tipH, 1, p -> spikeSkin(p, 2, tipH));
        }

        static int spikeSkin(Px p, int seg, int tipH) {
            double total = 2.5 + tipH;
            double along = (-p.y) / total; // 0 root .. 1 point (all three boxes share the spike's space)
            int c = along < 0.3 ? mix(PINK_DARK, PINK, along / 0.3) : mix(PINK, PINK_LIGHT, (along - 0.3) / 0.7);
            if (p.face == Face.TOP) {
                c = seg == 2 ? PINK_LIGHT : shade(c, 1.08);
            } else if (p.face == Face.BOTTOM) {
                c = shade(PINK_DARK, 0.8);
            } else if (p.face == Face.BACK) {
                c = shade(c, 0.88);
            } else if (p.face == Face.RIGHT) {
                c = shade(c, 0.94);
            }
            return grain(p, c, 0.05);
        }
    }
    /**
     * Frieza: the first form's horns ("horns", head), shoulder pads ("pads", body) and thick tail ("tail", body), and
     * the final form's smooth tail ("tail_final", body). Colours follow the skins (white F4F2F4, purple 8A3AAA, the
     * first form's brown straps 8A5A3A). The tails are chains: the group holds the root segment, then "tail_2".."tail_5"
     * (or "tail_final_2"..) each pivoted at the end of the one before, bent by TAIL_BEND / TAIL_FINAL_BEND -
     * client/parts/FriezaParts.animate keeps those bends and sways on top of them.
     */
    static final class FriezaParts {
        static final int SHELL = 0xFF8A3AAA, SHELL_DARK = 0xFF5C2476, SHELL_DEEP = 0xFF3A1450, SHELL_LIGHT = 0xFFAE64CC, SHELL_RIM = 0xFFD6A4EA;
        static final int STRAP = 0xFF8A5A3A, STRAP_DARK = 0xFF5A3A24;
        static final int WHITE = 0xFFF4F2F4, BAND = 0xFF9A50B8;
        static final int HORN = 0xFF26222A, HORN_LIGHT = 0xFF4E4856, HORN_DEEP = 0xFF131016;
        /** Root pitch, then the bend each later segment adds (radians; negative hangs down-back). Mirrored in the model class. */
        static final float[] TAIL_BEND = {-0.8F, 0.2F, 0.25F, 0.25F, 0.15F};
        static final float[] TAIL_FINAL_BEND = {-0.75F, 0.2F, 0.22F, 0.22F, 0.15F};

        static Model build() {
            Model m = Parts.partsModel("parts_frieza", 128, 64);
            // Preview rows: the first form's set, then the final form's tail on its own.
            m.previewGroups.add("horns,pads,tail");
            m.previewGroups.add("tail_final");
            Part head = Parts.head(m);
            Part horns = head.child("horns", 0, 0, 0);
            horn(horns, false);
            horn(horns, true);

            Part body = Parts.body(m);
            Part pads = body.child("pads", 0, 0, 0);
            pad(pads, false);
            pad(pads, true);

            // First form: thick, segmented, two purple bands. Lengths pivot to pivot 4,4,4,3,3 (+ a 2 px tip).
            tail(body, "tail", TAIL_BEND, new int[]{4, 4, 4, 3, 3}, new int[]{4, 4, 3, 3, 2},
                    new float[]{0, -0.2F, 0.1F, -0.2F, 0}, new boolean[]{false, true, false, true, false}, 0.2F);
            // Final form: slimmer and smoother, pure white. 4,3,3,3,3 (+ tip).
            tail(body, "tail_final", TAIL_FINAL_BEND, new int[]{4, 3, 3, 3, 3}, new int[]{3, 3, 3, 2, 2},
                    new float[]{0, -0.15F, -0.3F, 0.1F, -0.1F}, new boolean[5], 0.1F);
            return m;
        }

        /** One horn: a 2x2 root box jutting sideways and a little up, a 1x1 tip box bending further up and back. */
        static void horn(Part horns, boolean left) {
            float s = left ? 1 : -1;
            String side = left ? "left" : "right";
            Part root = horns.child("horn_" + side, s * 3.5F, -6.5F, 0, 0, 0, -s * 0.3F);
            root.box("horn_" + side + "_a", left ? 0 : -3, -1, -1, 3, 2, 2, p -> hornPaint(p, left, false));
            Part tip = root.child("horn_" + side + "_tip", s * 3, 0, 0, 0, -s * 0.4F, -s * 0.45F);
            tip.box("horn_" + side + "_b", left ? 0 : -3, -0.5F, -0.5F, 3, 1, 1, p -> hornPaint(p, left, true));
        }

        static int hornPaint(Px p, boolean left, boolean tip) {
            // Keratin: dark with growth rings across its length, a lit ridge on top, the end faces a touch greyer.
            double along = Math.abs(p.x);
            int c = HORN;
            if (((int) Math.floor(along + 0.5)) % 2 == 1) {
                c = mix(HORN, HORN_DEEP, 0.5);
            }
            if (p.face == Face.TOP) {
                c = mix(c, HORN_LIGHT, tip ? 0.55 : 0.4);
            } else if (p.face == Face.BOTTOM) {
                c = mix(c, HORN_DEEP, 0.5);
            } else if (p.face == (left ? Face.LEFT : Face.RIGHT)) {
                c = HORN_LIGHT;
            } else if (p.side()) {
                c = shade(c, 1.12 - p.fy * 0.25);
            }
            return grain(p, c, 0.04);
        }

        /**
         * One shoulder dome over the arm's top (its centre 6.5 px out from the body's middle): a wide base around the
         * shoulder, a narrower middle tier and a small cap, the whole thing tilted so its outer edge dips over the
         * shoulder. Pad space is the body's shifted to the pivot (x +-4, y -1).
         */
        static void pad(Part pads, boolean left) {
            float s = left ? 1 : -1;
            String side = left ? "left" : "right";
            Part pad = pads.child("pad_" + side, s * 4, -1, 0, 0, 0, s * 0.12F);
            pad.box("pad_" + side + "_base", left ? -0.5F : -5.5F, -0.5F, -3, 6, 3, 6, p -> padPaint(p, left, 0));
            pad.box("pad_" + side + "_mid", left ? 0.25F : -5.25F, -2.5F, -2.5F, 5, 2, 5, p -> padPaint(p, left, 1));
            pad.box("pad_" + side + "_cap", left ? 1 : -4, -3.5F, -1.5F, 3, 1, 3, p -> padPaint(p, left, 2));
        }

        static int padPaint(Px p, boolean left, int tier) {
            // A glossy purple dome lit from above, the outside and a little from the front. The shading runs down
            // the whole dome's height (p.y, not each tier's own), so the three steps read as one curved shell; a
            // highlight sits where the middle tier faces the light. The base's foot row and underside are the brown
            // strap the dome is buckled to.
            if (p.face == Face.BOTTOM) {
                return grain(p, tier == 0 ? STRAP_DARK : SHELL_DEEP, 0.04);
            }
            if (p.face == Face.TOP) {
                double r = Math.max(Math.abs(p.fx - 0.5), Math.abs(p.fy - 0.5)) * 2;
                int c = tier == 2 ? (r < 0.5 ? SHELL_RIM : mix(SHELL_LIGHT, SHELL_RIM, 0.6)) : tier == 1 ? mix(SHELL_LIGHT, SHELL_RIM, 0.3) : mix(SHELL_LIGHT, SHELL_RIM, 0.1);
                return grain(p, c, 0.03);
            }
            boolean outer = p.face == (left ? Face.LEFT : Face.RIGHT);
            boolean inner = p.face == (left ? Face.RIGHT : Face.LEFT);
            double outward = outer ? 1 : inner ? -1 : 0;
            double forward = p.face == Face.FRONT ? 1 : p.face == Face.BACK ? -1 : 0;
            if (tier == 0 && p.j == p.fh - 1) {
                return grain(p, shade(STRAP, 0.82 + outward * 0.14), 0.05);
            }
            // Top of the cap (y -3.5) bright, the foot of the base (y 2.5) deep in shadow.
            double lit = 1.0 - (p.y + 3.5) / 6.0 * 0.55 + outward * 0.16 + forward * 0.06;
            if (tier == 1 && p.j == 0 && (outer || p.face == Face.FRONT) && (p.face == Face.FRONT ? (left ? p.fx : 1 - p.fx) : 1 - p.fx) > 0.4) {
                lit += 0.35;
            }
            int c = lit > 1.0 ? mix(SHELL_LIGHT, SHELL_RIM, Math.min(1.0, (lit - 1.0) * 2.5)) : mix(SHELL_DEEP, SHELL_LIGHT, lit);
            if (p.j == 0 && tier == 2) {
                c = mix(c, SHELL_RIM, 0.25);
            }
            return grain(p, c, 0.03);
        }

        /**
         * A tail chain under the body from the small of the back: segment k is {@code len[k]} long pivot to pivot,
         * {@code size[k]} square (plus {@code inflate[k]}), bent by {@code bend[k]} from the one before; each box
         * starts 0.5 px back inside its parent so the bends never open a gap, and a rounded tip ends the chain.
         */
        static void tail(Part body, String name, float[] bend, int[] len, int[] size, float[] inflate, boolean[] band, float tipInflate) {
            Part seg = body.child(name, 0, 10, 1, bend[0], 0, 0);
            boolean first = name.equals("tail");
            for (int k = 0; k < len.length; k++) {
                if (k > 0) {
                    seg = seg.child(name + "_" + (k + 1), 0, 0, len[k - 1], bend[k], 0, 0);
                }
                float half = size[k] / 2.0F;
                float z0 = k == 0 ? -1.5F : -0.5F;
                int d = len[k] + (k == 0 ? 2 : 1);
                boolean ring = band[k];
                int segment = k;
                seg.box(name + "_seg_" + (k + 1), -half, -half, z0, size[k], size[k], d, inflate[k], p -> tailPaint(p, first, ring, segment));
            }
            seg.box(name + "_tip", -0.5F, -0.5F, len[len.length - 1] - 0.5F, 1, 1, 2, tipInflate, p -> tailPaint(p, first, false, 9));
        }

        static int tailPaint(Px p, boolean first, boolean band, int segment) {
            // A round white tail lit from above: top bright, flanks falling off downward, underside in shadow. The
            // far end face of each segment is the ring where the next, thinner one starts; the first form carries a
            // purple band around two of its segments and a crease just past each ring.
            double lit;
            switch (p.face) {
                case TOP -> lit = 1.0;
                case BOTTOM -> lit = 0.74;
                case RIGHT, LEFT -> lit = 0.98 - p.fy * 0.22;
                case BACK -> lit = 0.84;
                default -> lit = 0.9;
            }
            if (first && p.side() && p.z > 0.2 && p.z < 1.0 && segment > 0 && segment < 9) {
                lit -= 0.06;
            }
            int c = WHITE;
            if (first && band && p.z > 1.4 && p.z < 2.6 && p.face != Face.BACK && p.face != Face.FRONT) {
                c = BAND;
                lit += 0.05;
            }
            if (segment == 9) {
                lit -= 0.04;
            }
            return grain(p, shade(c, lit), first ? 0.03 : 0.02);
        }
    }

    /**
     * Cell and Buu: Cell's head crest per form ("crest_imperfect", "crest_semi", "crest", "crest_jr", head), his
     * wings ("wings", body) and the imperfect form's tail ("tail", body); Buu's antenna ("antenna_fat",
     * "antenna_super", "antenna_kid", head). TODO (M18 agent).
     */
    /**
     * The Doom saga's parts (DESIGN.md 4-17): the capes that make the silhouettes - Doom's green cloak, God Emperor
     * Doom's (green, gold-edged, white-lined), Magneto's purple, the Latverian witches' black - each a group under the
     * body, hung from the shoulders behind the back in two panels ("<group>" and "<group>_low", pivoted at the
     * shoulders and at the hem of the upper panel so MarvelParts.animate can sway and stream them); and the feathered
     * headdress of Talokan's warriors ("feathers", under the head: a fan of five quills over the crown).
     * Body space: shoulders at y 0, the back at z 2; the cape hangs to the ankles (23 px).
     */
    static final class MarvelParts {
        /** Rest pose (x rotation) of the upper and lower panels - the same as client/parts/MarvelParts.REST. */
        static final float[] REST = {0.08F, 0.06F};

        static Model build() {
            Model m = Parts.partsModel("parts_marvel", 128, 64);
            Part body = Parts.body(m);
            cape(body, "cape_doom", 0xFF2A5E2E, 0xFF1E4422, 0xFF2A5E2E, 0);
            cape(body, "cape_god", 0xFF2E6B34, 0xFFEDE8DA, 0xFFE6BE48, 1);
            cape(body, "cape_magneto", 0xFF6A2A8A, 0xFF4A1A62, 0xFF6A2A8A, 0);
            cape(body, "cape_witch", 0xFF1C2A20, 0xFF121A14, 0xFF3A7A44, 1);
            feathers(Parts.head(m));
            return m;
        }

        /**
         * A cape: {@code outer} on the outside with long folds, {@code inner} lining against the back, {@code trim}
         * along the edges when {@code trimmed} is 1. Ten wide (a pixel past each shoulder), one thick.
         */
        static void cape(Part body, String name, int outer, int inner, int trim, int trimmed) {
            Paint cloth = p -> clothAt(p, outer, inner, trim, trimmed == 1, false);
            Paint hem = p -> clothAt(p, outer, inner, trim, trimmed == 1, true);
            Part top = body.child(name, 0, 0.2F, 2.4F, REST[0], 0, 0);
            top.box(name + "_upper", -5, 0, 0, 10, 12, 1, cloth);
            Part low = top.child(name + "_low", 0, 12, 0, REST[1], 0, 0);
            low.box(name + "_lower", -5, 0, 0, 10, 11, 1, hem);
        }

        /** Folds: darker vertical bands every few texels, a lighter crest beside each; the inside plain and shadowed. */
        static int clothAt(Px p, int outer, int inner, int trim, boolean trimmed, boolean hem) {
            if (p.face == Face.FRONT) {
                return grain(p, shade(inner, 0.9 - p.fy * 0.1), 0.05);
            }
            int fold = Math.floorMod((int) Math.floor(p.x + 5), 3);
            int c = fold == 0 ? shade(outer, 0.78) : fold == 1 ? shade(outer, 1.1) : outer;
            if (trimmed && (p.face == Face.BACK) && (p.i == 0 || p.i == p.fw - 1 || hem && p.j == p.fh - 1)) {
                c = trim;
            }
            if (p.face == Face.BOTTOM || p.face == Face.TOP) {
                c = shade(outer, 0.8);
            }
            if (p.face == Face.BACK) {
                c = shade(c, 1.0 - p.fy * 0.12);
            }
            return grain(p, c, 0.05);
        }

        /** Five quills fanned over the crown, red, gold and jade by turns, a band at the base. */
        static void feathers(Part head) {
            Part fan = head.child("feathers", 0, -8.2F, 1.0F);
            int[] colours = {0xFFE04A3A, 0xFFE0B440, 0xFF2EC08A, 0xFFE0B440, 0xFFE04A3A};
            for (int i = 0; i < 5; i++) {
                float angle = (i - 2) * 0.32F;
                int colour = colours[i];
                Part quill = fan.child("quill_" + i, (i - 2) * 1.2F, 0, 0, -0.35F, 0, angle);
                quill.box("quill_" + i + "_vane", -0.5F, -6, -0.5F, 1, 6, 1, 0.1F, p -> {
                    int c = p.fy < 0.2 ? shade(colour, 1.2) : p.fy > 0.85 ? 0xFFE8E0C8 : colour;
                    return grain(p, c, 0.06);
                });
            }
        }
    }

    static final class CellBuuParts {
        // Skin colours: cell_imperfect / cell_semi / cell / cell_jr / majin_buu / super_buu / kid_buu.
        static final int IMP = 0xFF4A8A3E, IMP_DARK = 0xFF2A5A2A, SEMI = 0xFF5AAA4A, SEMI_DARK = 0xFF3B6E2E;
        static final int PERFECT = 0xFF6ABF5A, PERFECT_DARK = 0xFF3B6E2E, JR_DARK = 0xFF1E3A1E;
        static final int SPOT = 0xFF141216, SHELL = 0xFF1A221C, SHELL_LIGHT = 0xFF2C3A2C, SHELL_RIM = 0xFF4A8A3A;
        static final int FAT = 0xFFF0A0C0, FAT_DARK = 0xFFE08AAA, FAT_LINE = 0xFFB36E88;
        static final int BUU = 0xFFE88AB0, BUU_DARK = 0xFFC86A90, BUU_LINE = 0xFF9D5D77;
        /** The wings' resting spread (yRot of each wing, outer edge back); CellBuuParts.animate uses the same value. */
        static final float WING_SPREAD = 0.25F;
        /** The tail's bend per segment (group, then tail_2..tail_5, then the bulb); CellBuuParts.animate repeats these. */
        static final float[] TAIL_BEND = {-0.7F, 0.35F, 0.4F, 0.45F, 0.45F, 0.2F};

        static Model build() {
            Model m = Parts.partsModel("parts_cell_buu", 128, 64);
            // Preview: one row per group (the crests and antennae would hide each other drawn together).
            m.previewGroups.addAll(List.of("crest_imperfect", "crest_semi", "crest", "crest_jr", "wings", "tail", "antenna_fat", "antenna_super", "antenna_kid"));
            Part head = Parts.head(m);
            Part body = Parts.body(m);
            crestImperfect(head);
            crestSemi(head);
            crestPerfect(head);
            crestJr(head);
            wings(body);
            tail(body);
            antennaFat(head);
            antennaSuper(head);
            antennaKid(head);
            return m;
        }

        // ---------------------------------------------------------------- Cell's crests

        /**
         * Imperfect Cell: two long crests from above the brow (where the skin's hat layer has its bump), rising and
         * curving back over the head, splaying out a little - each a thick stub, a thinner middle and a needle tip.
         */
        static void crestImperfect(Part head) {
            Part g = head.child("crest_imperfect", 0, -8.5F, -1.5F);
            for (int s = -1; s <= 1; s += 2) {
                String n = s < 0 ? "ci_r" : "ci_l";
                Part base = g.child(n, 2.0F * s, 0, 0, -0.6F, 0, -0.2F * s);
                base.box(n + "_base", -1.5F, -3, -1.5F, 3, 4, 3, p -> crest(p, IMP, IMP_DARK, 0.22));
                Part mid = base.child(n + "_mid", 0, -2.8F, 0, -0.45F, 0, -0.12F * s);
                mid.box(n + "_mid", -1, -3, -1, 2, 3, 2, p -> crest(p, IMP, IMP_DARK, 0.2));
                Part tip = mid.child(n + "_tip", 0, -2.8F, 0, -0.45F, 0, -0.1F * s);
                tip.box(n + "_tip", -0.5F, -3, -0.5F, 1, 3, 1, p -> crestTip(p, IMP, IMP_DARK));
            }
        }

        /** Semi-perfect Cell: shorter, thicker crests from the top corners of the head, thrown up and out like a Y. */
        static void crestSemi(Part head) {
            Part g = head.child("crest_semi", 0, -8.5F, -0.5F);
            for (int s = -1; s <= 1; s += 2) {
                String n = s < 0 ? "cs_r" : "cs_l";
                Part base = g.child(n, 2.8F * s, 0, 0, -0.3F, 0, -0.65F * s);
                base.box(n + "_base", -1.5F, -3, -1.5F, 3, 4, 3, p -> crest(p, SEMI, SEMI_DARK, 0.18));
                Part tip = base.child(n + "_tip", 0, -2.8F, 0, -0.25F, 0, -0.3F * s);
                tip.box(n + "_tip", -1, -3, -1, 2, 3, 2, p -> crestTip(p, SEMI, SEMI_DARK));
            }
        }

        /**
         * Perfect Cell: two broad plates (deeper front-to-back than wide) from the sides of the head behind the brow,
         * rising up and back in a V - the black shell with the lighter green ridge along its front edge and top.
         */
        static void crestPerfect(Part head) {
            Part g = head.child("crest", 0, -8.5F, 0.5F);
            for (int s = -1; s <= 1; s += 2) {
                String n = s < 0 ? "cp_r" : "cp_l";
                Part base = g.child(n, 3.3F * s, 0, 0, -0.35F, 0, -0.3F * s);
                base.box(n + "_base", -1, -3, -2, 2, 4, 4, p -> shell(p, PERFECT, PERFECT_DARK, SHELL));
                Part mid = base.child(n + "_mid", 0, -2.8F, -0.5F, -0.15F, 0, -0.15F * s);
                mid.box(n + "_mid", -1, -3, -1.5F, 2, 3, 3, p -> shell(p, PERFECT, PERFECT_DARK, SHELL));
                Part tip = mid.child(n + "_tip", 0, -2.8F, -0.5F, -0.1F, 0, -0.1F * s);
                tip.box(n + "_tip", -0.5F, -2, -1, 1, 2, 2, p -> shell(p, PERFECT, PERFECT_DARK, SHELL));
            }
        }

        /** Cell Jr.: the perfect crest at child size. */
        static void crestJr(Part head) {
            Part g = head.child("crest_jr", 0, -8.5F, 0.5F);
            for (int s = -1; s <= 1; s += 2) {
                String n = s < 0 ? "cj_r" : "cj_l";
                Part base = g.child(n, 3.3F * s, 0, 0, -0.3F, 0, -0.3F * s);
                base.box(n + "_base", -1, -2, -1.5F, 2, 3, 3, p -> shell(p, PERFECT, PERFECT_DARK, JR_DARK));
                Part tip = base.child(n + "_tip", 0, -1.8F, -0.5F, -0.2F, 0, -0.15F * s);
                tip.box(n + "_tip", -0.5F, -3, -1, 1, 3, 2, p -> shell(p, PERFECT, PERFECT_DARK, JR_DARK));
            }
        }

        /** Green shell with black speckles; darker underneath; the top face a touch lighter where the light lands. */
        static int crest(Px p, int green, int dark, double speckle) {
            double n = p.noise();
            int c = n < speckle ? SPOT : n < speckle + 0.12 ? dark : green;
            if (p.face == Face.BOTTOM) {
                c = shade(c, 0.72);
            } else if (p.face == Face.TOP) {
                c = shade(c, 1.08);
            } else if (p.face == Face.BACK) {
                c = shade(c, 0.9);
            }
            return grain(p, c, 0.04);
        }

        /** The last segment: speckled green darkening to a black point. */
        static int crestTip(Px p, int green, int dark) {
            int c = crest(p, green, dark, 0.15);
            if (p.face == Face.TOP || p.side() && p.j == 0) {
                return SPOT;
            }
            if (p.side() && p.fy < 0.45) {
                c = mix(c, dark, 0.5);
            }
            return c;
        }

        /**
         * Perfect Cell's crest and the Cell Jr.'s: a near-black shell, a lighter green ridge running up the front
         * edge and over the top, a darker green rim down the back edge.
         */
        static int shell(Px p, int ridge, int ridgeDark, int black) {
            double n = p.noise();
            if (p.face == Face.FRONT || p.face == Face.TOP) {
                int c = p.face == Face.TOP && p.fy > 0.6 ? ridgeDark : ridge;
                return grain(p, c, 0.05);
            }
            if ((p.face == Face.RIGHT || p.face == Face.LEFT) && p.i == (p.face == Face.RIGHT ? p.fw - 1 : 0)) {
                return grain(p, ridgeDark, 0.05); // the ridge wraps the front edge
            }
            if (p.face == Face.BACK && p.edge() == 0 && p.fw > 1) {
                return grain(p, mix(black, ridgeDark, 0.5), 0.05);
            }
            int c = n < 0.15 ? SHELL_LIGHT : black;
            if (p.face == Face.BOTTOM) {
                c = shade(c, 0.8);
            }
            return grain(p, c, 0.05);
        }

        // ---------------------------------------------------------------- wings

        /**
         * Folded wing cases on the upper back: each a plate from the shoulder blade (inner edge at the spine, outer
         * edge swung back by WING_SPREAD), a thick fold ridge down the inner edge, a narrower lower plate splayed
         * outward and a short tip - black-green with green rims.
         */
        static void wings(Part body) {
            Part g = body.child("wings", 0, 1.0F, 2.25F);
            for (int s = -1; s <= 1; s += 2) {
                String n = s < 0 ? "wing_r" : "wing_l";
                Part wing = g.child(n, 0.25F * s, 0, 0, 0, WING_SPREAD * -s, 0.1F * -s);
                float x0 = s < 0 ? -5 : 0;
                wing.box(n + "_plate", x0, 0, 0, 5, 7, 1, CellBuuParts::wing)
                        .box(n + "_fold", s < 0 ? -1.5F : 0.5F, -0.5F, 0.5F, 1, 8, 2, CellBuuParts::wingFold)
                        .box(n + "_shoulder", s < 0 ? -4 : 1, -0.5F, 0.25F, 3, 1, 1, CellBuuParts::wingFold);
                Part low = wing.child(n + "_low", (s < 0 ? -3.5F : 3.5F), 7, 0, 0.05F, 0, 0.3F * -s);
                low.box(n + "_low", -1, -0.5F, 0, 2, 4, 1, CellBuuParts::wing);
                Part tip = low.child(n + "_tip", (s < 0 ? -0.5F : 0.5F), 3.5F, 0, 0.05F, 0, 0.3F * -s);
                tip.box(n + "_tip", -0.5F, -0.5F, 0.25F, 1, 2, 1, CellBuuParts::wingFold);
            }
        }

        /** Wing plate: black-green shell, lighter toward the shoulder, a green rim all round the visible (back) face. */
        static int wing(Px p) {
            if (p.face == Face.BACK) {
                if (p.edge() == 0) {
                    return grain(p, p.j == p.fh - 1 ? SHELL_RIM : PERFECT_DARK, 0.06);
                }
                int c = mix(SHELL_LIGHT, SHELL, p.fy);
                // Wing veins: faint lighter diagonals.
                if ((p.i + p.j) % 3 == 0 && p.noise() < 0.6) {
                    c = shade(c, 1.25);
                }
                return grain(p, c, 0.05);
            }
            if (p.face == Face.FRONT) {
                return grain(p, SHELL, 0.03);
            }
            return grain(p, SHELL_RIM, 0.06); // the edges read green
        }

        /** The fold ridge and tips: green-black with a light centre line. */
        static int wingFold(Px p) {
            if (p.face == Face.BACK || p.face == Face.TOP) {
                return grain(p, p.noise() < 0.3 ? SHELL_LIGHT : PERFECT_DARK, 0.06);
            }
            return grain(p, SHELL, 0.05);
        }

        // ---------------------------------------------------------------- tail

        /**
         * Imperfect Cell's tail from the small of the back: hangs back and down, then curls up - four tube segments
         * tapering 3x3 to 2x2, the stinger bulb and its needle. Green with a black band at each joint.
         */
        static void tail(Part body) {
            Part g = body.child("tail", 0, 10, 2.25F, TAIL_BEND[0], 0, 0);
            g.box("tail_1", -1.5F, -1.5F, 0, 3, 3, 4, p -> tail(p, 4));
            Part s2 = g.child("tail_2", 0, 0, 4, TAIL_BEND[1], 0, 0);
            s2.box("tail_2", -1.5F, -1.5F, -0.5F, 3, 3, 4, p -> tail(p, 3.5F));
            Part s3 = s2.child("tail_3", 0, 0, 3.5F, TAIL_BEND[2], 0, 0);
            s3.box("tail_3", -1, -1, -0.5F, 2, 2, 4, p -> tail(p, 3.5F));
            Part s4 = s3.child("tail_4", 0, 0, 3.5F, TAIL_BEND[3], 0, 0);
            s4.box("tail_4", -1, -1, -0.5F, 2, 2, 4, p -> tail(p, 3.5F));
            Part s5 = s4.child("tail_5", 0, 0, 3.5F, TAIL_BEND[4], 0, 0);
            s5.box("tail_5", -1, -1, -0.5F, 2, 2, 3, p -> tail(p, 2.5F));
            Part bulb = s5.child("tail_bulb", 0, 0, 2.5F, TAIL_BEND[5], 0, 0);
            bulb.box("tail_bulb", -1.5F, -1.5F, 0, 3, 3, 3, CellBuuParts::bulb)
                    .box("tail_needle", -0.5F, -0.5F, 3, 1, 1, 2, p -> grain(p, SPOT, 0.08));
        }

        /** A tail segment: green, speckled, a black band in the last pixel before the joint, darker underneath. */
        static int tail(Px p, float end) {
            double n = p.noise();
            int c = n < 0.1 ? SPOT : n < 0.2 ? IMP_DARK : IMP;
            if (p.face != Face.FRONT && p.face != Face.BACK && p.z > p.box.z + end - 1.0) {
                c = SPOT; // the band
            }
            if (p.face == Face.BOTTOM) {
                c = shade(c, 0.75);
            } else if (p.face == Face.TOP) {
                c = shade(c, 1.08);
            }
            return grain(p, c, 0.05);
        }

        /** The stinger bulb: black shell with green flecks, the needle's root green. */
        static int bulb(Px p) {
            double n = p.noise();
            int c = n < 0.2 ? IMP_DARK : n < 0.3 ? IMP : SPOT;
            if (p.face == Face.BACK && p.edge() == 0) {
                c = IMP_DARK;
            }
            return grain(p, c, 0.05);
        }

        // ---------------------------------------------------------------- Buu's antennae

        /** Fat Majin Buu: a short thick tentacle from the crown, bending forward and drooping at the tip. */
        static void antennaFat(Part head) {
            Part g = head.child("antenna_fat", 0, -8.5F, 0, 0.3F, 0, 0);
            g.box("af_base", -1.5F, -2.5F, -1.5F, 3, 4, 3, p -> buu(p, FAT, FAT_DARK, FAT_LINE));
            Part mid = g.child("af_mid", 0, -2.3F, 0, 0.6F, 0, 0);
            mid.box("af_mid", -1, -2.5F, -1, 2, 3, 2, p -> buu(p, FAT, FAT_DARK, FAT_LINE));
            Part tip = mid.child("af_tip", 0, -2.3F, 0, 0.8F, 0, 0);
            tip.box("af_tip", -1, -2, -1, 2, 2, 2, p -> buu(p, FAT, FAT_DARK, FAT_LINE));
        }

        /** Super Buu: long and thin, swept back from the forehead, then rising at the end. */
        static void antennaSuper(Part head) {
            Part g = head.child("antenna_super", 0, -8.5F, -1, -0.9F, 0, 0);
            g.box("as_base", -1, -2.5F, -1, 2, 4, 2, p -> buu(p, BUU, BUU_DARK, BUU_LINE));
            Part mid = g.child("as_mid", 0, -2.3F, 0, 0.45F, 0, 0);
            mid.box("as_mid", -1, -3, -1, 2, 3, 2, p -> buu(p, BUU, BUU_DARK, BUU_LINE));
            Part tip = mid.child("as_tip", 0, -2.8F, 0, 0.55F, 0, 0);
            tip.box("as_tip", -0.5F, -3.5F, -0.5F, 1, 4, 1, p -> buu(p, BUU, BUU_DARK, BUU_LINE));
        }

        /** Kid Buu: medium, rising nearly straight and curling back over the head. */
        static void antennaKid(Part head) {
            Part g = head.child("antenna_kid", 0, -8.5F, -0.5F, -0.1F, 0, 0);
            g.box("ak_base", -1, -2.5F, -1, 2, 4, 2, p -> buu(p, BUU, BUU_DARK, BUU_LINE));
            Part mid = g.child("ak_mid", 0, -2.3F, 0, -0.55F, 0, 0);
            mid.box("ak_mid", -1, -3, -1, 2, 3, 2, p -> buu(p, BUU, BUU_DARK, BUU_LINE));
            Part tip = mid.child("ak_tip", 0, -2.8F, 0, -0.9F, 0, 0);
            tip.box("ak_tip", -0.5F, -2.5F, -0.5F, 1, 3, 1, p -> buu(p, BUU, BUU_DARK, BUU_LINE));
        }

        /** Rubbery pink: lit from the front and top, darker behind and below, a crease where each segment meets the next. */
        static int buu(Px p, int pink, int dark, int line) {
            int c = pink;
            if (p.face == Face.BOTTOM) {
                c = shade(dark, 0.9);
            } else if (p.face == Face.BACK) {
                c = dark;
            } else if (p.face == Face.RIGHT || p.face == Face.LEFT) {
                c = p.fx < 0.5 ? pink : mix(pink, dark, 0.5);
            } else if (p.face == Face.TOP) {
                c = shade(pink, 1.04);
            }
            if (p.side() && p.j == p.fh - 1 && p.fh > 2) {
                c = mix(c, line, 0.6); // crease at the joint
            }
            return grain(p, c, 0.03);
        }
    }

    // ================================================================ weapons (item models)

    static final class Weapons {
        /** One JSON element: from/to in item pixels, an optional rotation, painted per texel. */
        static final class El {
            final String name;
            final double[] from, to;
            final Paint paint;
            String axis;
            double angle;
            double[] origin;
            final Map<String, int[]> uv = new LinkedHashMap<>();
            int seed;

            El(String name, double[] from, double[] to, Paint paint) {
                this.name = name;
                this.from = from;
                this.to = to;
                this.paint = paint;
                this.seed = name.hashCode();
            }

            El rot(String axis, double angle, double ox, double oy, double oz) {
                this.axis = axis;
                this.angle = angle;
                this.origin = new double[]{ox, oy, oz};
                return this;
            }
        }

        /** A weapon: its elements, where the hand grips it, and how it relates to a vanilla sprite. */
        static final class W {
            final String id;
            final List<El> els = new ArrayList<>();
            double[] grip = {8, 4, 8};
            /** Extra roll from the upright model into the sprite frame: -45 for anything held like a sword. */
            double preRoll = -45;
            double handScale = 1.0;
            String ref = "sword";
            /** Fixed rotation overrides (third and first person) for things that aren't held like a blade. */
            double[] thirdRot, firstRot;
            Map<String, double[][]> rawDisplay;
            int density = 2;
            /** Item model overrides (predicate id, value, model) written into the outer model (2026-10-12: the ocarina). */
            final List<String[]> overrides = new ArrayList<>();

            W(String id) {
                this.id = id;
            }

            El add(String name, double x0, double y0, double z0, double x1, double y1, double z1, Paint paint) {
                El e = new El(id + "." + name, new double[]{x0, y0, z0}, new double[]{x1, y1, z1}, paint);
                els.add(e);
                return e;
            }

            /** A box centred on the item's long axis (x = z = 8). */
            El rod(String name, double w, double d, double y0, double y1, Paint paint) {
                return add(name, 8 - w / 2, y0, 8 - d / 2, 8 + w / 2, y1, 8 + d / 2, paint);
            }
        }

        static void buildAll() throws IOException {
            List<W> all = List.of(trunksSword(), masterSword(), mjolnir(), captainShield(), widowBaton(), hawkeyeBow(),
                    shieldPistol(), greenDragonBlade(), serpentSpear(), xiahouBroadsword(), belledSabre(), twinSwords(),
                    yitianSword(), guDingDao(), bishopStaff(), cannon(), monkeyPistol(), monkeyMachineGun(), c4(), ocarina());
            List<BufferedImage> previews = new ArrayList<>();
            for (W w : all) {
                BufferedImage tex = packAndPaint(w);
                write(tex, asset("textures/item/3d/" + w.id + ".png"));
                File flat = asset("textures/item/" + w.id + ".png");
                if (!flat.exists()) {
                    // No hand-drawn inventory sprite yet: render one from the model, laid diagonally like a sword.
                    write(icon(w, tex), flat);
                }
                writeJson(w, tex.getWidth(), tex.getHeight());
                previews.add(preview(w, tex));
                System.out.println("weapon " + w.id + ": " + w.els.size() + " elements, texture " + tex.getWidth() + "x" + tex.getHeight());
            }
            // Contact sheet, 7 per row.
            List<BufferedImage> rows = new ArrayList<>();
            for (int i = 0; i < previews.size(); i += 7) {
                rows.add(Preview.join(previews.subList(i, Math.min(previews.size(), i + 7)), true));
            }
            write(Preview.join(rows, false), reference("weapons_preview.png"));
        }

        // ------------------------------------------------------------ materials

        static final int STEEL = 0xFFD5DBE3, STEEL_MID = 0xFFA9B2BD, STEEL_DARK = 0xFF6E7681, EDGE = 0xFFF4F7FA;
        static final int BRASS = 0xFFC2963E, BRASS_DARK = 0xFF8A6624, GOLD = 0xFFE3B53A, GOLD_DARK = 0xFFA27B1C;
        static final int WOOD = 0xFF6B4423, WOOD_DARK = 0xFF4A2E17, LEATHER = 0xFF6E4127, LEATHER_DARK = 0xFF4A2A18;
        static final int BLACK = 0xFF1F2024, BLACK_LIGHT = 0xFF34363C, RED = 0xFFB3262A, RED_DARK = 0xFF7A1418;
        static final int JADE = 0xFF2F8A5A, JADE_DARK = 0xFF1E5C3B, PURPLE = 0xFF4E2C78, PURPLE_LIGHT = 0xFF7448A8;
        static final int BLUE = 0xFF3E5EC8, BLUE_DARK = 0xFF263C8A, ZAP = 0xFF7FE6FF, ZAP_CORE = 0xFFE8FDFF;

        static Paint metal(int base) {
            return p -> {
                int c = grain(p, base, 0.04);
                return p.edge() == 0 && p.fw > 2 && p.fh > 2 ? shade(c, 0.8) : c;
            };
        }

        /** A blade: bright edges toward ±x, a darker fuller down the middle of the flat. */
        static Paint blade(double halfWidth, boolean fuller) {
            return p -> {
                double ax = Math.abs(p.x - 8);
                if (p.face == Face.RIGHT || p.face == Face.LEFT) {
                    return EDGE;
                }
                if (ax > halfWidth - 0.55) {
                    return EDGE;
                }
                if (fuller && ax < 0.35) {
                    return STEEL_MID;
                }
                return grain(p, STEEL, 0.03);
            };
        }

        /** A single-edged blade: edge toward +x, thick back toward -x. */
        static Paint saber(double edgeX) {
            return p -> {
                if (p.face == Face.LEFT || p.x > edgeX - 0.55 && (p.face == Face.FRONT || p.face == Face.BACK)) {
                    return EDGE;
                }
                if (p.face == Face.RIGHT || p.face == Face.TOP) {
                    return STEEL_MID;
                }
                return grain(p, STEEL, 0.03);
            };
        }

        static Paint wrap(int light, int dark) {
            return p -> ((int) Math.floor(p.y * 2) + (p.face == Face.RIGHT || p.face == Face.LEFT ? (int) Math.floor(p.z * 2) : (int) Math.floor(p.x * 2))) % 2 == 0
                    ? light : dark;
        }

        static Paint wood(int base) {
            return p -> {
                int c = grain(p, base, 0.05);
                return (int) Math.floor(p.y * 1.3 + (p.side() ? p.i : 0) * 0.4) % 4 == 0 ? shade(c, 0.85) : c;
            };
        }

        static Paint flat(int c) {
            return p -> grain(p, c, 0.04);
        }

        // ------------------------------------------------------------ designs

        static W trunksSword() {
            W w = new W("trunks_sword");
            w.grip = new double[]{8, -3, 8};
            w.handScale = 0.9;
            w.rod("pommel", 2, 2, -7, -6, metal(BRASS));
            w.rod("grip", 1.2, 1.2, -6, 0, wrap(0xFF8E3B22, 0xFF5A2213));
            w.rod("guard", 6, 2, 0, 1.5, metal(BRASS));
            w.rod("collar", 3, 1.4, 1.5, 2.5, metal(BRASS_DARK));
            w.rod("blade", 2.6, 0.6, 2.5, 25, blade(1.3, true));
            w.rod("tip_a", 1.8, 0.5, 25, 26.5, blade(0.9, false));
            w.rod("tip_b", 0.8, 0.4, 26.5, 27.5, blade(0.4, false));
            return w;
        }

        static W masterSword() {
            W w = new W("master_sword");
            // While Link plays his song the sword in hand is drawn as the ocarina (client: HeroSongClient).
            w.overrides.add(new String[]{"flightsuit:ocarina", "1", "flightsuit:item/ocarina"});
            w.grip = new double[]{8, -2.5, 8};
            w.rod("pommel", 2, 2, -6.5, -5.5, metal(0xFF5A4FB0));
            w.rod("grip", 1.2, 1.2, -5.5, 0, wrap(0xFF4B3C9E, 0xFF2E2470));
            w.rod("guard", 3, 1.6, 0, 1.5, metal(0xFF5A4FB0));
            w.add("wing_right", 4.0, 0.2, 7.3, 7.0, 1.4, 8.7, metal(0xFF5A4FB0)).rot("z", -22.5, 7, 0.8, 8);
            w.add("wing_left", 9.0, 0.2, 7.3, 12.0, 1.4, 8.7, metal(0xFF5A4FB0)).rot("z", 22.5, 9, 0.8, 8);
            w.rod("gem", 1.0, 1.9, 0.4, 1.2, flat(0xFFF2D54A));
            w.rod("blade", 2.4, 0.6, 1.5, 21, (Paint) p -> {
                // The Triforce at the base of the blade.
                if ((p.face == Face.FRONT || p.face == Face.BACK) && p.y < 3.5 && Math.abs(p.x - 8) < 0.6) {
                    return GOLD;
                }
                return blade(1.2, true).at(p);
            });
            w.rod("tip_a", 1.6, 0.5, 21, 22.5, blade(0.8, false));
            w.rod("tip_b", 0.7, 0.4, 22.5, 23.5, blade(0.35, false));
            return w;
        }

        /**
         * M18 the bishop's staff: a long white shaft bound in gold, a winged gold head cradling a glowing blue-white
         * orb (the Maple Bishop's), held upright like a sword's hilt but gripped low.
         */
        static W bishopStaff() {
            W w = new W("bishop_staff");
            w.grip = new double[]{8, 2, 8};
            w.handScale = 0.95;
            int ivory = 0xFFF4F1E8, ivoryDark = 0xFFCFC9B8, orb = 0xFFBFF4FF, orbCore = 0xFFFFFFFF, wing = 0xFFF8F8FF;
            w.rod("ferrule", 1.6, 1.6, -6, -4.5, metal(GOLD));
            w.rod("shaft", 1.2, 1.2, -4.5, 17, (Paint) p -> {
                // Gold bindings every few pixels up the ivory shaft.
                int band = (int) Math.floor(p.y + 6);
                return band % 6 == 0 ? GOLD : grain(p, p.edge() == 0 ? ivoryDark : ivory, 0.03);
            });
            w.rod("collar", 2.4, 2.4, 17, 18.5, metal(GOLD));
            w.rod("cradle", 1.4, 1.4, 18.5, 21, metal(GOLD_DARK));
            // Wings sweeping up and out from the collar.
            w.add("wing_right", 3.4, 18.5, 7.4, 6.6, 24.5, 8.6, flat(wing)).rot("z", 22.5, 6.6, 18.5, 8);
            w.add("wing_left", 9.4, 18.5, 7.4, 12.6, 24.5, 8.6, flat(wing)).rot("z", -22.5, 9.4, 18.5, 8);
            w.add("wing_tip_right", 2.6, 23.5, 7.5, 4.6, 27, 8.5, flat(wing)).rot("z", 45, 4.6, 23.5, 8);
            w.add("wing_tip_left", 11.4, 23.5, 7.5, 13.4, 27, 8.5, flat(wing)).rot("z", -45, 11.4, 23.5, 8);
            w.rod("orb", 3, 3, 21, 24, (Paint) p -> p.edge() == 0 ? orb : orbCore);
            w.rod("orb_cap", 1.6, 1.6, 24, 25, metal(GOLD));
            return w;
        }

        /**
         * M18 the cannon: a stout bronze barrel with iron hoops and a flared muzzle, a grip under it and a lit fuse at
         * the breech - carried on the shoulder, barrel pointing where the gunner looks.
         */
        static W cannon() {
            W w = new W("cannon");
            w.grip = new double[]{8, 3, 8};
            w.handScale = 1.0;
            w.preRoll = 0;
            int bronze = 0xFF9A6A2E, bronzeDark = 0xFF6B4720, bronzeLight = 0xFFC08A44, iron = 0xFF4A4C52, fuse = 0xFFFFB347;
            // Upright in model space (+y toward the muzzle); the display transforms lay it along the shoulder.
            w.rod("breech", 4.4, 4.4, -2, 1, metal(iron));
            w.rod("knob", 2, 2, -3.5, -2, metal(iron));
            w.rod("barrel", 4, 4, 1, 14, (Paint) p -> grain(p, p.edge() == 0 ? bronzeDark : bronze, 0.04));
            w.rod("hoop_a", 4.6, 4.6, 3, 4, metal(iron));
            w.rod("hoop_b", 4.6, 4.6, 8, 9, metal(iron));
            w.rod("muzzle", 5, 5, 14, 16.5, (Paint) p -> p.face == Face.TOP ? 0xFF1A1410 : grain(p, bronzeLight, 0.04));
            w.rod("bore", 3, 3, 16.5, 16.8, flat(0xFF1A1410));
            w.add("grip", 7, 0, 10, 9, 3, 11.5, wrap(LEATHER, LEATHER_DARK));
            w.add("fuse", 7.7, 0.5, 5.2, 8.3, 3, 5.8, flat(0xFF3A2416));
            w.add("fuse_tip", 7.6, 3, 5.1, 8.4, 3.8, 5.9, flat(fuse));
            // Shouldered: barrel forward and a little up in third person, held out front in first.
            w.thirdRot = new double[]{-80, 0, 0};
            w.firstRot = new double[]{-70, 10, 0};
            return w;
        }

        // ------------------------------------------------------------ 2026-10-12: props for the suit roles round 2

        /** The Cannon Shooter monkey's pistol: a stubby revolver - held like the S.H.I.E.L.D. pistol. */
        static W monkeyPistol() {
            W w = new W("monkey_pistol");
            w.grip = new double[]{4.2, 5.2, 8};
            w.handScale = 0.8;
            w.thirdRot = new double[]{0, -90, 90};
            w.firstRot = new double[]{0, -90, 90};
            w.add("barrel", 7.3, 8, 7.4, 8.9, 14, 8.6, (Paint) p -> p.face == Face.TOP ? 0xFF0B0B0D : metal(STEEL_MID).at(p));
            w.add("cylinder", 6.6, 5.6, 6.9, 9.4, 8.4, 9.1, (Paint) p -> {
                // Flutes round the cylinder.
                if (p.side() && (int) Math.floor((p.face == Face.RIGHT || p.face == Face.LEFT ? p.z : p.x) * 2) % 2 == 0) {
                    return STEEL_DARK;
                }
                return grain(p, STEEL, 0.04);
            });
            w.add("frame", 6, 4.4, 7.3, 9, 5.6, 8.7, flat(STEEL_DARK));
            w.add("hammer", 8.6, 4.4, 7.7, 9.6, 5.4, 8.3, flat(BLACK_LIGHT));
            w.add("grip", 2, 3.6, 7.2, 6, 6.2, 8.8, wood(WOOD)).rot("z", 22.5, 6, 5, 8);
            w.add("trigger_guard", 5.2, 6.0, 7.7, 6.2, 7.8, 8.3, flat(STEEL_DARK));
            return w;
        }

        /**
         * The monkey's machine gun (X): a long barrel in a cooling jacket, a drum magazine under the body, a carry
         * handle on top - held the same way as the pistol, but it's a handful.
         */
        static W monkeyMachineGun() {
            W w = new W("monkey_machine_gun");
            w.grip = new double[]{3.6, 4.6, 8};
            w.handScale = 0.85;
            w.thirdRot = new double[]{0, -90, 90};
            w.firstRot = new double[]{0, -90, 90};
            w.add("stock", 6.6, -3, 7.2, 9.4, 3, 8.8, wood(WOOD_DARK));
            w.add("body", 6, 3, 6.8, 10, 10, 9.2, metal(BLACK_LIGHT));
            w.add("jacket", 6.9, 10, 6.9, 9.1, 19, 9.1, (Paint) p -> {
                // Cooling holes down the jacket.
                if (p.side() && (int) Math.floor(p.y) % 2 == 0 && Math.abs((p.face == Face.RIGHT || p.face == Face.LEFT ? p.z : p.x) - 8) < 0.5) {
                    return 0xFF0B0B0D;
                }
                return grain(p, BLACK, 0.04);
            });
            w.add("muzzle", 7.3, 19, 7.3, 8.7, 21, 8.7, (Paint) p -> p.face == Face.TOP ? 0xFF0B0B0D : metal(STEEL_DARK).at(p));
            w.add("drum", 2.4, 5, 6.2, 6, 9, 9.8, (Paint) p -> {
                double r = Math.hypot(p.y - 7, (p.face == Face.FRONT || p.face == Face.BACK ? p.x : p.z) - 8);
                return r < 0.8 ? STEEL_DARK : grain(p, 0xFF3C4A2E, 0.05);
            });
            w.add("handle", 10, 5, 7.6, 11.2, 9, 8.4, flat(BLACK));
            w.add("grip", 2, 2.6, 7.2, 6, 4.8, 8.8, wrap(BLACK, 0xFF2A2C31)).rot("z", 22.5, 6, 3.6, 8);
            return w;
        }

        /**
         * The Cannon Shooter's C4 (B): a brick of olive putty bound with black tape, a little timer with green digits
         * and a red light on top, a red and a blue wire. Drawn on its own (CannonArts' C4Entity), not held.
         */
        static W c4() {
            W w = new W("c4");
            w.preRoll = 0;
            w.grip = new double[]{8, 8, 8};
            int putty = 0xFFB8B08A, puttyDark = 0xFF8C8566;
            w.add("brick", 4, 6.5, 5.5, 12, 9.5, 10.5, (Paint) p -> {
                if (p.face == Face.FRONT && p.y > 7.3 && p.y < 8.4 && p.x > 5 && p.x < 11 && ((int) Math.floor(p.x * 2) % 3 != 0)) {
                    return 0xFF3A3626; // stencilled lettering
                }
                return grain(p, p.edge() == 0 ? puttyDark : putty, 0.05);
            });
            w.add("tape_a", 5, 6.3, 5.3, 6.2, 9.7, 10.7, flat(BLACK_LIGHT));
            w.add("tape_b", 9.8, 6.3, 5.3, 11, 9.7, 10.7, flat(BLACK_LIGHT));
            w.add("timer", 6.4, 9.5, 6.6, 9.6, 10.6, 9.4, (Paint) p -> p.face == Face.TOP && Math.abs(p.z - 7.6) < 0.5 && p.x > 6.8 && p.x < 9.2
                    ? ((int) Math.floor(p.x * 2) % 2 == 0 ? 0xFF4CFF6A : 0xFF0E3A16) : grain(p, BLACK, 0.04));
            w.add("light", 8.1, 10.6, 8.2, 8.9, 11.2, 9.0, flat(0xFFFF2A2A));
            w.add("wire_red", 9.6, 9.6, 7.2, 11.2, 10.1, 7.7, flat(RED));
            w.add("wire_blue", 9.6, 9.6, 8.4, 11.6, 10.1, 8.9, flat(BLUE));
            return w;
        }

        /**
         * Link's ocarina (his song, B): the blue Ocarina of Time - a plump body, a mouthpiece, finger holes and the
         * Triforce in gold. Not an item of its own: the Master Sword's model while he plays.
         */
        static W ocarina() {
            W w = new W("ocarina");
            w.preRoll = 0;
            w.grip = new double[]{6, 7, 8};
            w.handScale = 0.9;
            w.thirdRot = new double[]{0, -90, 0};
            w.firstRot = new double[]{0, -90, 0};
            int blue = 0xFF3A6FD8, blueDark = 0xFF24479A, blueLight = 0xFF6E9BF0;
            Paint body = p -> {
                if (p.face == Face.TOP && Math.hypot((p.x - 6.5) % 1.6, p.z - 8) < 0.35 && p.x > 5.5 && p.x < 10.5) {
                    return 0xFF101A33; // finger holes
                }
                if ((p.face == Face.FRONT || p.face == Face.BACK) && Math.abs(p.x - 8) < 0.9 && p.y > 6.8 && p.y < 8.2) {
                    return GOLD; // the Triforce
                }
                return grain(p, p.edge() == 0 ? blueDark : blue, 0.04);
            };
            w.add("body", 5, 6, 6.6, 11, 9, 9.4, body);
            w.add("hump", 6, 9, 7, 10, 9.8, 9, flat(blueLight));
            w.add("belly", 6, 5.4, 7, 10, 6, 9, flat(blueDark));
            w.add("mouthpiece", 11, 7, 7.4, 13, 8.2, 8.6, flat(blueLight));
            return w;
        }

        static W mjolnir() {
            W w = new W("mjolnir");
            w.grip = new double[]{8, 4, 8};
            w.handScale = 1.0;
            w.rod("loop", 0.4, 0.4, -4.5, -2, flat(LEATHER_DARK));
            w.rod("pommel", 2, 2, -2, -1, metal(0xFFB8BEC6));
            w.rod("handle", 1.6, 1.6, -1, 11, wrap(LEATHER, LEATHER_DARK));
            w.rod("collar", 2.2, 2.2, 11, 12, metal(0xFFB8BEC6));
            // The head lies across the swing: long along x.
            Paint head = p -> {
                int c = grain(p, 0xFF9AA2AC, 0.04);
                if (p.face == Face.RIGHT || p.face == Face.LEFT) {
                    // Asgardian knot on the striking faces.
                    double r = Math.hypot(p.z - 8, p.y - 14.5);
                    if (Math.abs(r - 1.4) < 0.35 || r < 0.4) {
                        return 0xFF5E656E;
                    }
                }
                return p.edge() == 0 ? shade(c, 0.85) : c;
            };
            w.add("head", 4, 12, 5.5, 12, 17, 10.5, head);
            w.add("bevel_top", 4.5, 17, 6, 11.5, 17.5, 10, metal(0xFF8A929C));
            w.add("bevel_bottom", 4.5, 11.5, 6, 11.5, 12, 10, metal(0xFF8A929C));
            return w;
        }

        static W captainShield() {
            W w = new W("captain_shield");
            w.density = 3;
            // Same flat placement as before (centred, z 7.5 - 8.5), now a stepped disc with a rim and a boss.
            Paint face = p -> {
                double r = Math.hypot(p.x - 8, p.y - 8);
                if (p.face == Face.RIGHT || p.face == Face.LEFT || p.face == Face.TOP || p.face == Face.BOTTOM) {
                    return shade(r > 7 ? 0xFFC8CDD3 : 0xFFB22A2E, 0.85);
                }
                if (r > 7.0) {
                    return 0xFFC8CDD3;
                }
                if (r > 5.6) {
                    return 0xFFB22A2E;
                }
                if (r > 4.2) {
                    return 0xFFE9EAEC;
                }
                if (r > 2.9) {
                    return 0xFFB22A2E;
                }
                double a = Math.atan2(p.y - 8, p.x - 8) - Math.PI / 2;
                // Five-pointed star: radius swings between the points and the notches.
                double k = Math.cos(5 * a);
                double edge = 1.15 + 1.45 * Math.pow((k + 1) / 2, 2.2);
                return r < edge ? 0xFFF4F4F2 : 0xFF2E4C9E;
            };
            for (int row = 0; row < 16; row++) {
                double y0 = row, y1 = row + 1;
                double dy = Math.min(Math.abs(y0 - 8), Math.abs(y1 - 8));
                double half = Math.sqrt(Math.max(0, 64 - dy * dy));
                if (half < 1) {
                    continue;
                }
                double bulge = Math.max(0, 0.3 - Math.abs(row + 0.5 - 8) / 26);
                w.add("row" + row, 8 - half, y0, 7.5 - bulge, 8 + half, y1, 8.5 + bulge, face);
            }
            w.rawDisplay = new LinkedHashMap<>();
            w.rawDisplay.put("thirdperson_righthand", new double[][]{{0, 90, 0}, {0, 2, 1}, {0.85, 0.85, 0.85}});
            w.rawDisplay.put("thirdperson_lefthand", new double[][]{{0, 90, 0}, {0, 2, 1}, {0.85, 0.85, 0.85}});
            w.rawDisplay.put("firstperson_righthand", new double[][]{{0, 180, 5}, {-2, 2, -4}, {0.8, 0.8, 0.8}});
            w.rawDisplay.put("firstperson_lefthand", new double[][]{{0, 180, 5}, {2, 2, -4}, {0.8, 0.8, 0.8}});
            w.rawDisplay.put("ground", new double[][]{{0, 0, 0}, {0, 2, 0}, {0.5, 0.5, 0.5}});
            w.rawDisplay.put("fixed", new double[][]{{0, 180, 0}, {0, 0, 0}, {1, 1, 1}});
            return w;
        }

        static W widowBaton() {
            W w = new W("widow_baton");
            w.grip = new double[]{8, 6, 8};
            w.handScale = 0.9;
            Paint zap = p -> p.noise() < 0.3 ? ZAP_CORE : ZAP;
            w.rod("cap_bottom", 1.6, 1.6, -3, -2, flat(BLACK_LIGHT));
            w.rod("tip_bottom", 1.3, 1.3, -2, 0, zap);
            w.rod("shaft", 1.2, 1.2, 0, 12, (Paint) p -> p.y > 3 && p.y < 9 ? wrap(BLACK, BLACK_LIGHT).at(p) : grain(p, BLACK, 0.05));
            w.rod("band", 1.5, 1.5, 9, 10, flat(BLUE));
            w.rod("tip_top", 1.3, 1.3, 12, 14.5, zap);
            w.rod("cap_top", 1.6, 1.6, 14.5, 15.5, flat(BLACK_LIGHT));
            return w;
        }

        static W hawkeyeBow() {
            W w = new W("hawkeye_bow");
            w.ref = "bow";
            w.grip = new double[]{7.3, 8, 8};
            w.handScale = 1.0;
            Paint limb = p -> p.face == Face.RIGHT && p.y > 0 ? flat(PURPLE_LIGHT).at(p) : grain(p, BLACK, 0.05);
            // Riser (grip) with an arrow shelf; limbs sweep back toward -x and recurve at the tips; the string runs
            // straight down the +x side.
            w.add("riser", 6.4, 5, 7.2, 8.2, 11, 8.8, wrap(PURPLE, 0xFF2E1A4A));
            w.add("shelf", 8.2, 9.2, 7.6, 8.8, 9.6, 8.4, flat(BLACK_LIGHT));
            w.add("upper_limb", 6.6, 11, 7.4, 7.8, 17, 8.6, limb).rot("z", 22.5, 7.2, 11, 8);
            w.add("upper_tip", 4.6, 16.2, 7.5, 5.6, 19.2, 8.5, limb).rot("z", -22.5, 5.1, 16.2, 8);
            w.add("lower_limb", 6.6, -1, 7.4, 7.8, 5, 8.6, limb).rot("z", -22.5, 7.2, 5, 8);
            w.add("lower_tip", 4.6, -3.2, 7.5, 5.6, -0.2, 8.5, limb).rot("z", 22.5, 5.1, -0.2, 8);
            w.add("string", 7.4, -2.6, 7.95, 7.6, 18.6, 8.05, flat(0xFFE8E8E8));
            return w;
        }

        static W shieldPistol() {
            W w = new W("shield_pistol");
            // Barrel along +y, the grip out toward -x; held like a pistol in both hands (rotations below).
            w.grip = new double[]{4.2, 5.2, 8};
            w.handScale = 0.8;
            w.thirdRot = new double[]{0, -90, 90};
            w.firstRot = new double[]{0, -90, 90};
            w.add("slide", 7, 4, 7.25, 9.5, 13, 8.75, (Paint) p -> {
                if (p.face == Face.TOP) {
                    return Math.hypot(p.x - 8.3, p.z - 8) < 0.5 ? 0xFF0B0B0D : grain(p, BLACK_LIGHT, 0.04);
                }
                if ((p.face == Face.FRONT || p.face == Face.BACK) && p.y < 6.5 && (int) Math.floor(p.y * 2) % 2 == 0) {
                    return 0xFF101114; // serrations
                }
                return grain(p, BLACK_LIGHT, 0.04);
            });
            w.add("frame", 6, 5, 7.35, 7, 12.5, 8.65, flat(BLACK));
            w.add("grip", 2, 4, 7.3, 6, 6.6, 8.7, (Paint) p -> {
                if ((p.face == Face.FRONT || p.face == Face.BACK) && Math.abs(p.x - 4) < 0.8 && Math.abs(p.y - 5.3) < 0.7) {
                    return 0xFFD9DCE0; // S.H.I.E.L.D. eagle badge
                }
                return wrap(BLACK, 0xFF2A2C31).at(p);
            }).rot("z", 22.5, 6, 5.3, 8);
            w.add("trigger_guard", 5.2, 6.6, 7.7, 6, 8.8, 8.3, flat(BLACK));
            w.add("trigger", 5.4, 7.4, 7.85, 6, 7.8, 8.15, flat(STEEL_DARK));
            return w;
        }

        static W greenDragonBlade() {
            W w = new W("green_dragon_blade");
            w.grip = new double[]{8, 1, 8};
            w.handScale = 1.0;
            w.rod("spike", 0.8, 0.8, -16, -14, metal(GOLD));
            w.rod("ferrule", 1.6, 1.6, -14, -12.5, metal(GOLD_DARK));
            w.rod("pole", 1.2, 1.2, -12.5, 19, wood(0xFF2E4A2A));
            w.rod("collar", 1.8, 1.8, 19, 20, metal(GOLD));
            // The dragon's head holding the blade in its jaws, a red tassel hanging from it.
            w.add("dragon_head", 6.8, 20, 7, 10.2, 23, 9, (Paint) p -> {
                if (p.face == Face.LEFT && p.y > 21.4 && p.y < 22.2 && p.z < 8) {
                    return 0xFFE04030; // eye
                }
                return grain(p, GOLD, 0.06);
            });
            w.add("tassel", 5.6, 16, 7.6, 6.6, 20.5, 8.4, flat(RED));
            w.add("tassel_knot", 5.4, 20.2, 7.4, 6.8, 21, 8.6, flat(RED_DARK));
            // Crescent blade: broad belly toward +x, back spine with a hook toward -x.
            Paint b = saber(13.6);
            w.add("blade_base", 7.4, 22, 7.7, 11.6, 26, 8.3, jadeEtched(b));
            w.add("blade_belly", 8.2, 26, 7.7, 13.6, 30, 8.3, b);
            w.add("blade_tip", 9.6, 29, 7.75, 13.2, 32, 8.25, b).rot("z", 22.5, 11.4, 29, 8);
            w.add("spine_hook", 6.0, 24, 7.75, 7.4, 26.5, 8.25, metal(STEEL_MID));
            return w;
        }

        static Paint jadeEtched(Paint base) {
            return p -> (p.face == Face.FRONT || p.face == Face.BACK) && Math.abs(p.x - 9.3) < 1.1 && p.y < 25.2 && p.y > 22.6
                    ? (p.i % 2 == 0 ? JADE : JADE_DARK) : base.at(p);
        }

        static W serpentSpear() {
            W w = new W("serpent_spear");
            w.grip = new double[]{8, 1, 8};
            w.rod("spike", 0.8, 0.8, -16, -14, metal(STEEL_MID));
            w.rod("pole", 1.2, 1.2, -14, 21, wood(0xFF2A2522));
            w.rod("collar", 1.8, 1.8, 21, 22, metal(BRASS));
            w.add("tassel", 7.0, 18, 6.6, 9.0, 21, 7.2, flat(RED));
            // The snake blade wriggles: offset blocks one way and the other, narrowing to a point.
            Paint b = blade(0.9, true);
            double[][] wiggle = {{0.0, 22, 24}, {0.7, 24, 26}, {-0.5, 26, 28}, {0.5, 28, 30}};
            for (int k = 0; k < wiggle.length; k++) {
                double cx = 8 + wiggle[k][0];
                final double centre = cx;
                w.add("blade_" + k, cx - 0.9, wiggle[k][1], 7.7, cx + 0.9, wiggle[k][2], 8.3,
                        (Paint) p -> {
                            Px q = shifted(p, 8 - centre);
                            return b.at(q);
                        });
            }
            w.rod("point_a", 1.0, 0.5, 30, 31.2, blade(0.5, false));
            w.rod("point_b", 0.4, 0.4, 31.2, 32, blade(0.2, false));
            return w;
        }

        /** The same texel as if its box sat centred on x = 8 (for blade painters on offset boxes). */
        static Px shifted(Px p, double dx) {
            Px q = new Px();
            q.box = p.box;
            q.face = p.face;
            q.fx = p.fx;
            q.fy = p.fy;
            q.i = p.i;
            q.j = p.j;
            q.fw = p.fw;
            q.fh = p.fh;
            q.x = p.x + dx;
            q.y = p.y;
            q.z = p.z;
            return q;
        }

        static W xiahouBroadsword() {
            W w = new W("xiahou_broadsword");
            w.grip = new double[]{8, -4, 8};
            w.rod("pommel", 1.8, 1.8, -12, -11, metal(BRASS));
            w.rod("handle", 1.3, 1.3, -11, 1, wrap(0xFF3C3A60, 0xFF24223E));
            w.rod("guard", 4, 3, 1, 2, metal(BRASS));
            Paint b = saber(10.6);
            // Broad blade, widening toward the tip, the edge sweeping back up to the point.
            w.add("blade", 6.4, 2, 7.65, 10.0, 12, 8.35, b);
            w.add("blade_wide", 6.4, 12, 7.65, 10.6, 18, 8.35, b);
            w.add("blade_tip", 6.4, 18, 7.7, 9.6, 19.5, 8.3, b);
            w.add("blade_point", 6.4, 19.5, 7.75, 8.2, 20.5, 8.25, b);
            w.add("back_ridge", 6.2, 2, 7.8, 6.6, 19.5, 8.2, metal(STEEL_MID));
            return w;
        }

        static W belledSabre() {
            W w = new W("belled_sabre");
            w.grip = new double[]{8, -2.5, 8};
            w.rod("pommel", 1.8, 1.8, -6.5, -5.5, metal(GOLD));
            w.add("bell_a", 6.2, -9, 7.3, 7.6, -7.6, 8.7, metal(GOLD));
            w.add("bell_b", 8.6, -8.4, 7.3, 10.0, -7.0, 8.7, metal(GOLD));
            w.add("cord", 7.6, -7.6, 7.9, 8.6, -6.5, 8.1, flat(RED));
            w.rod("grip", 1.2, 1.2, -5.5, 0, wrap(RED, RED_DARK));
            w.rod("guard", 3.6, 2, 0, 1, metal(GOLD));
            Paint b = saber(9.3);
            w.add("blade_root", 6.9, 1, 7.7, 9.3, 9, 8.3, b);
            w.add("blade_mid", 6.9, 9, 7.72, 9.3, 15, 8.28, b).rot("z", -22.5, 6.9, 9, 8);
            w.add("blade_tip", 8.3, 14, 7.75, 10.1, 18, 8.25, b).rot("z", -45, 8.3, 14, 8);
            return w;
        }

        static W twinSwords() {
            W w = new W("twin_swords");
            w.grip = new double[]{8, -2.5, 8};
            for (int k = 0; k < 2; k++) {
                double angle = k == 0 ? 0 : -22.5;
                double z = k == 0 ? 0 : 0.9;
                String s = k == 0 ? "a" : "b";
                List<El> parts = new ArrayList<>();
                parts.add(w.add("pommel_" + s, 7.1, -6.5, 7.1 + z, 8.9, -5.5, 8.9 + z, metal(GOLD)));
                parts.add(w.add("grip_" + s, 7.4, -5.5, 7.4 + z, 8.6, 0, 8.6 + z, wrap(0xFF2B6B4A, 0xFF1A4430)));
                parts.add(w.add("guard_" + s, 6.3, 0, 7.2 + z, 9.7, 1, 8.8 + z, metal(GOLD)));
                parts.add(w.add("blade_" + s, 7.1, 1, 7.75 + z, 8.9, 18, 8.25 + z, blade(0.9, true)));
                parts.add(w.add("tip_" + s, 7.5, 18, 7.8 + z, 8.5, 19.2, 8.2 + z, blade(0.5, false)));
                if (angle != 0) {
                    for (El e : parts) {
                        e.rot("z", angle, 8, -2.5, 8);
                    }
                }
            }
            return w;
        }

        static W yitianSword() {
            W w = new W("yitian_sword");
            w.grip = new double[]{8, -2.5, 8};
            w.add("tassel", 7.6, -11, 7.6, 8.4, -7, 8.4, flat(0xFFC43A2E));
            w.rod("pommel", 1.8, 1.4, -7, -6, metal(GOLD));
            w.rod("grip", 1.2, 1.0, -6, 0, wrap(BLACK, 0xFF3A2A1A));
            w.rod("guard", 3.6, 1.8, 0, 1.2, metal(GOLD));
            w.rod("guard_cap", 2.4, 1.4, 1.2, 1.7, metal(GOLD_DARK));
            w.rod("blade", 1.8, 0.5, 1.7, 22, blade(0.9, true));
            w.rod("tip_a", 1.1, 0.45, 22, 23.2, blade(0.55, false));
            w.rod("tip_b", 0.4, 0.4, 23.2, 24, blade(0.2, false));
            return w;
        }

        static W guDingDao() {
            W w = new W("gu_ding_dao");
            w.grip = new double[]{8, -2.5, 8};
            // Ring pommel.
            w.add("ring_top", 7, -7, 7.6, 9, -6.5, 8.4, metal(BRASS));
            w.add("ring_bottom", 7, -9, 7.6, 9, -8.5, 8.4, metal(BRASS));
            w.add("ring_right", 6.5, -9, 7.6, 7, -6.5, 8.4, metal(BRASS));
            w.add("ring_left", 9, -9, 7.6, 9.5, -6.5, 8.4, metal(BRASS));
            w.rod("grip", 1.2, 1.2, -6.5, 0, wrap(0xFF7A5A2A, 0xFF4E3A1A));
            w.rod("guard", 3.2, 2.2, 0, 0.8, metal(BRASS));
            Paint b = saber(10.0);
            w.add("blade", 6.8, 0.8, 7.68, 10.0, 18, 8.32, b);
            // Clipped point: the edge rises to meet the back.
            w.add("clip", 6.8, 18, 7.7, 9.0, 20, 8.3, b);
            return w;
        }

        // ------------------------------------------------------------ texture, json, transforms

        static final String[] FACES = {"north", "south", "west", "east", "up", "down"};

        static BufferedImage packAndPaint(W w) {
            int k = w.density;
            // Face sizes in texels.
            List<Object[]> rects = new ArrayList<>();
            for (El e : w.els) {
                double sx = e.to[0] - e.from[0], sy = e.to[1] - e.from[1], sz = e.to[2] - e.from[2];
                for (String f : FACES) {
                    double a, b;
                    switch (f) {
                        case "north", "south" -> { a = sx; b = sy; }
                        case "west", "east" -> { a = sz; b = sy; }
                        default -> { a = sx; b = sz; }
                    }
                    int tw = Math.max(1, (int) Math.ceil(a * k - 1e-6)), th = Math.max(1, (int) Math.ceil(b * k - 1e-6));
                    rects.add(new Object[]{e, f, tw, th});
                }
            }
            rects.sort((p, q) -> (int) q[3] - (int) p[3]);
            int size = 16;
            while (!fits(rects, size)) {
                size *= 2;
            }
            BufferedImage tex = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            int x = 0, y = 0, rowH = 0;
            for (Object[] r : rects) {
                El e = (El) r[0];
                String f = (String) r[1];
                int tw = (int) r[2], th = (int) r[3];
                if (x + tw > size) {
                    x = 0;
                    y += rowH;
                    rowH = 0;
                }
                e.uv.put(f, new int[]{x, y, tw, th});
                paintFace(w, e, f, tex, x, y, tw, th);
                x += tw;
                rowH = Math.max(rowH, th);
            }
            return tex;
        }

        static boolean fits(List<Object[]> rects, int size) {
            int x = 0, y = 0, rowH = 0;
            for (Object[] r : rects) {
                int tw = (int) r[2], th = (int) r[3];
                if (tw > size) {
                    return false;
                }
                if (x + tw > size) {
                    x = 0;
                    y += rowH;
                    rowH = 0;
                }
                x += tw;
                rowH = Math.max(rowH, th);
            }
            return y + rowH <= size;
        }

        /** Paints a face's texels by the point each covers (block-model UV conventions, see BlockElement#uvsByFace). */
        static void paintFace(W w, El e, String f, BufferedImage tex, int ox, int oy, int tw, int th) {
            double x0 = e.from[0], y0 = e.from[1], z0 = e.from[2], x1 = e.to[0], y1 = e.to[1], z1 = e.to[2];
            Box fake = new Box(e.name, 0, 0, 0, 1, 1, 1, 0, null);
            fake.seed = e.seed;
            for (int j = 0; j < th; j++) {
                for (int i = 0; i < tw; i++) {
                    Px p = new Px();
                    p.box = fake;
                    p.i = i;
                    p.j = j;
                    p.fw = tw;
                    p.fh = th;
                    p.fx = (i + 0.5) / tw;
                    p.fy = (j + 0.5) / th;
                    switch (f) {
                        case "north" -> { p.face = Face.FRONT; p.x = x1 - p.fx * (x1 - x0); p.y = y1 - p.fy * (y1 - y0); p.z = z0; }
                        case "south" -> { p.face = Face.BACK; p.x = x0 + p.fx * (x1 - x0); p.y = y1 - p.fy * (y1 - y0); p.z = z1; }
                        case "west" -> { p.face = Face.RIGHT; p.z = z0 + p.fx * (z1 - z0); p.y = y1 - p.fy * (y1 - y0); p.x = x0; }
                        case "east" -> { p.face = Face.LEFT; p.z = z1 - p.fx * (z1 - z0); p.y = y1 - p.fy * (y1 - y0); p.x = x1; }
                        case "up" -> { p.face = Face.TOP; p.x = x0 + p.fx * (x1 - x0); p.z = z0 + p.fy * (z1 - z0); p.y = y1; }
                        default -> { p.face = Face.BOTTOM; p.x = x0 + p.fx * (x1 - x0); p.z = z1 - p.fy * (z1 - z0); p.y = y0; }
                    }
                    tex.setRGB(ox + i, oy + j, e.paint.at(p) | 0xFF000000);
                }
            }
        }

        static void writeJson(W w, int texW, int texH) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"textures\": {\n    \"tex\": \"flightsuit:item/3d/").append(w.id).append("\",\n")
                    .append("    \"particle\": \"flightsuit:item/").append(w.id).append("\"\n  },\n");
            sb.append("  \"gui_light\": \"front\",\n  \"elements\": [\n");
            for (int k = 0; k < w.els.size(); k++) {
                El e = w.els.get(k);
                check(e);
                sb.append("    {\"name\": \"").append(e.name.substring(e.name.indexOf('.') + 1)).append("\", ");
                sb.append("\"from\": ").append(vec(e.from)).append(", \"to\": ").append(vec(e.to)).append(",\n");
                if (e.axis != null) {
                    sb.append("      \"rotation\": {\"angle\": ").append(num(e.angle)).append(", \"axis\": \"").append(e.axis)
                            .append("\", \"origin\": ").append(vec(e.origin)).append("},\n");
                }
                sb.append("      \"faces\": {");
                int n = 0;
                for (String f : FACES) {
                    int[] r = e.uv.get(f);
                    double u1 = r[0] * 16.0 / texW, v1 = r[1] * 16.0 / texH;
                    double u2 = (r[0] + r[2]) * 16.0 / texW, v2 = (r[1] + r[3]) * 16.0 / texH;
                    sb.append(n++ == 0 ? "" : ", ").append("\"").append(f).append("\": {\"uv\": [")
                            .append(num(u1)).append(", ").append(num(v1)).append(", ").append(num(u2)).append(", ").append(num(v2))
                            .append("], \"texture\": \"#tex\"}");
                }
                sb.append("}}").append(k == w.els.size() - 1 ? "\n" : ",\n");
            }
            sb.append("  ],\n  \"display\": {\n");
            Map<String, double[][]> display = w.rawDisplay != null ? w.rawDisplay : display(w);
            int n = 0;
            for (Map.Entry<String, double[][]> d : display.entrySet()) {
                double[][] t = d.getValue();
                sb.append("    \"").append(d.getKey()).append("\": {\"rotation\": ").append(vec(t[0])).append(", \"translation\": ")
                        .append(vec(t[1])).append(", \"scale\": ").append(vec(t[2])).append("}")
                        .append(++n == display.size() ? "\n" : ",\n");
            }
            sb.append("  }\n}\n");
            File model = asset("models/item/3d/" + w.id + ".json");
            model.getParentFile().mkdirs();
            Files.writeString(model.toPath(), sb.toString(), StandardCharsets.UTF_8);

            String outer = "{\n"
                    + "  \"loader\": \"forge:separate_transforms\",\n"
                    + "  \"gui_light\": \"front\",\n"
                    + "  \"base\": {\"parent\": \"flightsuit:item/3d/" + w.id + "\"},\n"
                    + "  \"perspectives\": {\n"
                    + "    \"gui\": {\"parent\": \"minecraft:item/generated\", \"textures\": {\"layer0\": \"flightsuit:item/" + w.id + "\"}}\n"
                    + "  }" + overrides(w) + "\n"
                    + "}\n";
            Files.writeString(asset("models/item/" + w.id + ".json").toPath(), outer, StandardCharsets.UTF_8);
        }

        static String overrides(W w) {
            if (w.overrides.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder(",\n  \"overrides\": [\n");
            for (int i = 0; i < w.overrides.size(); i++) {
                String[] o = w.overrides.get(i);
                sb.append("    {\"predicate\": {\"").append(o[0]).append("\": ").append(o[1]).append("}, \"model\": \"").append(o[2]).append("\"}")
                        .append(i == w.overrides.size() - 1 ? "\n" : ",\n");
            }
            return sb.append("  ]").toString();
        }

        static void check(El e) {
            for (int a = 0; a < 3; a++) {
                if (e.from[a] < -16 || e.to[a] > 32 || e.from[a] > e.to[a]) {
                    throw new IllegalStateException(e.name + " is outside the -16..32 element range");
                }
            }
            if (e.axis != null && !(e.angle == 0 || Math.abs(e.angle) == 22.5 || Math.abs(e.angle) == 45)) {
                throw new IllegalStateException(e.name + ": rotation must be 0/22.5/45");
            }
        }

        /**
         * Hand, ground and frame transforms: rotate the upright model into the sprite frame (preRoll about z) on
         * top of vanilla's transform, and translate so the grip lands where the vanilla grip lands.
         */
        static Map<String, double[][]> display(W w) {
            Map<String, double[][]> out = new LinkedHashMap<>();
            boolean bow = w.ref.equals("bow");
            double[] third = bow ? new double[]{-80, 260, -40} : new double[]{0, -90, 55};
            double[] thirdT = bow ? new double[]{-1, -2, 2.5} : new double[]{0, 4, 0.5};
            double thirdS = bow ? 0.9 : 0.85;
            double[] g0 = bow ? new double[]{5.5, 11.5, 8} : new double[]{3, 3, 8};
            double[] first = {0, -90, 25};
            double[] firstT = {1.13, 3.2, 1.13};
            double firstS = 0.68;

            double[] tr = w.thirdRot != null ? w.thirdRot : new double[]{third[0], third[1], third[2] + w.preRoll};
            double[] t3 = place(third, thirdT, thirdS, g0, tr, thirdS * w.handScale, w.grip);
            out.put("thirdperson_righthand", new double[][]{tr, t3, s(thirdS * w.handScale)});
            out.put("thirdperson_lefthand", new double[][]{mirror(tr), t3, s(thirdS * w.handScale)});
            double[] fr = w.firstRot != null ? w.firstRot : new double[]{first[0], first[1], first[2] + w.preRoll};
            double[] t1 = place(first, firstT, firstS, g0, fr, firstS * w.handScale, w.grip);
            out.put("firstperson_righthand", new double[][]{fr, t1, s(firstS * w.handScale)});
            out.put("firstperson_lefthand", new double[][]{mirror(fr), t1, s(firstS * w.handScale)});

            // Ground and frame: centred on the model's bounding box, scaled to fit like a sprite would.
            double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
            for (El e : w.els) {
                for (int a = 0; a < 3; a++) {
                    lo[a] = Math.min(lo[a], e.from[a]);
                    hi[a] = Math.max(hi[a], e.to[a]);
                }
            }
            double[] mid = {(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2};
            double length = Math.max(hi[0] - lo[0], hi[1] - lo[1]);
            double fit = Math.min(1.0, 21.0 / length);
            double[] c = {8, 8, 8};
            double[] groundR = {0, 0, w.preRoll};
            out.put("ground", new double[][]{groundR, place(new double[]{0, 0, 0}, new double[]{0, 2, 0}, 0.5, c, groundR, 0.5 * fit, mid), s(0.5 * fit)});
            double[] fixedR = {0, 180, w.preRoll};
            out.put("fixed", new double[][]{fixedR, place(new double[]{0, 180, 0}, new double[]{0, 0, 0}, 1.0, c, fixedR, fit, mid), s(fit)});
            return out;
        }

        static double[] s(double v) {
            return new double[]{round(v), round(v), round(v)};
        }

        /** Left-hand transforms mirror the right-hand rotation (y and z), as vanilla's models do. */
        static double[] mirror(double[] r) {
            return new double[]{r[0], -r[1], -r[2]};
        }

        /**
         * Translation so model point {@code grip}, under rotation {@code rot} and scale {@code scale}, lands where
         * the reference point {@code g0} lands under vanilla's ({@code vRot}, {@code vT}, {@code vS}).
         * Everything in item pixels; the model's centre (8, 8, 8) is the pivot (ItemRenderer translates -0.5).
         */
        static double[] place(double[] vRot, double[] vT, double vS, double[] g0, double[] rot, double scale, double[] grip) {
            double[] want = add(vT, rotateXYZ(vRot, mulS(sub(g0, new double[]{8, 8, 8}), vS)));
            double[] t = sub(want, rotateXYZ(rot, mulS(sub(grip, new double[]{8, 8, 8}), scale)));
            for (int a = 0; a < 3; a++) {
                t[a] = round(Math.max(-80, Math.min(80, t[a])));
            }
            return t;
        }

        /** Quaternionf.rotationXYZ(x, y, z) in degrees: Rx * Ry * Rz. */
        static double[] rotateXYZ(double[] deg, double[] v) {
            double[] r = rotZ(Math.toRadians(deg[2]), v);
            r = rotY(Math.toRadians(deg[1]), r);
            return rotX(Math.toRadians(deg[0]), r);
        }

        static double[] rotX(double a, double[] v) {
            return new double[]{v[0], v[1] * Math.cos(a) - v[2] * Math.sin(a), v[1] * Math.sin(a) + v[2] * Math.cos(a)};
        }

        static double[] rotY(double a, double[] v) {
            return new double[]{v[0] * Math.cos(a) + v[2] * Math.sin(a), v[1], -v[0] * Math.sin(a) + v[2] * Math.cos(a)};
        }

        static double[] rotZ(double a, double[] v) {
            return new double[]{v[0] * Math.cos(a) - v[1] * Math.sin(a), v[0] * Math.sin(a) + v[1] * Math.cos(a), v[2]};
        }

        static double[] add(double[] a, double[] b) {
            return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
        }

        static double[] sub(double[] a, double[] b) {
            return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
        }

        static double[] mulS(double[] a, double s) {
            return new double[]{a[0] * s, a[1] * s, a[2] * s};
        }

        static double round(double v) {
            return Math.round(v * 100) / 100.0;
        }

        static String num(double v) {
            double r = Math.round(v * 1000) / 1000.0;
            if (r == Math.rint(r)) {
                return String.valueOf((long) r);
            }
            return String.format(Locale.ROOT, "%.3f", r).replaceAll("0+$", "");
        }

        static String vec(double[] v) {
            return "[" + num(v[0]) + ", " + num(v[1]) + ", " + num(v[2]) + "]";
        }

        /** Side-on and three-quarter render of the element model (y up, flipped into the preview's y-down space). */
        static BufferedImage preview(W w, BufferedImage tex) {
            Model m = new Model(w.id, tex.getWidth(), tex.getHeight());
            m.tex = tex;
            List<BufferedImage> views = new ArrayList<>();
            for (double[] v : new double[][]{{0, 0}, {40, 20}}) {
                List<double[][]> polys = new ArrayList<>();
                for (El e : w.els) {
                    for (String f : FACES) {
                        polys.add(elementFace(e, f, tex.getWidth(), tex.getHeight()));
                    }
                }
                views.add(renderPolys(m, polys, v[0], v[1], 6));
            }
            return Preview.join(views, true);
        }

        /** A flat icon: the model seen face-on, rolled so the tip points to the top right like vanilla swords. */
        static BufferedImage icon(W w, BufferedImage tex) {
            Model m = new Model(w.id, tex.getWidth(), tex.getHeight());
            m.tex = tex;
            List<double[][]> polys = new ArrayList<>();
            double a = Math.toRadians(45);
            for (El e : w.els) {
                for (String f : FACES) {
                    double[][] q = elementFace(e, f, tex.getWidth(), tex.getHeight());
                    for (double[] v : q) {
                        double x = v[0], y = v[1];
                        v[0] = x * Math.cos(a) - y * Math.sin(a);
                        v[1] = x * Math.sin(a) + y * Math.cos(a);
                    }
                    polys.add(q);
                }
            }
            return Preview.fit16(renderPolys(m, polys, 0, 0, 8));
        }

        /** One element face as a preview polygon: corners in y-down space with their texture UVs. */
        static double[][] elementFace(El e, String f, int texW, int texH) {
            double x0 = e.from[0], y0 = e.from[1], z0 = e.from[2], x1 = e.to[0], y1 = e.to[1], z1 = e.to[2];
            int[] r = e.uv.get(f);
            double u1 = r[0], v1 = r[1], u2 = r[0] + r[2], v2 = r[1] + r[3];
            // Corners in order (u1,v1) (u2,v1) (u2,v2) (u1,v2), from the same conventions as paintFace.
            double[][] c = switch (f) {
                case "north" -> new double[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
                case "south" -> new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
                case "west" -> new double[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
                case "east" -> new double[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
                case "up" -> new double[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
                default -> new double[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
            };
            double[][] uv = {{u1, v1}, {u2, v1}, {u2, v2}, {u1, v2}};
            double[][] out = new double[4][];
            for (int k = 0; k < 4; k++) {
                double[] p = c[k];
                if (e.axis != null) {
                    p = rotateAbout(p, e.axis, e.angle, e.origin);
                }
                // Item space is y-up; the preview draws y-down with the front toward -z.
                out[k] = new double[]{p[0], -p[1], p[2], uv[k][0], uv[k][1]};
            }
            return out;
        }

        static double[] rotateAbout(double[] p, String axis, double deg, double[] o) {
            double[] v = sub(p, o);
            double a = Math.toRadians(deg);
            v = switch (axis) {
                case "x" -> rotX(a, v);
                case "y" -> rotY(a, v);
                default -> rotZ(a, v);
            };
            return add(v, o);
        }

        static BufferedImage renderPolys(Model m, List<double[][]> polys, double yawDeg, double pitchDeg, double scale) {
            double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
            double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
            List<double[][]> view = new ArrayList<>();
            for (double[][] q : polys) {
                double[][] r = new double[4][];
                for (int k = 0; k < 4; k++) {
                    double[] p = Preview.viewPoint(q[k], yaw, pitch);
                    r[k] = new double[]{p[0], p[1], p[2], q[k][3], q[k][4]};
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                }
                view.add(r);
            }
            int pad = 6;
            int wpx = (int) Math.ceil((maxX - minX) * scale) + pad * 2;
            int hpx = (int) Math.ceil((maxY - minY) * scale) + pad * 2;
            BufferedImage img = new BufferedImage(Math.max(wpx, 60), Math.max(hpx, 60), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    img.setRGB(x, y, ((x / 8 + y / 8) & 1) == 0 ? 0xFF3A3D44 : 0xFF33363C);
                }
            }
            double[] depth = new double[img.getWidth() * img.getHeight()];
            java.util.Arrays.fill(depth, 1e9);
            for (int k = 0; k < view.size(); k++) {
                double[][] q = view.get(k);
                double light = Preview.lightOf(polys.get(k), yaw, pitch);
                for (int[] tri : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                    Preview.raster(m.tex, img, depth, q[tri[0]], q[tri[1]], q[tri[2]], minX, minY, scale, pad, light);
                }
            }
            return img;
        }
    }
}
