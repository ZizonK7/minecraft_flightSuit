package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.entity.CardEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Mark 3, the phantom thief (after MapleStory's Phantom). Cards instead of beams:
 * - Carte Blanche (hold right click): a stream of white cards that curve onto whatever is near the crosshair;
 * - Carte Noir (passive): the phantom's own hits sometimes send a black card after the target;
 * - Judgment Draw (X): every card that lands fills the gauge; full, it draws a card - spade (card damage up),
 *   heart (heal + patch the suit), diamond (reactor recharge), club (a fan of seeking cards), or, rarely, the
 *   joker: all four;
 * - card duel (C): see CardDuel.
 * Stealing other monsters' skills is planned for once there are monsters with skills to steal.
 */
public final class PhantomCards {
    private enum Draw {
        SPADE("♠", ChatFormatting.DARK_AQUA),
        HEART("♥", ChatFormatting.RED),
        DIAMOND("♦", ChatFormatting.GOLD),
        CLUB("♣", ChatFormatting.GREEN),
        JOKER("★", ChatFormatting.LIGHT_PURPLE);

        final String symbol;
        final ChatFormatting color;

        Draw(String symbol, ChatFormatting color) {
            this.symbol = symbol;
            this.color = color;
        }
    }

    private PhantomCards() {
    }

    private static float damageMultiplier(ServerPlayer player, SuitWeapons.State state) {
        return player.level().getGameTime() < state.spadeUntil ? SuitTuning.SPADE_DAMAGE_MULTIPLIER : 1.0F;
    }

    /** The right hand, roughly - where thrown cards leave from. */
    private static Vec3 hand(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0D, look.x).normalize();
        return player.getEyePosition().add(look.scale(0.6D)).add(right.scale(0.35D)).add(0.0D, -0.25D, 0.0D);
    }

    /** The living thing nearest the crosshair within a narrow cone, for the cards to curve onto. */
    private static LivingEntity aimAssist(ServerPlayer player, double range, double angleDeg) {
        LivingEntity direct = SuitWeapons.aim(player, range).target();
        if (direct != null) {
            return direct;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double cos = Math.cos(Math.toRadians(angleDeg));
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                        entity -> entity.isAlive() && !SuitWeapons.isFriendly(player, entity) && entity instanceof Enemy
                                && entity.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) >= cos
                                && player.hasLineOfSight(entity))
                .stream()
                .max(Comparator.comparingDouble(entity -> entity.getBoundingBox().getCenter().subtract(eye).normalize().dot(look)))
                .orElse(null);
    }

    // ---------------------------------------------------------------- Carte Blanche / Carte Noir

    static void tickStream(ServerPlayer player, SuitWeapons.State state) {
        if (state.firingTicks % SuitTuning.CARD_INTERVAL != 1) {
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.CARD_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            SuitWeapons.handle(player, SuitWeapons.PRIMARY_STOP);
            return;
        }
        RandomSource random = player.getRandom();
        Vec3 spread = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.03D);
        Vec3 velocity = player.getLookAngle().add(spread).normalize().scale(1.7D);
        CardEntity.throwCard(player.serverLevel(), player, hand(player), velocity, aimAssist(player, 32.0D, 12.0D),
                SuitTuning.CARD_DAMAGE * damageMultiplier(player, state), CardEntity.BLANCHE);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.7F,
                1.5F + random.nextFloat() * 0.4F);
    }

    static void carteNoir(ServerPlayer player, SuitWeapons.State state, LivingEntity target) {
        if (player.getRandom().nextFloat() >= SuitTuning.NOIR_CHANCE) {
            return;
        }
        // From over the shoulder, curving in after the hit.
        Vec3 from = player.getEyePosition().add(0.0D, 0.4D, 0.0D);
        Vec3 velocity = target.getBoundingBox().getCenter().subtract(from).normalize().add(0.0D, 0.3D, 0.0D).normalize().scale(1.2D);
        CardEntity.throwCard(player.serverLevel(), player, from, velocity, target,
                SuitTuning.NOIR_DAMAGE * damageMultiplier(player, state), CardEntity.NOIR);
    }

    /** A card of the phantom's landed: one more toward Judgment Draw. */
    public static void onCardHit(ServerPlayer owner) {
        SuitWeapons.State state = SuitWeapons.state(owner);
        if (state.gauge < SuitTuning.JUDGMENT_GAUGE) {
            state.gauge++;
            SuitWeapons.sendStatus(owner, state);
            if (state.gauge == SuitTuning.JUDGMENT_GAUGE) {
                owner.level().playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
            }
        }
    }

    // ---------------------------------------------------------------- Judgment Draw (X)

    static void judgmentDraw(ServerPlayer player, SuitWeapons.State state) {
        if (state.gauge < SuitTuning.JUDGMENT_GAUGE) {
            player.displayClientMessage(Component.translatable("message.flightsuit.judgment_gauge", state.gauge, SuitTuning.JUDGMENT_GAUGE), true);
            return;
        }
        state.gauge = 0;
        RandomSource random = player.getRandom();
        Draw draw = random.nextFloat() < 0.1F ? Draw.JOKER : Draw.values()[random.nextInt(4)];
        boolean joker = draw == Draw.JOKER;
        if (draw == Draw.SPADE || joker) {
            state.spadeUntil = player.level().getGameTime() + SuitTuning.SPADE_BUFF_TICKS;
        }
        if (draw == Draw.HEART || joker) {
            player.heal(8.0F);
            for (EquipmentSlot slot : WornSuit.SLOTS) {
                ItemStack piece = player.getItemBySlot(slot);
                if (piece.getItem() instanceof SuitArmorItem && piece.isDamaged()) {
                    piece.setDamageValue(Math.max(0, piece.getDamageValue() - piece.getMaxDamage() * 15 / 100));
                }
            }
        }
        if (draw == Draw.DIAMOND || joker) {
            SuitEnergy.receive(player.getItemBySlot(EquipmentSlot.CHEST), SuitTuning.DIAMOND_ENERGY);
        }
        if (draw == Draw.CLUB || joker) {
            fanOfCards(player, state);
        }
        SuitWeapons.sendStatus(player, state);

        // A card flips over above the phantom's head.
        ServerLevel level = player.serverLevel();
        Vec3 top = player.position().add(0.0D, player.getBbHeight() + 0.8D, 0.0D);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, top.x, top.y, top.z, joker ? 60 : 25, 0.4D, 0.4D, 0.4D, 0.3D);
        level.sendParticles(ParticleTypes.ENCHANT, top.x, top.y, top.z, 40, 0.6D, 0.6D, 0.6D, 0.5D);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, joker ? 1.0F : 1.7F);
        player.displayClientMessage(Component.translatable("message.flightsuit.judgment_draw",
                Component.literal(draw.symbol).withStyle(draw.color),
                Component.translatable("message.flightsuit.judgment." + draw.name().toLowerCase())).withStyle(draw.color), true);
    }

    /** Club: a ring of cards fans out from the phantom and seeks out everything hostile around. */
    private static void fanOfCards(ServerPlayer player, SuitWeapons.State state) {
        List<Mob> targets = player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0D),
                mob -> mob instanceof Enemy && mob.isAlive());
        targets.sort(Comparator.comparingDouble(player::distanceToSqr));
        int count = 12;
        for (int i = 0; i < count; i++) {
            double angle = 2.0D * Math.PI * i / count;
            Vec3 out = new Vec3(Math.cos(angle), 0.25D, Math.sin(angle)).normalize();
            Vec3 from = player.position().add(0.0D, 1.2D, 0.0D).add(out.scale(0.6D));
            LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
            CardEntity.throwCard(player.serverLevel(), player, from, out.scale(1.1D), target,
                    SuitTuning.CARD_DAMAGE * 1.5F * damageMultiplier(player, state), CardEntity.BLANCHE);
        }
    }
}
