package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.CommandAttackC2SPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.RepulsorC2SPacket;
import com.pfkfks.flightsuit.network.SuitToggleC2SPacket;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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
        while (ModKeys.COMMAND_ATTACK.consumeClick()) {
            ModNetwork.sendToServer(new CommandAttackC2SPacket());
        }
        SuitFlightClient.tick(player);
        CinematicCamera.tick();
        if (!minecraft.isPaused()) {
            SuitAnimator.spawnThrusterParticles(minecraft.level);
        }
    }

    /** G is a tap/hold key: a hold fires once at the threshold, a release before it counts as a tap. */
    private static final int HOLD_TICKS = 10;
    private static int suitKeyHeld = -1;
    private static boolean holdSent;

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

    /** Empty-hand right click on air fires the palm repulsor (needs the chestplate - it carries the arms). */
    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity() != Minecraft.getInstance().player) {
            return;
        }
        if (event.getEntity().getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SuitArmorItem) {
            ModNetwork.sendToServer(new RepulsorC2SPacket());
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
        HumanoidModel<LivingEntity> model = SuitArmorModels.forSlot(EquipmentSlot.CHEST);
        ModelPart arm = right ? model.rightArm : model.leftArm;
        arm.visible = true;
        arm.setPos(right ? -5.0F : 5.0F, 2.0F, 0.0F);
        arm.setRotation(0.0F, 0.0F, right ? 0.1F : -0.1F);
        ResourceLocation texture = SuitArmorModels.texture(armor.getSuitType().id(), EquipmentSlot.CHEST);
        arm.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.armorCutoutNoCull(texture)),
                event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        event.setCanceled(true);
    }

    /** Stand still while the suit assembles around you. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!CinematicCamera.isActive()) {
            return;
        }
        Input input = event.getInput();
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
    }
}
