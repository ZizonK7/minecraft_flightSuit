package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pfkfks.flightsuit.client.parts.CellBuuParts;
import com.pfkfks.flightsuit.client.parts.DbzParts;
import com.pfkfks.flightsuit.client.parts.DbzPartsModel;
import com.pfkfks.flightsuit.client.parts.FriezaParts;
import com.pfkfks.flightsuit.client.parts.NamekianParts;
import com.pfkfks.flightsuit.client.parts.SaiyanParts;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.EnumMap;
import java.util.Map;

/**
 * M18: draws a Dragon Ball character's 3D parts (client/parts) over the posed body - the head's groups with the
 * head's transform, the body's with the body's - so a tail follows the body's lean and the antennae turn with the
 * head. Which model and which groups is DbzParts' table; the models are baked once per renderer.
 */
public class DbzPartsLayer extends RenderLayer<DbzFighterEntity, DbzFighterModel> {
    private final Map<DbzParts.Set_, DbzPartsModel> models = new EnumMap<>(DbzParts.Set_.class);

    public DbzPartsLayer(RenderLayerParent<DbzFighterEntity, DbzFighterModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        models.put(DbzParts.Set_.SAIYAN, new SaiyanParts(context.bakeLayer(SaiyanParts.LAYER)));
        models.put(DbzParts.Set_.NAMEKIAN, new NamekianParts(context.bakeLayer(NamekianParts.LAYER)));
        models.put(DbzParts.Set_.FRIEZA, new FriezaParts(context.bakeLayer(FriezaParts.LAYER)));
        models.put(DbzParts.Set_.CELL_BUU, new CellBuuParts(context.bakeLayer(CellBuuParts.LAYER)));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, DbzFighterEntity fighter, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        DbzParts.Wear wear = DbzParts.of(fighter.getCharacter());
        if (wear == null || fighter.isInvisible()) {
            return;
        }
        DbzPartsModel parts = models.get(wear.set());
        if (parts == null) {
            return;
        }
        parts.show(wear.groups());
        parts.animate(fighter, ageInTicks);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(parts.texture()));
        DbzFighterModel body = getParentModel();
        int overlay = OverlayTexture.pack(0.0F, fighter.hurtTime > 0);
        poseStack.pushPose();
        body.head.translateAndRotate(poseStack);
        parts.renderHead(poseStack, buffer, light, overlay);
        poseStack.popPose();
        poseStack.pushPose();
        body.body.translateAndRotate(poseStack);
        parts.renderBody(poseStack, buffer, light, overlay);
        poseStack.popPose();
    }
}
