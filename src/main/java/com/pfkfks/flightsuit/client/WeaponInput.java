package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.block.StationFrameBlock;
import com.pfkfks.flightsuit.block.SuitStationBlock;
import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.WeaponC2SPacket;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
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
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Weapon input. Right click with an empty main hand and a suit chestplate on pulls the primary trigger - aimed
 * at the air, at a mob (the old repulsor ignored clicks that landed on one), or at a block that does nothing
 * when used. Things you'd normally use with an empty hand still get used: villagers, horses, your own suits,
 * doors, chests and the like, and anything while sneaking. The trigger is released with the button.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class WeaponInput {
    private static boolean firing;

    private WeaponInput() {
    }

    @SubscribeEvent
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
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

    /** Every client tick: release the trigger with the button (or when firing stops making sense). */
    public static void tick(Minecraft minecraft, LocalPlayer player) {
        if (firing && (!minecraft.options.keyUse.isDown() || !canFire(player) || minecraft.screen != null)) {
            firing = false;
            ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.PRIMARY_STOP));
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
                ModNetwork.sendToServer(new WeaponC2SPacket(SuitWeapons.STOLEN_SKILL));
            }
        }
    }

    public static void reset() {
        firing = false;
    }

    private static boolean wearsSuitChest(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SuitArmorItem;
    }

    private static boolean canFire(LocalPlayer player) {
        return wearsSuitChest(player) && player.getMainHandItem().isEmpty() && !player.isSpectator() && !CinematicCamera.isActive();
    }

    private static boolean wantsToFire(Minecraft minecraft, LocalPlayer player) {
        HitResult hit = minecraft.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        if (hit instanceof EntityHitResult entityHit) {
            return isTarget(entityHit.getEntity());
        }
        // A block: sneaking always uses it, as does an item in the other hand (a torch to place, say).
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        return !player.isShiftKeyDown() && player.getOffhandItem().isEmpty() && !isUsable(player.level(), pos);
    }

    /** Living things you'd shoot rather than talk to, ride, or step into. */
    private static boolean isTarget(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof AbstractVillager)
                && !(entity instanceof AbstractHorse) && !(entity instanceof TamableAnimal tamable && tamable.isTame())
                && !(entity instanceof SuitCompanionEntity) && !(entity instanceof RemoteBodyEntity) && !(entity instanceof ArmorStand);
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
