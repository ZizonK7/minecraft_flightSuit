package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.suit.FlightPose;
import com.pfkfks.flightsuit.suit.SuitAnim;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * PlayerAnimator glue. Every player gets two layers:
 * - pose (priority 1000): looping flight pose (hover / boost), switched by server broadcasts;
 * - action (priority 1100): one-shots on top (suit-up, repulsor), which win over the pose while playing.
 * Parts an animation doesn't key stay vanilla, so e.g. the repulsor only takes over the right arm.
 *
 * Also spawns thruster particles for every player in a flight pose, since that state is already here.
 */
public final class SuitAnimator {
    private static final float DEG = (float) (Math.PI / 180.0D);
    /**
     * Layers live inside the player object (PlayerAnimator's per-player data) rather than in a map keyed by
     * player: Entity#hashCode is the entity id, which the client reassigns after construction, so a
     * player-keyed map silently loses its entry.
     */
    private static final ResourceLocation LAYERS_KEY = new ResourceLocation(FlightSuitMod.MODID, "suit_layers");

    private SuitAnimator() {
    }

    /** The pose layer doubles as the handle stored on the player, carrying the rest of the state. */
    private static final class PoseLayer extends ModifierLayer<IAnimation> {
        final Layers owner;

        PoseLayer(Layers owner) {
            this.owner = owner;
        }
    }

    private static final class Layers {
        final PoseLayer pose = new PoseLayer(this);
        final ModifierLayer<IAnimation> action = new ModifierLayer<>();
        FlightPose currentPose = FlightPose.NONE;
        SuitAnim currentAction;

        Layers(AbstractClientPlayer player) {
            // Boost: lie along the look direction like an elytra glide (body pitch = -90 - xRot).
            pose.addModifierLast(new AdjustmentModifier(part -> {
                if (currentPose != FlightPose.BOOST || !"body".equals(part)) {
                    return Optional.empty();
                }
                return Optional.of(new AdjustmentModifier.PartModifier(new Vec3f(-player.getXRot() * DEG, 0.0F, 0.0F), Vec3f.ZERO));
            }));
            // Repulsor: aim the raised right arm where the player looks (vanilla bow-aim formula).
            action.addModifierLast(new AdjustmentModifier(part -> {
                if (currentAction != SuitAnim.REPULSOR_RIGHT || !"rightArm".equals(part)) {
                    return Optional.empty();
                }
                return Optional.of(new AdjustmentModifier.PartModifier(new Vec3f(player.getXRot() * DEG, 0.0F, 0.0F), Vec3f.ZERO));
            }));
        }
    }

    public static void init() {
        PlayerAnimationAccess.REGISTER_ANIMATION_EVENT.register((player, stack) -> {
            Layers layers = new Layers(player);
            stack.addAnimLayer(1000, layers.pose);
            stack.addAnimLayer(1100, layers.action);
            PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYERS_KEY, layers.pose);
        });
    }

    private static Layers layersOf(AbstractClientPlayer player) {
        return PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYERS_KEY) instanceof PoseLayer pose ? pose.owner : null;
    }

    private static KeyframeAnimation animation(String name) {
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(new ResourceLocation(FlightSuitMod.MODID, name));
        if (animation == null) {
            FlightSuitMod.LOGGER.warn("Missing player animation {}:{}", FlightSuitMod.MODID, name);
        }
        return animation;
    }

    public static void playOneShot(AbstractClientPlayer player, SuitAnim anim) {
        Layers layers = layersOf(player);
        KeyframeAnimation animation = animation(anim.animationName());
        if (layers == null || animation == null) {
            return;
        }
        layers.currentAction = anim;
        layers.action.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTQUAD),
                new KeyframeAnimationPlayer(animation), true);
    }

    public static void setPose(AbstractClientPlayer player, FlightPose pose) {
        Layers layers = layersOf(player);
        if (layers == null || layers.currentPose == pose) {
            return;
        }
        layers.currentPose = pose;
        if (pose == FlightPose.NONE) {
            layers.pose.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE), null);
            return;
        }
        KeyframeAnimation animation = animation(pose.animationName());
        if (animation != null) {
            layers.pose.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE),
                    new KeyframeAnimationPlayer(animation), true);
        }
    }

    public static FlightPose poseOf(Player player) {
        Layers layers = player instanceof AbstractClientPlayer clientPlayer ? layersOf(clientPlayer) : null;
        return layers == null ? FlightPose.NONE : layers.currentPose;
    }

    /** Thruster fire for every player currently flying or thrusting. */
    public static void spawnThrusterParticles(ClientLevel level) {
        for (AbstractClientPlayer player : level.players()) {
            FlightPose pose = poseOf(player);
            if (pose == FlightPose.NONE) {
                continue;
            }
            double yaw = Math.toRadians(player.yBodyRot);
            Vec3 side = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
            Vec3 pos = player.position();
            if (pose == FlightPose.BOOST) {
                Vec3 look = player.getLookAngle();
                Vec3 pivot = pos.add(0.0D, 0.7D, 0.0D);
                Vec3 feet = pivot.subtract(look.scale(0.8D));
                Vec3 exhaust = look.scale(-0.35D);
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 p = feet.add(side.scale(0.12D * s));
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, exhaust.x, exhaust.y, exhaust.z);
                }
                if (player.tickCount % 3 == 0) {
                    level.addParticle(ParticleTypes.CLOUD, feet.x, feet.y, feet.z, exhaust.x * 0.3D, exhaust.y * 0.3D, exhaust.z * 0.3D);
                }
            } else {
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 foot = pos.add(side.scale(0.12D * s)).add(0.0D, 0.05D, 0.0D);
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, foot.x, foot.y, foot.z, 0.0D, -0.2D, 0.0D);
                    if (pose == FlightPose.HOVER) {
                        // Palm stabilizers.
                        Vec3 palm = pos.add(side.scale(0.45D * s)).add(0.0D, 0.75D, 0.0D);
                        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, palm.x, palm.y, palm.z, 0.0D, -0.12D, 0.0D);
                    }
                }
            }
        }
    }
}
