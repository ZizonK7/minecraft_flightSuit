package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Suit armor rendered from a 64x64 player-skin-layout texture (the suit is drawn as a skin for now -
 * real modeling comes later). Each part carries the skin's base cube plus its overlay cube, and each
 * slot gets its own inflation so pieces stack outward: legs < chest < boots < helmet. The per-slot
 * textures (tools/SkinSplitter.java) blank out everything that slot doesn't own, which is how leggings
 * and boots share one leg cube split at the knee.
 *
 * Suits with a model of their own (SuitType#ownModel: Hulkbuster, Trunks) use a SuitModel per slot instead,
 * all four on one texture; the Hulkbuster is drawn half again as big when it stands on its own (station,
 * flying pieces) - worn, the wearer's whole render is scaled instead (SuitSize).
 */
public final class SuitArmorModels {
    public static final ModelLayerLocation HELMET = layer("helmet");
    public static final ModelLayerLocation CHESTPLATE = layer("chestplate");
    public static final ModelLayerLocation LEGGINGS = layer("leggings");
    public static final ModelLayerLocation BOOTS = layer("boots");

    public static final ModelLayerLocation GLASSES = layer("edith_glasses");
    public static final ModelLayerLocation EMPTY = layer("empty");
    private static HumanoidModel<LivingEntity> emptyModel;

    private static final Map<EquipmentSlot, HumanoidModel<LivingEntity>> BAKED = new EnumMap<>(EquipmentSlot.class);
    /**
     * Separate instances for non-player wearers (companion suits): PlayerAnimator attaches a player's
     * animation state to any armor model a player model copies its pose into, so sharing one instance would
     * let a player's flight pose bleed into a companion's render.
     */
    private static final Map<EquipmentSlot, HumanoidModel<LivingEntity>> BAKED_OTHER = new EnumMap<>(EquipmentSlot.class);
    private static HumanoidModel<LivingEntity> glassesModel;

    /** A suit with its own model: layer, render-time limb offsets, its one texture and its size. */
    private record Custom(ModelLayerLocation layer, Supplier<LayerDefinition> mesh, float[][] shifts, ResourceLocation texture, float size) {
    }

    private static final Map<String, Custom> CUSTOM = Map.of(
            "hulkbuster_mk44", custom("hulkbuster_mk44", HulkbusterModel::createLayer, HulkbusterModel.SHIFTS, HulkbusterModel.SIZE),
            "trunks_mk5", custom("trunks_mk5", TrunksSuitModel::createLayer, TrunksSuitModel.SHIFTS, 1.0F));
    private static final Map<String, Map<EquipmentSlot, SuitModel>> CUSTOM_BAKED = new HashMap<>();
    private static final Map<String, Map<EquipmentSlot, SuitModel>> CUSTOM_OTHER = new HashMap<>();

    private SuitArmorModels() {
    }

    private static Custom custom(String id, Supplier<LayerDefinition> mesh, float[][] shifts, float size) {
        return new Custom(layer(id), mesh, shifts, new ResourceLocation(FlightSuitMod.MODID, "textures/models/armor/" + id + ".png"), size);
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(new ResourceLocation(FlightSuitMod.MODID, "suit_armor"), name);
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HELMET, () -> create(0.75F));
        event.registerLayerDefinition(CHESTPLATE, () -> create(0.40F));
        event.registerLayerDefinition(LEGGINGS, () -> create(0.30F));
        event.registerLayerDefinition(BOOTS, () -> create(0.50F));
        event.registerLayerDefinition(GLASSES, SuitArmorModels::createGlasses);
        event.registerLayerDefinition(EMPTY, SuitArmorModels::createEmpty);
        for (Custom custom : CUSTOM.values()) {
            event.registerLayerDefinition(custom.layer(), custom.mesh());
        }
    }

    /** Re-bakes on every resource reload (AddLayers fires each time). */
    public static void bake(EntityModelSet models) {
        BAKED.put(EquipmentSlot.HEAD, new HumanoidModel<>(models.bakeLayer(HELMET)));
        BAKED.put(EquipmentSlot.CHEST, new HumanoidModel<>(models.bakeLayer(CHESTPLATE)));
        BAKED.put(EquipmentSlot.LEGS, new HumanoidModel<>(models.bakeLayer(LEGGINGS)));
        BAKED.put(EquipmentSlot.FEET, new HumanoidModel<>(models.bakeLayer(BOOTS)));
        BAKED_OTHER.put(EquipmentSlot.HEAD, new HumanoidModel<>(models.bakeLayer(HELMET)));
        BAKED_OTHER.put(EquipmentSlot.CHEST, new HumanoidModel<>(models.bakeLayer(CHESTPLATE)));
        BAKED_OTHER.put(EquipmentSlot.LEGS, new HumanoidModel<>(models.bakeLayer(LEGGINGS)));
        BAKED_OTHER.put(EquipmentSlot.FEET, new HumanoidModel<>(models.bakeLayer(BOOTS)));
        glassesModel = new HumanoidModel<>(models.bakeLayer(GLASSES));
        emptyModel = new HumanoidModel<>(models.bakeLayer(EMPTY));
        for (Map.Entry<String, Custom> entry : CUSTOM.entrySet()) {
            Custom custom = entry.getValue();
            Map<EquipmentSlot, SuitModel> own = new EnumMap<>(EquipmentSlot.class);
            Map<EquipmentSlot, SuitModel> other = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                own.put(slot, new SuitModel(models.bakeLayer(custom.layer()), custom.shifts(), slot));
                other.put(slot, new SuitModel(models.bakeLayer(custom.layer()), custom.shifts(), slot));
            }
            CUSTOM_BAKED.put(entry.getKey(), own);
            CUSTOM_OTHER.put(entry.getKey(), other);
        }
    }

    /** A humanoid model with no geometry - what a cloaked stealth suit renders as. */
    public static HumanoidModel<LivingEntity> empty() {
        if (emptyModel == null) {
            bake(Minecraft.getInstance().getEntityModels());
        }
        return emptyModel;
    }

    public static LayerDefinition createEmpty() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (String part : new String[]{"head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            root.addOrReplaceChild(part, CubeListBuilder.create(), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 16, 16);
    }

    public static HumanoidModel<LivingEntity> glasses() {
        if (glassesModel == null) {
            bake(Minecraft.getInstance().getEntityModels());
        }
        return glassesModel;
    }

    /**
     * EDITH glasses: a thin frame across the eyes, two lenses and temple arms, just outside the skin's hat
     * layer. Every other part is empty. UVs match textures/models/armor/edith_glasses.png (tools/TextureGen).
     */
    public static LayerDefinition createGlasses() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.6F, -5.0F, -4.9F, 9.2F, 1.0F, 1.0F)   // brow bar
                .texOffs(0, 4).addBox(-3.8F, -4.0F, -4.8F, 3.0F, 2.0F, 1.0F)   // lens
                .texOffs(0, 4).addBox(0.8F, -4.0F, -4.8F, 3.0F, 2.0F, 1.0F)    // lens
                .texOffs(0, 8).addBox(-0.8F, -4.0F, -4.85F, 1.6F, 1.0F, 1.0F)  // bridge
                .texOffs(0, 12).addBox(-4.9F, -5.0F, -4.6F, 1.0F, 1.0F, 5.0F)  // temple
                .texOffs(0, 12).addBox(3.9F, -5.0F, -4.6F, 1.0F, 1.0F, 5.0F),  // temple
                PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Armor model for whoever is wearing the piece (players and everything else get separate instances). */
    public static HumanoidModel<LivingEntity> forWearer(LivingEntity wearer, String suitId, EquipmentSlot slot) {
        boolean player = wearer instanceof net.minecraft.world.entity.player.Player;
        forSlot(slot);
        if (CUSTOM.containsKey(suitId)) {
            SuitModel model = (player ? CUSTOM_BAKED : CUSTOM_OTHER).get(suitId).get(slot);
            // Trunks: the hilt leaves his back while the sword is in his hand.
            model.setSwordDrawn(wearer instanceof net.minecraft.world.entity.player.Player p ? HeroRenderer.isSwordOut(p)
                    : wearer.getMainHandItem().is(com.pfkfks.flightsuit.registry.ModItems.TRUNKS_SWORD.get()));
            return model;
        }
        return player ? BAKED.get(slot) : BAKED_OTHER.get(slot);
    }

    /** The model a suit's piece is drawn with on its own (station, flying pieces, first-person arm). */
    public static HumanoidModel<LivingEntity> forSlot(String suitId, EquipmentSlot slot) {
        HumanoidModel<LivingEntity> plain = forSlot(slot);
        Map<EquipmentSlot, SuitModel> own = CUSTOM_BAKED.get(suitId);
        return own != null ? own.get(slot) : plain;
    }

    /** How big the suit is drawn standing on its own (1 except the Hulkbuster). */
    public static float size(String suitId) {
        Custom custom = CUSTOM.get(suitId);
        return custom == null ? 1.0F : custom.size();
    }

    public static HumanoidModel<LivingEntity> forSlot(EquipmentSlot slot) {
        if (BAKED.isEmpty()) {
            bake(Minecraft.getInstance().getEntityModels());
        }
        return BAKED.get(slot);
    }

    public static ResourceLocation texture(String suitId, EquipmentSlot slot) {
        Custom custom = CUSTOM.get(suitId);
        if (custom != null) {
            return custom.texture();
        }
        String piece = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            default -> "boots";
        };
        return new ResourceLocation(FlightSuitMod.MODID, "textures/models/armor/" + suitId + "_" + piece + ".png");
    }

    /** Same visibility rules as vanilla HumanoidArmorLayer#setPartVisibility. */
    public static void showOnly(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> model.head.visible = true;
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
        }
    }

    public static LayerDefinition create(float inflate) {
        CubeDeformation base = new CubeDeformation(inflate);
        CubeDeformation outer = base.extend(0.25F);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, base)
                .texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, outer), PartPose.ZERO);
        // HumanoidModel requires a "hat" part; the hat overlay already lives on the head cube above.
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, base)
                .texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, outer), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, base)
                .texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, base)
                .texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, base)
                .texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, base)
                .texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer), PartPose.offset(1.9F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
