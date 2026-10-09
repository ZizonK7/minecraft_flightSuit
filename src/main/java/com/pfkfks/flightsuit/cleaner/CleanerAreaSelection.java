package com.pfkfks.flightsuit.cleaner;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drawing a cleaning area (ported from minebutler's GuardAreaSelectionEvents): after starting at the dock,
 * every right-clicked block is a corner; clicking near the first corner (with 3+ corners) closes the loop
 * and saves it. Corners and edges are shown to the drawing player as particles while they work.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class CleanerAreaSelection {
    private static final int MAX_VERTICES = 16;
    private static final int MAX_BOUNDING_AREA = 64 * 64;
    private static final double CLOSE_DISTANCE_SQR = 2.5D * 2.5D;
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    private record Selection(BlockPos dockPos, List<BlockPos> vertices) {
    }

    private CleanerAreaSelection() {
    }

    public static void toggle(ServerPlayer player, BlockPos dockPos) {
        if (SELECTIONS.remove(player.getUUID()) != null) {
            player.displayClientMessage(Component.translatable("message.flightsuit.cleaner_area_cancelled"), true);
            return;
        }
        SELECTIONS.put(player.getUUID(), new Selection(dockPos, new ArrayList<>()));
        player.displayClientMessage(Component.translatable("message.flightsuit.cleaner_area_start"), true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Selection selection = SELECTIONS.get(player.getUUID());
        if (selection == null || event.getPos().equals(selection.dockPos())) {
            return;
        }
        event.setCanceled(true);
        if (!(player.serverLevel().getBlockEntity(selection.dockPos()) instanceof CleanerDockBlockEntity dock)) {
            SELECTIONS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.flightsuit.cleaner_area_cancelled"), true);
            return;
        }
        BlockPos clicked = event.getPos();
        List<BlockPos> points = selection.vertices();
        if (points.size() >= 3 && clicked.distSqr(points.get(0)) <= CLOSE_DISTANCE_SQR) {
            SELECTIONS.remove(player.getUUID());
            if (boundingArea(points) > MAX_BOUNDING_AREA) {
                player.displayClientMessage(Component.translatable("message.flightsuit.cleaner_area_too_large"), true);
                return;
            }
            CleanArea area = new CleanArea(List.copyOf(points), points.get(0).getY(), CleanArea.DEFAULT_UP_RANGE, CleanArea.DEFAULT_DOWN_RANGE);
            player.displayClientMessage(Component.translatable(dock.addArea(area)
                    ? "message.flightsuit.cleaner_area_saved" : "message.flightsuit.cleaner_area_limit"), true);
            return;
        }
        if (points.size() >= MAX_VERTICES) {
            SELECTIONS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.flightsuit.cleaner_area_cancelled"), true);
            return;
        }
        points.add(clicked);
        player.displayClientMessage(Component.translatable(points.size() >= 3
                ? "message.flightsuit.cleaner_area_corner_close" : "message.flightsuit.cleaner_area_corner", points.size()), true);
    }

    /** Draws the in-progress outline for its owner only. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 5 != 0) {
            return;
        }
        Selection selection = SELECTIONS.get(player.getUUID());
        if (selection == null) {
            return;
        }
        List<BlockPos> points = selection.vertices();
        for (int i = 0; i < points.size(); i++) {
            Vec3 a = Vec3.atBottomCenterOf(points.get(i)).add(0.0D, 1.1D, 0.0D);
            player.serverLevel().sendParticles(player, ParticleTypes.END_ROD, true, a.x, a.y, a.z, 2, 0.05D, 0.2D, 0.05D, 0.0D);
            if (i + 1 < points.size()) {
                Vec3 b = Vec3.atBottomCenterOf(points.get(i + 1)).add(0.0D, 1.1D, 0.0D);
                double length = a.distanceTo(b);
                for (double d = 0.5D; d < length; d += 0.75D) {
                    Vec3 p = a.add(b.subtract(a).normalize().scale(d));
                    player.serverLevel().sendParticles(player, ParticleTypes.WAX_ON, true, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private static int boundingArea(List<BlockPos> points) {
        int minX = points.get(0).getX(), maxX = minX, minZ = points.get(0).getZ(), maxZ = minZ;
        for (BlockPos point : points) {
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minZ = Math.min(minZ, point.getZ());
            maxZ = Math.max(maxZ, point.getZ());
        }
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }
}
