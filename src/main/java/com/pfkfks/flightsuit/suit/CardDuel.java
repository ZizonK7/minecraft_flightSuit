package com.pfkfks.flightsuit.suit;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.CardDuelS2CPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mark 3's C: the phantom pulls the aimed monster into a card duel - a hand of blackjack (the client shows it
 * as a 2D stage the two are dragged into; in the world they stand frozen in a whirl of cards). Win and the
 * monster dies on the spot (a boss loses a big share of its health instead); lose and you're left at one
 * heart with the suit badly worn. The server deals and decides; the client only says what it wants to do.
 *
 * The phantom is a card sharp, so the odds lean their way:
 * - a tie is the phantom's win;
 * - once a hand, "dealing from the bottom": peek at the top and the bottom card of the deck and take either -
 *   the top card stays where it is, so leaving a bad one there hands it to the house.
 *
 * Cards are 0-51: rank = card % 13 (0 = ace, 1-9 = two to ten, 10-12 = jack, queen, king), suit = card / 13.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class CardDuel {
    public static final byte HIT = 0;
    public static final byte STAND = 1;
    /** Deal from the bottom: peek at the top and bottom cards (once a hand)... */
    public static final byte SLEIGHT = 2;
    /** ...then take one of them. */
    public static final byte TAKE_TOP = 3;
    public static final byte TAKE_BOTTOM = 4;

    public static final byte PLAYING = 0;
    public static final byte WIN = 1;
    public static final byte LOSE = 2;
    /** Called off (opponent gone, player left) - the client just closes the table. */
    public static final byte CANCELLED = 3;

    /** Hidden card in the opponent's hand while the hand is still being played. */
    public static final byte FACE_DOWN = -1;

    private static final class Duel {
        final LivingEntity opponent;
        final boolean boss;
        final List<Byte> deck = new ArrayList<>();
        final List<Byte> mine = new ArrayList<>();
        final List<Byte> theirs = new ArrayList<>();
        long lastMove;
        boolean sleightUsed;
        boolean peeking;

        Duel(LivingEntity opponent) {
            this.opponent = opponent;
            this.boss = Stasis.isBoss(opponent);
        }

        /** The top of the deck is the end of the list. */
        byte draw() {
            return deck.remove(deck.size() - 1);
        }

        byte top() {
            return deck.get(deck.size() - 1);
        }

        byte bottom() {
            return deck.get(0);
        }
    }

    private static final Map<UUID, Duel> DUELS = new HashMap<>();

    private CardDuel() {
    }

    public static boolean isDueling(Player player) {
        return DUELS.containsKey(player.getUUID());
    }

    /** Blackjack total: aces count 11 unless that busts the hand. */
    public static int total(List<Byte> hand) {
        int total = 0;
        int aces = 0;
        for (byte card : hand) {
            if (card < 0) {
                continue;
            }
            int rank = card % 13;
            if (rank == 0) {
                aces++;
                total += 11;
            } else {
                total += Math.min(10, rank + 1);
            }
        }
        while (total > 21 && aces-- > 0) {
            total -= 10;
        }
        return total;
    }

    // ---------------------------------------------------------------- start

    static void challenge(ServerPlayer player, SuitWeapons.State state) {
        long now = player.level().getGameTime();
        if (now < state.skill2Ready || isDueling(player)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.skill_cooldown",
                    String.format("%.1f", Math.max(0L, state.skill2Ready - now) / 20.0F)), true);
            return;
        }
        LivingEntity opponent = SuitWeapons.aim(player, SuitTuning.DUEL_RANGE).target();
        if (opponent == null || opponent instanceof Player) {
            player.displayClientMessage(Component.translatable("message.flightsuit.duel_no_target"), true);
            return;
        }
        if (!SuitEnergy.tryDrain(player, EquipmentSlot.CHEST, SuitTuning.DUEL_COST)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.low_power"), true);
            return;
        }
        state.skill2Ready = now + SuitTuning.DUEL_COOLDOWN_TICKS;
        SuitWeapons.sendStatus(player, state);
        SuitWeapons.handle(player, SuitWeapons.PRIMARY_STOP);

        ServerLevel level = player.serverLevel();
        // The opponent waits at the table (bosses can't be held - they just keep going in the world).
        Stasis.hold(level, opponent, SuitTuning.DUEL_TIMEOUT_TICKS + 200, false);
        Duel duel = new Duel(opponent);
        for (byte card = 0; card < 52; card++) {
            duel.deck.add(card);
        }
        Collections.shuffle(duel.deck, new java.util.Random(player.getRandom().nextLong()));
        duel.mine.add(duel.draw());
        duel.theirs.add(duel.draw());
        duel.mine.add(duel.draw());
        duel.theirs.add(duel.draw());
        duel.lastMove = now;
        DUELS.put(player.getUUID(), duel);

        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.2F);
        level.playSound(null, opponent.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.4F);
        if (total(duel.mine) == 21) {
            // Natural blackjack: an instant win (a tie with the house's would be the phantom's anyway).
            finish(player, duel, WIN);
        } else {
            send(player, duel, PLAYING);
        }
    }

    // ---------------------------------------------------------------- play

    public static void act(ServerPlayer player, byte action) {
        Duel duel = DUELS.get(player.getUUID());
        if (duel == null) {
            return;
        }
        duel.lastMove = player.level().getGameTime();
        if (duel.peeking) {
            // Mid-sleight only taking a card makes sense.
            if (action == TAKE_TOP || action == TAKE_BOTTOM) {
                duel.peeking = false;
                take(player, duel, action == TAKE_TOP ? duel.draw() : duel.deck.remove(0));
            }
            return;
        }
        switch (action) {
            case HIT -> take(player, duel, duel.draw());
            case SLEIGHT -> {
                if (!duel.sleightUsed) {
                    duel.sleightUsed = true;
                    duel.peeking = true;
                    player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.6F, 1.8F);
                    send(player, duel, PLAYING);
                }
            }
            case STAND -> stand(player, duel);
            default -> {
            }
        }
    }

    private static void take(ServerPlayer player, Duel duel, byte card) {
        duel.mine.add(card);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.4F);
        int total = total(duel.mine);
        if (total > 21) {
            finish(player, duel, LOSE);
        } else if (total == 21) {
            stand(player, duel);
        } else {
            send(player, duel, PLAYING);
        }
    }

    /** The house plays out its hand (draws to 17), then the hands are compared - ties go to the phantom. */
    private static void stand(ServerPlayer player, Duel duel) {
        duel.peeking = false;
        while (total(duel.theirs) < 17) {
            duel.theirs.add(duel.draw());
        }
        int mine = total(duel.mine);
        int theirs = total(duel.theirs);
        finish(player, duel, theirs > 21 || mine >= theirs ? WIN : LOSE);
    }

    private static void finish(ServerPlayer player, Duel duel, byte result) {
        DUELS.remove(player.getUUID());
        send(player, duel, result);
        LivingEntity opponent = duel.opponent;
        Stasis.release(opponent);
        ServerLevel level = player.serverLevel();
        switch (result) {
            case WIN -> {
                // The cards come down on it.
                opponent.invulnerableTime = 0;
                float damage = duel.boss ? opponent.getMaxHealth() * SuitTuning.DUEL_BOSS_DAMAGE : Float.MAX_VALUE;
                opponent.hurt(player.damageSources().playerAttack(player), damage);
                if (!duel.boss && opponent.isAlive()) {
                    opponent.kill();
                }
                Vec3 at = opponent.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 40, 0.5D, 0.6D, 0.5D, 0.4D);
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.4D, 0.5D, 0.4D, 0.5D);
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.playSound(null, opponent.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
                player.displayClientMessage(Component.translatable("message.flightsuit.duel_win", opponent.getDisplayName()), true);
            }
            case LOSE -> {
                // Critical wound: down to one heart (never killed outright), and the suit takes a beating.
                player.setHealth(Math.min(player.getHealth(), 2.0F));
                boolean broken = false;
                for (EquipmentSlot slot : WornSuit.SLOTS) {
                    ItemStack piece = player.getItemBySlot(slot);
                    if (piece.getItem() instanceof SuitArmorItem && piece.isDamageableItem()) {
                        int wear = (int) (piece.getMaxDamage() * SuitTuning.DUEL_LOSS_WEAR);
                        piece.setDamageValue(Math.min(piece.getMaxDamage() - 1, piece.getDamageValue() + wear));
                        broken |= SuitArmorItem.isBroken(piece);
                    }
                }
                if (broken) {
                    SuitUpManager.requestEject(player);
                }
                Vec3 at = player.getBoundingBox().getCenter();
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 12, 0.3D, 0.5D, 0.3D, 0.2D);
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.3D, 0.5D, 0.3D, 0.4D);
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.8F);
                level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.6F);
                player.displayClientMessage(Component.translatable("message.flightsuit.duel_lose"), true);
            }
            default -> {
            }
        }
    }

    private static void cancel(ServerPlayer player, Duel duel) {
        DUELS.remove(player.getUUID());
        Stasis.release(duel.opponent);
        send(player, duel, CANCELLED);
        player.displayClientMessage(Component.translatable("message.flightsuit.duel_cancelled"), true);
    }

    private static void send(ServerPlayer player, Duel duel, byte result) {
        List<Byte> theirs = new ArrayList<>(duel.theirs);
        if (result == PLAYING) {
            theirs.set(1, FACE_DOWN);
        }
        boolean peek = duel.peeking && result == PLAYING;
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CardDuelS2CPacket(duel.opponent.getId(),
                duel.opponent.getDisplayName().getString(), duel.boss, new ArrayList<>(duel.mine), theirs, result,
                duel.sleightUsed, peek ? duel.top() : FACE_DOWN, peek ? duel.bottom() : FACE_DOWN));
    }

    // ---------------------------------------------------------------- upkeep

    /** Called every tick for every player: the card whirl in the world, timeouts, a vanished opponent. */
    static void tick(ServerPlayer player) {
        Duel duel = DUELS.get(player.getUUID());
        if (duel == null) {
            return;
        }
        LivingEntity opponent = duel.opponent;
        if (!player.isAlive() || !opponent.isAlive() || opponent.level() != player.level() || opponent.distanceTo(player) > 64.0F) {
            cancel(player, duel);
            return;
        }
        if (player.level().getGameTime() - duel.lastMove > SuitTuning.DUEL_TIMEOUT_TICKS) {
            stand(player, duel);
            return;
        }
        // Onlookers see the two of them caught in a slow whirl of cards.
        ServerLevel level = player.serverLevel();
        double spin = player.tickCount * 0.3D;
        for (LivingEntity who : new LivingEntity[]{player, opponent}) {
            double radius = who.getBbWidth() + 0.6D;
            for (int k = 0; k < 2; k++) {
                double a = spin + k * Math.PI;
                level.sendParticles(ParticleTypes.ENCHANT, who.getX() + Math.cos(a) * radius, who.getY() + 0.3D + (player.tickCount % 20) * 0.08D,
                        who.getZ() + Math.sin(a) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    public static void forget(ServerPlayer player) {
        Duel duel = DUELS.remove(player.getUUID());
        if (duel != null) {
            Stasis.release(duel.opponent);
        }
    }

    /** Nothing in the world can touch the phantom while the duel is on. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isDueling(player)) {
            event.setCanceled(true);
        }
    }
}
