package com.pfkfks.flightsuit.fx;

import com.pfkfks.flightsuit.network.KiFxS2CPacket;
import com.pfkfks.flightsuit.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Drawn ki effects (after the M17 test, which found the moves were only particles): the server says what and where,
 * every client near draws it itself (client.KiFxClient) - glowing beams with a white-hot core, balls of light
 * gathering in the hands, shots with a tail, blasts that swell and ring out, pillars of light, flame-tongued auras,
 * the Solar Flare's white-out. Nothing here touches the world; the moves' damage stays where it was.
 */
public final class KiFx {
    // Kinds (KiFxS2CPacket.kind).
    public static final byte BEAM = 0;
    public static final byte CHARGE = 1;
    public static final byte CHARGE_STOP = 2;
    public static final byte SHOT = 3;
    public static final byte SHOT_END = 4;
    public static final byte BURST = 5;
    public static final byte PILLAR = 6;
    public static final byte AURA = 7;
    public static final byte FLARE = 8;

    // Colours.
    public static final int KAME = 0x4FB2FF;
    public static final int GALICK = 0xB04CFF;
    public static final int DEATH = 0xF06AF0;
    public static final int SBC = 0xFFE860;
    public static final int SBC_SPIRAL = 0x9A48E8;
    public static final int CANDY = 0xFF86CC;
    public static final int WHITE = 0xFFFFFF;
    public static final int GOLD = 0xFFCC30;
    public static final int RED = 0xFF3A28;
    public static final int KI = 0x88D4FF;
    public static final int KI_DARK = 0xC060FF;
    public static final int FIRE = 0xFF8A28;
    public static final int BURNING = 0xFFD040;
    public static final int UNIBEAM = 0xB4EEFF;
    public static final int MOUTH = 0xFF8AD0;
    public static final int ERASER = 0xC04CE8;
    public static final int SPIRIT = 0x8CD0FF;
    public static final int MOON = 0xFFF6D0;

    // Beam styles.
    public static final byte BEAM_PLAIN = 0;
    public static final byte BEAM_SPIRAL = 1;
    public static final byte BEAM_THIN = 2;

    // Where an effect on an entity sits.
    public static final byte AT_POS = 0;
    public static final byte AT_HANDS = 1;
    public static final byte AT_EYES = 2;
    public static final byte AT_FOREHEAD = 3;
    public static final byte AT_OVERHEAD = 4;
    public static final byte AT_CHEST = 5;

    // Burst styles.
    /** A swelling ball of light, a flash and a ring going out along the ground. */
    public static final byte BLAST = 0;
    /** Just a flash. */
    public static final byte FLASH = 1;
    /** A ball of light hanging there (the Spirit Bomb gathering, the false moon). */
    public static final byte ORB = 2;
    /** A small star of light (a blow landing, someone vanishing). */
    public static final byte SPARK = 3;
    /** Only the ring along the ground (a blast wave, a stamp). */
    public static final byte RING = 4;

    // Shot styles: KiShots.Style's order, then the Spirit Bomb coming down.
    public static final byte SHOT_SPIRIT = 10;

    /** Everyone this close sees an effect. */
    private static final double RANGE = 128.0D;

    private static int nextShot;

    private KiFx() {
    }

    private static void send(ServerLevel level, Vec3 near, KiFxS2CPacket packet) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(near) < RANGE * RANGE) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    private static int idOf(@Nullable Entity entity) {
        return entity == null ? -1 : entity.getId();
    }

    /**
     * A beam for {@code ticks}: from {@code source}'s hands/eyes/... ({@code at}) if given, else from {@code from};
     * to {@code target}'s middle if given, else to {@code to}. {@code width}: across, in blocks.
     */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, int colour, float width, int ticks, byte style,
                            @Nullable Entity source, byte at, @Nullable Entity target) {
        send(level, from, new KiFxS2CPacket(BEAM, style, idOf(source), idOf(target), at, colour, width, ticks, from, to));
    }

    /** A fixed beam (a straight shot that's already landed) with a burst where it ends. */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, int colour, float width, byte style) {
        int ticks = style == BEAM_THIN ? 7 : 14;
        beam(level, from, to, colour, width, ticks, style, null, AT_POS, null);
        if (style == BEAM_THIN) {
            burst(level, to, colour, 0.6F, 6, SPARK);
        } else {
            burst(level, to, colour, Math.max(1.2F, width * 1.4F), 16, BLAST);
        }
    }

    /** A ball of light gathering on {@code who} for {@code ticks}, {@code size} across at the end. */
    public static void charge(ServerLevel level, Entity who, int colour, float size, int ticks, byte at) {
        send(level, who.position(), new KiFxS2CPacket(CHARGE, (byte) 0, who.getId(), -1, at, colour, size, ticks, who.position(), who.position()));
    }

    public static void stopCharge(ServerLevel level, Entity who) {
        send(level, who.position(), new KiFxS2CPacket(CHARGE_STOP, (byte) 0, who.getId(), -1, 0, 0, 0.0F, 0, who.position(), who.position()));
    }

    /** A shot flying (each client flies it itself, the same way); its number, for {@link #shotEnd}. */
    public static int shot(ServerLevel level, byte style, int colour, float size, Vec3 from, Vec3 dir, double speed, double range) {
        int id = ++nextShot;
        send(level, from, new KiFxS2CPacket(SHOT, style, -1, -1, id, colour, size, (int) Math.ceil(range / Math.max(0.05D, speed)),
                from, dir.normalize().scale(speed)));
        return id;
    }

    public static void shotEnd(ServerLevel level, int id, Vec3 at) {
        send(level, at, new KiFxS2CPacket(SHOT_END, (byte) 0, -1, -1, id, 0, 0.0F, 0, at, at));
    }

    /** A burst ({@link #BLAST}, {@link #FLASH}, ...) {@code size} blocks out, lasting {@code ticks}. */
    public static void burst(ServerLevel level, Vec3 at, int colour, float size, int ticks, byte style) {
        send(level, at, new KiFxS2CPacket(BURST, style, -1, -1, 0, colour, size, ticks, at, at));
    }

    /** A pillar of light {@code height} high, round {@code who} (following it) or at {@code at}. */
    public static void pillar(ServerLevel level, @Nullable Entity who, Vec3 at, int colour, float height, int ticks) {
        send(level, at, new KiFxS2CPacket(PILLAR, (byte) 0, idOf(who), -1, 0, colour, height, ticks, at, at));
    }

    /** An aura round {@code who} for {@code ticks} (colour 0: off). Fighters' lasting auras are synced on the fighter instead. */
    public static void aura(ServerLevel level, Entity who, int colour, int ticks) {
        send(level, who.position(), new KiFxS2CPacket(AURA, (byte) 0, who.getId(), -1, 0, colour, 0.0F, ticks, who.position(), who.position()));
    }

    /** The Solar Flare from {@code who}: a burst of white, and everyone within {@code radius} but them sees nothing for a moment. */
    public static void flare(ServerLevel level, Entity who, Vec3 at, float radius) {
        send(level, at, new KiFxS2CPacket(FLARE, (byte) 0, who.getId(), -1, 0, WHITE, radius, 40, at, at));
    }

    /** A dust particle's colour as 0xRRGGBB (the beams still pass their colours along as dust options). */
    public static int fromRgb(org.joml.Vector3f c) {
        return (Math.round(c.x() * 255.0F) & 0xFF) << 16 | (Math.round(c.y() * 255.0F) & 0xFF) << 8 | Math.round(c.z() * 255.0F) & 0xFF;
    }
}
