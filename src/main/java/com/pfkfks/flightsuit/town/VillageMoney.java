package com.pfkfks.flightsuit.town;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.registry.ModItems;
import com.pfkfks.flightsuit.village.VillageTuning;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

/**
 * The towns' money in vanilla village chests (2026-10-12 user: "바닐라 마을 상자에 각종 마을의 돈"): wuzhu coins from
 * the fortresses and Hero City's dollars - never zeni, that's another planet's. A village is where the Three Kingdoms'
 * and Hero City's traders pass through, so a little of their money stays behind; the smiths', the armourer's, the
 * temple's and the mapmaker's chests (the well-off) hold more than the houses. A global loot modifier
 * (data/forge/loot_modifiers/global_loot_modifiers.json -> data/flightsuit/loot_modifiers/village_money.json).
 */
public class VillageMoney extends LootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, FlightSuitMod.MODID);
    public static final RegistryObject<Codec<VillageMoney>> CODEC = SERIALIZERS.register("village_money",
            () -> RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, VillageMoney::new)));

    private static final String VILLAGE_CHESTS = "chests/village/";
    /** The well-off households: more money, more often. */
    private static final Set<String> WELL_OFF = Set.of("village_weaponsmith", "village_toolsmith", "village_armorer",
            "village_temple", "village_cartographer");

    public VillageMoney(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        ResourceLocation table = context.getQueriedLootTableId();
        if (!"minecraft".equals(table.getNamespace()) || !table.getPath().startsWith(VILLAGE_CHESTS)) {
            return loot;
        }
        boolean wellOff = WELL_OFF.contains(table.getPath().substring(VILLAGE_CHESTS.length()));
        RandomSource random = context.getRandom();
        if (wellOff) {
            add(loot, random, ModItems.WUZHU_COIN.get(), VillageTuning.VILLAGE_COIN_CHANCE_RICH, VillageTuning.VILLAGE_COIN_MIN_RICH, VillageTuning.VILLAGE_COIN_MAX_RICH);
            add(loot, random, ModItems.DOLLAR.get(), VillageTuning.VILLAGE_DOLLAR_CHANCE_RICH, VillageTuning.VILLAGE_COIN_MIN_RICH, VillageTuning.VILLAGE_COIN_MAX_RICH);
        } else {
            add(loot, random, ModItems.WUZHU_COIN.get(), VillageTuning.VILLAGE_COIN_CHANCE, VillageTuning.VILLAGE_COIN_MIN, VillageTuning.VILLAGE_COIN_MAX);
            add(loot, random, ModItems.DOLLAR.get(), VillageTuning.VILLAGE_DOLLAR_CHANCE, VillageTuning.VILLAGE_COIN_MIN, VillageTuning.VILLAGE_COIN_MAX);
        }
        return loot;
    }

    private static void add(ObjectArrayList<ItemStack> loot, RandomSource random, Item money, float chance, int min, int max) {
        if (random.nextFloat() < chance) {
            loot.add(new ItemStack(money, min + random.nextInt(max - min + 1)));
        }
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
