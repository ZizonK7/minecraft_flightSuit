package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.CounterC2SPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.WeaponC2SPacket;
import com.pfkfks.flightsuit.suit.CounterHandler;
import com.pfkfks.flightsuit.suit.SuitClass;
import com.pfkfks.flightsuit.suit.SuitSkills;
import com.pfkfks.flightsuit.suit.SuitTuning;
import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Mark 3's Z key.
 * - Tapped: aims the shadow step (after the M16 test: "Z shows where it goes, a click goes") - a ring marks where
 *   the phantom would land (behind the monster it looks at, up on the ledge it looks at, the ground under the sky
 *   it looks at, or the open air; red when there's nowhere to stand) and a click (either button) sends the step
 *   (CounterHandler.STEP) with the distance shown; Z again cancels. Since M17 the mouse wheel moves the ring nearer
 *   or farther (2 blocks a notch) while aiming. The server works the spot out again with the same function
 *   (SuitSkills.stepTarget) and plays the card swirls.
 * - Held (STEAL_HOLD_TICKS, M17): steals the aimed foe's skill instead (SuitWeapons.STEAL).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class PhantomAimClient {
    private static final DustParticleOptions OK = new DustParticleOptions(new Vector3f(0.75F, 0.45F, 1.0F), 1.0F);
    private static final DustParticleOptions AIR = new DustParticleOptions(new Vector3f(0.55F, 0.85F, 1.0F), 1.0F);
    private static final DustParticleOptions NO = new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.2F), 1.0F);

    private static boolean aiming;
    /** Ticks Z has been held (-1 = up); whether this hold already stole. */
    private static int zHeld = -1;
    private static boolean stoleThisHold;
    private static int ticks;
    private static double range = SuitTuning.SHADOW_STEP_RANGE;

    private PhantomAimClient() {
    }

    public static boolean isAiming() {
        return aiming;
    }

    private static boolean phantom(LocalPlayer player) {
        return SuitWeapons.armedClass(player) == SuitClass.PHANTOM;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean zDown = ModKeys.COUNTER.isDown();
        if (player == null || minecraft.screen != null || !phantom(player)) {
            aiming = false;
            zHeld = zDown ? 0 : -1;
            stoleThisHold = zDown;
            return;
        }
        if (zDown) {
            zHeld++;
            if (zHeld >= SuitTuning.STEAL_HOLD_TICKS && !stoleThisHold) {
                // Held: steal instead of aiming.
                stoleThisHold = true;
                aiming = false;
                ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.STEAL));
            }
        } else if (zHeld >= 0) {
            if (!stoleThisHold) {
                // A tap: aim (or stop aiming) the step.
                aiming = !aiming;
                if (aiming) {
                    range = SuitTuning.SHADOW_STEP_RANGE;
                }
                player.displayClientMessage(Component.translatable(aiming ? "message.flightsuit.shadow_step_aim"
                        : "message.flightsuit.shadow_step_cancel"), true);
            }
            zHeld = -1;
            stoleThisHold = false;
        }
        if (!aiming) {
            return;
        }
        ticks++;
        if (ticks % 2 != 0) {
            return;
        }
        SuitSkills.StepTarget target = SuitSkills.stepTarget(player.level(), player, range);
        Vec3 at;
        if (target != null) {
            at = target.spot();
        } else {
            at = player.getEyePosition().add(player.getLookAngle().scale(range));
        }
        // A turning ring where they'd land: purple on ground, pale blue in the open air, red if there's no room.
        DustParticleOptions dust = target == null ? NO : target.air() ? AIR : OK;
        for (int i = 0; i < 12; i++) {
            double angle = ticks * 0.15D + i * Math.PI / 6.0D;
            player.level().addParticle(dust, at.x + Math.cos(angle) * 0.7D, at.y + 0.1D, at.z + Math.sin(angle) * 0.7D, 0.0D, 0.0D, 0.0D);
        }
        if (target != null) {
            player.level().addParticle(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0D, at.z, 0.0D, 0.02D, 0.0D);
            if (ticks % 10 == 0) {
                player.displayClientMessage(Component.translatable("message.flightsuit.shadow_step_range",
                        String.format("%.0f", target.spot().distanceTo(player.position())),
                        SuitSkills.stepCost(target.spot().distanceTo(player.position()))), true);
            }
        }
    }

    /** While aiming, the wheel moves the ring nearer or farther instead of changing the hotbar slot. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!aiming || player == null || !phantom(player) || Minecraft.getInstance().screen != null) {
            return;
        }
        event.setCanceled(true);
        range = SuitSkills.clampStepRange(range + Math.signum(event.getScrollDelta()) * 2.0D);
    }

    /** While aiming, a click goes - and doesn't swing or use whatever's in hand. */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!aiming || !(event.isAttack() || event.isUseItem())) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !phantom(player)) {
            aiming = false;
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        aiming = false;
        ModNetwork.sendToServer(new CounterC2SPacket(CounterHandler.STEP, (float) range));
    }
}
