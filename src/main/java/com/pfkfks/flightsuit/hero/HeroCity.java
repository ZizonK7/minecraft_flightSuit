package com.pfkfks.flightsuit.hero;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.suit.EdithAlert;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitEnergy;
import com.pfkfks.flightsuit.suit.SuitType;
import com.pfkfks.flightsuit.suit.WornSuit;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.Villages;
import com.pfkfks.flightsuit.war.FortressBuilder;
import com.pfkfks.flightsuit.war.FortressManager;
import com.pfkfks.flightsuit.war.WarData;
import com.pfkfks.flightsuit.war.WarTuning;
import com.pfkfks.flightsuit.war.ai.MarchOnVillageGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Hero City (DESIGN.md 4-13, M13): one very advanced city, far out from spawn between two of the kingdoms.
 * Getting on well with it is worth a lot - Iron Man looks after your suits - and it never attacks anyone, but
 * you can make an enemy of it, and even take it.
 *
 * Like the fortresses: built the first time someone comes near, its people put back while someone's around.
 * It asks for help with villain attacks and for soldiers on loan; Captain is who you talk to.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class HeroCity {
    private static final int BUILD_PER_TICK = 4000;
    private static final int AGENTS = 6;
    private static final String VILLAIN_TAG = "flightsuit_villain";
    private static final TicketType<ChunkPos> STORM_TICKET =
            TicketType.create("flightsuit_hero_storm", Comparator.comparingLong(ChunkPos::toLong), 100);

    private static @Nullable HeroCityBuilder job;
    private static int jobIndex;
    private static long tickingSince = -1L;
    private static int losses;
    private static long lastLossAt;

    private HeroCity() {
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        job = null;
        jobIndex = 0;
        tickingSince = -1L;
        losses = 0;
        lastLossAt = 0L;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        HeroData data = HeroData.get(level.getServer());
        tickBuild(level, data);
        if (level.getGameTime() % 20 != 10) {
            return;
        }
        ensureSite(level, data);
        long day = level.getDayTime() / 24000L;
        boolean anyoneNear = false;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - data.x;
            double dz = player.getZ() - data.z;
            double distSqr = dx * dx + dz * dz;
            if (distSqr < 128.0D * 128.0D) {
                discover(player, data);
            }
            if (distSqr < 160.0D * 160.0D) {
                anyoneNear = true;
            }
        }
        if (!data.built) {
            if (anyoneNear && job == null) {
                startBuild(level, data);
            }
        } else if (anyoneNear || data.storm != null) {
            maintain(level, data, day);
        } else {
            tickingSince = -1L;
        }
        if (data.storm != null) {
            tickStorm(level, data);
        }
        if (level.getGameTime() % 100 == 10) {
            tickRequests(level, data, day);
        }
    }

    // ---------------------------------------------------------------- site and building

    private static void ensureSite(ServerLevel level, HeroData data) {
        if (data.sited) {
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        RandomSource random = RandomSource.create(level.getSeed() ^ 0x5DEECE66DL);
        // Same base angle as the fortresses, but half-way between two of them and further out.
        double base = random.nextDouble() * Math.PI * 2.0D + Math.PI / 3.0D;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = base + Math.toRadians(12.0D * ((attempt + 1) / 2)) * (attempt % 2 == 0 ? 1 : -1);
            int distance = 950 + (attempt * 29) % 200;
            data.x = spawn.getX() + Mth.floor(Math.cos(angle) * distance);
            data.z = spawn.getZ() + Mth.floor(Math.sin(angle) * distance);
            Holder<Biome> biome = level.getBiome(new BlockPos(data.x, level.getSeaLevel(), data.z));
            if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH)) {
                break;
            }
        }
        data.sited = true;
        data.setDirty();
    }

    /** Where the city stands (picking the site now if nobody has come near yet) - for "/flightsuit hero tp". */
    public static BlockPos site(ServerLevel level) {
        HeroData data = HeroData.get(level.getServer());
        ensureSite(level, data);
        return new BlockPos(data.x, data.y, data.z);
    }

    private static void startBuild(ServerLevel level, HeroData data) {
        int[] heights = new int[9];
        int i = 0;
        for (int dx = -20; dx <= 20; dx += 20) {
            for (int dz = -20; dz <= 20; dz += 20) {
                heights[i++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, data.x + dx, data.z + dz) - 1;
            }
        }
        Arrays.sort(heights);
        data.y = Math.max(heights[4], level.getSeaLevel());
        data.setDirty();
        job = HeroCityBuilder.plan(data.center());
        jobIndex = 0;
    }

    private static void tickBuild(ServerLevel level, HeroData data) {
        if (job == null) {
            return;
        }
        List<FortressBuilder.Placement> placements = job.placements();
        int end = Math.min(placements.size(), jobIndex + BUILD_PER_TICK);
        for (; jobIndex < end; jobIndex++) {
            FortressBuilder.Placement placement = placements.get(jobIndex);
            if (placement.fillOnly()) {
                BlockState current = level.getBlockState(placement.pos());
                if (!current.isAir() && !current.canBeReplaced() && current.getFluidState().isEmpty()) {
                    continue;
                }
            }
            level.setBlock(placement.pos(), placement.state(), Block.UPDATE_CLIENTS);
        }
        if (jobIndex >= placements.size()) {
            stockVault(level, job.vault());
            job = null;
            data.built = true;
            data.setDirty();
        }
    }

    /** The two vault chests in the HQ lobby (HeroCityBuilder.headquarters). */
    private static List<BlockPos> vault(HeroData data) {
        BlockPos center = data.center();
        return List.of(center.offset(-2, 1, -4), center.offset(2, 1, -4));
    }

    /** The vault stays shut while the city stands - it's theirs - and opens once the city has fallen. */
    @SubscribeEvent
    public static void onUseBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (guardsVault(event.getLevel(), event.getPos(), event.getEntity())) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(Component.translatable("hero.flightsuit.vault_locked").withStyle(ChatFormatting.GRAY), true);
        }
    }

    @SubscribeEvent
    public static void onBreakBlock(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && guardsVault(level, event.getPos(), event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    private static boolean guardsVault(Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(level instanceof ServerLevel server) || level.dimension() != Level.OVERWORLD || player.isCreative()) {
            return false;
        }
        HeroData data = HeroData.get(server.getServer());
        return data.built && vault(data).contains(pos) && !data.isFallen(level.getDayTime() / 24000L);
    }

    private static void stockVault(ServerLevel level, List<BlockPos> chests) {
        RandomSource random = level.random;
        for (BlockPos pos : chests) {
            if (level.getBlockEntity(pos) instanceof Container chest) {
                chest.setItem(0, new ItemStack(Items.DIAMOND, 3 + random.nextInt(4)));
                chest.setItem(2, new ItemStack(ModItems.ARC_REACTOR.get(), 1 + random.nextInt(2)));
                chest.setItem(4, new ItemStack(ModItems.ENERGY_CELL.get(), 3 + random.nextInt(3)));
                chest.setItem(6, new ItemStack(Items.REDSTONE_BLOCK, 4 + random.nextInt(5)));
                chest.setItem(8, new ItemStack(Items.NETHERITE_SCRAP, 1 + random.nextInt(2)));
                chest.setChanged();
            }
        }
    }

    // ---------------------------------------------------------------- people

    private static AABB area(HeroData data) {
        return new AABB(data.center()).inflate(HeroCityBuilder.EDGE + 12, 40.0D, HeroCityBuilder.EDGE + 12);
    }

    private static void maintain(ServerLevel level, HeroData data, long day) {
        BlockPos center = data.center();
        if (!level.isPositionEntityTicking(center)) {
            tickingSince = -1L;
            return;
        }
        long now = level.getGameTime();
        if (tickingSince < 0) {
            tickingSince = now;
        }
        if (now - tickingSince < 100L || (now / 20) % 5 != 0) {
            return;
        }
        if (losses > 0 && now - lastLossAt > WarTuning.LOSS_RESET_TICKS) {
            losses = 0;
        }
        List<CityHeroEntity> present = level.getEntitiesOfClass(CityHeroEntity.class, area(data));
        RandomSource random = level.random;
        boolean fallen = data.isFallen(day);
        for (HeroType type : HeroType.values()) {
            List<CityHeroEntity> mine = new ArrayList<>();
            for (CityHeroEntity hero : present) {
                if (hero.getHeroType() == type) {
                    mine.add(hero);
                }
            }
            int want = type == HeroType.AGENT ? (fallen ? 1 : AGENTS) : (!fallen && data.isHome(type, day) ? 1 : 0);
            for (int i = want; i < mine.size(); i++) {
                mine.get(i).discard();
            }
            for (int i = mine.size(); i < want; i++) {
                CityHeroEntity hero = CityHeroEntity.create(level, type, center);
                BlockPos spot = spotFor(level, type, center, random);
                hero.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                level.addFreshEntity(hero);
            }
        }
    }

    private static BlockPos spotFor(ServerLevel level, HeroType type, BlockPos center, RandomSource random) {
        if (type == HeroType.CAPTAIN) {
            return HeroCityBuilder.lobby(center).offset(-1, 0, 1);
        }
        if (type == HeroType.IRON_MAN) {
            return HeroCityBuilder.lobby(center).offset(1, 0, 1);
        }
        // Out on the open roads (never inside a tower - Hulk wouldn't fit back through its doors).
        int headroom = type == HeroType.HULK ? 4 : 2;
        for (int tries = 0; tries < 12; tries++) {
            int along = 7 + random.nextInt(17);
            int across = random.nextInt(5) - 2;
            BlockPos pos = switch (random.nextInt(3)) {
                case 0 -> center.offset(across, 1, along);
                case 1 -> center.offset(along, 1, across);
                default -> center.offset(-along, 1, across);
            };
            boolean clear = level.canSeeSky(pos);
            for (int up = 0; up < headroom && clear; up++) {
                clear = level.getBlockState(pos.above(up)).isAir();
            }
            if (clear) {
                return pos;
            }
        }
        return center.offset(0, 1, 9);
    }

    // ---------------------------------------------------------------- standing

    private static void discover(ServerPlayer player, HeroData data) {
        HeroData.Standing standing = data.standing(player.getUUID());
        if (standing.found) {
            return;
        }
        standing.found = true;
        standing.lastRequest = player.level().getDayTime() / 24000L - 2;
        data.setDirty();
        EdithAlert.send(player, Component.translatable("hero.flightsuit.found_title"), Component.translatable("hero.flightsuit.found"),
                Level.OVERWORLD, data.center(), EdithAlert.CYAN, true);
    }

    /** The player struck (or beat) one of the city's people. */
    public static void onHeroHit(ServerPlayer player, boolean beaten) {
        HeroData data = HeroData.get(player.server);
        int before = data.trust(player.getUUID());
        data.addTrust(player.getUUID(), beaten ? -15 : -3);
        if (before > -50 && data.trust(player.getUUID()) <= -50) {
            player.sendSystemMessage(Component.translatable("hero.flightsuit.now_hostile").withStyle(ChatFormatting.RED));
        }
    }

    /** A hero (or agent) withdrew beaten. Heroes beaten by the player's side count toward the city falling. */
    public static void onHeroBeaten(ServerLevel level, CityHeroEntity hero, @Nullable Entity killer) {
        HeroData data = HeroData.get(level.getServer());
        long day = level.getDayTime() / 24000L;
        HeroType type = hero.getHeroType();
        if (type.isHero()) {
            data.sendAway(type, day + 3);
        }
        if (!FortressManager.byPlayerSide(killer) || data.isFallen(day) || !atWar(data, killer)) {
            return;
        }
        losses += type.isHero() ? 3 : 1;
        lastLossAt = level.getGameTime();
        if (type == HeroType.CAPTAIN || losses >= 12) {
            fall(level, data, killer);
        }
    }

    /**
     * Only a real attack counts toward the city falling: a player striking its people themselves, or the suits and
     * soldiers of a player the city is hostile to - not a friendly player's suit hitting back at a stray shot.
     */
    private static boolean atWar(HeroData data, @Nullable Entity killer) {
        if (killer instanceof Player) {
            return true;
        }
        UUID backer = null;
        if (killer instanceof com.pfkfks.flightsuit.entity.SuitCompanionEntity suit) {
            backer = suit.getOwnerId();
        } else if (killer instanceof ResidentEntity resident) {
            backer = resident.getCommander();
        } else if (killer instanceof com.pfkfks.flightsuit.war.RaidMember member) {
            backer = member.commander();
        }
        return backer != null && data.trust(backer) <= -50;
    }

    /** Hero City is taken: its heroes pull out for a week, the vault in the HQ lobby is there for the taking. */
    private static void fall(ServerLevel level, HeroData data, @Nullable Entity killer) {
        long day = level.getDayTime() / 24000L;
        data.fallenUntilDay = day + WarTuning.FALLEN_DAYS;
        for (HeroType type : HeroType.values()) {
            if (type.isHero()) {
                data.sendAway(type, data.fallenUntilDay);
            }
        }
        losses = 0;
        data.setDirty();
        stockVault(level, vault(data));
        // The Time Stone is kept in the vault too (DESIGN 4-16: 시간 강탈 - or Captain's gift to an ally).
        if (level.getBlockEntity(vault(data).get(0)) instanceof Container chest) {
            chest.setItem(13, new ItemStack(com.pfkfks.flightsuit.registry.ModItems.stone(com.pfkfks.flightsuit.thanos.InfinityStone.TIME)));
            chest.setChanged();
        }
        for (CityHeroEntity hero : level.getEntitiesOfClass(CityHeroEntity.class, area(data))) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, hero.getX(), hero.getY() + 1.0D, hero.getZ(), 10, 0.3D, 0.6D, 0.3D, 0.02D);
            hero.discard();
        }
        level.playSound(null, data.center(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 3.0F, 0.5F);
        Player player = killer instanceof Player direct ? direct : null;
        if (player == null && killer instanceof com.pfkfks.flightsuit.entity.SuitCompanionEntity suit) {
            player = suit.getOwner();
        }
        if (player instanceof ServerPlayer serverPlayer) {
            data.addTrust(serverPlayer.getUUID(), -100);
            EdithAlert.send(serverPlayer, Component.translatable("hero.flightsuit.fallen_title").withStyle(ChatFormatting.GOLD),
                    Component.translatable("hero.flightsuit.fallen"), Level.OVERWORLD, data.center(), EdithAlert.AMBER, true);
        }
    }

    public static boolean isVillain(LivingEntity entity) {
        return entity.getTags().contains(VILLAIN_TAG);
    }

    // ---------------------------------------------------------------- talking

    public static void talk(ServerPlayer player, CityHeroEntity hero) {
        HeroData data = HeroData.get(player.server);
        HeroData.Standing standing = data.standing(player.getUUID());
        HeroType type = hero.getHeroType();
        int trust = standing.trust;
        String tier = tierKey(trust);
        if (type == HeroType.IRON_MAN && trust > -50) {
            ironMan(player, data, standing);
            return;
        }
        if (type != HeroType.CAPTAIN || trust <= -50) {
            player.sendSystemMessage(type == HeroType.AGENT
                    ? Component.translatable("general.flightsuit.says", type.displayName(), Component.translatable("hero.flightsuit.agent." + tier))
                    : type.line(tier, player.getName()));
            return;
        }
        player.sendSystemMessage(type.line(tier, player.getName()));
        player.sendSystemMessage(Component.translatable("fort.flightsuit.standing",
                Component.translatable("tier.flightsuit." + tier), trust, standing.done).withStyle(ChatFormatting.GRAY));
        if (com.pfkfks.flightsuit.thanos.ThanosSaga.offersTimeStone(player, trust)) {
            player.sendSystemMessage(button(Component.translatable("hero.flightsuit.button_time_stone"), "/village hero timestone",
                    "hero.flightsuit.button_time_stone_hint", ChatFormatting.GREEN));
        }
        HeroData.Request request = data.openRequest(player.getUUID());
        if (request != null) {
            player.sendSystemMessage(describe(request));
            if (request.state == HeroData.Request.State.OFFERED) {
                player.sendSystemMessage(answerButtons(request));
            } else if (request.type == HeroData.Request.Type.REINFORCE && request.state == HeroData.Request.State.ACCEPTED) {
                player.sendSystemMessage(button(Component.translatable("request.flightsuit.button_send"), "/village hero send " + request.id,
                        "request.flightsuit.button_send_hint", ChatFormatting.GREEN));
            }
        }
    }

    private static String tierKey(int trust) {
        return trust <= -50 ? "hostile" : trust < 0 ? "wary" : trust < 30 ? "neutral" : trust < 70 ? "friendly" : "allied";
    }

    /** Iron Man's workshop (DESIGN 4-13: 아이언맨 = 슈트 지원): what he'll do for you depends on how well you get on. */
    private static void ironMan(ServerPlayer player, HeroData data, HeroData.Standing standing) {
        HeroType tony = HeroType.IRON_MAN;
        player.sendSystemMessage(tony.line(tierKey(standing.trust), player.getName()));
        MutableComponent services = Component.empty();
        services.append(service("repair", standing.trust >= 30, "hero.flightsuit.ironman.repair_hint"));
        services.append(Component.literal(" ")).append(service("upgrade", standing.trust >= 50, "hero.flightsuit.ironman.upgrade_hint"));
        services.append(Component.literal(" ")).append(service("reactor", standing.trust >= 50, "hero.flightsuit.ironman.reactor_hint"));
        services.append(Component.literal(" ")).append(service("gift", standing.trust >= 70 && !standing.gift, "hero.flightsuit.ironman.gift_hint"));
        player.sendSystemMessage(services);
    }

    private static MutableComponent service(String what, boolean open, String hint) {
        Component label = Component.translatable("hero.flightsuit.ironman." + what);
        if (!open) {
            return Component.literal("[").append(label).append("]").withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(hint))));
        }
        return button(label, "/village ironman " + what, hint, ChatFormatting.GOLD);
    }

    /** "/village ironman <repair|upgrade|reactor|gift>". */
    public static boolean ironManService(ServerPlayer player, String what) {
        HeroData data = HeroData.get(player.server);
        HeroData.Standing standing = data.standing(player.getUUID());
        long day = player.level().getDayTime() / 24000L;
        boolean near = !player.level().getEntitiesOfClass(CityHeroEntity.class, player.getBoundingBox().inflate(8.0D),
                hero -> hero.getHeroType() == HeroType.IRON_MAN).isEmpty();
        if (!near) {
            player.sendSystemMessage(Component.translatable("hero.flightsuit.ironman.not_here").withStyle(ChatFormatting.GRAY));
            return false;
        }
        HeroType tony = HeroType.IRON_MAN;
        switch (what) {
            case "repair" -> {
                if (standing.trust < 30 || standing.repairDay == day) {
                    player.sendSystemMessage(tony.line("not_now"));
                    return false;
                }
                int fixed = 0;
                for (EquipmentSlot slot : WornSuit.SLOTS) {
                    ItemStack stack = player.getItemBySlot(slot);
                    if (stack.getItem() instanceof SuitArmorItem && stack.isDamageableItem()) {
                        stack.setDamageValue(0);
                        SuitEnergy.set(stack, SuitEnergy.capacity(stack));
                        fixed++;
                    }
                }
                if (fixed == 0) {
                    player.sendSystemMessage(tony.line("no_suit"));
                    return false;
                }
                standing.repairDay = day;
                data.setDirty();
                player.sendSystemMessage(tony.line("repaired"));
                return true;
            }
            case "upgrade" -> {
                ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
                if (standing.trust < 50 || !(chest.getItem() instanceof SuitArmorItem)) {
                    player.sendSystemMessage(tony.line(standing.trust < 50 ? "not_now" : "no_suit"));
                    return false;
                }
                int level = SuitEnergy.upgradeLevel(chest);
                if (level >= SuitEnergy.IRON_MAN_MAX) {
                    player.sendSystemMessage(tony.line("maxed"));
                    return false;
                }
                if (!take(player, Items.DIAMOND, 4)) {
                    player.sendSystemMessage(tony.line("need_diamonds"));
                    return false;
                }
                SuitEnergy.setUpgradeLevel(chest, level + 1);
                player.sendSystemMessage(tony.line("upgraded", level + 1, SuitEnergy.capacity(chest)));
                return true;
            }
            case "reactor" -> {
                if (standing.trust < 50 || standing.reactorDay >= 0 && day - standing.reactorDay < 3) {
                    player.sendSystemMessage(tony.line("not_now"));
                    return false;
                }
                standing.reactorDay = day;
                data.setDirty();
                give(player, new ItemStack(ModItems.ARC_REACTOR.get()));
                player.sendSystemMessage(tony.line("reactor"));
                return true;
            }
            case "gift" -> {
                if (standing.trust < 70 || standing.gift) {
                    player.sendSystemMessage(tony.line("not_now"));
                    return false;
                }
                standing.gift = true;
                data.setDirty();
                // Any suit but the Mark 50 - that one is earned against Thanos.
                SuitType[] types = java.util.Arrays.stream(SuitType.values()).filter(t -> t != SuitType.NANO_MK50).toArray(SuitType[]::new);
                SuitType pick = types[player.getRandom().nextInt(types.length)];
                give(player, ModItems.capsuleFor(pick).createFilledCapsule());
                player.sendSystemMessage(tony.line("gift", Component.literal(pick.hudName())));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // ---------------------------------------------------------------- requests

    private static void tickRequests(ServerLevel level, HeroData data, long day) {
        if (!data.built || data.isFallen(day)) {
            return;
        }
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            HeroData.Standing standing = data.standing(player.getUUID());
            if (!standing.found || standing.trust < 0 || data.openRequest(player.getUUID()) != null
                    || day - standing.lastRequest < WarTuning.REQUEST_INTERVAL) {
                continue;
            }
            offer(player, data, level.random.nextFloat() < 0.6F ? HeroData.Request.Type.DEFEND : HeroData.Request.Type.REINFORCE, day, day + 1);
        }
        for (HeroData.Request request : new ArrayList<>(data.requests().values())) {
            tickRequest(level, data, request, day);
        }
    }

    private static void offer(ServerPlayer player, HeroData data, HeroData.Request.Type type, long day, long due) {
        data.standing(player.getUUID()).lastRequest = day;
        HeroData.Request request = data.newRequest(player.getUUID(), type, day);
        if (type == HeroData.Request.Type.REINFORCE) {
            request.needed = 1 + player.getRandom().nextInt(2);
            request.days = 2;
        }
        request.day = due;
        data.setDirty();
        EdithAlert.send(player, Component.translatable("hero.flightsuit.request_title"), Component.translatable("hero.flightsuit.request_from"),
                null, null, EdithAlert.CYAN, false);
        player.sendSystemMessage(describe(request));
        player.sendSystemMessage(answerButtons(request));
    }

    /** Test helper ("/flightsuit hero request <defend|reinforce>"): a request right now, due today. */
    public static boolean forceOffer(ServerPlayer player, HeroData.Request.Type type) {
        HeroData data = HeroData.get(player.server);
        HeroData.Request open = data.openRequest(player.getUUID());
        if (open != null) {
            data.endRequest(open);
        }
        long day = player.level().getDayTime() / 24000L;
        offer(player, data, type, day, day);
        return true;
    }

    private static void tickRequest(ServerLevel level, HeroData data, HeroData.Request request, long day) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.player);
        switch (request.state) {
            case OFFERED -> {
                if (day > request.offeredDay + 1) {
                    data.addTrust(request.player, -3);
                    data.endRequest(request);
                }
            }
            case ACCEPTED -> {
                if (request.type == HeroData.Request.Type.REINFORCE) {
                    if (day > request.day) {
                        fail(level, data, request);
                    }
                    return;
                }
                long open = request.day * 24000L + WarTuning.RAID_TIME;
                long now = level.getDayTime();
                if (now < open) {
                    return;
                }
                if (now > open + 24000L) {
                    fail(level, data, request);
                    return;
                }
                boolean there = player != null && player.level().dimension() == Level.OVERWORLD
                        && player.distanceToSqr(data.x, player.getY(), data.z) < 128.0D * 128.0D;
                if (!there) {
                    if (!request.reminded && player != null) {
                        request.reminded = true;
                        data.setDirty();
                        EdithAlert.send(player, Component.translatable("request.flightsuit.now_title"),
                                Component.translatable("hero.flightsuit.now"), Level.OVERWORLD, data.center(), EdithAlert.AMBER, true);
                    }
                    return;
                }
                if (data.storm == null) {
                    HeroData.Storm storm = new HeroData.Storm();
                    storm.request = request.id;
                    storm.player = request.player;
                    storm.startedAt = level.getGameTime();
                    data.storm = storm;
                    request.state = HeroData.Request.State.ACTIVE;
                    data.setDirty();
                    player.sendSystemMessage(Component.translatable("hero.flightsuit.storm_on").withStyle(ChatFormatting.RED));
                }
            }
            case ACTIVE -> {
                if (request.type == HeroData.Request.Type.DEFEND && data.storm == null) {
                    data.endRequest(request);
                } else if (request.type == HeroData.Request.Type.REINFORCE
                        && (WarData.get(level.getServer()).villages().get(request.villageKey) == null || day > request.returnDay + 10)) {
                    // Their village is gone, or never visited again: the lent soldiers stay on in the city, the request is closed.
                    data.endRequest(request);
                }
            }
        }
    }

    private static Component describe(HeroData.Request request) {
        Component body = request.type == HeroData.Request.Type.DEFEND
                ? Component.translatable("hero.flightsuit.request.defend", request.day)
                : Component.translatable("request.flightsuit.reinforce", request.needed, request.days, request.day);
        Component state = Component.translatable("request.flightsuit.state." + request.state.name().toLowerCase(java.util.Locale.ROOT));
        return Component.translatable("request.flightsuit.line", Component.translatable("hero.flightsuit.city"), HeroType.CAPTAIN.displayName(), body, state);
    }

    private static Component answerButtons(HeroData.Request request) {
        return Component.empty()
                .append(button(Component.translatable("request.flightsuit.button_accept"), "/village hero accept " + request.id,
                        "request.flightsuit.button_accept_hint", ChatFormatting.GREEN))
                .append(Component.literal("  "))
                .append(button(Component.translatable("request.flightsuit.button_decline"), "/village hero decline " + request.id,
                        "request.flightsuit.button_decline_hint", ChatFormatting.GRAY));
    }

    public static boolean accept(ServerPlayer player, int id) {
        HeroData data = HeroData.get(player.server);
        HeroData.Request request = data.requests().get(id);
        if (request == null || !request.player.equals(player.getUUID()) || request.state != HeroData.Request.State.OFFERED) {
            player.sendSystemMessage(Component.translatable("request.flightsuit.none").withStyle(ChatFormatting.GRAY));
            return false;
        }
        request.state = HeroData.Request.State.ACCEPTED;
        data.setDirty();
        player.sendSystemMessage(Component.translatable(request.type == HeroData.Request.Type.DEFEND
                ? "hero.flightsuit.accepted_defend" : "hero.flightsuit.accepted_reinforce", request.day).withStyle(ChatFormatting.GREEN));
        if (request.type == HeroData.Request.Type.REINFORCE) {
            player.sendSystemMessage(button(Component.translatable("request.flightsuit.button_send"), "/village hero send " + request.id,
                    "request.flightsuit.button_send_hint", ChatFormatting.GREEN));
        }
        return true;
    }

    public static boolean decline(ServerPlayer player, int id) {
        HeroData data = HeroData.get(player.server);
        HeroData.Request request = data.requests().get(id);
        if (request == null || !request.player.equals(player.getUUID()) || request.state != HeroData.Request.State.OFFERED) {
            player.sendSystemMessage(Component.translatable("request.flightsuit.none").withStyle(ChatFormatting.GRAY));
            return false;
        }
        data.addTrust(player.getUUID(), -5);
        data.endRequest(request);
        player.sendSystemMessage(Component.translatable("request.flightsuit.declined", Component.translatable("hero.flightsuit.city"))
                .withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** REINFORCE: lend soldier residents (they come back via onVillageLoaded). */
    public static boolean send(ServerPlayer player, int id) {
        HeroData data = HeroData.get(player.server);
        HeroData.Request request = data.requests().get(id);
        if (request == null || !request.player.equals(player.getUUID()) || request.type != HeroData.Request.Type.REINFORCE
                || request.state != HeroData.Request.State.ACCEPTED) {
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
            CompoundTag saved = new CompoundTag();
            if (soldiers.get(i).save(saved)) {
                request.away.add(saved);
            }
            soldiers.get(i).discard();
        }
        request.state = HeroData.Request.State.ACTIVE;
        request.returnDay = player.level().getDayTime() / 24000L + request.days;
        request.villageKey = WarData.keyOf(player.level().dimension(), hall.getBlockPos());
        data.setDirty();
        hall.addNews(Component.translatable("news.flightsuit.soldiers_lent", request.needed, Component.translatable("hero.flightsuit.city")));
        hall.refreshStats();
        player.sendSystemMessage(Component.translatable("request.flightsuit.sent", request.needed, Component.translatable("hero.flightsuit.city"),
                request.days).withStyle(ChatFormatting.GREEN));
        return true;
    }

    /** From RaidManager.noteVillage, via the war side: lent soldiers come home when the days are up. */
    public static void onVillageLoaded(ServerLevel level, String villageKey, VillageHallBlockEntity hall) {
        HeroData data = HeroData.get(level.getServer());
        long day = level.getDayTime() / 24000L;
        for (HeroData.Request request : new ArrayList<>(data.requests().values())) {
            if (request.type != HeroData.Request.Type.REINFORCE || request.state != HeroData.Request.State.ACTIVE
                    || day < request.returnDay || !request.villageKey.equals(villageKey)) {
                continue;
            }
            BlockPos at = hall.getBlockPos().relative(hall.getBlockState().getValue(com.pfkfks.flightsuit.village.VillageHallBlock.FACING), 3);
            int back = 0;
            for (Tag raw : request.away) {
                Entity entity = EntityType.create(((CompoundTag) raw).copy(), level).orElse(null);
                if (entity instanceof ResidentEntity soldier) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
                    soldier.moveTo(at.getX() + 0.5D, y, at.getZ() + 0.5D, soldier.getYRot(), 0.0F);
                    level.addFreshEntity(soldier);
                    back++;
                }
            }
            hall.addNews(Component.translatable("news.flightsuit.soldiers_back", back));
            hall.refreshStats();
            succeed(level, data, request, 12);
        }
    }

    private static void succeed(ServerLevel level, HeroData data, HeroData.Request request, int trust) {
        data.addTrust(request.player, trust);
        data.standing(request.player).done++;
        data.endRequest(request);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.player);
        if (player != null) {
            give(player, new ItemStack(Items.DIAMOND, 1 + level.random.nextInt(2)));
            give(player, new ItemStack(ModItems.ENERGY_CELL.get(), 2));
            player.sendSystemMessage(HeroType.CAPTAIN.line("thanks", player.getName()));
            player.sendSystemMessage(Component.translatable("request.flightsuit.done", Component.translatable("hero.flightsuit.city"),
                    Component.translatable("tier.flightsuit." + tierKey(data.trust(request.player))), data.trust(request.player))
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    private static void fail(ServerLevel level, HeroData data, HeroData.Request request) {
        data.addTrust(request.player, -10);
        data.endRequest(request);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(request.player);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("hero.flightsuit.failed").withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------- villain attacks

    /** Three waves of villains (illager gangs - later the Chitauri, DESIGN 4-16) storm the city. */
    private static void tickStorm(ServerLevel level, HeroData data) {
        HeroData.Storm storm = data.storm;
        if (storm == null) {
            return;
        }
        ChunkPos chunk = new ChunkPos(data.center());
        level.getChunkSource().addRegionTicket(STORM_TICKET, chunk, 7, chunk);
        long now = level.getGameTime();
        HeroData.Request request = data.requests().get(storm.request);
        if (now - storm.startedAt > WarTuning.BATTLE_MAX_TICKS) {
            clearVillains(level, data);
            data.storm = null;
            data.setDirty();
            if (request != null) {
                fail(level, data, request);
            }
            return;
        }
        if (!level.isPositionEntityTicking(data.center())) {
            return;
        }
        int fighting = level.getEntitiesOfClass(Mob.class, area(data).inflate(8.0D), mob -> mob.isAlive() && isVillain(mob)).size();
        if (storm.wave < 3 && (storm.wave == 0 || fighting <= Math.max(1, storm.lastWave / 3) || now - storm.waveAt > WarTuning.WAVE_TIMEOUT)) {
            villainWave(level, data, storm);
        } else if (storm.wave >= 3 && fighting == 0) {
            clearVillains(level, data);
            data.storm = null;
            data.setDirty();
            if (request != null) {
                succeed(level, data, request, 15);
            }
        }
    }

    private static void villainWave(ServerLevel level, HeroData data, HeroData.Storm storm) {
        storm.wave++;
        storm.waveAt = level.getGameTime();
        RandomSource random = level.random;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int bx = data.x + Mth.floor(Math.cos(angle) * (HeroCityBuilder.EDGE + 6));
        int bz = data.z + Mth.floor(Math.sin(angle) * (HeroCityBuilder.EDGE + 6));
        List<EntityType<? extends Mob>> roster = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            roster.add(i % 2 == 0 ? EntityType.PILLAGER : EntityType.VINDICATOR);
        }
        if (storm.wave >= 2) {
            roster.add(EntityType.PILLAGER);
            roster.add(EntityType.EVOKER);
        }
        if (storm.wave == 3) {
            roster.add(EntityType.RAVAGER);
            roster.add(EntityType.EVOKER);
        }
        int spawned = 0;
        // The herald (DESIGN 4-16 전조): Chitauri among the villains.
        if (storm.wave >= 2 && com.pfkfks.flightsuit.thanos.ThanosSaga.heraldActive(level.getServer())) {
            for (int i = 0; i < storm.wave * 2 - 2; i++) {
                int x = bx + random.nextInt(9) - 4;
                int z = bz + random.nextInt(9) - 4;
                BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                com.pfkfks.flightsuit.thanos.ThanosForceEntity chitauri = com.pfkfks.flightsuit.thanos.ThanosForceEntity.create(level,
                        com.pfkfks.flightsuit.thanos.ThanosForce.CHITAURI, at, 0);
                chitauri.moveTo(x + 0.5D, at.getY(), z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                chitauri.addTag(VILLAIN_TAG);
                arm(chitauri, data.center());
                if (level.addFreshEntity(chitauri)) {
                    spawned++;
                }
            }
            if (storm.wave == 2 && storm.player != null) {
                ServerPlayer watcher = level.getServer().getPlayerList().getPlayer(storm.player);
                if (watcher != null) {
                    watcher.sendSystemMessage(HeroType.CAPTAIN.line("chitauri"));
                }
            }
        }
        for (EntityType<? extends Mob> type : roster) {
            Mob mob = type.create(level);
            if (mob == null) {
                continue;
            }
            int x = bx + random.nextInt(9) - 4;
            int z = bz + random.nextInt(9) - 4;
            mob.moveTo(x + 0.5D, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            if (type == EntityType.PILLAGER) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            } else if (type == EntityType.VINDICATOR) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            }
            mob.addTag(VILLAIN_TAG);
            mob.setPersistenceRequired();
            arm(mob, data.center());
            if (level.addFreshEntity(mob)) {
                spawned++;
            }
        }
        storm.lastWave = spawned;
        data.setDirty();
        level.playSound(null, new BlockPos(bx, data.y, bz), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0F, 0.6F);
        if (storm.player != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(storm.player);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("hero.flightsuit.villain_wave", storm.wave).withStyle(ChatFormatting.RED));
            }
        }
    }

    /** Villains march on the city and go for its people (goals aren't saved - re-added on load, see onJoin). */
    private static void arm(Mob mob, BlockPos center) {
        if (mob instanceof PathfinderMob walker) {
            walker.goalSelector.addGoal(4, new MarchOnVillageGoal(walker, () -> walker.getTarget() == null ? center : null));
        }
        mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, CityHeroEntity.class, true));
    }

    /** A villain loaded back from disk: still fighting if the attack is on, else gone. */
    @SubscribeEvent
    public static void onJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob) || !isVillain(mob)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        HeroData data = HeroData.get(level.getServer());
        if (data.storm == null) {
            event.setCanceled(true);
            return;
        }
        arm(mob, data.center());
    }

    private static void clearVillains(ServerLevel level, HeroData data) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area(data).inflate(8.0D), HeroCity::isVillain)) {
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1.0D, mob.getZ(), 8, 0.3D, 0.5D, 0.3D, 0.02D);
            mob.discard();
        }
    }

    // ---------------------------------------------------------------- list line, helpers

    /** One line for "/village list". */
    public static Component listLine(ServerPlayer player) {
        HeroData data = HeroData.get(player.server);
        HeroData.Standing standing = data.standing(player.getUUID());
        double dx = data.x - player.getX();
        double dz = data.z - player.getZ();
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0D), 8);
        Component dir = Component.translatable("edith.flightsuit.dir." + sector);
        if (!standing.found) {
            boolean overworld = player.level().dimension() == Level.OVERWORLD;
            return Component.translatable("hero.flightsuit.list_rumour", overworld ? dir : Component.literal("?"),
                    Math.max(100, Math.round(distance / 100.0F) * 100)).withStyle(ChatFormatting.GRAY);
        }
        boolean overworld = player.level().dimension() == Level.OVERWORLD;
        MutableComponent line = Component.translatable("hero.flightsuit.list_found",
                overworld ? Component.translatable("edith.flightsuit.where", distance, dir) : Component.literal("-"),
                Component.translatable("tier.flightsuit." + tierKey(standing.trust)), standing.trust, standing.done);
        long day = player.level().getDayTime() / 24000L;
        if (data.isFallen(day)) {
            line.append(" ").append(Component.translatable("fort.flightsuit.list_fallen", data.fallenUntilDay - day).withStyle(ChatFormatting.DARK_RED));
        }
        HeroData.Request request = data.openRequest(player.getUUID());
        if (request != null) {
            line.append("\n").append(describe(request));
        }
        return line;
    }

    private static boolean take(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        int have = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                have += stack.getCount();
            }
        }
        if (have < count) {
            return false;
        }
        int left = count;
        for (ItemStack stack : player.getInventory().items) {
            if (left > 0 && stack.is(item)) {
                int used = Math.min(left, stack.getCount());
                stack.shrink(used);
                left -= used;
            }
        }
        return true;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static MutableComponent button(Component label, String command, String hint, ChatFormatting color) {
        return Component.literal("[").append(label).append("]").withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(hint))));
    }
}
