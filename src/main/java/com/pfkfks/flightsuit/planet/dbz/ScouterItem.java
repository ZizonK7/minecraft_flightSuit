package com.pfkfks.flightsuit.planet.dbz;

import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Scouter (DESIGN.md 4-16: 스카우터 - Raditz's): held in either hand, it reads the power level of whatever you
 * look at up to 64 blocks away.
 */
public class ScouterItem extends Item {
    public ScouterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || player.tickCount % 10 != 0
                || !(selected || player.getOffhandItem() == stack)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(64.0D));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, new AABB(eye, end).inflate(1.0D),
                target -> target instanceof LivingEntity && !target.isSpectator() && target != player);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            return;
        }
        int power = powerOf(target);
        Component line = Component.translatable("scouter.flightsuit.reading", target.getDisplayName(), power)
                .withStyle(power > 9000 ? ChatFormatting.RED : ChatFormatting.GREEN);
        if (power > 9000) {
            line = Component.translatable("scouter.flightsuit.over_9000", line);
        }
        player.displayClientMessage(line, true);
    }

    public static int powerOf(LivingEntity target) {
        if (target instanceof DbzFighterEntity fighter) {
            return fighter.getCharacter().power();
        }
        if (target instanceof Player player) {
            int pieces = 0;
            for (EquipmentSlot slot : WornSuit.SLOTS) {
                if (player.getItemBySlot(slot).getItem() instanceof SuitArmorItem) {
                    pieces++;
                }
            }
            return 5 + pieces * 1_500;
        }
        double attack = target.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? target.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0.0D;
        return Math.max(1, (int) (target.getMaxHealth() * 5.0D + attack * 40.0D));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.scouter").withStyle(ChatFormatting.GRAY));
    }
}
