package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.network.OreSightS2CPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * M18: the ores the suit's sensors picked out (OreSightS2CPacket), drawn as pulsing glows through the rock until
 * their time is up - coloured by what they are (diamond blue-white, gold yellow, iron tan, copper orange, redstone
 * red, lapis blue, emerald green, coal grey, quartz white, the rest violet). One that gets dug out stops showing.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID, value = Dist.CLIENT)
public final class OreSightClient {
    private record Sight(BlockPos pos, int colour, long until) {
    }

    private static final List<Sight> SIGHTS = new ArrayList<>();

    private OreSightClient() {
    }

    public static void handle(OreSightS2CPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        SIGHTS.clear();
        long until = level.getGameTime() + packet.ticks;
        for (BlockPos pos : packet.ores) {
            SIGHTS.add(new Sight(pos, colourOf(level.getBlockState(pos)), until));
        }
    }

    private static int colourOf(BlockState state) {
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return 0x9CF6FF;
        }
        if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE) || state.is(Blocks.NETHER_GOLD_ORE)) {
            return 0xFFD84A;
        }
        if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) {
            return 0xE0B890;
        }
        if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) {
            return 0xFF9A50;
        }
        if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) {
            return 0xFF4040;
        }
        if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) {
            return 0x4A6CFF;
        }
        if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) {
            return 0x50FF8A;
        }
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            return 0xB0B0B8;
        }
        if (state.is(Blocks.NETHER_QUARTZ_ORE)) {
            return 0xF4F4FF;
        }
        if (state.is(Blocks.ANCIENT_DEBRIS)) {
            return 0xC89060;
        }
        return 0xC080FF;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SIGHTS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        SIGHTS.removeIf(sight -> sight.until() <= now || level.getBlockState(sight.pos()).isAir());
        if (SIGHTS.isEmpty()) {
            return;
        }
        float pt = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        KiDraw.Ctx c = new KiDraw.Ctx(buffers.getBuffer(KiDraw.GLOW_XRAY), event.getPoseStack().last().pose(),
                new Vec3(camera.getLeftVector()), new Vec3(camera.getUpVector()), cam);
        float time = now + pt;
        for (Sight sight : SIGHTS) {
            // Fading out over the last two seconds; a slow pulse so the eye picks them out.
            float left = sight.until() - time;
            float fade = Mth.clamp(left / 40.0F, 0.0F, 1.0F);
            float pulse = 0.75F + 0.25F * Mth.sin(time * 0.25F + (sight.pos().getX() + sight.pos().getZ()) * 0.7F);
            Vec3 centre = Vec3.atCenterOf(sight.pos());
            double distance = centre.distanceTo(cam);
            // Far ores draw bigger so they stay a visible dot.
            double r = 0.42D + Math.min(1.2D, distance * 0.02D);
            KiDraw.glow(c, centre, r * pulse, sight.colour(), (int) (150 * fade));
            KiDraw.glow(c, centre, r * 0.45D, 0xFFFFFF, (int) (120 * fade));
        }
        buffers.endBatch(KiDraw.GLOW_XRAY);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SIGHTS.clear();
    }
}
