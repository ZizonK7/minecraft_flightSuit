package com.pfkfks.flightsuit.village;

import com.pfkfks.flightsuit.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A building's blueprint, handed out by the architects (DESIGN.md 4-12: 건의 + 설계도). Held, it shows the
 * building's outline where you look (ClientBlueprintPreview). Right-click the ground to pin the spot, again
 * on the same spot to order it; sneak + right-click turns it. The front faces you unless turned.
 */
public class BlueprintItem extends Item {
    private static final String KEY = "Blueprint";
    private static final String ROTATION = "Rotation";
    private static final String PENDING = "Pending";

    public BlueprintItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Blueprint blueprint) {
        ItemStack stack = new ItemStack(ModItems.BLUEPRINT.get());
        stack.getOrCreateTag().putString(KEY, blueprint.name());
        return stack;
    }

    public static @Nullable Blueprint blueprint(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(KEY)) {
            return null;
        }
        try {
            return Blueprint.valueOf(tag.getString(KEY));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The turn set with sneak-clicks, or (none set yet) front toward the player. */
    public static Rotation rotation(ItemStack stack, Player player) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(ROTATION)) {
            return Rotation.values()[Math.floorMod(tag.getInt(ROTATION), 4)];
        }
        return Blueprint.facing(player.getDirection().getOpposite());
    }

    public static @Nullable BlockPos pending(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(PENDING) ? NbtUtils.readBlockPos(tag.getCompound(PENDING)) : null;
    }

    /** Where a building clicked on this block face would stand: on the block you look at, or beside it. */
    public static BlockPos groundAt(BlockPos clicked, Direction face) {
        return face == Direction.UP ? clicked : clicked.relative(face).below();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Blueprint blueprint = blueprint(stack);
        Player player = context.getPlayer();
        if (blueprint == null || player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        BlockPos center = groundAt(context.getClickedPos(), context.getClickedFace());
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (player.isShiftKeyDown()) {
            Rotation turned = rotation(stack, player).getRotated(Rotation.CLOCKWISE_90);
            tag.putInt(ROTATION, turned.ordinal());
            Direction front = turned.rotate(Direction.NORTH);
            player.displayClientMessage(Component.translatable("message.flightsuit.blueprint_turned",
                    Component.translatable("direction.flightsuit." + front.getName())), true);
            return InteractionResult.CONSUME;
        }
        Rotation rotation = rotation(stack, player);
        if (!center.equals(pending(stack))) {
            tag.put(PENDING, NbtUtils.writeBlockPos(center));
            // Pin the turn too, so the outline stops following the player around.
            tag.putInt(ROTATION, rotation.ordinal());
            int clear = new Construction(blueprint, center, rotation).clearCount(level);
            player.displayClientMessage(clear > 0 ? Component.translatable("message.flightsuit.blueprint_pinned_clear", clear)
                    : Component.translatable("message.flightsuit.blueprint_pinned"), true);
            return InteractionResult.CONSUME;
        }
        VillageHallBlockEntity hall = Villages.containing(level, center);
        if (hall == null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.order_outside").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (!hall.isOwner(player)) {
            player.displayClientMessage(Component.translatable("message.flightsuit.order_not_yours").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        Component problem = hall.works().order(blueprint, center, rotation);
        if (problem != null) {
            player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.flightsuit.order_placed",
                Component.translatable(blueprint.translationKey())).withStyle(ChatFormatting.GREEN), true);
        // Used up even in creative: ask an architect for another (user's call).
        stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public Component getName(ItemStack stack) {
        Blueprint blueprint = blueprint(stack);
        return blueprint == null ? super.getName(stack)
                : Component.translatable("item.flightsuit.blueprint.named", Component.translatable(blueprint.translationKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.blueprint").withStyle(ChatFormatting.GRAY));
    }
}
