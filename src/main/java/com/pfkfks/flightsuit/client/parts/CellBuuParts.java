package com.pfkfks.flightsuit.client.parts;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * M18 Cell's and Buu's parts: Cell's head crest in each form (imperfect, semi-perfect, perfect, the Cell Jr.), his
 * wings and the imperfect form's tail; Buu's head antenna (fat, super, kid). Groups: {@code crest_imperfect},
 * {@code crest_semi}, {@code crest}, {@code crest_jr}, {@code antenna_fat}, {@code antenna_super}, {@code antenna_kid}
 * under the head; {@code wings}, {@code tail} under the body.
 *
 * Boxes, UVs and textures/entity/dbz/parts/cell_buu.png come from tools/ModelGen.java (parts_cell_buu).
 */
public class CellBuuParts extends DbzPartsModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(FlightSuitMod.MODID, "parts_cell_buu"), "main");
    private static final ResourceLocation TEXTURE = new ResourceLocation(FlightSuitMod.MODID, "textures/entity/dbz/parts/cell_buu.png");
    public static final List<String> GROUPS = List.of("crest_imperfect", "crest_semi", "crest", "crest_jr", "wings", "tail", "antenna_fat", "antenna_super", "antenna_kid");

    public CellBuuParts(ModelPart root) {
        super(root);
    }

    @Override
    public ResourceLocation texture() {
        return TEXTURE;
    }

    @Override
    public List<String> groups() {
        return GROUPS;
    }

    /** The wings' resting spread (each wing's yRot, outer edge back) - the same value as ModelGen.CellBuuParts.WING_SPREAD. */
    private static final float WING_SPREAD = 0.25F;
    /** The tail's bend at rest: the group, then tail_2..tail_5, then the bulb - the same values as ModelGen.CellBuuParts.TAIL_BEND. */
    private static final float[] TAIL_BEND = {-0.7F, 0.35F, 0.4F, 0.45F, 0.45F, 0.2F};
    private static final String[] TAIL_SEGMENTS = {"tail_2", "tail_3", "tail_4", "tail_5", "tail_bulb"};
    private static final String[][] ANTENNAE = {{"antenna_fat", "af_mid", "af_tip"}, {"antenna_super", "as_mid", "as_tip"}, {"antenna_kid", "ak_mid", "ak_tip"}};

    @Override
    public void animate(DbzFighterEntity fighter, float ageInTicks) {
        boolean flying = fighter.getAction() == com.pfkfks.flightsuit.planet.dbz.DbzAction.FLY;

        // The tail: a slow side-to-side sway and a smaller lift that travel down the segments with a lag each, so the
        // tip follows the root like a whip; in flight it streams back a little straighter.
        ModelPart tail = group("tail");
        if (tail != null) {
            float t = ageInTicks * 0.09F;
            float lift = flying ? 0.35F : 0.0F;
            tail.xRot = TAIL_BEND[0] + lift + Mth.sin(t) * 0.08F;
            tail.yRot = Mth.sin(t * 0.7F) * 0.25F;
            ModelPart segment = tail;
            for (int i = 0; i < TAIL_SEGMENTS.length && segment.hasChild(TAIL_SEGMENTS[i]); i++) {
                segment = segment.getChild(TAIL_SEGMENTS[i]);
                float lag = (i + 1) * 0.55F;
                segment.xRot = TAIL_BEND[i + 1] - lift * 0.15F + Mth.sin(t - lag) * (0.05F + i * 0.025F);
                segment.yRot = Mth.sin(t * 0.7F - lag) * 0.09F;
            }
        }

        // Buu's antenna: a soft wobble, each segment a little behind the one below it.
        for (String[] names : ANTENNAE) {
            ModelPart antenna = group(names[0]);
            if (antenna == null) {
                continue;
            }
            float t = ageInTicks * 0.16F;
            antenna.zRot = Mth.sin(t) * 0.05F;
            ModelPart segment = antenna;
            for (int i = 1; i < names.length && segment.hasChild(names[i]); i++) {
                segment = segment.getChild(names[i]);
                segment.zRot = Mth.sin(t - i * 0.7F) * (0.05F + i * 0.03F);
            }
        }

        // The wings: folded against the back in flight; at rest held a little open, breathing slowly.
        ModelPart wings = group("wings");
        if (wings != null) {
            float breath = flying ? 0.0F : Mth.sin(ageInTicks * 0.07F) * 0.03F;
            float spread = flying ? 0.05F : WING_SPREAD + breath;
            wings.xRot = flying ? 0.0F : 0.06F + breath;
            if (wings.hasChild("wing_r")) {
                wings.getChild("wing_r").yRot = spread;
            }
            if (wings.hasChild("wing_l")) {
                wings.getChild("wing_l").yRot = -spread;
            }
        }
    }

    // <ModelGen:parts_cell_buu>
    // Generated by tools/ModelGen.java - edit the layout there, not here.
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition crest_imperfect = head.addOrReplaceChild("crest_imperfect", CubeListBuilder.create(),
                PartPose.offset(0.0F, -8.5F, -1.5F));
        PartDefinition ci_r = crest_imperfect.addOrReplaceChild("ci_r", CubeListBuilder.create()
                .texOffs(88, 0).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 4.0F, 3.0F), // ci_r_base
                PartPose.offsetAndRotation(-2.0F, 0.0F, 0.0F, -0.6F, 0.0F, 0.2F));
        PartDefinition ci_r_mid = ci_r.addOrReplaceChild("ci_r_mid", CubeListBuilder.create()
                .texOffs(0, 17).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), // ci_r_mid
                PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, -0.45F, 0.0F, 0.12F));
        ci_r_mid.addOrReplaceChild("ci_r_tip", CubeListBuilder.create()
                .texOffs(104, 17).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F), // ci_r_tip
                PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, -0.45F, 0.0F, 0.1F));
        PartDefinition ci_l = crest_imperfect.addOrReplaceChild("ci_l", CubeListBuilder.create()
                .texOffs(100, 0).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 4.0F, 3.0F), // ci_l_base
                PartPose.offsetAndRotation(2.0F, 0.0F, 0.0F, -0.6F, 0.0F, -0.2F));
        PartDefinition ci_l_mid = ci_l.addOrReplaceChild("ci_l_mid", CubeListBuilder.create()
                .texOffs(8, 17).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), // ci_l_mid
                PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, -0.45F, 0.0F, -0.12F));
        ci_l_mid.addOrReplaceChild("ci_l_tip", CubeListBuilder.create()
                .texOffs(108, 17).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F), // ci_l_tip
                PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, -0.45F, 0.0F, -0.1F));
        PartDefinition crest_semi = head.addOrReplaceChild("crest_semi", CubeListBuilder.create(),
                PartPose.offset(0.0F, -8.5F, -0.5F));
        PartDefinition cs_r = crest_semi.addOrReplaceChild("cs_r", CubeListBuilder.create()
                .texOffs(112, 0).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 4.0F, 3.0F), // cs_r_base
                PartPose.offsetAndRotation(-2.8F, 0.0F, 0.0F, -0.3F, 0.0F, 0.65F));
        cs_r.addOrReplaceChild("cs_r_tip", CubeListBuilder.create()
                .texOffs(16, 17).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), // cs_r_tip
                PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, -0.25F, 0.0F, 0.3F));
        PartDefinition cs_l = crest_semi.addOrReplaceChild("cs_l", CubeListBuilder.create()
                .texOffs(0, 10).addBox(-1.5F, -3.0F, -1.5F, 3.0F, 4.0F, 3.0F), // cs_l_base
                PartPose.offsetAndRotation(2.8F, 0.0F, 0.0F, -0.3F, 0.0F, -0.65F));
        cs_l.addOrReplaceChild("cs_l_tip", CubeListBuilder.create()
                .texOffs(24, 17).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), // cs_l_tip
                PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, -0.25F, 0.0F, -0.3F));
        PartDefinition crest = head.addOrReplaceChild("crest", CubeListBuilder.create(),
                PartPose.offset(0.0F, -8.5F, 0.5F));
        PartDefinition cp_r = crest.addOrReplaceChild("cp_r", CubeListBuilder.create()
                .texOffs(12, 0).addBox(-1.0F, -3.0F, -2.0F, 2.0F, 4.0F, 4.0F), // cp_r_base
                PartPose.offsetAndRotation(-3.3F, 0.0F, 0.0F, -0.35F, 0.0F, 0.3F));
        PartDefinition cp_r_mid = cp_r.addOrReplaceChild("cp_r_mid", CubeListBuilder.create()
                .texOffs(60, 10).addBox(-1.0F, -3.0F, -1.5F, 2.0F, 3.0F, 3.0F), // cp_r_mid
                PartPose.offsetAndRotation(0.0F, -2.8F, -0.5F, -0.15F, 0.0F, 0.15F));
        cp_r_mid.addOrReplaceChild("cp_r_tip", CubeListBuilder.create()
                .texOffs(92, 17).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F), // cp_r_tip
                PartPose.offsetAndRotation(0.0F, -2.8F, -0.5F, -0.1F, 0.0F, 0.1F));
        PartDefinition cp_l = crest.addOrReplaceChild("cp_l", CubeListBuilder.create()
                .texOffs(24, 0).addBox(-1.0F, -3.0F, -2.0F, 2.0F, 4.0F, 4.0F), // cp_l_base
                PartPose.offsetAndRotation(3.3F, 0.0F, 0.0F, -0.35F, 0.0F, -0.3F));
        PartDefinition cp_l_mid = cp_l.addOrReplaceChild("cp_l_mid", CubeListBuilder.create()
                .texOffs(70, 10).addBox(-1.0F, -3.0F, -1.5F, 2.0F, 3.0F, 3.0F), // cp_l_mid
                PartPose.offsetAndRotation(0.0F, -2.8F, -0.5F, -0.15F, 0.0F, -0.15F));
        cp_l_mid.addOrReplaceChild("cp_l_tip", CubeListBuilder.create()
                .texOffs(98, 17).addBox(-0.5F, -2.0F, -1.0F, 1.0F, 2.0F, 2.0F), // cp_l_tip
                PartPose.offsetAndRotation(0.0F, -2.8F, -0.5F, -0.1F, 0.0F, -0.1F));
        PartDefinition crest_jr = head.addOrReplaceChild("crest_jr", CubeListBuilder.create(),
                PartPose.offset(0.0F, -8.5F, 0.5F));
        PartDefinition cj_r = crest_jr.addOrReplaceChild("cj_r", CubeListBuilder.create()
                .texOffs(80, 10).addBox(-1.0F, -2.0F, -1.5F, 2.0F, 3.0F, 3.0F), // cj_r_base
                PartPose.offsetAndRotation(-3.3F, 0.0F, 0.0F, -0.3F, 0.0F, 0.3F));
        cj_r.addOrReplaceChild("cj_r_tip", CubeListBuilder.create()
                .texOffs(56, 17).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F), // cj_r_tip
                PartPose.offsetAndRotation(0.0F, -1.8F, -0.5F, -0.2F, 0.0F, 0.15F));
        PartDefinition cj_l = crest_jr.addOrReplaceChild("cj_l", CubeListBuilder.create()
                .texOffs(90, 10).addBox(-1.0F, -2.0F, -1.5F, 2.0F, 3.0F, 3.0F), // cj_l_base
                PartPose.offsetAndRotation(3.3F, 0.0F, 0.0F, -0.3F, 0.0F, -0.3F));
        cj_l.addOrReplaceChild("cj_l_tip", CubeListBuilder.create()
                .texOffs(62, 17).addBox(-0.5F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F), // cj_l_tip
                PartPose.offsetAndRotation(0.0F, -1.8F, -0.5F, -0.2F, 0.0F, -0.15F));
        PartDefinition antenna_fat = head.addOrReplaceChild("antenna_fat", CubeListBuilder.create()
                .texOffs(12, 10).addBox(-1.5F, -2.5F, -1.5F, 3.0F, 4.0F, 3.0F), // af_base
                PartPose.offsetAndRotation(0.0F, -8.5F, 0.0F, 0.3F, 0.0F, 0.0F));
        PartDefinition af_mid = antenna_fat.addOrReplaceChild("af_mid", CubeListBuilder.create()
                .texOffs(32, 17).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 3.0F, 2.0F), // af_mid
                PartPose.offsetAndRotation(0.0F, -2.3F, 0.0F, 0.6F, 0.0F, 0.0F));
        af_mid.addOrReplaceChild("af_tip", CubeListBuilder.create()
                .texOffs(84, 17).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F), // af_tip
                PartPose.offsetAndRotation(0.0F, -2.3F, 0.0F, 0.8F, 0.0F, 0.0F));
        PartDefinition antenna_super = head.addOrReplaceChild("antenna_super", CubeListBuilder.create()
                .texOffs(100, 10).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 4.0F, 2.0F), // as_base
                PartPose.offsetAndRotation(0.0F, -8.5F, -1.0F, -0.9F, 0.0F, 0.0F));
        PartDefinition as_mid = antenna_super.addOrReplaceChild("as_mid", CubeListBuilder.create()
                .texOffs(40, 17).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), // as_mid
                PartPose.offsetAndRotation(0.0F, -2.3F, 0.0F, 0.45F, 0.0F, 0.0F));
        as_mid.addOrReplaceChild("as_tip", CubeListBuilder.create()
                .texOffs(80, 17).addBox(-0.5F, -3.5F, -0.5F, 1.0F, 4.0F, 1.0F), // as_tip
                PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, 0.55F, 0.0F, 0.0F));
        PartDefinition antenna_kid = head.addOrReplaceChild("antenna_kid", CubeListBuilder.create()
                .texOffs(108, 10).addBox(-1.0F, -2.5F, -1.0F, 2.0F, 4.0F, 2.0F), // ak_base
                PartPose.offsetAndRotation(0.0F, -8.5F, -0.5F, -0.1F, 0.0F, 0.0F));
        PartDefinition ak_mid = antenna_kid.addOrReplaceChild("ak_mid", CubeListBuilder.create()
                .texOffs(48, 17).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F), // ak_mid
                PartPose.offsetAndRotation(0.0F, -2.3F, 0.0F, -0.55F, 0.0F, 0.0F));
        ak_mid.addOrReplaceChild("ak_tip", CubeListBuilder.create()
                .texOffs(112, 17).addBox(-0.5F, -2.5F, -0.5F, 1.0F, 3.0F, 1.0F), // ak_tip
                PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, -0.9F, 0.0F, 0.0F));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        PartDefinition wings = body.addOrReplaceChild("wings", CubeListBuilder.create(),
                PartPose.offset(0.0F, 1.0F, 2.25F));
        PartDefinition wing_r = wings.addOrReplaceChild("wing_r", CubeListBuilder.create()
                .texOffs(36, 0).addBox(-5.0F, 0.0F, 0.0F, 5.0F, 7.0F, 1.0F) // wing_r_plate
                .texOffs(0, 0).addBox(-1.5F, -0.5F, 0.5F, 1.0F, 8.0F, 2.0F) // wing_r_fold
                .texOffs(4, 22).addBox(-4.0F, -0.5F, 0.25F, 3.0F, 1.0F, 1.0F), // wing_r_shoulder
                PartPose.offsetAndRotation(-0.25F, 0.0F, 0.0F, 0.0F, 0.25F, 0.1F));
        PartDefinition wing_r_low = wing_r.addOrReplaceChild("wing_r_low", CubeListBuilder.create()
                .texOffs(68, 17).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 4.0F, 1.0F), // wing_r_low
                PartPose.offsetAndRotation(-3.5F, 7.0F, 0.0F, 0.05F, 0.0F, 0.3F));
        wing_r_low.addOrReplaceChild("wing_r_tip", CubeListBuilder.create()
                .texOffs(122, 17).addBox(-0.5F, -0.5F, 0.25F, 1.0F, 2.0F, 1.0F), // wing_r_tip
                PartPose.offsetAndRotation(-0.5F, 3.5F, 0.0F, 0.05F, 0.0F, 0.3F));
        PartDefinition wing_l = wings.addOrReplaceChild("wing_l", CubeListBuilder.create()
                .texOffs(48, 0).addBox(0.0F, 0.0F, 0.0F, 5.0F, 7.0F, 1.0F) // wing_l_plate
                .texOffs(6, 0).addBox(0.5F, -0.5F, 0.5F, 1.0F, 8.0F, 2.0F) // wing_l_fold
                .texOffs(12, 22).addBox(1.0F, -0.5F, 0.25F, 3.0F, 1.0F, 1.0F), // wing_l_shoulder
                PartPose.offsetAndRotation(0.25F, 0.0F, 0.0F, 0.0F, -0.25F, -0.1F));
        PartDefinition wing_l_low = wing_l.addOrReplaceChild("wing_l_low", CubeListBuilder.create()
                .texOffs(74, 17).addBox(-1.0F, -0.5F, 0.0F, 2.0F, 4.0F, 1.0F), // wing_l_low
                PartPose.offsetAndRotation(3.5F, 7.0F, 0.0F, 0.05F, 0.0F, -0.3F));
        wing_l_low.addOrReplaceChild("wing_l_tip", CubeListBuilder.create()
                .texOffs(0, 22).addBox(-0.5F, -0.5F, 0.25F, 1.0F, 2.0F, 1.0F), // wing_l_tip
                PartPose.offsetAndRotation(0.5F, 3.5F, 0.0F, 0.05F, 0.0F, -0.3F));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(60, 0).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 4.0F), // tail_1
                PartPose.offsetAndRotation(0.0F, 10.0F, 2.25F, -0.7F, 0.0F, 0.0F));
        PartDefinition tail_2 = tail.addOrReplaceChild("tail_2", CubeListBuilder.create()
                .texOffs(74, 0).addBox(-1.5F, -1.5F, -0.5F, 3.0F, 3.0F, 4.0F), // tail_2
                PartPose.offsetAndRotation(0.0F, 0.0F, 4.0F, 0.35F, 0.0F, 0.0F));
        PartDefinition tail_3 = tail_2.addOrReplaceChild("tail_3", CubeListBuilder.create()
                .texOffs(24, 10).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 4.0F), // tail_3
                PartPose.offsetAndRotation(0.0F, 0.0F, 3.5F, 0.4F, 0.0F, 0.0F));
        PartDefinition tail_4 = tail_3.addOrReplaceChild("tail_4", CubeListBuilder.create()
                .texOffs(36, 10).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 4.0F), // tail_4
                PartPose.offsetAndRotation(0.0F, 0.0F, 3.5F, 0.45F, 0.0F, 0.0F));
        PartDefinition tail_5 = tail_4.addOrReplaceChild("tail_5", CubeListBuilder.create()
                .texOffs(116, 10).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 3.0F), // tail_5
                PartPose.offsetAndRotation(0.0F, 0.0F, 3.5F, 0.45F, 0.0F, 0.0F));
        tail_5.addOrReplaceChild("tail_bulb", CubeListBuilder.create()
                .texOffs(48, 10).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 3.0F) // tail_bulb
                .texOffs(116, 17).addBox(-0.5F, -0.5F, 3.0F, 1.0F, 1.0F, 2.0F), // tail_needle
                PartPose.offsetAndRotation(0.0F, 0.0F, 2.5F, 0.2F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }
    // </ModelGen:parts_cell_buu>
}
