package com.pfkfks.flightsuit.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The working day of the jobs that had only clothes until the M16 test ("make every job really work"), run by
 * the hall each morning - before breakfast, so the cook's bread is on the table:
 * - cook: bakes wheat into bread, cooks raw meat and fish, bakes potatoes; a warm meal lifts everyone's mood;
 * - blacksmith: smelts raw ore, cobblestone and sand from the storage, makes glass panes when the builders are
 *   short of them, and keeps the guards' and soldiers' weapons sharp (a damage bonus by their stars);
 * - rancher: breeds the village's cows, sheep, pigs and chickens from the storage's feed, gathers eggs and
 *   wool, and takes one for meat when a herd grows too big;
 * - musician: plays in the evening (MusicianGoal) - the next morning everyone is in better spirits.
 * More stars, more done. With their workplace built, a third more.
 */
public final class VillageWorkday {
    /** Raw → cooked, one for one (wheat is three to one, in cook()). */
    private static final Map<Item, Item> COOKED = new LinkedHashMap<>();
    /** Raw → smelted, one for one. */
    private static final Map<Item, Item> SMELTED = new LinkedHashMap<>();

    static {
        COOKED.put(Items.BEEF, Items.COOKED_BEEF);
        COOKED.put(Items.PORKCHOP, Items.COOKED_PORKCHOP);
        COOKED.put(Items.CHICKEN, Items.COOKED_CHICKEN);
        COOKED.put(Items.MUTTON, Items.COOKED_MUTTON);
        COOKED.put(Items.RABBIT, Items.COOKED_RABBIT);
        COOKED.put(Items.COD, Items.COOKED_COD);
        COOKED.put(Items.SALMON, Items.COOKED_SALMON);
        COOKED.put(Items.POTATO, Items.BAKED_POTATO);
        SMELTED.put(Items.RAW_IRON, Items.IRON_INGOT);
        SMELTED.put(Items.RAW_GOLD, Items.GOLD_INGOT);
        SMELTED.put(Items.RAW_COPPER, Items.COPPER_INGOT);
        SMELTED.put(Items.IRON_ORE, Items.IRON_INGOT);
        SMELTED.put(Items.DEEPSLATE_IRON_ORE, Items.IRON_INGOT);
        SMELTED.put(Items.GOLD_ORE, Items.GOLD_INGOT);
        SMELTED.put(Items.COBBLESTONE, Items.STONE);
        SMELTED.put(Items.SAND, Items.GLASS);
    }

    private VillageWorkday() {
    }

    /** How much one worker gets through in a day: by stars, and a third more with their workplace standing. */
    private static int budget(VillageHallBlockEntity hall, ResidentEntity worker, ResidentJob job, int base, int perStar) {
        int amount = base + perStar * worker.talent(job);
        return hall.works().workplaceOf(worker) != null ? amount + amount / 3 : amount;
    }

    private static List<ResidentEntity> workers(VillageHallBlockEntity hall, ResidentJob job) {
        List<ResidentEntity> out = new ArrayList<>();
        for (ResidentEntity resident : hall.residents()) {
            if (resident.getJob() == job && !resident.isBaby() && !resident.isDowned()) {
                out.add(resident);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- storage helpers

    static int count(SimpleContainer storage, Item item) {
        int count = 0;
        for (int i = 0; i < storage.getContainerSize(); i++) {
            if (storage.getItem(i).is(item)) {
                count += storage.getItem(i).getCount();
            }
        }
        return count;
    }

    static int take(SimpleContainer storage, Item item, int amount) {
        int taken = 0;
        for (int i = 0; i < storage.getContainerSize() && taken < amount; i++) {
            ItemStack stack = storage.getItem(i);
            if (stack.is(item)) {
                int n = Math.min(stack.getCount(), amount - taken);
                stack.shrink(n);
                taken += n;
            }
        }
        if (taken > 0) {
            storage.setChanged();
        }
        return taken;
    }

    /** Into the storage; what doesn't fit drops by the hall. */
    static void give(VillageHallBlockEntity hall, Item item, int amount) {
        while (amount > 0) {
            int n = Math.min(amount, item.getMaxStackSize());
            ItemStack left = hall.store(new ItemStack(item, n));
            if (!left.isEmpty() && hall.getLevel() != null) {
                BlockPos at = hall.getBlockPos();
                hall.getLevel().addFreshEntity(new ItemEntity(hall.getLevel(), at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D, left));
            }
            amount -= n;
        }
    }

    /** "빵 4, 익힌 소고기 3" */
    private static Component list(Map<Item, Integer> made) {
        MutableComponent line = Component.empty();
        boolean first = true;
        for (Map.Entry<Item, Integer> entry : made.entrySet()) {
            if (!first) {
                line.append(", ");
            }
            line.append(new ItemStack(entry.getKey()).getHoverName()).append(" " + entry.getValue());
            first = false;
        }
        return line;
    }

    // ---------------------------------------------------------------- cook

    /** Returns whether anyone got a warm meal today (everyone's mood goes up). */
    static boolean cook(VillageHallBlockEntity hall) {
        boolean cooked = false;
        SimpleContainer storage = hall.getStorage();
        for (ResidentEntity cook : workers(hall, ResidentJob.COOK)) {
            int budget = budget(hall, cook, ResidentJob.COOK, 6, 3);
            Map<Item, Integer> made = new LinkedHashMap<>();
            // Bread first: the farms grow wheat, and nobody eats it raw.
            int loaves = Math.min(budget, count(storage, Items.WHEAT) / 3);
            if (loaves > 0) {
                take(storage, Items.WHEAT, loaves * 3);
                give(hall, Items.BREAD, loaves);
                made.put(Items.BREAD, loaves);
                budget -= loaves;
            }
            for (Map.Entry<Item, Item> recipe : COOKED.entrySet()) {
                if (budget <= 0) {
                    break;
                }
                int n = take(storage, recipe.getKey(), budget);
                if (n > 0) {
                    give(hall, recipe.getValue(), n);
                    made.merge(recipe.getValue(), n, Integer::sum);
                    budget -= n;
                }
            }
            if (made.isEmpty()) {
                cook.setLastWork(Component.translatable("activity.flightsuit.cook_nothing"));
                continue;
            }
            cooked = true;
            Component what = list(made);
            cook.setLastWork(Component.translatable("activity.flightsuit.cooked", what));
            hall.addNews(Component.translatable("news.flightsuit.cooked", cook.getName(), what));
        }
        return cooked;
    }

    // ---------------------------------------------------------------- blacksmith

    /** Smelting and glass panes; returns the best smith's stars (for the fighters' weapons), 0 if none. */
    static int smith(VillageHallBlockEntity hall) {
        int best = 0;
        SimpleContainer storage = hall.getStorage();
        for (ResidentEntity smith : workers(hall, ResidentJob.BLACKSMITH)) {
            best = Math.max(best, smith.talent(ResidentJob.BLACKSMITH));
            int budget = budget(hall, smith, ResidentJob.BLACKSMITH, 8, 4);
            Map<Item, Integer> made = new LinkedHashMap<>();
            // Glass panes first when the builders are waiting on them (six glass make sixteen).
            boolean panesWanted = false;
            for (ItemStack missing : hall.works().missing()) {
                panesWanted |= missing.is(Items.GLASS_PANE);
            }
            if (panesWanted && count(storage, Items.GLASS) >= 6 && budget >= 6) {
                take(storage, Items.GLASS, 6);
                give(hall, Items.GLASS_PANE, 16);
                made.put(Items.GLASS_PANE, 16);
                budget -= 6;
            }
            for (Map.Entry<Item, Item> recipe : SMELTED.entrySet()) {
                if (budget <= 0) {
                    break;
                }
                int n = take(storage, recipe.getKey(), budget);
                if (n > 0) {
                    give(hall, recipe.getValue(), n);
                    made.merge(recipe.getValue(), n, Integer::sum);
                    budget -= n;
                }
            }
            Component what = made.isEmpty() ? null : list(made);
            smith.setLastWork(what == null ? Component.translatable("activity.flightsuit.smith_weapons")
                    : Component.translatable("activity.flightsuit.smelted", what));
            if (what != null) {
                hall.addNews(Component.translatable("news.flightsuit.smelted", smith.getName(), what));
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- rancher

    private record Herd(Class<? extends Animal> kind, EntityType<? extends Animal> type, Item feed, Item meat, Predicate<Item> altFeed) {
    }

    private static final List<Herd> HERDS = List.of(
            new Herd(Cow.class, EntityType.COW, Items.WHEAT, Items.BEEF, item -> false),
            new Herd(Sheep.class, EntityType.SHEEP, Items.WHEAT, Items.MUTTON, item -> false),
            new Herd(Pig.class, EntityType.PIG, Items.CARROT, Items.PORKCHOP, item -> item == Items.POTATO || item == Items.BEETROOT),
            new Herd(Chicken.class, EntityType.CHICKEN, Items.WHEAT_SEEDS, Items.CHICKEN, item -> item == Items.BEETROOT_SEEDS));

    private static final int HERD_MIN = 2;
    private static final int HERD_MAX = 8;

    /** Breeding, eggs and wool, and a cull when a herd is too big. */
    static void ranch(VillageHallBlockEntity hall) {
        if (!(hall.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<ResidentEntity> ranchers = workers(hall, ResidentJob.RANCHER);
        if (ranchers.isEmpty()) {
            return;
        }
        SimpleContainer storage = hall.getStorage();
        for (ResidentEntity rancher : ranchers) {
            int budget = budget(hall, rancher, ResidentJob.RANCHER, 2, 1);
            Map<Item, Integer> made = new LinkedHashMap<>();
            int born = 0;
            int animals = 0;
            for (Herd herd : HERDS) {
                List<? extends Animal> adults = level.getEntitiesOfClass(herd.kind(), hall.area(), animal -> animal.isAlive() && !animal.isBaby());
                animals += adults.size();
                if (adults.isEmpty()) {
                    continue;
                }
                // Gather what they give.
                if (herd.kind() == Chicken.class) {
                    made.merge(Items.EGG, adults.size(), Integer::sum);
                    give(hall, Items.EGG, adults.size());
                } else if (herd.kind() == Sheep.class) {
                    int wool = 0;
                    for (Animal animal : adults) {
                        if (animal instanceof Sheep sheep && !sheep.isSheared()) {
                            sheep.setSheared(true);
                            wool += 1 + level.random.nextInt(2);
                        }
                    }
                    if (wool > 0) {
                        give(hall, Items.WHITE_WOOL, wool);
                        made.merge(Items.WHITE_WOOL, wool, Integer::sum);
                    }
                }
                // Too many: one for the table.
                if (adults.size() > HERD_MAX && budget > 0) {
                    Animal culled = adults.get(level.random.nextInt(adults.size()));
                    level.sendParticles(ParticleTypes.POOF, culled.getX(), culled.getY() + 0.5D, culled.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.02D);
                    culled.discard();
                    int meat = 2 + level.random.nextInt(2);
                    give(hall, herd.meat(), meat);
                    made.merge(herd.meat(), meat, Integer::sum);
                    if (herd.kind() == Cow.class) {
                        give(hall, Items.LEATHER, 1);
                    }
                    budget--;
                    continue;
                }
                // A pair and feed: a young one.
                if (adults.size() >= HERD_MIN && adults.size() < HERD_MAX && budget > 0 && takeFeed(storage, herd, 2)) {
                    Animal parent = adults.get(0);
                    Animal baby = herd.type().create(level);
                    if (baby != null) {
                        baby.setBaby(true);
                        baby.moveTo(parent.getX(), parent.getY(), parent.getZ(), level.random.nextFloat() * 360.0F, 0.0F);
                        baby.finalizeSpawn(level, level.getCurrentDifficultyAt(parent.blockPosition()), MobSpawnType.BREEDING, null, null);
                        if (baby instanceof Sheep lamb && parent instanceof Sheep ewe) {
                            lamb.setColor(ewe.getColor() == null ? DyeColor.WHITE : ewe.getColor());
                        }
                        level.addFreshEntity(baby);
                        level.sendParticles(ParticleTypes.HEART, parent.getX(), parent.getY() + 1.0D, parent.getZ(), 5, 0.4D, 0.3D, 0.4D, 0.0D);
                        born++;
                        budget--;
                    }
                }
            }
            Component line;
            if (animals == 0) {
                line = Component.translatable("activity.flightsuit.no_animals");
            } else if (made.isEmpty() && born == 0) {
                line = Component.translatable("activity.flightsuit.herd_resting", animals);
            } else {
                line = Component.translatable("activity.flightsuit.ranched", animals, born, made.isEmpty() ? Component.literal("-") : list(made));
                hall.addNews(Component.translatable("news.flightsuit.ranched", rancher.getName(), born,
                        made.isEmpty() ? Component.literal("-") : list(made)));
            }
            rancher.setLastWork(line);
        }
    }

    private static boolean takeFeed(SimpleContainer storage, Herd herd, int amount) {
        if (count(storage, herd.feed()) >= amount) {
            take(storage, herd.feed(), amount);
            return true;
        }
        for (int i = 0; i < storage.getContainerSize(); i++) {
            ItemStack stack = storage.getItem(i);
            if (!stack.isEmpty() && herd.altFeed().test(stack.getItem()) && stack.getCount() >= amount) {
                stack.shrink(amount);
                storage.setChanged();
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- musician

    /** Last night's concert (MusicianGoal sets it): how much it lifts everyone's mood this morning. */
    static int concertBonus(VillageHallBlockEntity hall, @Nullable ResidentEntity musician) {
        if (musician == null) {
            return 0;
        }
        int stars = musician.talent(ResidentJob.MUSICIAN);
        return 2 + 2 * stars + (hall.works().workplaceOf(musician) != null ? 4 : 0);
    }

    /** Where a musician plays: their stage, else in front of the hall. */
    public static BlockPos venue(VillageHallBlockEntity hall, ResidentEntity musician) {
        Construction stage = hall.works().workplaceOf(musician);
        return stage != null ? stage.center().above() : hall.getBlockPos().relative(
                hall.getBlockState().getValue(VillageHallBlock.FACING), 4);
    }

    /** A note or two at the venue - played by MusicianGoal every couple of seconds. */
    public static void playNote(ServerLevel level, ResidentEntity musician) {
        musician.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        float pitch = (float) Math.pow(2.0D, (level.random.nextInt(25) - 12) / 12.0D);
        level.playSound(null, musician.blockPosition(), level.random.nextBoolean() ? SoundEvents.NOTE_BLOCK_FLUTE.get()
                : SoundEvents.NOTE_BLOCK_HARP.get(), SoundSource.RECORDS, 1.2F, pitch);
        level.sendParticles(ParticleTypes.NOTE, musician.getX(), musician.getY() + 2.2D, musician.getZ(), 1,
                level.random.nextInt(25) / 24.0D, 0.0D, 0.0D, 1.0D);
    }
}
