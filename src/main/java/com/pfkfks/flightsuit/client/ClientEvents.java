package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.CommandAttackC2SPacket;
import com.pfkfks.flightsuit.network.CounterC2SPacket;
import com.pfkfks.flightsuit.suit.CounterHandler;
import com.pfkfks.flightsuit.suit.SuitTuning;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.SuitToggleC2SPacket;
import com.pfkfks.flightsuit.network.SuitWheelC2SPacket;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitSize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        tickSuitKey();
        tickCounterKey(player);
        while (ModKeys.SUIT_WHEEL.consumeClick()) {
            ModNetwork.sendToServer(SuitWheelC2SPacket.open());
        }
        while (ModKeys.COMMAND_ATTACK.consumeClick()) {
            ModNetwork.sendToServer(new CommandAttackC2SPacket());
        }
        SuitFlightClient.tick(player);
        WeaponInput.tick(minecraft, player);
        RemoteLinkClient.tick(player);
        CinematicCamera.tick();
        if (!minecraft.isPaused()) {
            SuitAnimator.spawnThrusterParticles(minecraft.level);
            ClientWeapons.tick(minecraft.level);
            spawnCloakShimmer(minecraft);
        }
    }

    /** G is a tap/hold key: a hold fires once at the threshold, a release before it counts as a tap. */
    private static final int HOLD_TICKS = 10;
    private static int suitKeyHeld = -1;
    private static boolean holdSent;

    /** A faint heat-shimmer around cloaked stealth suits, so a careful eye can still spot them. */
    private static void spawnCloakShimmer(Minecraft minecraft) {
        for (net.minecraft.client.player.AbstractClientPlayer other : minecraft.level.players()) {
            if (other.isInvisible() && other.getRandom().nextInt(3) == 0
                    && com.pfkfks.flightsuit.suit.StealthHandler.wearsStealthSuit(com.pfkfks.flightsuit.suit.WornSuit.of(other))) {
                minecraft.level.addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                        other.getRandomX(0.6D), other.getRandomY(), other.getRandomZ(0.6D), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static void tickSuitKey() {
        while (ModKeys.SUIT_TOGGLE.consumeClick()) {
            // Edge detection is done with isDown below; drain the click queue so it can't pile up.
            if (suitKeyHeld < 0) {
                suitKeyHeld = 0;
                holdSent = false;
            }
        }
        if (ModKeys.SUIT_TOGGLE.isDown()) {
            if (suitKeyHeld < 0) {
                suitKeyHeld = 0;
                holdSent = false;
            } else {
                suitKeyHeld++;
            }
            if (!holdSent && suitKeyHeld >= HOLD_TICKS) {
                holdSent = true;
                ModNetwork.sendToServer(new SuitToggleC2SPacket(true));
            }
        } else if (suitKeyHeld >= 0) {
            if (!holdSent) {
                ModNetwork.sendToServer(new SuitToggleC2SPacket(false));
            }
            suitKeyHeld = -1;
        }
    }

    /** Counter key: the press itself opens the parry window; keep holding and it becomes the shield. */
    private static int counterHeld = -1;
    private static boolean shieldSent;

    private static void tickCounterKey(LocalPlayer player) {
        boolean pressedThisTick = false;
        while (ModKeys.COUNTER.consumeClick()) {
            pressedThisTick = true;
        }
        boolean wearingChest = player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SuitArmorItem;
        if ((pressedThisTick || ModKeys.COUNTER.isDown()) && counterHeld < 0 && wearingChest) {
            counterHeld = 0;
            shieldSent = false;
            ModNetwork.sendToServer(new CounterC2SPacket(CounterHandler.PRESS));
        } else if (ModKeys.COUNTER.isDown() && counterHeld >= 0) {
            counterHeld++;
            if (!shieldSent && counterHeld >= SuitTuning.SHIELD_HOLD_TICKS) {
                shieldSent = true;
                ModNetwork.sendToServer(new CounterC2SPacket(CounterHandler.SHIELD_ON));
            }
        }
        if (!ModKeys.COUNTER.isDown() && counterHeld >= 0) {
            if (shieldSent) {
                ModNetwork.sendToServer(new CounterC2SPacket(CounterHandler.RELEASE));
            }
            counterHeld = -1;
        }
    }

    /**
     * Vanilla never draws armor on the first-person arm, so the bare skin shows. With a suit chestplate on,
     * draw the suit's arm in its place instead. Pose mirrors PlayerRenderer#renderHand (setupAnim at age 0
     * -> idle arm bob of 0.1 rad roll, then xRot zeroed) without running setupAnim, which PlayerAnimator
     * would hook and could swing the arm out of view.
     */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof SuitArmorItem armor)) {
            return;
        }
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        String suitId = armor.getSuitType().id();
        HumanoidModel<LivingEntity> model = SuitArmorModels.forSlot(suitId, EquipmentSlot.CHEST);
        ResourceLocation texture = SuitArmorModels.texture(suitId, EquipmentSlot.CHEST);
        VertexConsumer buffer = event.getMultiBufferSource().getBuffer(RenderType.armorCutoutNoCull(texture));
        if (model instanceof SuitModel own) {
            own.renderArm(event.getPoseStack(), buffer, event.getPackedLight(), right);
        } else {
            ModelPart arm = right ? model.rightArm : model.leftArm;
            arm.visible = true;
            arm.setPos(right ? -5.0F : 5.0F, 2.0F, 0.0F);
            arm.setRotation(0.0F, 0.0F, right ? 0.1F : -0.1F);
            arm.render(event.getPoseStack(), buffer, event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        }
        event.setCanceled(true);
    }

    /**
     * A grown Hulkbuster wearer (SuitSize) is drawn half again as big from their feet - suit, held items and all
     * - and the skin underneath is hidden so it can't poke out of the bigger frame. Lowest priority and only when
     * nobody cancelled, so the push here always meets the pop in onRenderPlayerPost.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        float size = SuitSize.drawn(event.getEntity());
        if (size == 1.0F) {
            return;
        }
        event.getPoseStack().pushPose();
        event.getPoseStack().scale(size, size, size);
        event.getRenderer().getModel().setAllVisible(false);
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (SuitSize.drawn(event.getEntity()) != 1.0F) {
            event.getPoseStack().popPose();
        }
    }

    /** Stand still while the suit assembles around you. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Input input = event.getInput();
        if (SuitAnimator.isShielding(event.getEntity())) {
            // Bracing behind the shield: walk slowly, like holding up a vanilla shield.
            input.forwardImpulse *= 0.35F;
            input.leftImpulse *= 0.35F;
        }
        if (!CinematicCamera.isActive()) {
            return;
        }
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CinematicCamera.reset();
        SuitFlightClient.reset();
        RemoteLinkClient.reset();
        WeaponInput.reset();
        ClientWeapons.reset();
    }
}
