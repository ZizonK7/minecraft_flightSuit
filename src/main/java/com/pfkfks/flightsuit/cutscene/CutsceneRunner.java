package com.pfkfks.flightsuit.cutscene;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.CutsceneS2CPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.planet.PlanetData;
import com.pfkfks.flightsuit.planet.dbz.DbzAction;
import com.pfkfks.flightsuit.planet.dbz.DbzCharacter;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Plays cutscenes on the server (M17, the engine of the Dragon Ball story's scenes): spawns or borrows the actors
 * (Dragon Ball fighters in ACTING mode), moves and poses them tick by tick as the script says, sends the subtitles,
 * and holds everyone watching where they stand, untouchable, while the fighters around pause. The watchers' clients
 * run the camera themselves from the same script (client/CutsceneClient).
 *
 * Skipping (the jump key) jumps straight to the end state: every actor where, who and how the script leaves it - the
 * same as watching it through - and then the story carries on. A scene plays once per player (PlanetData remembers);
 * if everyone near has seen it, it's skipped at once. "/planet replay <id>" plays one again on the spot for you only,
 * with actors of its own and no effect on the story.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class CutsceneRunner {
    /** Everyone this close to a scene watches it. */
    public static final double VIEW_RANGE = 96.0D;

    private static final DustParticleOptions[] BEAM_GLOW = {
            dust(0.35F, 0.7F, 1.0F, 2.8F), dust(0.7F, 0.3F, 1.0F, 2.8F), dust(1.0F, 0.95F, 0.55F, 1.6F), dust(1.0F, 0.55F, 0.8F, 1.4F),
            dust(1.0F, 1.0F, 1.0F, 2.4F), dust(0.9F, 0.4F, 0.95F, 0.8F), dust(1.0F, 0.6F, 0.85F, 3.2F)};
    private static final DustParticleOptions WHITE = dust(1.0F, 1.0F, 1.0F, 1.3F);
    private static final DustParticleOptions GOLD = dust(1.0F, 0.85F, 0.2F, 2.5F);

    private static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }

    /** A scene being played. */
    public static final class Running {
        final Cutscene scene;
        final ServerLevel level;
        final Vec3 anchor;
        final float yaw;
        final boolean replay;
        final Consumer<Map<String, DbzFighterEntity>> onEnd;
        final Map<String, DbzFighterEntity> actors = new LinkedHashMap<>();
        final Set<String> spawned = new HashSet<>();
        final List<ServerPlayer> viewers = new ArrayList<>();
        final Map<UUID, Vec3> holdAt = new HashMap<>();
        final List<DbzFighterEntity> paused = new ArrayList<>();
        final Map<Cutscene.Act, Vec3> moveFrom = new HashMap<>();
        int age = -1;
        @Nullable Cutscene.Shot lastShot;
        boolean done;

        Running(Cutscene scene, ServerLevel level, Vec3 anchor, float yaw, boolean replay, Consumer<Map<String, DbzFighterEntity>> onEnd) {
            this.scene = scene;
            this.level = level;
            this.anchor = anchor;
            this.yaw = yaw;
            this.replay = replay;
            this.onEnd = onEnd;
        }

        Vec3 at(Vec3 rel) {
            return Cutscene.place(anchor, yaw, rel);
        }

        public String id() {
            return scene.id();
        }
    }

    private static final List<Running> RUNNING = new ArrayList<>();

    private CutsceneRunner() {
    }

    // ---------------------------------------------------------------- starting

    /**
     * Plays scene {@code id} at {@code anchor} (turned by {@code yaw}).
     * @param bound fighters already there to act in it, by cast id (the cast's bind() members)
     * @param onEnd the story's next step, with the actors still around at the end (not for a replay)
     * @param replay a replay: actors of its own, nothing remembered, no story
     * @param only who watches (null: everyone within VIEW_RANGE)
     * @return the scene, or null if there's no such script
     */
    public static @Nullable Running play(ServerLevel level, String id, Vec3 anchor, float yaw, Map<String, DbzFighterEntity> bound,
                                         Consumer<Map<String, DbzFighterEntity>> onEnd, boolean replay, @Nullable Collection<ServerPlayer> only) {
        Cutscene scene = Cutscenes.get(id);
        if (scene == null) {
            FlightSuitMod.LOGGER.warn("No cutscene {}", id);
            if (!replay) {
                onEnd.accept(bound);
            }
            return null;
        }
        Running run = new Running(scene, level, anchor, yaw, replay, onEnd);
        for (ServerPlayer player : only != null ? only : level.players()) {
            if (!player.isSpectator() && player.isAlive() && !isWatching(player)
                    && (only != null || player.distanceToSqr(anchor.x, player.getY(), anchor.z) < VIEW_RANGE * VIEW_RANGE)) {
                run.viewers.add(player);
            }
        }
        castActors(run, bound);
        precompute(run);
        PlanetData data = PlanetData.get(level.getServer());
        boolean allSeen = run.viewers.stream().allMatch(p -> data.traveller(p.getUUID()).seenCutscenes.contains(id));
        if (run.viewers.isEmpty() || !replay && allSeen) {
            finish(run, true);
            return run;
        }
        for (DbzFighterEntity fighter : level.getEntitiesOfClass(DbzFighterEntity.class, new AABB(BlockPos.containing(anchor)).inflate(VIEW_RANGE),
                f -> f.isAlive() && !f.isNoAi() && !run.actors.containsValue(f))) {
            fighter.setNoAi(true);
            fighter.getNavigation().stop();
            run.paused.add(fighter);
        }
        for (ServerPlayer viewer : run.viewers) {
            run.holdAt.put(viewer.getUUID(), viewer.position());
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), CutsceneS2CPacket.start(id, anchor, yaw));
        }
        RUNNING.add(run);
        return run;
    }

    /** Replays a scene for {@code player} only, a few blocks in front of them. */
    public static boolean replay(ServerPlayer player, String id) {
        if (Cutscenes.get(id) == null || isWatching(player)) {
            return false;
        }
        float yaw = player.getYRot();
        Vec3 ahead = Vec3.directionFromRotation(0.0F, yaw).scale(6.0D);
        Vec3 anchor = player.position().add(ahead.x, 0.0D, ahead.z);
        return play(player.serverLevel(), id, anchor, yaw, Map.of(), actors -> {
        }, true, List.of(player)) != null;
    }

    private static void castActors(Running run, Map<String, DbzFighterEntity> bound) {
        for (Cutscene.Cast cast : run.scene.cast()) {
            DbzFighterEntity actor = cast.bind() && !run.replay ? bound.get(cast.id()) : null;
            if (actor == null || !actor.isAlive()) {
                actor = DbzFighterEntity.create(run.level, cast.who(), BlockPos.containing(run.anchor), false);
                actor.setFighting(false);
                actor.moveTo(run.at(cast.at()).x, run.at(cast.at()).y, run.at(cast.at()).z, run.yaw + cast.yaw(), 0.0F);
                run.level.addFreshEntity(actor);
                run.spawned.add(cast.id());
            }
            actor.setActing(true);
            actor.place(run.at(cast.at()), run.yaw + cast.yaw());
            actor.setInvisible(cast.hidden());
            run.actors.put(cast.id(), actor);
        }
    }

    /** Where every move starts from (where the one before it left the actor). */
    private static void precompute(Running run) {
        Map<String, Vec3> pos = new HashMap<>();
        for (Cutscene.Cast cast : run.scene.cast()) {
            pos.put(cast.id(), cast.at());
        }
        for (Cutscene.Act act : run.scene.acts()) {
            switch (act.kind()) {
                case MOVE, FLY, KNOCKBACK -> {
                    run.moveFrom.put(act, pos.getOrDefault(act.actor(), Vec3.ZERO));
                    pos.put(act.actor(), act.pos());
                }
                case APPEAR -> pos.put(act.actor(), act.pos());
                default -> {
                }
            }
        }
    }

    // ---------------------------------------------------------------- running

    public static boolean isWatching(ServerPlayer player) {
        for (Running run : RUNNING) {
            for (ServerPlayer viewer : run.viewers) {
                if (viewer.getUUID().equals(player.getUUID())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True while a scene is playing within VIEW_RANGE of {@code pos} (the story waits for it). */
    public static boolean isPlayingNear(ServerLevel level, Vec3 pos) {
        for (Running run : RUNNING) {
            if (run.level == level && run.anchor.distanceToSqr(pos) < VIEW_RANGE * VIEW_RANGE) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNNING.isEmpty()) {
            return;
        }
        for (Running run : new ArrayList<>(RUNNING)) {
            step(run);
        }
    }

    private static void step(Running run) {
        run.age++;
        run.viewers.removeIf(viewer -> viewer.hasDisconnected() || !viewer.isAlive() || viewer.level() != run.level);
        Cutscene.Shot shot = run.scene.shotAt(run.age);
        if (shot != run.lastShot) {
            run.lastShot = shot;
            if (shot.line() != null) {
                sendLine(run, shot.line().speaker(), shot.line().key());
            }
        }
        for (Cutscene.Act act : run.scene.acts()) {
            if (act.at() > run.age) {
                break;
            }
            if (run.age <= act.end()) {
                apply(run, act);
            }
        }
        for (ServerPlayer viewer : run.viewers) {
            Vec3 hold = run.holdAt.get(viewer.getUUID());
            if (hold != null && viewer.position().distanceToSqr(hold) > 0.25D) {
                viewer.teleportTo(run.level, hold.x, hold.y, hold.z, viewer.getYRot(), viewer.getXRot());
            }
            viewer.setDeltaMovement(Vec3.ZERO);
            viewer.fallDistance = 0.0F;
        }
        if (run.age >= run.scene.length() || run.viewers.isEmpty() && !run.done) {
            finish(run, run.viewers.isEmpty());
        }
    }

    private static void apply(Running run, Cutscene.Act act) {
        DbzFighterEntity actor = run.actors.get(act.actor());
        boolean first = run.age == act.at();
        ServerLevel level = run.level;
        switch (act.kind()) {
            case MOVE, FLY, KNOCKBACK -> {
                if (actor == null) {
                    return;
                }
                Vec3 from = run.moveFrom.getOrDefault(act, act.pos());
                float t = act.ticks() <= 0 ? 1.0F : Math.min(1.0F, (run.age - act.at()) / (float) act.ticks());
                float e = act.kind() == Cutscene.Kind.MOVE ? t : Cutscene.ease(t);
                Vec3 rel = from.lerp(act.pos(), e);
                if (act.kind() != Cutscene.Kind.MOVE) {
                    rel = rel.add(0.0D, Math.sin(Math.PI * t) * act.value() / 10.0D, 0.0D);
                }
                if (first) {
                    actor.setAction(act.kind() == Cutscene.Kind.FLY ? DbzAction.FLY : act.kind() == Cutscene.Kind.KNOCKBACK ? DbzAction.HURT
                            : DbzAction.IDLE);
                }
                Vec3 to = run.at(act.pos());
                Vec3 now = run.at(rel);
                float facing = actor.getYRot();
                Vec3 dir = run.at(act.pos()).subtract(run.at(from));
                if (act.kind() != Cutscene.Kind.KNOCKBACK && dir.horizontalDistanceSqr() > 1.0E-4D) {
                    facing = (float) (Math.atan2(dir.z, dir.x) * (180.0D / Math.PI)) - 90.0F;
                }
                actor.place(now, facing);
                if (act.kind() == Cutscene.Kind.FLY && run.age % 2 == 0) {
                    level.sendParticles(ParticleTypes.CLOUD, now.x, now.y + 0.2D, now.z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
                }
                if (act.kind() == Cutscene.Kind.KNOCKBACK && t >= 1.0F && to.y <= now.y + 0.01D) {
                    level.sendParticles(ParticleTypes.CLOUD, to.x, to.y + 0.2D, to.z, 8, 0.4D, 0.1D, 0.4D, 0.05D);
                }
            }
            case FACE -> {
                if (actor == null || !first) {
                    return;
                }
                Vec3 target = act.target() != null && run.actors.containsKey(act.target()) ? run.actors.get(act.target()).position() : run.at(act.pos());
                Vec3 to = target.subtract(actor.position());
                if (to.horizontalDistanceSqr() > 1.0E-4D) {
                    actor.place(actor.position(), (float) (Math.atan2(to.z, to.x) * (180.0D / Math.PI)) - 90.0F);
                }
            }
            case POSE -> {
                if (actor != null && first) {
                    actor.setAction(DbzAction.byId(act.value()));
                }
            }
            case FALL -> {
                if (actor != null && first) {
                    actor.setAction(DbzAction.DOWN);
                    level.sendParticles(ParticleTypes.POOF, actor.getX(), actor.getY() + 0.2D, actor.getZ(), 6, 0.4D, 0.1D, 0.4D, 0.02D);
                }
            }
            case BEAM -> {
                if (actor != null) {
                    beam(run, actor, act);
                }
            }
            case TRANSFORM -> {
                if (actor != null && first && act.as() != null) {
                    actor.setCharacter(act.as());
                    DbzFighterEntity.transformBurst(level, actor);
                }
            }
            case GLOW -> {
                if (actor != null && first) {
                    actor.setGlowingTag(act.value() == 1);
                }
            }
            case VANISH -> {
                if (actor != null && first) {
                    Vec3 c = actor.getBoundingBox().getCenter();
                    if (act.value() == 1) {
                        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.4D, 0.6D, 0.4D, 0.15D);
                    } else {
                        level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 20, 0.3D, 0.6D, 0.3D, 0.03D);
                    }
                    actor.setInvisible(true);
                }
            }
            case APPEAR -> {
                if (actor != null && first) {
                    Vec3 at = run.at(act.pos());
                    actor.place(at, actor.getYRot());
                    actor.setInvisible(false);
                    level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1.0D, at.z, 12, 0.3D, 0.6D, 0.3D, 0.03D);
                    level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0D, at.z, 8, 0.2D, 0.6D, 0.2D, 0.02D);
                }
            }
            case SAY -> {
                if (actor != null && first && act.key() != null) {
                    sendLine(run, actor.getCharacter(), act.key());
                }
            }
            case BURST -> {
                if (first) {
                    burst(level, run.at(act.pos()), act.value());
                }
            }
            case SOUND -> {
                if (first) {
                    Vec3 at = actor != null ? actor.position() : run.anchor;
                    level.playSound(null, at.x, at.y, at.z, sound(act.value()), SoundSource.HOSTILE, 2.0F, act.value() == Cutscene.SOUND_POWER ? 1.3F : 1.0F);
                }
            }
            case SHAKE -> {
                if (first) {
                    for (ServerPlayer viewer : run.viewers) {
                        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), CutsceneS2CPacket.shake(Math.max(4, act.ticks())));
                    }
                }
            }
        }
    }

    /** A beam drawn from the actor's hands (its eyes for a Great Ape) to its target, bursting there on the last tick. */
    private static void beam(Running run, DbzFighterEntity actor, Cutscene.Act act) {
        ServerLevel level = run.level;
        Vec3 from = actor.getCharacter() == DbzCharacter.OOZARU_VEGETA || act.value() == Cutscene.BEAM_SBC || act.value() == Cutscene.BEAM_DEATH
                ? actor.getEyePosition() : com.pfkfks.flightsuit.suit.SwordArts.hands(actor);
        DbzFighterEntity other = act.target() != null ? run.actors.get(act.target()) : null;
        Vec3 to = other != null ? other.getBoundingBox().getCenter() : run.at(act.pos());
        if (run.age == act.at()) {
            actor.setAction(DbzAction.FIRE);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.5F,
                    act.value() == Cutscene.BEAM_GALICK ? 0.9F : 1.4F);
        }
        DustParticleOptions glow = BEAM_GLOW[Math.max(0, Math.min(BEAM_GLOW.length - 1, act.value()))];
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        int points = (int) (length * 3.0D);
        double spread = act.value() == Cutscene.BEAM_DEATH ? 0.03D : 0.22D;
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.add(dir.scale(i / (double) Math.max(1, points)));
            level.sendParticles(glow, p.x, p.y, p.z, 1, spread, spread, spread, 0.0D);
            if (i % 2 == 0) {
                level.sendParticles(WHITE, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        if (act.value() == Cutscene.BEAM_SBC && length > 0.1D) {
            Vec3 unit = dir.scale(1.0D / length);
            Vec3 side = unit.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(unit).normalize();
            for (double d = 0.0D; d < length; d += 0.35D) {
                double angle = d * 2.2D + run.age * 0.6D;
                Vec3 p = from.add(unit.scale(d)).add(side.scale(Math.cos(angle) * 0.7D)).add(up.scale(Math.sin(angle) * 0.7D));
                level.sendParticles(BEAM_GLOW[Cutscene.BEAM_GALICK], p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        if (run.age == act.end()) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y, to.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(glow, to.x, to.y, to.z, 40, 1.5D, 1.5D, 1.5D, 0.0D);
            level.playSound(null, to.x, to.y, to.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.8F);
        }
    }

    /** One of the scripts' effects at a spot. */
    public static void burst(ServerLevel level, Vec3 at, int kind) {
        switch (kind) {
            case Cutscene.BURST_EXPLOSION -> {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 0.9F);
            }
            case Cutscene.BURST_BIG_EXPLOSION -> {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 6, 2.0D, 2.0D, 2.0D, 0.0D);
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 2, 0.5D, 0.5D, 0.5D, 0.0D);
                level.sendParticles(WHITE, at.x, at.y, at.z, 60, 3.0D, 3.0D, 3.0D, 0.0D);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 4.0F, 0.5F);
            }
            case Cutscene.BURST_FLASH -> level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            case Cutscene.BURST_CRACK -> {
                BlockState ground = level.getBlockState(BlockPos.containing(at).below());
                if (ground.isAir()) {
                    ground = Blocks.STONE.defaultBlockState();
                }
                for (int i = 0; i < 16; i++) {
                    double angle = i * Math.PI / 8.0D;
                    for (int r = 1; r <= 5; r++) {
                        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x + Math.cos(angle) * r,
                                at.y + 0.1D, at.z + Math.sin(angle) * r, 2, 0.1D, 0.1D, 0.1D, 0.1D);
                    }
                }
            }
            case Cutscene.BURST_GOLD_PILLAR -> {
                for (int i = 0; i < 40; i++) {
                    level.sendParticles(GOLD, at.x, at.y + i * 0.6D, at.z, 3, 0.35D, 0.2D, 0.35D, 0.0D);
                    level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + i * 0.6D, at.z, 1, 0.2D, 0.2D, 0.2D, 0.0D);
                }
            }
            case Cutscene.BURST_SMOKE -> level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y, at.z, 20, 1.0D, 0.5D, 1.0D, 0.02D);
            case Cutscene.BURST_MOON -> {
                // The false moon: a pale ball of light high up.
                for (int i = 0; i < 80; i++) {
                    level.sendParticles(dust(1.0F, 1.0F, 0.85F, 3.0F), at.x, at.y, at.z, 1, 1.5D, 1.5D, 1.5D, 0.0D);
                }
                level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            case Cutscene.BURST_LIGHTNING -> {
                net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(at.x, at.y, at.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
            case Cutscene.BURST_SPIRIT_BOMB -> {
                for (int i = 0; i < 120; i++) {
                    level.sendParticles(dust(0.6F, 0.85F, 1.0F, 3.5F), at.x, at.y, at.z, 1, 3.0D, 3.0D, 3.0D, 0.0D);
                }
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 60, 3.0D, 3.0D, 3.0D, 0.02D);
            }
            case Cutscene.BURST_LAVA -> {
                level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 20, 2.0D, 0.3D, 2.0D, 0.0D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 20, 2.0D, 0.5D, 2.0D, 0.02D);
            }
            default -> {
            }
        }
    }

    private static SoundEvent sound(int kind) {
        return switch (kind) {
            case Cutscene.SOUND_PUNCH -> SoundEvents.PLAYER_ATTACK_STRONG;
            case Cutscene.SOUND_POWER -> SoundEvents.BEACON_POWER_SELECT;
            case Cutscene.SOUND_ROAR -> SoundEvents.RAVAGER_ROAR;
            case Cutscene.SOUND_WHOOSH -> SoundEvents.ENDER_DRAGON_FLAP;
            case Cutscene.SOUND_THUNDER -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case Cutscene.SOUND_CROWD -> SoundEvents.VILLAGER_CELEBRATE;
            case Cutscene.SOUND_CHARGE -> SoundEvents.BEACON_ACTIVATE;
            default -> SoundEvents.GENERIC_EXPLODE;
        };
    }

    private static void sendLine(Running run, @Nullable DbzCharacter speaker, String key) {
        Component name = speaker == null ? Component.empty() : speaker.displayName();
        Component text = speaker == null ? Component.translatable("cutscene.flightsuit." + run.scene.id() + "." + key).withStyle(ChatFormatting.ITALIC)
                : Component.translatable("dbz.flightsuit." + speaker.voice() + "." + key);
        for (ServerPlayer viewer : run.viewers) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), CutsceneS2CPacket.line(name, text));
        }
    }

    /** The screen shakes for those within {@code range} of {@code at} (a transformation outside a cutscene too). */
    public static void shake(ServerLevel level, Vec3 at, double range, int ticks) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < range * range) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), CutsceneS2CPacket.shake(ticks));
            }
        }
    }

    // ---------------------------------------------------------------- ending

    /** The jump key: the scene this player watches jumps to its end (for everyone watching it). */
    public static void skip(ServerPlayer player) {
        for (Running run : new ArrayList<>(RUNNING)) {
            for (ServerPlayer viewer : run.viewers) {
                if (viewer.getUUID().equals(player.getUUID())) {
                    finish(run, true);
                    return;
                }
            }
        }
    }

    /**
     * Ends a scene: every actor put where, who and how the script leaves it (so skipping comes out the same as
     * watching), the cast's ends carried out, the watchers let go, the paused fighters woken - then the story.
     */
    private static void finish(Running run, boolean skipped) {
        if (run.done) {
            return;
        }
        run.done = true;
        RUNNING.remove(run);
        Map<String, Vec3> pos = new HashMap<>();
        Map<String, DbzCharacter> who = new HashMap<>();
        Map<String, Boolean> hidden = new HashMap<>();
        Map<String, DbzAction> pose = new HashMap<>();
        for (Cutscene.Cast cast : run.scene.cast()) {
            pos.put(cast.id(), cast.at());
            who.put(cast.id(), cast.who());
            hidden.put(cast.id(), cast.hidden());
        }
        for (Cutscene.Act act : run.scene.acts()) {
            switch (act.kind()) {
                case MOVE, FLY, KNOCKBACK -> pos.put(act.actor(), act.pos());
                case APPEAR -> {
                    pos.put(act.actor(), act.pos());
                    hidden.put(act.actor(), false);
                }
                case VANISH -> hidden.put(act.actor(), true);
                case TRANSFORM -> {
                    if (act.as() != null) {
                        who.put(act.actor(), act.as());
                    }
                }
                case POSE -> pose.put(act.actor(), DbzAction.byId(act.value()));
                case FALL -> pose.put(act.actor(), DbzAction.DOWN);
                default -> {
                }
            }
        }
        Map<String, DbzFighterEntity> remaining = new LinkedHashMap<>();
        for (Cutscene.Cast cast : run.scene.cast()) {
            DbzFighterEntity actor = run.actors.get(cast.id());
            if (actor == null || !actor.isAlive()) {
                continue;
            }
            if (skipped) {
                Vec3 rel = pos.get(cast.id());
                actor.place(run.at(rel), actor.getYRot());
            }
            if (actor.getCharacter() != who.get(cast.id())) {
                actor.setCharacter(who.get(cast.id()));
            }
            actor.setGlowingTag(false);
            boolean gone = hidden.getOrDefault(cast.id(), false);
            if (run.replay ? run.spawned.contains(cast.id()) : gone || cast.end() == Cutscene.End.REMOVE) {
                actor.discard();
                continue;
            }
            actor.setInvisible(false);
            switch (cast.end()) {
                case FIGHT -> {
                    actor.setActing(false);
                    actor.setFighting(true);
                }
                case STAY -> {
                    // A prop: stays posed (lying where it fell, say) until the scene's fight is cleared away.
                    actor.setAction(pose.getOrDefault(cast.id(), DbzAction.IDLE));
                }
                default -> {
                }
            }
            remaining.put(cast.id(), actor);
        }
        for (DbzFighterEntity fighter : run.paused) {
            if (fighter.isAlive()) {
                fighter.setNoAi(false);
            }
        }
        PlanetData data = PlanetData.get(run.level.getServer());
        for (ServerPlayer viewer : run.viewers) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), CutsceneS2CPacket.end());
            if (!run.replay) {
                data.traveller(viewer.getUUID()).seenCutscenes.add(run.scene.id());
            }
        }
        data.setDirty();
        if (!run.replay) {
            run.onEnd.accept(remaining);
        }
    }

    // ---------------------------------------------------------------- watchers

    /** Nothing touches someone watching a scene. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isWatching(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        for (Running run : RUNNING) {
            run.viewers.removeIf(viewer -> viewer.getUUID().equals(event.getEntity().getUUID()));
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        RUNNING.clear();
    }
}
