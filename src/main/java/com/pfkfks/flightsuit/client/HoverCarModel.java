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
 * Capsule Corp hover car: a yellow two-tone open roadster with round fender pods (big headlights in front,
 * tail lights and fins at the back), a chrome running board, twin rear thrusters, four hover pads and a
 * whip antenna. The driver sits in the open cockpit with their legs under the hood. Front is -Z; the bottom
 * of the pads sits at model y = 0 (model y points down), so the renderer only needs the usual flip.
 *
 * The box list, UVs and textures/entity/hover_car.png all come from tools/HoverCarGen.java - edit the
 * layout there and paste the printed lines back in here.
 */
public class HoverCarModel extends EntityModel<HoverCarEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(FlightSuitMod.MODID, "hover_car"), "main");

    private final ModelPart body;
    private final ModelPart glass;

    public HoverCarModel(ModelPart root) {
        this.body = root.getChild("body");
        this.glass = root.getChild("glass");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(112, 0).addBox(-9.0F, -3.0F, -17.0F, 18.0F, 2.0F, 34.0F) // belly
                .texOffs(86, 70).addBox(-9.0F, -1.0F, -15.0F, 5.0F, 1.0F, 5.0F) // pad_right
                .texOffs(86, 70).addBox(4.0F, -1.0F, -15.0F, 5.0F, 1.0F, 5.0F) // pad_left
                .texOffs(106, 70).addBox(-9.0F, -1.0F, 10.0F, 5.0F, 1.0F, 5.0F) // pad_rear_right
                .texOffs(106, 70).addBox(4.0F, -1.0F, 10.0F, 5.0F, 1.0F, 5.0F) // pad_rear_left
                .texOffs(0, 0).addBox(-10.0F, -9.0F, -18.0F, 20.0F, 6.0F, 36.0F) // hull
                .texOffs(18, 70).addBox(-10.0F, -8.0F, -20.0F, 20.0F, 5.0F, 2.0F) // nose
                .texOffs(204, 70).addBox(-7.0F, -7.0F, -21.0F, 14.0F, 3.0F, 1.0F) // bumper
                .texOffs(212, 58).addBox(-10.0F, -9.0F, 18.0F, 20.0F, 6.0F, 2.0F) // tail
                .texOffs(216, 0).addBox(-11.0F, -4.0F, -10.0F, 1.0F, 1.0F, 19.0F) // running_board_right
                .texOffs(216, 0).addBox(10.0F, -4.0F, -10.0F, 1.0F, 1.0F, 19.0F) // running_board_left
                .texOffs(126, 70).addBox(-7.0F, -8.0F, 20.0F, 4.0F, 4.0F, 2.0F) // thruster_right
                .texOffs(126, 70).addBox(3.0F, -8.0F, 20.0F, 4.0F, 4.0F, 2.0F) // thruster_left
                .texOffs(182, 42).addBox(-13.0F, -10.0F, -18.0F, 3.0F, 6.0F, 8.0F) // pod_front_right
                .texOffs(182, 42).addBox(10.0F, -10.0F, -18.0F, 3.0F, 6.0F, 8.0F) // pod_front_left
                .texOffs(62, 70).addBox(-12.0F, -11.0F, -17.0F, 2.0F, 1.0F, 6.0F) // pod_front_top_right
                .texOffs(62, 70).addBox(10.0F, -11.0F, -17.0F, 2.0F, 1.0F, 6.0F) // pod_front_top_left
                .texOffs(98, 58).addBox(-14.0F, -9.0F, -17.0F, 1.0F, 4.0F, 6.0F) // pod_front_side_right
                .texOffs(98, 58).addBox(13.0F, -9.0F, -17.0F, 1.0F, 4.0F, 6.0F) // pod_front_side_left
                .texOffs(184, 70).addBox(-12.0F, -9.0F, -19.0F, 2.0F, 4.0F, 1.0F) // pod_front_cap_right
                .texOffs(184, 70).addBox(10.0F, -9.0F, -19.0F, 2.0F, 4.0F, 1.0F) // pod_front_cap_left
                .texOffs(244, 70).addBox(-12.0F, -8.0F, -20.0F, 2.0F, 2.0F, 1.0F) // headlight_right
                .texOffs(244, 70).addBox(10.0F, -8.0F, -20.0F, 2.0F, 2.0F, 1.0F) // headlight_left
                .texOffs(66, 42).addBox(-13.0F, -10.0F, 9.0F, 3.0F, 6.0F, 9.0F) // pod_rear_right
                .texOffs(66, 42).addBox(10.0F, -10.0F, 9.0F, 3.0F, 6.0F, 9.0F) // pod_rear_left
                .texOffs(0, 70).addBox(-12.0F, -11.0F, 10.0F, 2.0F, 1.0F, 7.0F) // pod_rear_top_right
                .texOffs(0, 70).addBox(10.0F, -11.0F, 10.0F, 2.0F, 1.0F, 7.0F) // pod_rear_top_left
                .texOffs(54, 58).addBox(-14.0F, -9.0F, 10.0F, 1.0F, 4.0F, 7.0F) // pod_rear_side_right
                .texOffs(54, 58).addBox(13.0F, -9.0F, 10.0F, 1.0F, 4.0F, 7.0F) // pod_rear_side_left
                .texOffs(190, 70).addBox(-12.0F, -9.0F, 18.0F, 2.0F, 4.0F, 1.0F) // tail_light_right
                .texOffs(190, 70).addBox(10.0F, -9.0F, 18.0F, 2.0F, 4.0F, 1.0F) // tail_light_left
                .texOffs(112, 58).addBox(-12.0F, -15.0F, 12.0F, 1.0F, 4.0F, 6.0F) // fin_right
                .texOffs(112, 58).addBox(11.0F, -15.0F, 12.0F, 1.0F, 4.0F, 6.0F) // fin_left
                .texOffs(196, 70).addBox(-12.0F, -17.0F, 15.0F, 1.0F, 2.0F, 3.0F) // fin_tip_right
                .texOffs(196, 70).addBox(11.0F, -17.0F, 15.0F, 1.0F, 2.0F, 3.0F) // fin_tip_left
                .texOffs(0, 42).addBox(-10.0F, -12.0F, -18.0F, 20.0F, 3.0F, 13.0F) // hood
                .texOffs(0, 58).addBox(-8.0F, -13.0F, -16.0F, 16.0F, 1.0F, 11.0F) // hood_top
                .texOffs(90, 42).addBox(-10.0F, -12.0F, -5.0F, 2.0F, 3.0F, 12.0F) // door_right
                .texOffs(90, 42).addBox(8.0F, -12.0F, -5.0F, 2.0F, 3.0F, 12.0F) // door_left
                .texOffs(118, 42).addBox(-10.0F, -12.0F, 7.0F, 20.0F, 3.0F, 12.0F) // deck
                .texOffs(126, 58).addBox(-8.0F, -13.0F, 9.0F, 16.0F, 1.0F, 9.0F) // deck_top
                .texOffs(176, 58).addBox(-5.0F, -11.0F, -3.0F, 10.0F, 2.0F, 8.0F) // seat
                .texOffs(70, 58).addBox(-5.0F, -18.0F, 5.0F, 10.0F, 9.0F, 2.0F) // seat_back
                .texOffs(234, 70).addBox(-2.0F, -16.0F, -7.0F, 4.0F, 3.0F, 1.0F) // wheel
                .texOffs(0, 78).addBox(-8.0F, -19.0F, -10.0F, 16.0F, 1.0F, 1.0F) // windshield_frame
                .texOffs(94, 58).addBox(-8.0F, -23.0F, 16.0F, 1.0F, 10.0F, 1.0F) // antenna
                .texOffs(138, 70).addBox(-9.0F, -26.0F, 15.0F, 3.0F, 3.0F, 3.0F) // antenna_tip
                , PartPose.ZERO);
        root.addOrReplaceChild("glass", CubeListBuilder.create()
                .texOffs(150, 70).addBox(-8.0F, -18.0F, -10.0F, 16.0F, 5.0F, 1.0F) // windshield
                .texOffs(78, 70).addBox(-9.0F, -17.0F, -10.0F, 1.0F, 4.0F, 3.0F) // side_glass_right
                .texOffs(78, 70).addBox(8.0F, -17.0F, -10.0F, 1.0F, 4.0F, 3.0F) // side_glass_left
                , PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public void setupAnim(HoverCarEntity car, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    /** Everything but the windshield (cutout pass). */
    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        body.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** The windshield and side glass, for a translucent buffer. */
    public void renderGlass(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        glass.render(poseStack, buffer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
