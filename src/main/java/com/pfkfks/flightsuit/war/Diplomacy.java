package com.pfkfks.flightsuit.war;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.suit.EdithAlert;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dealing with the kingdoms (DESIGN.md 4-11 외교와 의뢰, M12): finding their fortresses, trust going up and down,
 * the requests they send (defend us, lend us soldiers, march with us), support troops for allies, and the
 * long road to leading a kingdom (공물, 침략 명령). Everything the player answers goes through chat buttons
 * that run "/village ..." (WarCommands).
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class Diplomacy {
    private Diplomacy() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % 100 != 50) {
            return;
        }
        MinecraftServer server = level.getServer();
        WarData data = WarData.get(server);
        long day = level.getDayTime() / 24000L;
        for (Map.Entry<UUID, Standing> entry : data.standings().entrySet()) {
            Standing standing = entry.getValue();
            if (standing.lastDecayDay < day) {
                standing.lastDecayDay = day;
                for (int i = 0; i < standing.trust.length; i++) {
                    if (standing.trust[i] < 0) {
                        standing.trust[i]++;
                    }
                }
                data.setDirty();
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Standing standing = data.standing(player.getUUID());
            for (Kingdom kingdom : Kingdom.values()) {
                maybeOffer(level, data, player, standing, kingdom, day);
                if (standing.leads(kingdom) && standing.lastTribute[kingdom.ordinal()] < day) {
                    standing.lastTribute[kingdom.ordinal()] = day;
                    data.setDirty();
                    tribute(player, kingdom, level.random);
                }
            }
        }
        for (Request request : new ArrayList<>(data.requests().values())) {
            tickRequest(level, data, request, day);
        }
    }

    // ---------------------------------------------------------------- finding and standing

    /** First time near a fortress: it's found; the kingdom will start asking things of this player. */
    public static void discover(ServerPlayer player, WarData data, FortRecord fort) {
        Standing standing = data.standing(player.getUUID());
        if (standing.hasFound(fort.kingdom)) {
            return;
        }
        data.markFound(player.getUUID(), fort.kingdom);
        long day = player.level().getDayTime() / 24000L;
        // First request comes the next day.
        standing.lastRequest[fort.kingdom.ordinal()] = day - WarTuning.REQUEST_INTERVAL + 1;
        General leader = General.leaderOf(fort.kingdom);
        EdithAlert.send(player, Component.translatable("fort.flightsuit.found_title"),
                Component.translatable("fort.flightsuit.found", fort.kingdom.displayName(), leader == null ? Component.empty() : leader.displayName()),
                Level.OVERWORLD, fort.center(), EdithAlert.CYAN, true);
    }

    /** The player struck (or killed) one of a kingdom's garrison. */
    public static void onGarrisonHit(ServerPlayer player, Kingdom kingdom, boolean killed) {
        WarData data = WarData.get(player.server);
        int before = data.trust(player.getUUID(), kingdom);
        data.addTrust(player.getUUID(), kingdom, killed ? -10 : -3);
        if (before > -50 && data.trust(player.getUUID(), kingdom) <= -50) {
            player.sendSystemMessage(Component.translatable("fort.flightsuit.now_hostile", kingdom.displayName()).withStyle(ChatFormatting.RED));
        }
    }

    public static Component tierName(int trust) {
        return Component.translatable(Standing.tier(trust).key());
    }

    // ---------------------------------------------------------------- talking to the ruler

    /** Right-click on a general at their fortress; the ruler opens the whole conversation. */
    public static void talk(ServerPlayer player, GeneralEntity general) {
        WarData data = WarData.get(player.server);
        Kingdom kingdom = general.kingdom();
        Standing standing = data.standing(player.getUUID());
        int trust = standing.trust(kingdom);
        Standing.Tier tier = Standing.tier(trust);
        General who = general.getGeneral();
        if (!who.isLeader()) {
            player.sendSystemMessage(Component.translatable("general.flightsuit.says", who.displayName(),
                    Component.translatable("fort.flightsuit.officer." + tier.name().toLowerCase(java.util.Locale.ROOT), player.getName())));
            return;
        }
        String tierKey = tier.name().toLowerCase(java.util.Locale.ROOT);
        player.sendSystemMessage(standing.leads(kingdom) ? who.line("lord", player.getName()) : who.line(tierKey, player.getName()));
        player.sendSystemMessage(Component.translatable("fort.flightsuit.standing", tierName(trust), trust, standing.done(kingdom))
                .withStyle(ChatFormatting.GRAY));
        if (tier == Standing.Tier.HOSTILE) {
            return;
        }
        Request request = data.openRequest(player.getUUID(), kingdom);
        if (request != null) {
            player.sendSystemMessage(describe(data, request));
            if (request.state == Request.State.OFFERED) {
                player.sendSystemMessage(answerButtons(request));
            } else if (request.type == Request.Type.REINFORCE && request.state == Request.State.ACCEPTED) {
                player.sendSystemMessage(button("request.flightsuit.button_send", "/village send " + request.id,
                        "request.flightsuit.button_send_hint", ChatFormatting.GREEN));
            }
        }
        MutableComponent actions = Component.empty();
        boolean any = false;
        if (tier == Standing.Tier.ALLIED || standing.leads(kingdom)) {
            actions.append(button("fort.flightsuit.button_muster", "/village muster " + kingdom.id(), "fort.flightsuit.button_muster_hint",
                    ChatFormatting.AQUA));
            any = true;
        }
        if (!standing.leads(kingdom) && trust >= 100 && standing.done(kingdom) >= WarTuning.LEADER_REQUESTS) {
            actions.append(Component.literal(" ")).append(button("fort.flightsuit.button_lead", "/village lead " + kingdom.id(),
                    "fort.flightsuit.button_lead_hint", ChatFormatting.GOLD));
            any = true;
        }
        if (standing.leads(kingdom)) {
            for (Kingdom other : Kingdom.values()) {
                if (other != kingdom) {
                    actions.append(Component.literal(" ")).append(button(Component.translatable("fort.flightsuit.button_war", other.displayName()),
                            "/village war " + other.id(), "fort.flightsuit.button_war_hint", ChatFormatting.RED));
                }
            }
            any = true;
        }
        if (any) {
            player.sendSystemMessage(actions);
        }
    }

    // ---------------------------------------------------------------- requests

    /** Test helper (/flightsuit fort request): this kingdom asks this player now (and the fortress counts as found). */
    public static boolean forceOffer(ServerPlayer player, Kingdom kingdom, Request.Type type) {
        WarData data = WarData.get(player.server);
        Request open = data.openRequest(player.getUUID(), kingdom);
        if (open != null) {
            data.endRequest(open);
        }
        data.markFound(player.getUUID(), kingdom);
        long day = player.level().getDayTime() / 24000L;
        Kingdom target = null;
        for (Kingdom other : Kingdom.values()) {
            if (other != kingdom) {
                target = other;
                break;
            }
        }
        Request request = data.newRequest(player.getUUID(), kingdom, type, type == Request.Type.REINFORCE ? null : target, day);
        request.needed = 1;
        request.days = 1;
        // Due today, so the battle can start right away (at dusk; or now if it's already past dusk).
        request.day = day;
        data.setDirty();
        player.sendSystemMessage(describe(data, request));
        player.sendSystemMessage(answerButtons(request));
        return true;
    }

    private static void maybeOffer(ServerLevel level, WarData data, ServerPlayer player, Standing standing, Kingdom kingdom, long day) {
        FortRecord fort = data.fort(kingdom);
        if (!standing.hasFound(kingdom) || fort == null || fort.isFallen(day) || standing.trust(kingdom) < 0
                || data.openRequest(player.getUUID(), kingdom) != null || day - standing.lastRequest[kingdom.ordinal()] < WarTuning.REQUEST_INTERVAL) {
            return;
        }
        standing.lastRequest[kingdom.ordinal()] = day;
        data.setDirty();
        RandomSource random = level.random;
        List<Kingdom> others = new ArrayList<>();
        for (Kingdom other : Kingdom.values()) {
            FortRecord otherFort = data.fort(other);
            if (other != kingdom && otherFort != null && !otherFort.isFallen(day)) {
                others.add(other);
            }
        }
        float roll = random.nextFloat();
        Request request;
        if (others.isEmpty() || roll < 0.35F) {
            request = data.newRequest(player.getUUID(), kingdom, Request.Type.REINFORCE, null, day);
            request.needed = 1 + random.nextInt(2);
            request.days = 2 + random.nextInt(2);
            request.day = day + 1;
        } else if (roll < 0.7F || standing.trust(kingdom) < 10) {
            request = data.newRequest(player.getUUID(), kingdom, Request.Type.DEFEND, others.get(random.nextInt(others.size())), day);
            request.day = day + 1;
        } else {
            request = data.newRequest(player.getUUID(), kingdom, Request.Type.INVADE, others.get(random.nextInt(others.size())), day);
            request.day = day + 2;
        }
        data.setDirty();
        General leader = General.leaderOf(kingdom);
        EdithAlert.send(player, Component.translatable("request.flightsuit.title", kingdom.displayName()),
                Component.translatable("request.flightsuit.from", leader == null ? kingdom.displayName() : leader.displayName()),
                null, null, EdithAlert.CYAN, false);
        player.sendSystemMessage(describe(data, request));
        player.sendSystemMessage(answerButtons(request));
    }

    /** "[촉] 유비의 의뢰: 내일 해질녘 위군이 성채를 칩니다. 와서 도와주시오." and so on. */
    private static Component describe(WarData data, Request request) {
        General leader = General.leaderOf(request.kingdom);
        Component who = leader == null ? request.kingdom.displayName() : leader.displayName();
        Component target = request.target == null ? Component.empty() : request.target.displayName();
        String key = "request.flightsuit." + request.type.name().toLowerCase(java.util.Locale.ROOT);
        Component body = switch (request.type) {
            case DEFEND -> Component.translatable(key, target, request.day);
            case REINFORCE -> Component.translatable(key, request.needed, request.days, request.day);
            case INVADE -> Component.translatable(key, target, request.day);
        };
        Component state = Component.translatable("request.flightsuit.state." + request.state.name().toLowerCase(java.util.Locale.ROOT));
        return Component.translatable("request.flightsuit.line", request.kingdom.displayName(), who, body, state);
    }

    private static Component answerButtons(Request request) {
        return Component.empty()
                .append(button("request.flightsuit.button_accept", "/village accept " + request.id, "request.flightsuit.button_accept_hint", ChatFormatting.GREEN))
                .append(Component.literal("  "))
                .append(button("request.flightsuit.button_decline", "/village decline " + request.id, "request.flightsuit.button_decline_hint", ChatFormatting.GRAY));
    }

    public static boolean accept(ServerPlayer player, int id) {
        WarData data = WarData.get(player.server);
        Request request = data.requests().get(id);
        if (request == null || !request.player.equals(player.getUUID()) || request.state != Request.State.OFFERED) {
            player.sendSystemMessage(Component.translatable("request.flightsuit.none").withStyle(ChatFormatting.GRAY));
            return false;
        }
        request.state = Request.State.ACCEPTED;
        data.setDirty();
        player.sendSystemMessage(Component.translatable("request.flightsuit.accepted." + request.type.name().toLowerCase(java.util.Locale.ROOT),
                request.kingdom.displayName(), request.target == null ? Component.empty() : request.target.displayName(), request.day)
                .withStyle(ChatFormatting.GREEN));
        if (request.type == Request.Type.REINFORCE) {
            player.sendSystemMessage(button("request.flightsuit.button_send", "/village send " + request.id,
                    "request.flightsuit.button_send_hint", ChatFormatting.GREEN));
        }
        return true;
    }

    public static boolean decline(ServerPlayer player, int id) {
        WarData data = WarData.get(player.server);
        Request request = data.requests().get(id);
        if (request == null || !request.player.equals(player.getUUID()) || request.state != Request.State.OFFERED) {
            player.sendSystemMessage(Component.translatable("request.flightsuit.none").withStyle(ChatFormatting.GRAY));
            return false;
        }
        data.addTrust(player.getUUID(), request.kingdom, -5);
        data.endRequest(request);
        player.sendSystemMessage(Component.translatable("request.flightsuit.declined", request.kingdom.displayName()).withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** REINFORCE: the soldier residents near the player march off (saved away until they come back). */
    public static boolean send(ServerPlayer player, int id) {
        WarData data = WarData.get(player.server);
        Request request = data.requests().get(id);
        if (request == null || !request.player.equals(player.getUUID()) || request.type != Request.Type.REINFORCE
                || request.state != Request.State.ACCEPTED) {
            player.sendSystemMessage(Component.translatable("request.flightsuit.none").withStyle(ChatFormatting.GRAY));
            return false;
        }
        VillageHallBlockEntity hall = Villages.containing(player.level(), player.blockPosition());
        if (hall == null || !hall.isOwner(player)) {
            player.sendSystemMessage(Component.translatable("request.flightsuit.send_in_village").withStyle(ChatFormatting.GRAY));
            return false;
        }
        List<ResidentEntity> soldiers = new ArrayList<>();
        for (ResidentEntity resident : hall.residents()) {
            if (resident.getJob() == ResidentJob.SOLDIER && !resident.isDowned() && !resident.isBaby() && resident.getCommander() == null) {
                soldiers.add(resident);
            }
        }
        if (soldiers.size() < request.needed) {
            player.sendSystemMessage(Component.translatable("request.flightsuit.not_enough", request.needed, soldiers.size())
                    .withStyle(ChatFormatting.YELLOW));
            return false;
        }
        for (int i = 0; i < request.needed; i++) {
            ResidentEntity soldier = soldiers.get(i);
            CompoundTag saved = new CompoundTag();
            if (soldier.save(saved)) {
                request.away.add(saved);
            }
            if (player.level() instanceof ServerLevel server) {
                server.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, soldier.getX(), soldier.getY() + 1.0D, soldier.getZ(),
                        8, 0.3D, 0.5D, 0.3D, 0.02D);
            }
            soldier.discard();
        }
        long day = player.level().getDayTime() / 24000L;
        request.state = Request.State.ACTIVE;
        request.returnDay = day + request.days;
        request.villageKey = WarData.keyOf(player.level().dimension(), hall.getBlockPos());
        data.setDirty();
        hall.addNews(Component.translatable("news.flightsuit.soldiers_lent", request.needed, request.kingdom.displayName()));
        hall.refreshStats();
        player.sendSystemMessage(Component.translatable("request.flightsuit.sent", request.needed, request.kingdom.displayName(), request.days)
                .withStyle(ChatFormatting.GREEN));
        return true;
    }

    private static void tickRequest(ServerLevel level, WarData data, Request request, long day) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.player);
        long now = level.getDayTime();
        switch (request.state) {
            case OFFERED -> {
                if (day > request.offeredDay + 1) {
                    data.addTrust(request.player, request.kingdom, -3);
                    data.endRequest(request);
                    if (player != null) {
                        player.sendSystemMessage(Component.translatable("request.flightsuit.expired", request.kingdom.displayName())
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            case ACCEPTED -> {
                if (request.type == Request.Type.REINFORCE) {
                    if (day > request.day) {
                        fail(level, data, request, "request.flightsuit.fail_reinforce");
                    }
                    return;
                }
                long open = request.day * 24000L + WarTuning.RAID_TIME;
                if (now < open) {
                    return;
                }
                if (now > open + 24000L) {
                    fail(level, data, request, "request.flightsuit.fail_absent");
                    return;
                }
                Kingdom where = request.type == Request.Type.DEFEND ? request.kingdom : request.target;
                FortRecord fort = where == null ? null : data.fort(where);
                if (fort == null) {
                    data.endRequest(request);
                    return;
                }
                boolean there = player != null && player.level().dimension() == Level.OVERWORLD
                        && player.distanceToSqr(fort.x, player.getY(), fort.z) < 128.0D * 128.0D;
                if (!there) {
                    if (!request.reminded && player != null) {
                        request.reminded = true;
                        data.setDirty();
                        EdithAlert.send(player, Component.translatable("request.flightsuit.now_title"),
                                Component.translatable("request.flightsuit.now", where.displayName()), Level.OVERWORLD,
                                new BlockPos(fort.x, fort.y, fort.z), EdithAlert.AMBER, true);
                    }
                    return;
                }
                if (data.battleAt(where) != null) {
                    return;
                }
                Battle battle = request.type == Request.Type.DEFEND
                        ? FortressManager.startBattle(level, data, Battle.Kind.STORM, request.kingdom, request.target, request.player, request.id)
                        : FortressManager.startBattle(level, data, Battle.Kind.INVADE, request.target, request.kingdom, request.player, request.id);
                request.battle = battle.id;
                request.state = Request.State.ACTIVE;
                if (request.type == Request.Type.DEFEND && request.target != null) {
                    // Standing with the defenders costs you with the attackers.
                    data.addTrust(request.player, request.target, -10);
                }
                data.setDirty();
                player.sendSystemMessage(Component.translatable("request.flightsuit.battle_on." + request.type.name().toLowerCase(java.util.Locale.ROOT),
                        request.kingdom.displayName(), request.target == null ? Component.empty() : request.target.displayName())
                        .withStyle(ChatFormatting.RED));
            }
            case ACTIVE -> {
                if (request.type != Request.Type.REINFORCE && data.battles().get(request.battle) == null) {
                    // The battle ended without word back (e.g. a restart): don't leave it hanging.
                    data.endRequest(request);
                }
            }
        }
    }

    /** REINFORCE: lent soldiers come back when their village is loaded and the days are up (not all of them, maybe). */
    public static void onVillageLoaded(ServerLevel level, WarData data, WarData.VillageRecord record, VillageHallBlockEntity hall) {
        long day = level.getDayTime() / 24000L;
        for (Request request : new ArrayList<>(data.requests().values())) {
            if (request.type != Request.Type.REINFORCE || request.state != Request.State.ACTIVE || day < request.returnDay
                    || !request.villageKey.equals(record.key())) {
                continue;
            }
            int back = 0;
            List<Component> fallen = new ArrayList<>();
            BlockPos at = hall.getBlockPos().relative(hall.getBlockState().getValue(com.pfkfks.flightsuit.village.VillageHallBlock.FACING), 3);
            for (Tag raw : request.away) {
                CompoundTag saved = ((CompoundTag) raw).copy();
                Entity entity = EntityType.create(saved, level).orElse(null);
                if (!(entity instanceof ResidentEntity soldier)) {
                    continue;
                }
                if (level.random.nextFloat() < WarTuning.REINFORCE_LOSS) {
                    fallen.add(soldier.getName());
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                soldier.moveTo(at.getX() + 0.5D, y, at.getZ() + 0.5D, soldier.getYRot(), 0.0F);
                soldier.setCommander(null);
                level.addFreshEntity(soldier);
                back++;
            }
            for (Component name : fallen) {
                hall.addNews(Component.translatable("news.flightsuit.fell_abroad", name));
            }
            hall.addNews(Component.translatable("news.flightsuit.soldiers_back", back));
            hall.refreshStats();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.player);
            if (player != null && !fallen.isEmpty()) {
                player.sendSystemMessage(Component.translatable("request.flightsuit.losses", fallen.size()).withStyle(ChatFormatting.DARK_RED));
            }
            succeed(level, data, request, 12);
        }
    }

    /** An invasion's target fortress fell (FortressManager.surrender). */
    public static void onInvasionWon(ServerLevel level, WarData data, Battle battle) {
        data.endBattle(battle);
        Request request = data.requests().get(battle.request);
        if (request != null) {
            succeed(level, data, request, 25);
        } else if (battle.player != null) {
            data.addTrust(battle.player, battle.attacker, 5);
        }
    }

    /** A storm was held off (or not), or an invasion ran out of time. */
    public static void onBattleOver(ServerLevel level, WarData data, Battle battle, boolean held) {
        Request request = data.requests().get(battle.request);
        if (request == null) {
            return;
        }
        if (battle.kind == Battle.Kind.STORM && held) {
            succeed(level, data, request, 15);
        } else {
            fail(level, data, request, battle.kind == Battle.Kind.STORM ? "request.flightsuit.fail_storm" : "request.flightsuit.fail_invade");
        }
    }

    private static void succeed(ServerLevel level, WarData data, Request request, int trust) {
        data.addTrust(request.player, request.kingdom, trust);
        data.markDone(request.player, request.kingdom);
        data.endRequest(request);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.player);
        if (player == null) {
            return;
        }
        RandomSource random = level.random;
        List<ItemStack> reward = new ArrayList<>(List.of(new ItemStack(Items.EMERALD, 4 + random.nextInt(5)),
                new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(4))));
        if (request.type == Request.Type.INVADE) {
            reward.add(new ItemStack(Items.DIAMOND, 1 + random.nextInt(2)));
        }
        give(player, reward);
        General leader = General.leaderOf(request.kingdom);
        if (leader != null) {
            player.sendSystemMessage(leader.line("thanks", player.getName()));
        }
        Standing standing = data.standing(request.player);
        player.sendSystemMessage(Component.translatable("request.flightsuit.done", request.kingdom.displayName(),
                tierName(standing.trust(request.kingdom)), standing.trust(request.kingdom)).withStyle(ChatFormatting.GREEN));
        if (standing.trust(request.kingdom) >= 100 && standing.done(request.kingdom) >= WarTuning.LEADER_REQUESTS && !standing.leads(request.kingdom)) {
            player.sendSystemMessage(Component.translatable("fort.flightsuit.lead_ready", request.kingdom.displayName()).withStyle(ChatFormatting.GOLD));
        }
    }

    private static void fail(ServerLevel level, WarData data, Request request, String why) {
        data.addTrust(request.player, request.kingdom, -10);
        data.endRequest(request);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.player);
        if (player != null) {
            player.sendSystemMessage(Component.translatable(why, request.kingdom.displayName()).withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------- allies and leaders

    /** "/village muster": allied (or led) kingdoms send support troops that march with the player for a day. */
    public static boolean muster(ServerPlayer player, Kingdom kingdom) {
        WarData data = WarData.get(player.server);
        Standing standing = data.standing(player.getUUID());
        boolean leads = standing.leads(kingdom);
        if (!leads && Standing.tier(standing.trust(kingdom)) != Standing.Tier.ALLIED) {
            player.sendSystemMessage(Component.translatable("fort.flightsuit.muster_not_allied", kingdom.displayName()).withStyle(ChatFormatting.GRAY));
            return false;
        }
        long day = player.level().getDayTime() / 24000L;
        int cooldown = leads ? WarTuning.MUSTER_COOLDOWN_LEADER : WarTuning.MUSTER_COOLDOWN_DAYS;
        long last = standing.lastMuster[kingdom.ordinal()];
        if (last > 0 && day - last < cooldown) {
            player.sendSystemMessage(Component.translatable("fort.flightsuit.muster_wait", cooldown - (day - last)).withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        standing.lastMuster[kingdom.ordinal()] = day;
        data.setDirty();
        int count = leads ? WarTuning.MUSTER_SIZE_LEADER : WarTuning.MUSTER_SIZE;
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            KingdomSoldierEntity.Type type = i % 3 == 2 ? KingdomSoldierEntity.Type.ARCHER
                    : random.nextBoolean() ? KingdomSoldierEntity.Type.SWORD : KingdomSoldierEntity.Type.SPEAR;
            KingdomSoldierEntity soldier = KingdomSoldierEntity.create(level, kingdom, type, -1, null, false)
                    .asAlly(player.getUUID(), null, null, level.getGameTime() + WarTuning.MUSTER_TICKS, -1);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = player.getBlockX() + Mth.floor(Math.cos(angle) * 10.0D);
            int z = player.getBlockZ() + Mth.floor(Math.sin(angle) * 10.0D);
            soldier.moveTo(x + 0.5D, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(soldier);
        }
        player.sendSystemMessage(Component.translatable("fort.flightsuit.mustered", count, kingdom.displayName()).withStyle(ChatFormatting.AQUA));
        return true;
    }

    /** "/village lead": take up the leadership a kingdom offers (full trust, enough requests done). */
    public static boolean lead(ServerPlayer player, Kingdom kingdom) {
        WarData data = WarData.get(player.server);
        Standing standing = data.standing(player.getUUID());
        if (standing.leads(kingdom) || standing.trust(kingdom) < 100 || standing.done(kingdom) < WarTuning.LEADER_REQUESTS) {
            player.sendSystemMessage(Component.translatable("fort.flightsuit.lead_not_yet", kingdom.displayName(),
                    standing.trust(kingdom), standing.done(kingdom), WarTuning.LEADER_REQUESTS).withStyle(ChatFormatting.GRAY));
            return false;
        }
        data.setLeader(player.getUUID(), kingdom);
        standing.lastTribute[kingdom.ordinal()] = player.level().getDayTime() / 24000L;
        General leader = General.leaderOf(kingdom);
        if (leader != null) {
            player.sendSystemMessage(leader.line("crown", player.getName()));
        }
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            other.sendSystemMessage(Component.translatable("fort.flightsuit.new_leader", player.getName(), kingdom.displayName())
                    .withStyle(ChatFormatting.GOLD));
        }
        return true;
    }

    /** "/village war": as a kingdom's leader, march its army on another kingdom's fortress (be near it). */
    public static boolean war(ServerPlayer player, Kingdom target) {
        WarData data = WarData.get(player.server);
        Standing standing = data.standing(player.getUUID());
        Kingdom led = null;
        for (Kingdom kingdom : Kingdom.values()) {
            if (kingdom != target && standing.leads(kingdom)) {
                led = kingdom;
                break;
            }
        }
        FortRecord fort = data.fort(target);
        if (led == null || fort == null) {
            player.sendSystemMessage(Component.translatable("fort.flightsuit.war_not_leader").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || player.distanceToSqr(fort.x, player.getY(), fort.z) > 128.0D * 128.0D) {
            player.sendSystemMessage(Component.translatable("fort.flightsuit.war_go_there", target.displayName()).withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (data.battleAt(target) != null || fort.isFallen(level.getDayTime() / 24000L)) {
            player.sendSystemMessage(Component.translatable("fort.flightsuit.war_busy", target.displayName()).withStyle(ChatFormatting.GRAY));
            return false;
        }
        FortressManager.startBattle(level, data, Battle.Kind.INVADE, target, led, player.getUUID(), -1);
        player.sendSystemMessage(Component.translatable("fort.flightsuit.war_on", led.displayName(), target.displayName()).withStyle(ChatFormatting.RED));
        return true;
    }

    private static void tribute(ServerPlayer player, Kingdom kingdom, RandomSource random) {
        List<ItemStack> goods = new ArrayList<>(List.of(new ItemStack(Items.EMERALD, 3 + random.nextInt(4)),
                new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(3)), new ItemStack(Items.BREAD, 6 + random.nextInt(6))));
        goods.add(switch (kingdom) {
            case SHU -> new ItemStack(Items.WHEAT, 16);
            case WEI -> new ItemStack(Items.IRON_INGOT, 6);
            case WU -> new ItemStack(Items.COD, 8);
        });
        give(player, goods);
        player.sendSystemMessage(Component.translatable("fort.flightsuit.tribute", kingdom.displayName()).withStyle(ChatFormatting.GOLD));
    }

    // ---------------------------------------------------------------- the list

    /** "/village list": every fortress - found ones with distance, bearing and standing; the rest as rumours. */
    public static void list(ServerPlayer player) {
        WarData data = WarData.get(player.server);
        Standing standing = data.standing(player.getUUID());
        long day = player.level().getDayTime() / 24000L;
        player.sendSystemMessage(Component.translatable("fort.flightsuit.list_title").withStyle(ChatFormatting.BOLD));
        for (Kingdom kingdom : Kingdom.values()) {
            FortRecord fort = data.fort(kingdom);
            if (fort == null) {
                continue;
            }
            double dx = fort.x - player.getX();
            double dz = fort.z - player.getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);
            int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0D), 8);
            Component dir = Component.translatable("edith.flightsuit.dir." + sector);
            boolean overworld = player.level().dimension() == Level.OVERWORLD;
            if (standing.hasFound(kingdom)) {
                MutableComponent line = Component.translatable("fort.flightsuit.list_found", kingdom.displayName(),
                        overworld ? Component.translatable("edith.flightsuit.where", distance, dir) : Component.literal("-"),
                        tierName(standing.trust(kingdom)), standing.trust(kingdom), standing.done(kingdom));
                if (fort.isFallen(day)) {
                    line.append(" ").append(Component.translatable("fort.flightsuit.list_fallen", fort.fallenUntilDay - day).withStyle(ChatFormatting.DARK_RED));
                }
                if (standing.leads(kingdom)) {
                    line.append(" ").append(Component.translatable("fort.flightsuit.list_leader").withStyle(ChatFormatting.GOLD));
                }
                player.sendSystemMessage(line);
            } else {
                int rough = Math.max(100, Math.round(distance / 100.0F) * 100);
                player.sendSystemMessage(Component.translatable("fort.flightsuit.list_rumour", kingdom.displayName(),
                        overworld ? dir : Component.literal("?"), rough).withStyle(ChatFormatting.GRAY));
            }
        }
        for (Request request : data.requests().values()) {
            if (request.player.equals(player.getUUID())) {
                player.sendSystemMessage(describe(data, request));
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void give(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    private static MutableComponent button(String key, String command, String hint, ChatFormatting color) {
        return button(Component.translatable(key), command, hint, color);
    }

    private static MutableComponent button(Component label, String command, String hint, ChatFormatting color) {
        return Component.literal("[").append(label).append("]").withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(hint))));
    }
}
