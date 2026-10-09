package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.car.HoverCarEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Placeholder Capsule Corp hover car: a long rounded-off hull, a short nose, a glass bubble cabin, two tail
 * fins and four glowing hover pads underneath. Front is -Z. Bottom of the pads sits at model y = 0 (model
 * y points down), so the renderer only needs the usual flip. Real modeling comes later.
 * UVs match textures/entity/hover_car.png (tools/TextureGen.java).
 */
public class HoverCarModel extends EntityModel<HoverCarEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(FlightSuitMod.MODID, "hover_car"), "main");

    private final ModelPart root;

    public HoverCarModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hull", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-10.0F, -7.0F, -17.0F, 20.0F, 6.0F, 34.0F), PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-8.0F, -6.0F, -21.0F, 16.0F, 4.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("cabin", CubeListBuilder.create()
                .texOffs(40, 40).addBox(-7.0F, -12.0F, -6.0F, 14.0F, 5.0F, 14.0F), PartPose.ZERO);
        root.addOrReplaceChild("fins", CubeListBuilder.create()
                .texOffs(96, 40).addBox(-10.0F, -11.0F, 12.0F, 2.0F, 4.0F, 4.0F)
                .texOffs(96, 40).addBox(8.0F, -11.0F, 12.0F, 2.0F, 4.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("pads", CubeListBuilder.create()
                .texOffs(96, 52).addBox(-9.0F, -1.0F, -14.0F, 4.0F, 1.0F, 4.0F)
                .texOffs(96, 52).addBox(5.0F, -1.0F, -14.0F, 4.0F, 1.0F, 4.0F)
                .texOffs(96, 52).addBox(-9.0F, -1.0F, 10.0F, 4.0F, 1.0F, 4.0F)
                .texOffs(96, 52).addBox(5.0F, -1.0F, 10.0F, 4.0F, 1.0F, 4.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(HoverCarEntity car, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
