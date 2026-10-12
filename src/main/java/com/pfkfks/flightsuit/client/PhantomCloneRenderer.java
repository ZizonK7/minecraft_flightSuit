package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pfkfks.flightsuit.entity.PhantomCloneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * The phantom's slime clones (StolenSkill.SLIME_CLONES): drawn like the owner - their skin, arm width and suit - the
 * same way as the remote pilot's body (RemoteBodyRenderer).
 */
public class PhantomCloneRenderer extends EntityRenderer<PhantomCloneEntity> {
    private final Body wide;
    private final Body slim;

    public PhantomCloneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wide = new Body(context, false);
        this.slim = new Body(context, true);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(PhantomCloneEntity body, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        (isSlim(body) ? slim : wide).render(body, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomCloneEntity body) {
        return skin(body);
    }

    private static PlayerInfo info(PhantomCloneEntity body) {
        UUID owner = body.getOwnerId();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return owner == null || connection == null ? null : connection.getPlayerInfo(owner);
    }

    private static UUID skinId(PhantomCloneEntity body) {
        return body.getOwnerId() != null ? body.getOwnerId() : body.getUUID();
    }

    static ResourceLocation skin(PhantomCloneEntity body) {
        PlayerInfo info = info(body);
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(skinId(body));
    }

    private static boolean isSlim(PhantomCloneEntity body) {
        PlayerInfo info = info(body);
        return "slim".equals(info != null ? info.getModelName() : DefaultPlayerSkin.getSkinModelName(skinId(body)));
    }

    private static final class Body extends LivingEntityRenderer<PhantomCloneEntity, PlayerModel<PhantomCloneEntity>> {
        Body(EntityRendererProvider.Context context, boolean slim) {
            super(context, new PlayerModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim), 0.5F);
            addLayer(new HumanoidArmorLayer<>(this,
                    new HumanoidModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidModel<>(context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR)),
                    context.getModelManager()));
        }

        @Override
        public ResourceLocation getTextureLocation(PhantomCloneEntity body) {
            return skin(body);
        }

        @Override
        protected void scale(PhantomCloneEntity body, PoseStack pose, float partialTick) {
            // Same as PlayerRenderer, so the body is exactly the player's size.
            pose.scale(0.9375F, 0.9375F, 0.9375F);
        }

        @Override
        protected boolean shouldShowName(PhantomCloneEntity body) {
            return false;
        }
    }
}
