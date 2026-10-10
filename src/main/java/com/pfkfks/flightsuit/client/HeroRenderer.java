package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.ClawshotS2CPacket;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Mark 4 visuals: the Master Sword in the empty hand while the hero slashes or spins (third person through a
 * player render layer, first person by drawing it in place of the bare arm), and the clawshot - a chain from
 * the hand (a player's or a Mark 4 companion's) to a claw, following the server's claw position tick by tick.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class HeroRenderer {
    private static final ResourceLocation CHAIN = new ResourceLocation("textures/block/chain.png");
    /** Spin attacks: the sword stays drawn until this game tick, by entity id. */
    private static final Map<Integer, Long> SWORD_UNTIL = new HashMap<>();
    private static final Map<Integer, Hook> HOOKS = new HashMap<>();
    private static ItemStack sword;
    private static ItemStack trunksSword;

    private static final class Hook {
        Vec3 previous;
        Vec3 tip;

        Hook(Vec3 tip) {
            this.previous = tip;
            this.tip = tip;
        }
    }

    private HeroRenderer() {
    }

    private static ItemStack sword() {
        if (sword == null) {
            sword = new ItemStack(ModItems.MASTER_SWORD.get());
        }
        return sword;
    }

    /** The blade the wearer draws: Trunks' sword for Mark 5 (M17), the Master Sword otherwise. */
    private static ItemStack sword(Player player) {
        if (SuitWeapons.armedClass(player) == com.pfkfks.flightsuit.suit.SuitClass.SWORDSMAN) {
            if (trunksSword == null) {
                trunksSword = new ItemStack(ModItems.TRUNKS_SWORD.get());
            }
            return trunksSword;
        }
        return sword();
    }

    public static void showSword(int entityId, int ticks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            SWORD_UNTIL.put(entityId, level.getGameTime() + ticks);
        }
    }

    /** Swinging (primary held) or mid spin attack, with nothing in the main hand. */
    public static boolean isSwordOut(Player player) {
        if (!player.getMainHandItem().isEmpty()) {
            return false;
        }
        if (SuitWeapons.drawsSword(ClientWeapons.firing(player.getId()))) {
            return true;
        }
        Long until = SWORD_UNTIL.get(player.getId());
        return until != null && until > player.level().getGameTime();
    }

    public static void handleClawshot(ClawshotS2CPacket packet) {
        if (!packet.active) {
            HOOKS.remove(packet.entityId);
            return;
        }
        Hook hook = HOOKS.get(packet.entityId);
        if (hook == null) {
            HOOKS.put(packet.entityId, new Hook(packet.tip));
        } else {
            hook.previous = hook.tip;
            hook.tip = packet.tip;
        }
    }

    public static void reset() {
        SWORD_UNTIL.clear();
        HOOKS.clear();
    }

    // ---------------------------------------------------------------- the sword

    /** Third person: the sword in the right hand, posed like any held sword (same transform as vanilla's held items). */
    public static final class SwordLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final ItemInHandRenderer itemInHandRenderer;

        public SwordLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ItemInHandRenderer itemInHandRenderer) {
            super(parent);
            this.itemInHandRenderer = itemInHandRenderer;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!isSwordOut(player)) {
                return;
            }
            HumanoidArm arm = player.getMainArm();
            boolean left = arm == HumanoidArm.LEFT;
            poseStack.pushPose();
            getParentModel().translateToHand(arm, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
            itemInHandRenderer.renderItem(player, sword(player), left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, poseStack, buffers, light);
            poseStack.popPose();
        }
    }

    /** First person: the sword instead of the bare arm, swinging with vanilla's attack motion. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !isSwordOut(player)) {
            return;
        }
        event.setCanceled(true);
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        int side = right ? 1 : -1;
        float swing = event.getSwingProgress();
        float root = Mth.sqrt(swing);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // ItemInHandRenderer's swing offset, arm placement and attack rotation for a held item.
        poseStack.translate(side * -0.4F * Mth.sin(root * Mth.PI), 0.2F * Mth.sin(root * Mth.PI * 2.0F), -0.2F * Mth.sin(swing * Mth.PI));
        poseStack.translate(side * 0.56F, -0.52F, -0.72F);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0F - 20.0F * Mth.sin(swing * swing * Mth.PI))));
        float lift = Mth.sin(root * Mth.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * lift * -20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(lift * -80.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
        minecraft.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, sword(player),
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !right,
                poseStack, event.getMultiBufferSource(), event.getPackedLight());
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- the clawshot

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || HOOKS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float partialTick = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        HOOKS.entrySet().removeIf(entry -> !(level.getEntity(entry.getKey()) instanceof LivingEntity));
        for (Map.Entry<Integer, Hook> entry : HOOKS.entrySet()) {
            LivingEntity holder = (LivingEntity) level.getEntity(entry.getKey());
            Hook hook = entry.getValue();
            Vec3 tip = hook.previous.lerp(hook.tip, partialTick);
            Vec3 hand = hand(holder, partialTick);
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            drawChain(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN)), hand, tip,
                    LevelRenderer.getLightColor(level, BlockPos.containing(hand.lerp(tip, 0.5D))));
            drawClaw(minecraft, level, poseStack, buffers, hand, tip);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    /** Where the chain leaves from: the right palm, as the beams use. */
    private static Vec3 hand(LivingEntity holder, float partialTick) {
        Vec3 eye = holder.getEyePosition(partialTick);
        Vec3 look = holder.getViewVector(partialTick);
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return eye.add(look.scale(0.55D)).add(right.scale(0.33D)).add(0.0D, -0.28D, 0.0D);
    }

    /** Two crossed strips of the vanilla chain texture (front and side views of the links), one link per block. */
    private static void drawChain(PoseStack poseStack, VertexConsumer consumer, Vec3 from, Vec3 to, int light) {
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 0.05D) {
            return;
        }
        dir = dir.scale(1.0D / length);
        Vec3 a = dir.cross(Math.abs(dir.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D)).normalize().scale(0.09D);
        Vec3 b = dir.cross(a).normalize().scale(0.09D);
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        Vec3[] sides = {a, b};
        for (int s = 0; s < 2; s++) {
            Vec3 side = sides[s];
            float u0 = s * 3.0F / 16.0F;
            float u1 = u0 + 3.0F / 16.0F;
            for (double start = 0.0D; start < length; start += 1.0D) {
                double end = Math.min(length, start + 1.0D);
                Vec3 p0 = from.add(dir.scale(start));
                Vec3 p1 = from.add(dir.scale(end));
                float v1 = (float) (end - start);
                vertex(consumer, pose, normal, p0.add(side), u0, 0.0F, light);
                vertex(consumer, pose, normal, p0.subtract(side), u1, 0.0F, light);
                vertex(consumer, pose, normal, p1.subtract(side), u1, v1, light);
                vertex(consumer, pose, normal, p1.add(side), u0, v1, light);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, Vec3 p, float u, float v, int light) {
        consumer.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
    }

    /** The claw itself: a tripwire hook pointing along the chain. */
    private static void drawClaw(Minecraft minecraft, ClientLevel level, PoseStack poseStack, MultiBufferSource buffers, Vec3 from, Vec3 tip) {
        Vec3 dir = tip.subtract(from);
        if (dir.lengthSqr() < 1.0E-4D) {
            return;
        }
        dir = dir.normalize();
        poseStack.pushPose();
        poseStack.translate(tip.x, tip.y, tip.z);
        poseStack.mulPose(Axis.YP.rotation((float) Mth.atan2(dir.x, dir.z)));
        poseStack.mulPose(Axis.XP.rotation((float) -Math.asin(dir.y) + Mth.HALF_PI));
        poseStack.scale(0.7F, 0.7F, 0.7F);
        minecraft.getItemRenderer().renderStatic(new ItemStack(Items.TRIPWIRE_HOOK), ItemDisplayContext.FIXED,
                LevelRenderer.getLightColor(level, BlockPos.containing(tip)), OverlayTexture.NO_OVERLAY, poseStack, buffers, level, 0);
        poseStack.popPose();
    }
}
