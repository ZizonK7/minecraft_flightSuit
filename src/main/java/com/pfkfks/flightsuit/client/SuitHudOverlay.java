package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.EdithStatusS2CPacket;
import com.pfkfks.flightsuit.suit.EdithGlassesItem;
import com.pfkfks.flightsuit.suit.FlightPose;
import com.pfkfks.flightsuit.suit.SuitArmorItem;
import com.pfkfks.flightsuit.suit.SuitEnergy;
import com.pfkfks.flightsuit.suit.SuitClass;
import com.pfkfks.flightsuit.suit.SuitTuning;
import com.pfkfks.flightsuit.suit.SuitType;
import com.pfkfks.flightsuit.suit.SuitWeapons;
import com.pfkfks.flightsuit.suit.WornSuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Minimal suit-wearing HUD (DESIGN.md 4-1 "전투 HUD", first pass): suit name, which pieces are on,
 * battery, and what the suit can do right now. The glasses-only command HUD comes with the EDITH
 * glasses in M2.
 */
public final class SuitHudOverlay implements IGuiOverlay {
    public static final SuitHudOverlay INSTANCE = new SuitHudOverlay();

    private static final int CYAN = 0xFF5FE3FF;
    private static final int DIM = 0xFF3A4A55;
    private static final int TEXT = 0xFFDDF6FF;
    private static final int WARN = 0xFFFF6A4D;
    private static final int DIM_TEXT = 0xFF8FA9B5;
    private static final int CAUTION = 0xFFFFC94D;

    private SuitHudOverlay() {
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        WornSuit worn = WornSuit.of(player);
        if (!worn.any()) {
            if (EdithGlassesItem.isWearing(player)) {
                renderEdith(minecraft, player, graphics);
            }
            return;
        }
        Font font = minecraft.font;
        int x = 6;
        int y = 6;

        SuitType type = WornSuit.primaryType(player);
        String name = worn.fullSet() ? type.hudName() : type.hudName() + " (PARTIAL)";
        Component stolenLine = stolenLine(player);
        graphics.fill(x - 3, y - 3, x + 124, y + (stolenLine != null ? 75 : 64), 0x66000000);
        graphics.drawString(font, name, x, y, CYAN, true);

        // H C L B piece lights, colored by each piece's durability; the weakest piece's % underneath.
        int px = x;
        String[] labels = {"H", "C", "L", "B"};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int weakest = 100;
        for (int i = 0; i < labels.length; i++) {
            ItemStack piece = player.getItemBySlot(slots[i]);
            int color = DIM;
            if (piece.getItem() instanceof SuitArmorItem) {
                int percent = durabilityPercent(piece);
                weakest = Math.min(weakest, percent);
                color = percent <= 20 ? WARN : percent <= 50 ? CAUTION : CYAN;
            }
            graphics.drawString(font, labels[i], px, y + 11, color, false);
            px += 9;
        }
        graphics.drawString(font, Component.translatable("hud.flightsuit.armor", weakest), x, y + 31,
                weakest <= 20 ? WARN : weakest <= 50 ? CAUTION : TEXT, false);

        // Battery: chest reactor if worn, else whichever piece is on.
        EquipmentSlot sourceSlot = worn.chest() ? EquipmentSlot.CHEST
                : worn.boots() ? EquipmentSlot.FEET : worn.legs() ? EquipmentSlot.LEGS : EquipmentSlot.HEAD;
        ItemStack source = SuitEnergy.source(player, sourceSlot);
        int energy = SuitEnergy.get(source);
        int capacity = Math.max(1, SuitEnergy.capacity(source));
        float ratio = energy / (float) capacity;
        int barX = x + 40;
        int barY = y + 12;
        int barW = 78;
        graphics.fill(barX, barY, barX + barW, barY + 5, 0xFF10181D);
        graphics.fill(barX, barY, barX + Math.round(barW * ratio), barY + 5, ratio < 0.15F ? WARN : CYAN);
        graphics.drawString(font, energy + " FE", barX, y + 20, TEXT, false);

        graphics.drawString(font, status(player, worn, energy), x, y + 42, energy <= 0 ? WARN : TEXT, false);
        Component weapons = weaponLine(player);
        if (weapons != null) {
            graphics.drawString(font, weapons, x, y + 53, CAUTION, false);
        }
        if (stolenLine != null) {
            graphics.drawString(font, stolenLine, x, y + 64, 0xFFD9A6FF, false);
        }
    }

    /** Mark 3 (M17): the stolen card on B - its name and cooldown, or the empty card. */
    private static Component stolenLine(LocalPlayer player) {
        if (SuitWeapons.armedClass(player) != SuitClass.PHANTOM) {
            return null;
        }
        if (ClientWeapons.stolen.isEmpty()) {
            return Component.translatable("hud.flightsuit.weapon.stolen_empty");
        }
        com.pfkfks.flightsuit.suit.StolenSkill skill = com.pfkfks.flightsuit.suit.StolenSkill.byName(ClientWeapons.stolen);
        return skill == null ? null : Component.translatable("hud.flightsuit.weapon.stolen",
                Component.translatable("skill.flightsuit.stolen." + skill.id()), ready(ClientWeapons.stolenReady));
    }

    /** The ultimate (V): ready / cooldown, or Super Saiyan on / locked out after running dry. */
    private static Component ultimate(SuitClass suitClass) {
        if (suitClass == SuitClass.SWORDSMAN) {
            if (ClientWeapons.superSaiyan) {
                return Component.translatable("hud.flightsuit.weapon.ssj_on");
            }
            float locked = ClientWeapons.secondsLeft(ClientWeapons.ssjLockedUntil);
            return Component.translatable("hud.flightsuit.weapon.ultimate", locked > 0.0F
                    ? Component.literal(String.format("%.1fs", locked)) : Component.translatable("hud.flightsuit.weapon.ready"));
        }
        return Component.translatable("hud.flightsuit.weapon.ultimate", ready(ClientWeapons.ultReady));
    }

    /** The class's weapons at a glance: skill cooldowns, the phantom's card gauge and spade buff, the hero's full-health edge. */
    private static Component weaponLine(LocalPlayer player) {
        SuitClass suitClass = SuitWeapons.armedClass(player);
        if (suitClass == null) {
            return null;
        }
        return switch (suitClass) {
            case STANDARD -> Component.translatable("hud.flightsuit.weapon.missiles", ready(ClientWeapons.skill1Ready),
                    ready(ClientWeapons.skill2Ready));
            case STEALTH -> Component.translatable("hud.flightsuit.weapon.cryo", ready(ClientWeapons.skill2Ready));
            case PHANTOM -> {
                float spade = ClientWeapons.secondsLeft(ClientWeapons.spadeUntil);
                Component line = Component.translatable("hud.flightsuit.weapon.cards", ClientWeapons.gauge, SuitTuning.JUDGMENT_GAUGE,
                        ready(ClientWeapons.skill2Ready)).copy().append(" ").append(ultimate(suitClass));
                yield spade > 0.0F ? line.copy().append(Component.literal(String.format(" ♠%.0fs", spade))) : line;
            }
            case HERO -> {
                Component line = Component.translatable("hud.flightsuit.weapon.hero", ready(ClientWeapons.skill1Ready),
                        ready(ClientWeapons.skill2Ready));
                // Full health: the sword looses beams and X becomes the great spin.
                yield player.getHealth() >= player.getMaxHealth() - 0.01F
                        ? line.copy().append(Component.translatable("hud.flightsuit.weapon.hero_full")) : line;
            }
            case HULKBUSTER -> Component.translatable("hud.flightsuit.weapon.hulkbuster", ready(ClientWeapons.skill1Ready),
                    ready(ClientWeapons.skill2Ready));
            case SWORDSMAN -> Component.translatable("hud.flightsuit.weapon.swordsman", ready(ClientWeapons.skill1Ready),
                    ready(ClientWeapons.skill2Ready)).copy().append(" ").append(ultimate(suitClass));
        };
    }

    private static Component ready(long readyTick) {
        float left = ClientWeapons.secondsLeft(readyTick);
        return left <= 0.0F ? Component.translatable("hud.flightsuit.weapon.ready") : Component.literal(String.format("%.1fs", left));
    }

    /**
     * Glasses-only command HUD (DESIGN.md 4-1 "지휘용 HUD"): clock, position, and the main suit waiting at
     * its station - name, charge, distance and station power.
     */
    private static void renderEdith(Minecraft minecraft, LocalPlayer player, GuiGraphics graphics) {
        Font font = minecraft.font;
        int x = 6;
        int y = 6;
        graphics.fill(x - 3, y - 3, x + 170, y + 52, 0x55000000);
        graphics.drawString(font, "E.D.I.T.H.", x, y, CYAN, true);

        long time = player.level().getDayTime();
        int hours = (int) ((time / 1000L + 6L) % 24L);
        int minutes = (int) ((time % 1000L) * 60L / 1000L);
        String clock = String.format("DAY %d  %02d:%02d   %d %d %d", time / 24000L + 1, hours, minutes,
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
        graphics.drawString(font, clock, x, y + 11, TEXT, false);

        EdithStatusS2CPacket status = ClientPacketHandler.edithStatus;
        Component suitLine;
        Component stationLine;
        int suitColor = TEXT;
        if (status == null || status.state == EdithStatusS2CPacket.NO_STATION) {
            suitLine = Component.translatable("hud.flightsuit.edith.no_station");
            stationLine = Component.translatable("hud.flightsuit.edith.no_station_hint");
            suitColor = WARN;
        } else if (status.state == EdithStatusS2CPacket.OTHER_DIMENSION) {
            suitLine = Component.translatable("hud.flightsuit.edith.other_dimension");
            stationLine = Component.empty();
            suitColor = WARN;
        } else if (status.state == EdithStatusS2CPacket.NO_SIGNAL) {
            suitLine = Component.translatable("hud.flightsuit.edith.no_signal");
            stationLine = Component.translatable("hud.flightsuit.edith.station_distance", status.distance);
        } else {
            if (status.suitName.isEmpty()) {
                suitLine = Component.translatable("hud.flightsuit.edith.station_empty");
                suitColor = WARN;
            } else {
                suitLine = Component.translatable("hud.flightsuit.edith.main_suit", status.suitName, status.suitChargePercent);
                suitColor = CYAN;
            }
            stationLine = Component.translatable("hud.flightsuit.edith.station", status.distance, status.stationEnergy);
        }
        graphics.drawString(font, suitLine, x, y + 22, suitColor, false);
        graphics.drawString(font, stationLine, x, y + 32, TEXT, false);
        graphics.drawString(font, Component.translatable("hud.flightsuit.edith.call_hint"), x, y + 42, DIM_TEXT, false);
    }

    private static int durabilityPercent(ItemStack piece) {
        if (!piece.isDamageableItem()) {
            return 100;
        }
        int max = piece.getMaxDamage();
        return Math.round((max - piece.getDamageValue()) * 100.0F / max);
    }

    private static Component status(LocalPlayer player, WornSuit worn, int energy) {
        if (CinematicCamera.isActive()) {
            return Component.translatable("hud.flightsuit.assembling");
        }
        if (energy <= 0) {
            return Component.translatable("hud.flightsuit.no_power");
        }
        if (player.isInvisible() && com.pfkfks.flightsuit.suit.StealthHandler.wearsStealthSuit(worn)) {
            return Component.translatable("hud.flightsuit.cloaked");
        }
        FlightPose pose = SuitAnimator.poseOf(player);
        if (pose == FlightPose.BOOST) {
            return Component.translatable("hud.flightsuit.boost");
        }
        if (pose == FlightPose.HOVER) {
            return Component.translatable("hud.flightsuit.hover");
        }
        if (worn.fullSet()) {
            return Component.translatable(worn.canFly() ? "hud.flightsuit.flight_ready" : "hud.flightsuit.on_foot");
        }
        if (WornSuit.hasThrusterBoots(player)) {
            return Component.translatable("hud.flightsuit.thrusters");
        }
        return Component.translatable("hud.flightsuit.partial");
    }
}
