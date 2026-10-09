package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.block.SuitStationBlock;
import com.pfkfks.flightsuit.block.SuitStationBlockEntity;
import com.pfkfks.flightsuit.suit.StationRigTimeline;
import com.pfkfks.flightsuit.suit.SuitType;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The station rig: five jointed robot arms on the frame (ceiling = helmet, upper pair = chest, lower pair =
 * legs) and the suit - standing on the platform when docked, or in the arms' grippers while a run takes it
 * apart and puts it on someone (StationRigTimeline). Arms are drawn in code as boxes with two-bone IK, so the
 * grippers land exactly on the piece they carry.
 *
 * Drawn in the station's frame: +x = the suit's right, +y up, +z toward the back, origin = platform top center.
 */
public class SuitStationRenderer implements BlockEntityRenderer<SuitStationBlockEntity> {
    private static final ResourceLocation ARM_TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/station_arm.png");

    private static final int[] ARM_COLOR = {196, 202, 210};
    private static final int[] JOINT_COLOR = {224, 174, 58};
    private static final int[] DARK_COLOR = {62, 67, 75};
    private static final int[] GLOW_COLOR = {95, 227, 255};

    private enum Arm {
        CEILING(EquipmentSlot.HEAD, 0, new Vec3(0.0D, 3.2D, 0.1D), new Vec3(0.0D, 2.7D, 0.55D), 0.7F, 0.7F, new Vec3(0.0D, 0.0D, 1.0D)),
        UPPER_RIGHT(EquipmentSlot.CHEST, 1, new Vec3(0.78D, 1.8D, 0.78D), new Vec3(0.6D, 1.45D, 0.55D), 0.6F, 0.6F, new Vec3(1.0D, 0.5D, 0.3D)),
        UPPER_LEFT(EquipmentSlot.CHEST, -1, new Vec3(-0.78D, 1.8D, 0.78D), new Vec3(-0.6D, 1.45D, 0.55D), 0.6F, 0.6F, new Vec3(-1.0D, 0.5D, 0.3D)),
        LOWER_RIGHT(EquipmentSlot.LEGS, 1, new Vec3(0.78D, 0.55D, 0.78D), new Vec3(0.62D, 0.3D, 0.5D), 0.55F, 0.55F, new Vec3(1.0D, -0.3D, 0.3D)),
        LOWER_LEFT(EquipmentSlot.LEGS, -1, new Vec3(-0.78D, 0.55D, 0.78D), new Vec3(-0.62D, 0.3D, 0.5D), 0.55F, 0.55F, new Vec3(-1.0D, -0.3D, 0.3D));

        final EquipmentSlot slot;
        final int side;
        final Vec3 mount;
        final Vec3 park;
        final float upper;
        final float fore;
        /** Which way the elbow bends. */
        final Vec3 pole;

        Arm(EquipmentSlot slot, int side, Vec3 mount, Vec3 park, float upper, float fore, Vec3 pole) {
            this.slot = slot;
            this.side = side;
            this.mount = mount;
            this.park = park;
            this.upper = upper;
            this.fore = fore;
            this.pole = pole;
        }

        /** Where this arm holds its piece when the piece sits where it's worn. */
        Vec3 grip() {
            return switch (slot) {
                case HEAD -> new Vec3(0.0D, 2.05D, 0.0D);
                case CHEST -> new Vec3(0.42D * side, 1.25D, 0.0D);
                default -> new Vec3(0.3D * side, 0.62D, 0.0D);
            };
        }
    }

    public SuitStationRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SuitStationBlockEntity station, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (station.getLevel() == null) {
            return;
        }
        Direction facing = station.getBlockState().getValue(SuitStationBlock.FACING);
        int light = LevelRenderer.getLightColor(station.getLevel(), station.getBlockPos().above());
        byte mode = station.rigMode();
        float t = station.rigTime(partialTick);
        if (mode != StationRigTimeline.NONE && t > StationRigTimeline.end(mode) + 5) {
            mode = StationRigTimeline.NONE; // the end-of-run update got lost: show the station at rest
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.25D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));

        VertexConsumer arms = buffers.getBuffer(RenderType.entityCutoutNoCull(ARM_TEXTURE));
        for (Arm arm : Arm.values()) {
            drawArm(arm, mode, t, poseStack, arms, light);
        }
        if (mode == StationRigTimeline.NONE) {
            SuitType type = station.getSuitType();
            if (type != null) {
                for (EquipmentSlot slot : station.getParts().keySet()) {
                    drawPiece(slot, type.id(), Vec3.ZERO, -1.0F, poseStack, buffers, light);
                }
            }
        } else {
            for (EquipmentSlot slot : WornSuit.SLOTS) {
                Vec3 offset = station.rigMoves(slot) ? StationRigTimeline.pieceOffset(mode, slot, t) : null;
                if (offset != null) {
                    drawPiece(slot, station.rigSuitId(), offset, StationRigTimeline.poseBlend(mode, t), poseStack, buffers, light);
                }
            }
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SuitStationBlockEntity station) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    // ---------------------------------------------------------------- suit pieces

    /** @param blend rig pose blend (see SuitUpPose#applyRig), or negative for the docked at-ease pose */
    private static void drawPiece(EquipmentSlot slot, String suitId, Vec3 offset, float blend, PoseStack poseStack,
                                  MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0D, -1.501D, 0.0D);
        HumanoidModel<LivingEntity> model = SuitArmorModels.forSlot(slot);
        if (blend < 0.0F) {
            SuitUpPose.applyStanding(model);
        } else {
            SuitUpPose.applyRig(model, blend);
        }
        SuitPartRenderer.draw(model, slot, suitId, poseStack, buffers, light);
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- arms

    private static void drawArm(Arm arm, byte mode, float t, PoseStack poseStack, VertexConsumer consumer, int light) {
        float reach = StationRigTimeline.armReach(mode, arm.slot, t);
        Vec3 offset = mode == StationRigTimeline.NONE ? null : StationRigTimeline.pieceOffset(mode, arm.slot, t);
        Vec3 grip = arm.grip().add(offset == null ? Vec3.ZERO : offset);
        Vec3 target = arm.park.lerp(grip, reach);
        // Fingers close once the gripper is on the piece.
        float open = reach > 0.95F && offset != null ? 0.02F : 0.07F;

        // Two-bone IK: elbow in the plane of (mount -> target) and the pole direction.
        Vec3 root = arm.mount;
        Vec3 toTarget = target.subtract(root);
        double distance = Math.max(Math.abs(arm.upper - arm.fore) + 0.05D, Math.min(arm.upper + arm.fore - 0.01D, toTarget.length()));
        Vec3 dir = toTarget.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, -1.0D, 0.0D) : toTarget.normalize();
        Vec3 wrist = root.add(dir.scale(distance));
        double along = (arm.upper * arm.upper - arm.fore * arm.fore + distance * distance) / (2.0D * distance);
        double height = Math.sqrt(Math.max(0.0D, arm.upper * arm.upper - along * along));
        Vec3 bend = arm.pole.subtract(dir.scale(arm.pole.dot(dir)));
        bend = bend.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : bend.normalize();
        Vec3 elbow = root.add(dir.scale(along)).add(bend.scale(height));

        cube(poseStack, consumer, root, 0.2F, DARK_COLOR, light);
        segment(poseStack, consumer, root, elbow, 0.12F, ARM_COLOR, light);
        cube(poseStack, consumer, elbow, 0.15F, JOINT_COLOR, light);
        segment(poseStack, consumer, elbow, wrist, 0.1F, ARM_COLOR, light);
        cube(poseStack, consumer, wrist, 0.11F, JOINT_COLOR, light);
        claw(poseStack, consumer, wrist, wrist.subtract(elbow).normalize(), open, light);
    }

    /** Two fingers past the wrist, along the forearm, plus a little status light. */
    private static void claw(PoseStack poseStack, VertexConsumer consumer, Vec3 wrist, Vec3 along, float open, int light) {
        poseStack.pushPose();
        poseStack.translate(wrist.x, wrist.y, wrist.z);
        poseStack.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), along.toVector3f()));
        box(poseStack, consumer, -open - 0.03F, -0.025F, 0.02F, -open + 0.01F, 0.025F, 0.17F, DARK_COLOR, light);
        box(poseStack, consumer, open - 0.01F, -0.025F, 0.02F, open + 0.03F, 0.025F, 0.17F, DARK_COLOR, light);
        box(poseStack, consumer, -0.02F, 0.055F, -0.02F, 0.02F, 0.07F, 0.02F, GLOW_COLOR, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static void segment(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to, float thickness, int[] color, int light) {
        Vec3 d = to.subtract(from);
        float length = (float) d.length();
        if (length < 1.0E-4F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(from.x, from.y, from.z);
        poseStack.mulPose(new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), d.normalize().toVector3f()));
        float h = thickness / 2.0F;
        box(poseStack, consumer, -h, -h, 0.0F, h, h, length, color, light);
        poseStack.popPose();
    }

    private static void cube(PoseStack poseStack, VertexConsumer consumer, Vec3 center, float size, int[] color, int light) {
        float h = size / 2.0F;
        box(poseStack, consumer, (float) center.x - h, (float) center.y - h, (float) center.z - h,
                (float) center.x + h, (float) center.y + h, (float) center.z + h, color, light);
    }

    private static void box(PoseStack poseStack, VertexConsumer consumer, float x0, float y0, float z0, float x1, float y1, float z1,
                            int[] color, int light) {
        PoseStack.Pose last = poseStack.last();
        Matrix4f pose = last.pose();
        Matrix3f normal = last.normal();
        // down, up, north (-z), south (+z), west (-x), east (+x)
        quad(pose, normal, consumer, color, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, normal, consumer, color, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(pose, normal, consumer, color, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        quad(pose, normal, consumer, color, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, normal, consumer, color, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, normal, consumer, color, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
    }

    private static void quad(Matrix4f pose, Matrix3f normal, VertexConsumer consumer, int[] color, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(pose, normal, consumer, color, light, nx, ny, nz, ax, ay, az, 0.0F, 1.0F);
        vertex(pose, normal, consumer, color, light, nx, ny, nz, bx, by, bz, 1.0F, 1.0F);
        vertex(pose, normal, consumer, color, light, nx, ny, nz, cx, cy, cz, 1.0F, 0.0F);
        vertex(pose, normal, consumer, color, light, nx, ny, nz, dx, dy, dz, 0.0F, 0.0F);
    }

    private static void vertex(Matrix4f pose, Matrix3f normal, VertexConsumer consumer, int[] color, int light,
                               float nx, float ny, float nz, float x, float y, float z, float u, float v) {
        consumer.vertex(pose, x, y, z).color(color[0], color[1], color[2], 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, nx, ny, nz).endVertex();
    }
}
