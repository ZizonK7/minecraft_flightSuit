package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.block.StationFrameBlock;
import com.pfkfks.flightsuit.block.SuitStationBlock;
import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.WeaponC2SPacket;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitClass;
import com.pfkfks.flightsuit.suit.SuitTuning;
import com.pfkfks.flightsuit.suit.SuitWeaponItem;
import com.pfkfks.flightsuit.suit.SuitWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Weapon input. Right click with a suit chestplate on pulls the primary trigger - from an empty main hand for the
 * suits that fight bare-handed, from the suit's weapon in hand for the rest (M18, SuitWeaponItem) - aimed at the
 * air, at a mob (the old repulsor ignored clicks that landed on one), or at a block that does nothing when used.
 * Things you'd normally use still get used: villagers, horses, your own suits, doors, chests and the like, and
 * anything while sneaking. The trigger is released with the button.
 *
 * M18: the attack key with the weapon in hand swings it (SuitWeapons.SWING) - once on the press and, held, every
 * SLASH_INTERVAL ticks - instead of vanilla's hit, and never digs.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class WeaponInput {
    private static boolean firing;
    /** Ticks the attack key has been held with the weapon out (-1 = up). */
    private static int swinging = -1;

    private WeaponInput() {
    }

    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (event.isAttack()) {
            // The weapon's swing is ours: no vanilla hit, no digging.
            if (holdsWeapon(player)) {
                event.setCanceled(true);
                event.setSwingHand(false);
                if (swinging < 0) {
                    swinging = 0;
                    ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.SWING));
                }
            }
            return;
        }
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        // Held down: vanilla keeps re-triggering use; swallow it so a beam sweeping over a door doesn't open it.
        if (firing || (canFire(player) && wantsToFire(minecraft, player))) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!firing) {
                firing = true;
                ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.PRIMARY_START));
            }
        }
    }

    /** Every client tick: release the trigger with the button (or when firing stops making sense); keep swinging while held. */
    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (firing && (!minecraft.options.keyUse.isDown() || !canFire(player) || minecraft.screen != null)) {
            firing = false;
            ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.PRIMARY_STOP));
        }
        if (swinging >= 0) {
            if (!minecraft.options.keyAttack.isDown() || !holdsWeapon(player) || minecraft.screen != null) {
                swinging = -1;
            } else if (++swinging % SuitTuning.SLASH_INTERVAL == 0) {
                ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.SWING));
            }
        }
        while (ModKeys.SKILL_1.consumeClick()) {
            if (wearsSuitChest(player)) {
                ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.SKILL_1));
            }
        }
        while (ModKeys.SKILL_2.consumeClick()) {
            if (wearsSuitChest(player)) {
                ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.SKILL_2));
            }
        }
        while (ModKeys.ULTIMATE.consumeClick()) {
            if (wearsSuitChest(player)) {
                ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.ULTIMATE));
            }
        }
        while (ModKeys.STOLEN_SKILL.consumeClick()) {
            if (wearsSuitChest(player)) {
                ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.SKILL_3));
            }
        }
    }

    public static void reset() {
        firing = false;
        swinging = -1;
    }

    private static boolean wearsSuitChest(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SuitArmorItem;
    }

    /** The suit's own weapon in the main hand (the sword suits, the staff, the cannon). */
    private static boolean holdsWeapon(LocalPlayer player) {
        return SuitWeaponItem.holding(player) && !player.isSpectator() && !CinematicCamera.isActive();
    }

    private static boolean canFire(LocalPlayer player) {
        if (!wearsSuitChest(player) || player.isSpectator() || CinematicCamera.isActive()) {
            return false;
        }
        SuitClass suitClass = SuitWeapons.armedClass(player);
        return suitClass != null && (suitClass.handsFree() ? player.getMainHandItem().isEmpty() : SuitWeaponItem.holding(player));
    }

    private static boolean wantsToFire(Minecraft minecraft, LocalPlayer player) {
        HitResult hit = minecraft.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        if (hit instanceof EntityHitResult entityHit) {
            // The bishop's staff heals whoever it's pointed at - players, villagers, the suits - so it fires on them too.
            return isTarget(entityHit.getEntity()) || SuitWeapons.armedClass(player) == SuitClass.BISHOP && entityHit.getEntity() instanceof LivingEntity;
        }
        // A block: sneaking always uses it, as does an item in the other hand (a torch to place, say).
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        return !player.isShiftKeyDown() && player.getOffhandItem().isEmpty() && !isUsable(player.level(), pos);
    }

    /** Living things you'd shoot rather than talk to, ride, or step into. */
    private static boolean isTarget(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof AbstractVillager)
                && !(entity instanceof AbstractHorse) && !(entity instanceof TamableAnimal tamable && tamable.isTame())
                && !(entity instanceof SuitCompanionEntity) && !(entity instanceof RemoteBodyEntity) && !(entity instanceof ArmorStand)
                && !(entity instanceof com.pfkfks.flightsuit.entity.MonkeyEntity);
    }

    /** Blocks that do something when used with an empty hand. */
    private static boolean isUsable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        return level.getBlockEntity(pos) != null
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS) || state.is(BlockTags.BEDS) || state.is(BlockTags.ANVIL)
                || block instanceof LeverBlock || block instanceof CraftingTableBlock || block instanceof NoteBlock
                || block instanceof RepeaterBlock || block instanceof CakeBlock || block instanceof FlowerPotBlock
                || block instanceof ComposterBlock || block instanceof LoomBlock || block instanceof StonecutterBlock
                || block instanceof GrindstoneBlock || block instanceof CartographyTableBlock || block instanceof SmithingTableBlock
                || block instanceof RespawnAnchorBlock || block instanceof AbstractCauldronBlock
                || block instanceof StationFrameBlock || block instanceof SuitStationBlock;
    }
}
