package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.CounterC2SPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.suit.CounterHandler;
import com.pfkfks.flightsuit.suit.SuitClass;
import com.pfkfks.flightsuit.suit.SuitSkills;
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
 * Mark 3's shadow step, aimed (after the M16 test: "Z shows where it goes, a click goes"): Z toggles aiming -
 * a ring marks where the phantom would land (behind the monster it looks at, or where it looks; red when there's
 * nowhere to stand) - and a click (either button) sends the step (CounterHandler.STEP); Z again cancels. The
 * server works the spot out again itself (SuitSkills.stepTarget) and plays the card swirls.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class PhantomAimClient {
    private static final DustParticleOptions OK = new DustParticleOptions(new Vector3f(0.75F, 0.45F, 1.0F), 1.0F);
    private static final DustParticleOptions NO = new DustParticleOptions(new Vector3f(1.0F, 0.2F, 0.2F), 1.0F);

    private static boolean aiming;
    private static boolean zWasDown;
    private static int ticks;

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
        boolean pressed = zDown && !zWasDown;
        zWasDown = zDown;
        if (player == null || minecraft.screen != null || !phantom(player)) {
            aiming = false;
            return;
        }
        if (pressed) {
            aiming = !aiming;
            player.displayClientMessage(Component.translatable(aiming ? "message.flightsuit.shadow_step_aim" : "message.flightsuit.shadow_step_cancel"), true);
        }
        if (!aiming) {
            return;
        }
        ticks++;
        if (ticks % 2 != 0) {
            return;
        }
        SuitSkills.StepTarget target = SuitSkills.stepTarget(player.level(), player);
        Vec3 at;
        if (target != null) {
            at = target.spot();
        } else {
            Vec3 eye = player.getEyePosition();
            at = eye.add(player.getLookAngle().scale(6.0D));
        }
        // A turning ring on the ground where they'd land.
        DustParticleOptions dust = target != null ? OK : NO;
        for (int i = 0; i < 12; i++) {
            double angle = ticks * 0.15D + i * Math.PI / 6.0D;
            player.level().addParticle(dust, at.x + Math.cos(angle) * 0.7D, at.y + 0.1D, at.z + Math.sin(angle) * 0.7D, 0.0D, 0.0D, 0.0D);
        }
        if (target != null) {
            player.level().addParticle(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0D, at.z, 0.0D, 0.02D, 0.0D);
        }
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
        ModNetwork.sendToServer(new CounterC2SPacket(CounterHandler.STEP));
    }
}
