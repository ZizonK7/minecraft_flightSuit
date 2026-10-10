package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Shapes of light for the ki effects (KiFxClient): balls, tubes, rings, discs, soft glows facing the camera and
 * ribbons along a line - all in one additive render type (colours add up towards white where they overlap, nothing
 * writes depth, so layers inside layers all show; the world in front still hides them). Positions are relative to
 * the camera.
 */
public final class KiDraw extends RenderType {
    /** Additive, untextured, both sides, no depth writing. */
    public static final RenderType GLOW = create("flightsuit_ki_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 16,
            false, false, CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private static final int RINGS = 8;
    private static final int SIDES = 14;

    private KiDraw(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup,
                   Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    /** What to draw with this frame: the buffer, the pose, and which way the camera's right and up are. */
    public record Ctx(VertexConsumer vc, Matrix4f pose, Vec3 right, Vec3 up, Vec3 camera) {
    }

    private static void v(Ctx c, Vec3 p, int rgb, int a) {
        c.vc().vertex(c.pose(), (float) p.x, (float) p.y, (float) p.z).color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, clamp(a)).endVertex();
    }

    private static int clamp(int a) {
        return a < 0 ? 0 : Math.min(255, a);
    }

    private static void quad(Ctx c, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, int rgb, int a0, int a1, int a2, int a3) {
        v(c, p0, rgb, a0);
        v(c, p1, rgb, a1);
        v(c, p2, rgb, a2);
        v(c, p3, rgb, a3);
    }

    /** Two unit vectors square to {@code axis} (and to each other). */
    static Vec3[] basis(Vec3 axis) {
        Vec3 n = axis.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 1.0D, 0.0D) : axis.normalize();
        Vec3 u = n.cross(Math.abs(n.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        return new Vec3[]{u, n.cross(u).normalize()};
    }

    /** A ball of light. */
    public static void sphere(Ctx c, Vec3 centre, double r, int rgb, int a) {
        if (r <= 0.0D || a <= 0) {
            return;
        }
        Vec3 o = centre.subtract(c.camera());
        for (int i = 0; i < RINGS; i++) {
            double t0 = Math.PI * i / RINGS - Math.PI / 2.0D;
            double t1 = Math.PI * (i + 1) / RINGS - Math.PI / 2.0D;
            for (int j = 0; j < SIDES; j++) {
                double p0 = Math.PI * 2.0D * j / SIDES;
                double p1 = Math.PI * 2.0D * (j + 1) / SIDES;
                quad(c, o.add(onSphere(t0, p0, r)), o.add(onSphere(t0, p1, r)), o.add(onSphere(t1, p1, r)), o.add(onSphere(t1, p0, r)), rgb, a, a, a, a);
            }
        }
    }

    private static Vec3 onSphere(double lat, double lon, double r) {
        double cl = Math.cos(lat);
        return new Vec3(Math.cos(lon) * cl * r, Math.sin(lat) * r, Math.sin(lon) * cl * r);
    }

    /** A soft glow facing the camera: bright in the middle, gone at the rim. */
    public static void glow(Ctx c, Vec3 centre, double r, int rgb, int a) {
        if (r <= 0.0D || a <= 0) {
            return;
        }
        Vec3 o = centre.subtract(c.camera());
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2.0D * i / n;
            double a1 = Math.PI * 2.0D * (i + 1) / n;
            Vec3 e0 = o.add(c.right().scale(Math.cos(a0) * r)).add(c.up().scale(Math.sin(a0) * r));
            Vec3 e1 = o.add(c.right().scale(Math.cos(a1) * r)).add(c.up().scale(Math.sin(a1) * r));
            Vec3 m0 = o.lerp(e0, 0.35D);
            Vec3 m1 = o.lerp(e1, 0.35D);
            quad(c, o, m0, m1, o, rgb, a, a * 2 / 3, a * 2 / 3, a);
            quad(c, m0, e0, e1, m1, rgb, a * 2 / 3, 0, 0, a * 2 / 3);
        }
    }

    /** A star of {@code points} thin rays facing the camera, turned by {@code turn}. */
    public static void star(Ctx c, Vec3 centre, double r, double turn, int points, int rgb, int a) {
        Vec3 o = centre.subtract(c.camera());
        for (int i = 0; i < points; i++) {
            double ang = turn + Math.PI * 2.0D * i / points;
            Vec3 dir = c.right().scale(Math.cos(ang)).add(c.up().scale(Math.sin(ang)));
            Vec3 side = c.right().scale(-Math.sin(ang)).add(c.up().scale(Math.cos(ang))).scale(r * 0.06D);
            Vec3 tip = o.add(dir.scale(r));
            quad(c, o.add(side), o.subtract(side), tip, tip, rgb, a, a, 0, 0);
        }
    }

    /** An open tube from {@code a} ({@code ra} round) to {@code b} ({@code rb}), its alpha going {@code aa} to {@code ab}. */
    public static void tube(Ctx c, Vec3 a, Vec3 b, double ra, double rb, int rgb, int aa, int ab) {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0E-6D || aa <= 0 && ab <= 0) {
            return;
        }
        Vec3[] uv = basis(axis);
        Vec3 oa = a.subtract(c.camera());
        Vec3 ob = b.subtract(c.camera());
        for (int j = 0; j < SIDES; j++) {
            double p0 = Math.PI * 2.0D * j / SIDES;
            double p1 = Math.PI * 2.0D * (j + 1) / SIDES;
            Vec3 d0 = uv[0].scale(Math.cos(p0)).add(uv[1].scale(Math.sin(p0)));
            Vec3 d1 = uv[0].scale(Math.cos(p1)).add(uv[1].scale(Math.sin(p1)));
            quad(c, oa.add(d0.scale(ra)), oa.add(d1.scale(ra)), ob.add(d1.scale(rb)), ob.add(d0.scale(rb)), rgb, aa, aa, ab, ab);
        }
    }

    /** A flat ring round {@code centre} square to {@code normal}, from {@code rIn} to {@code rOut}; {@code wobble} roughens the rim. */
    public static void ring(Ctx c, Vec3 centre, Vec3 normal, double rIn, double rOut, double turn, double wobble, int rgb, int aIn, int aOut) {
        if (rOut <= 0.0D) {
            return;
        }
        Vec3[] uv = basis(normal);
        Vec3 o = centre.subtract(c.camera());
        int n = 28;
        for (int j = 0; j < n; j++) {
            double p0 = turn + Math.PI * 2.0D * j / n;
            double p1 = turn + Math.PI * 2.0D * (j + 1) / n;
            Vec3 d0 = uv[0].scale(Math.cos(p0)).add(uv[1].scale(Math.sin(p0)));
            Vec3 d1 = uv[0].scale(Math.cos(p1)).add(uv[1].scale(Math.sin(p1)));
            double w0 = 1.0D + wobble * saw(j);
            double w1 = 1.0D + wobble * saw(j + 1);
            quad(c, o.add(d0.scale(rIn)), o.add(d1.scale(rIn)), o.add(d1.scale(rOut * w1)), o.add(d0.scale(rOut * w0)), rgb, aIn, aIn, aOut, aOut);
        }
    }

    private static double saw(int j) {
        return (j % 4) / 3.0D - 0.5D;
    }

    /**
     * A ribbon along {@code points} turned to face the camera: {@code widths} and {@code alphas} per point
     * (a tail, a spiral, a bolt).
     */
    public static void ribbon(Ctx c, Vec3[] points, double[] widths, int[] alphas, int rgb) {
        for (int i = 0; i + 1 < points.length; i++) {
            Vec3 p0 = points[i].subtract(c.camera());
            Vec3 p1 = points[i + 1].subtract(c.camera());
            Vec3 along = p1.subtract(p0);
            if (along.lengthSqr() < 1.0E-8D) {
                continue;
            }
            Vec3 mid = p0.add(p1).scale(0.5D);
            Vec3 side = along.cross(mid);
            if (side.lengthSqr() < 1.0E-8D) {
                side = basis(along)[0];
            }
            side = side.normalize();
            Vec3 s0 = side.scale(widths[i] / 2.0D);
            Vec3 s1 = side.scale(widths[i + 1] / 2.0D);
            quad(c, p0.add(s0), p0.subtract(s0), p1.subtract(s1), p1.add(s1), rgb, alphas[i], alphas[i], alphas[i + 1], alphas[i + 1]);
        }
    }

    /** A straight ribbon from {@code a} to {@code b}, {@code wa} to {@code wb} wide. */
    public static void streak(Ctx c, Vec3 a, Vec3 b, double wa, double wb, int rgb, int aa, int ab) {
        ribbon(c, new Vec3[]{a, b}, new double[]{wa, wb}, new int[]{aa, ab}, rgb);
    }

    /** A crackling bolt from {@code a} to {@code b}, zigzagging by {@code seed}. */
    public static void bolt(Ctx c, Vec3 a, Vec3 b, long seed, double jag, double width, int rgb, int alpha) {
        int n = 7;
        Vec3[] uv = basis(b.subtract(a));
        Vec3[] pts = new Vec3[n + 1];
        double[] widths = new double[n + 1];
        int[] alphas = new int[n + 1];
        java.util.Random random = new java.util.Random(seed);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double off = i == 0 || i == n ? 0.0D : jag;
            pts[i] = a.lerp(b, t).add(uv[0].scale((random.nextDouble() - 0.5D) * off)).add(uv[1].scale((random.nextDouble() - 0.5D) * off));
            widths[i] = width;
            alphas[i] = alpha;
        }
        ribbon(c, pts, widths, alphas, rgb);
        for (int i = 0; i <= n; i++) {
            widths[i] = width * 3.0D;
            alphas[i] = alpha / 4;
        }
        ribbon(c, pts, widths, alphas, rgb);
    }

    /** Layers of a glowing ball: white core, colour, a faint halo, and a soft glow round it all. */
    public static void orb(Ctx c, Vec3 centre, double r, int rgb, float bright) {
        sphere(c, centre, r * 1.45D, rgb, (int) (26 * bright));
        sphere(c, centre, r, rgb, (int) (90 * bright));
        sphere(c, centre, r * 0.72D, rgb, (int) (120 * bright));
        sphere(c, centre, r * 0.45D, 0xFFFFFF, (int) (200 * bright));
        glow(c, centre, r * 2.6D, rgb, (int) (70 * bright));
    }
}
