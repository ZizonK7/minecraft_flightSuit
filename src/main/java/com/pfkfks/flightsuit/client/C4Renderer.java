package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.entity.C4Entity;
import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The C4 (C4Entity, 2026-10-12): its 3D model (the "c4" item's, tools/ModelGen weapons) laid flat on the face it's
 * stuck to; on each blink - slow at first, faster and faster as the fuse runs down - it's drawn lit up and a red
 * spark comes off the light.
 */
public class C4Renderer extends EntityRenderer<C4Entity> {
    private static final DustParticleOptions BLINK = new DustParticleOptions(new Vector3f(1.0F, 0.1F, 0.1F), 0.7F);
    private final ItemRenderer items;
    private ItemStack stack;

    public C4Renderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    /** Lit for two ticks of every blink: every 16 ticks with time to spare, every 6 near the end, every 2 at the last. */
    static boolean lit(long left) {
        int every = left > 60L ? 16 : left > 20L ? 6 : 2;
        return Math.floorMod(left, every) < Math.max(1, every / 4);
    }

    @Override
    public void render(C4Entity c4, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (stack == null) {
            stack = new ItemStack(ModItems.C4.get());
        }
        boolean lit = lit(c4.fuseLeft());
        pose.pushPose();
        // Top of the brick out along the face: turned to the face's yaw, tipped from straight up.
        pose.mulPose(Axis.YP.rotationDegrees(-c4.faceYaw()));
        pose.mulPose(Axis.XP.rotationDegrees(c4.faceTilt()));
        // Sitting on the face rather than half inside it.
        pose.translate(0.0D, 0.08D, 0.0D);
        pose.scale(0.75F, 0.75F, 0.75F);
        items.renderStatic(stack, ItemDisplayContext.FIXED, lit ? 0xF000F0 : light, OverlayTexture.NO_OVERLAY, pose, buffers, c4.level(), c4.getId());
        pose.popPose();
        if (lit && c4.tickCount % 2 == 0 && partialTick < 0.34F) {
            Vec3 up = Vec3.directionFromRotation(c4.faceTilt() - 90.0F, c4.faceYaw()).scale(0.2D);
            c4.level().addParticle(BLINK, c4.getX() + up.x, c4.getY() + up.y, c4.getZ() + up.z, 0.0D, 0.0D, 0.0D);
        }
        super.render(c4, entityYaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(C4Entity c4) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
