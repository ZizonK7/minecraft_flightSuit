package com.pfkfks.flightsuit.suit;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.client.SuitArmorModels;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ForgeMod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One piece of a suit. The four slots map to DESIGN.md 4-3: helmet = senses, chestplate (incl. arms) =
 * repulsor + arc reactor battery, leggings = power assist, boots = thrusters.
 */
public class SuitArmorItem extends ArmorItem {
    private static final UUID LEGS_SPEED_ID = UUID.fromString("6f3b6a0e-3b1f-4f43-9a7d-1d2a0f6c9e11");
    private static final UUID LEGS_STEP_ID = UUID.fromString("0c8c2d55-7a1e-4d55-8f0b-3f1c5b7e2a42");

    private final SuitType suitType;

    public SuitArmorItem(SuitType suitType, ArmorItem.Type type, Properties properties) {
        super(suitType.material(), type, properties);
        this.suitType = suitType;
    }

    public SuitType getSuitType() {
        return suitType;
    }

    public int getEnergyCapacity() {
        return getType() == Type.CHESTPLATE ? SuitTuning.CHEST_CAPACITY : SuitTuning.PIECE_CAPACITY;
    }

    /** A piece worn down to its last durability point: it can't take more and must go back to a station. */
    public static boolean isBroken(ItemStack stack) {
        return stack.getItem() instanceof SuitArmorItem && stack.isDamageableItem()
                && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /**
     * Suit pieces never shatter like vanilla armor (that would delete the suit): damage stops one point short
     * of breaking, and a wearer whose piece hits that point gets ejected (DESIGN.md 4-2 "강제 이탈").
     */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        int room = Math.max(0, stack.getMaxDamage() - 1 - stack.getDamageValue());
        int applied = Math.min(amount, room);
        if (applied >= room && entity instanceof net.minecraft.server.level.ServerPlayer player) {
            SuitUpManager.requestEject(player);
        }
        return applied;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        if (suitType.ownModel()) {
            return FlightSuitMod.MODID + ":textures/models/armor/" + suitType.id() + ".png";
        }
        return FlightSuitMod.MODID + ":textures/models/armor/" + suitType.id() + "_" + getType().getName() + ".png";
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (getType() != Type.LEGGINGS || slot != EquipmentSlot.LEGS) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(base);
        builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(LEGS_SPEED_ID, "Suit power assist",
                SuitTuning.LEGS_SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
        builder.put(ForgeMod.STEP_HEIGHT_ADDITION.get(), new AttributeModifier(LEGS_STEP_ID, "Suit power assist",
                SuitTuning.LEGS_STEP_BONUS, AttributeModifier.Operation.ADDITION));
        return builder.build();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.flightsuit.energy", SuitEnergy.get(stack), SuitEnergy.capacity(stack))
                .withStyle(ChatFormatting.AQUA));
        int max = stack.getMaxDamage();
        int left = max - stack.getDamageValue();
        tooltip.add(Component.translatable(isBroken(stack) ? "tooltip.flightsuit.durability_broken" : "tooltip.flightsuit.durability", left, max)
                .withStyle(isBroken(stack) ? ChatFormatting.RED : left * 5 < max ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flightsuit.piece." + getType().getName())
                .withStyle(ChatFormatting.GRAY));
        if (suitType.suitClass() == SuitClass.STEALTH) {
            tooltip.add(Component.translatable("tooltip.flightsuit.class.stealth").withStyle(ChatFormatting.DARK_PURPLE));
        } else if (suitType.suitClass() == SuitClass.PHANTOM) {
            tooltip.add(Component.translatable("tooltip.flightsuit.class.phantom").withStyle(ChatFormatting.GOLD));
        } else if (suitType.suitClass() == SuitClass.HERO) {
            tooltip.add(Component.translatable("tooltip.flightsuit.class.hero").withStyle(ChatFormatting.GREEN));
        } else if (suitType.suitClass() == SuitClass.SWORDSMAN) {
            tooltip.add(Component.translatable("tooltip.flightsuit.class.swordsman").withStyle(ChatFormatting.BLUE));
        } else if (suitType.suitClass() == SuitClass.HULKBUSTER) {
            tooltip.add(Component.translatable("tooltip.flightsuit.class.hulkbuster").withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
                                                         EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
                // Active camouflage hides the suit itself, not just the body under it.
                if (livingEntity.isInvisible() && livingEntity instanceof net.minecraft.world.entity.player.Player player
                        && StealthHandler.wearsStealthSuit(WornSuit.of(player))) {
                    return SuitArmorModels.empty();
                }
                return SuitArmorModels.forWearer(livingEntity, suitType.id(), equipmentSlot);
            }
        });
    }
}
