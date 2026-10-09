package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.SpaceshipEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Capsule Corp spaceship (DESIGN.md 4-16): a round white hull with a yellow ring, the CC mark on four sides, a
 * domed top with an antenna, a front window and four landing legs. Feet at model y = 0 (y points down).
 *
 * The box list, UVs and textures/entity/spaceship.png come from tools/SpaceGen.java - edit the layout there
 * and paste the printed lines back in here.
 */
public class SpaceshipModel extends HierarchicalModel<SpaceshipEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(FlightSuitMod.MODID, "spaceship"), "main");

    private final ModelPart root;

    public SpaceshipModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-18.0F, -21.0F, -18.0F, 36.0F, 3.0F, 36.0F) // ring
                .texOffs(0, 39).addBox(-16.0F, -18.0F, -16.0F, 32.0F, 6.0F, 32.0F) // hull_low
                .texOffs(128, 39).addBox(-15.0F, -29.0F, -15.0F, 30.0F, 8.0F, 30.0F) // hull_high
                .texOffs(0, 77).addBox(-12.0F, -12.0F, -12.0F, 24.0F, 3.0F, 24.0F) // belly
                .texOffs(96, 77).addBox(-10.0F, -34.0F, -10.0F, 20.0F, 5.0F, 20.0F) // dome
                .texOffs(176, 77).addBox(-5.0F, -36.0F, -5.0F, 10.0F, 2.0F, 10.0F) // cap
                .texOffs(216, 77).addBox(-7.0F, -27.0F, -16.0F, 14.0F, 5.0F, 1.0F) // window
                .texOffs(246, 77).addBox(0.0F, -41.0F, 0.0F, 1.0F, 5.0F, 1.0F) // antenna
                .texOffs(0, 104).addBox(-16.0F, -9.0F, -16.0F, 2.0F, 8.0F, 2.0F) // leg_0
                .texOffs(8, 104).addBox(-17.0F, -1.0F, -17.0F, 4.0F, 1.0F, 4.0F) // foot_0
                .texOffs(0, 104).addBox(15.0F, -9.0F, -16.0F, 2.0F, 8.0F, 2.0F) // leg_1
                .texOffs(8, 104).addBox(14.0F, -1.0F, -17.0F, 4.0F, 1.0F, 4.0F) // foot_1
                .texOffs(0, 104).addBox(-16.0F, -9.0F, 15.0F, 2.0F, 8.0F, 2.0F) // leg_2
                .texOffs(8, 104).addBox(-17.0F, -1.0F, 14.0F, 4.0F, 1.0F, 4.0F) // foot_2
                .texOffs(0, 104).addBox(15.0F, -9.0F, 15.0F, 2.0F, 8.0F, 2.0F) // leg_3
                .texOffs(8, 104).addBox(14.0F, -1.0F, 14.0F, 4.0F, 1.0F, 4.0F) // foot_3
                , PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(SpaceshipEntity ship, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }
}
