package com.pfkfks.flightsuit.cutscene;

import com.pfkfks.flightsuit.planet.dbz.DbzAction;
import com.pfkfks.flightsuit.planet.dbz.DbzCharacter;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A cutscene script (M17): who's in it (cast), and a list of shots - each a stretch of time with a camera move, maybe
 * a subtitle, and what the actors do. Written in Java with {@link Builder} (no JSON); the same script runs on the
 * server (actors, story, CutsceneRunner) and on the client (camera, CutsceneClient), so both agree on every frame.
 * Every position is relative to the anchor the scene is played at, turned by the anchor's yaw: at yaw 0 x is east
 * and z south; +z is always the way the anchor faces.
 */
public final class Cutscene {
    /** What becomes of an actor when the scene ends (or is skipped). */
    public enum End {
        /** Fights on (a bound fighter goes back to its fight; a new one joins it). */
        FIGHT,
        /** Gone. */
        REMOVE,
        /** Stays standing there, not fighting. */
        STAY
    }

    /** What an actor does (Act). */
    public enum Kind {
        /** Walk to {@code pos} over {@code ticks}. */
        MOVE,
        /** Fly to {@code pos} over {@code ticks} (an arc {@code value} tenths of a block high). */
        FLY,
        /** Turn to face {@code target} (an actor) or {@code pos}. */
        FACE,
        /** Take a pose ({@code value} = DbzAction ordinal). */
        POSE,
        /** A beam from the actor at {@code target} (or {@code pos}) for {@code ticks}; {@code value} = Beam colour. */
        BEAM,
        /** Thrown to {@code pos} over {@code ticks}, reeling. */
        KNOCKBACK,
        /** Collapse and lie there. */
        FALL,
        /** Turns into {@code as}: a pillar of gold, cracking ground, the screen shaking. */
        TRANSFORM,
        /** Glowing outline on ({@code value} 1) or off. */
        GLOW,
        /** Gone in a puff ({@code value} 1: a burst of light instead - blown away, teleported). */
        VANISH,
        /** There (at {@code pos}) in a puff. */
        APPEAR,
        /** A line spoken ({@code key}: dbz.flightsuit.<voice>.<key>). */
        SAY,
        /** An effect at {@code pos}: {@code value} = Burst kind. */
        BURST,
        /** A sound at the actor or the anchor: {@code value} = Sound kind. */
        SOUND,
        /** The screen shakes for {@code ticks}. */
        SHAKE
    }

    /** Beam colours. */
    public static final int BEAM_KAME = 0;
    public static final int BEAM_GALICK = 1;
    public static final int BEAM_SBC = 2;
    public static final int BEAM_CANDY = 3;
    public static final int BEAM_WHITE = 4;
    public static final int BEAM_DEATH = 5;
    public static final int BEAM_MOUTH = 6;

    /** Burst kinds. */
    public static final int BURST_EXPLOSION = 0;
    public static final int BURST_FLASH = 1;
    public static final int BURST_CRACK = 2;
    public static final int BURST_GOLD_PILLAR = 3;
    public static final int BURST_SMOKE = 4;
    public static final int BURST_MOON = 5;
    public static final int BURST_BIG_EXPLOSION = 6;
    public static final int BURST_LIGHTNING = 7;
    public static final int BURST_SPIRIT_BOMB = 8;
    public static final int BURST_LAVA = 9;

    /** Sound kinds. */
    public static final int SOUND_BOOM = 0;
    public static final int SOUND_PUNCH = 1;
    public static final int SOUND_POWER = 2;
    public static final int SOUND_ROAR = 3;
    public static final int SOUND_WHOOSH = 4;
    public static final int SOUND_THUNDER = 5;
    public static final int SOUND_CROWD = 6;
    public static final int SOUND_CHARGE = 7;

    /** Someone in the scene: a character, where and facing which way it starts, and what becomes of it. */
    public record Cast(String id, DbzCharacter who, Vec3 at, float yaw, boolean bind, boolean hidden, End end) {
    }

    /** One thing an actor does, starting at tick {@code at} of the scene. */
    public record Act(int at, String actor, Kind kind, Vec3 pos, @Nullable String target, int ticks, int value,
                      @Nullable DbzCharacter as, @Nullable String key) {
        int end() {
            return at + Math.max(0, ticks);
        }
    }

    /** A subtitle: the speaker (null = narration, cutscene.flightsuit.<scene>.<key>) and the line. */
    public record Line(@Nullable DbzCharacter speaker, String key) {
    }

    /** A stretch of the scene: the camera moving from/to, looking from/to, the subtitle shown. */
    public record Shot(int start, int ticks, Vec3 camFrom, Vec3 camTo, Vec3 lookFrom, Vec3 lookTo, boolean smooth, @Nullable Line line) {
    }

    private final String id;
    private final List<Cast> cast;
    private final List<Shot> shots;
    private final List<Act> acts;
    private final int length;

    private Cutscene(String id, List<Cast> cast, List<Shot> shots, List<Act> acts) {
        this.id = id;
        this.cast = Collections.unmodifiableList(cast);
        this.shots = Collections.unmodifiableList(shots);
        List<Act> sorted = new ArrayList<>(acts);
        sorted.sort((a, b) -> Integer.compare(a.at(), b.at()));
        this.acts = Collections.unmodifiableList(sorted);
        int total = 0;
        for (Shot shot : shots) {
            total = Math.max(total, shot.start() + shot.ticks());
        }
        this.length = total;
    }

    public String id() {
        return id;
    }

    public List<Cast> cast() {
        return cast;
    }

    public List<Shot> shots() {
        return shots;
    }

    public List<Act> acts() {
        return acts;
    }

    /** Ticks from start to end. */
    public int length() {
        return length;
    }

    public @Nullable Cast castOf(String actor) {
        for (Cast c : cast) {
            if (c.id().equals(actor)) {
                return c;
            }
        }
        return null;
    }

    /** The shot running at {@code tick} (the last one past the end). */
    public Shot shotAt(float tick) {
        Shot last = shots.get(0);
        for (Shot shot : shots) {
            if (tick >= shot.start()) {
                last = shot;
            }
        }
        return last;
    }

    /** Where the camera is and what it looks at, at {@code tick} (relative to the anchor, before turning). */
    public Vec3[] camera(float tick) {
        Shot shot = shotAt(tick);
        float t = shot.ticks() <= 0 ? 1.0F : Math.max(0.0F, Math.min(1.0F, (tick - shot.start()) / shot.ticks()));
        float e = shot.smooth() ? ease(t) : t;
        return new Vec3[]{shot.camFrom().lerp(shot.camTo(), e), shot.lookFrom().lerp(shot.lookTo(), e)};
    }

    /** Ease in and out. */
    public static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    /** {@code rel} turned by the anchor's {@code yaw} and moved to {@code anchor}. */
    public static Vec3 place(Vec3 anchor, float yaw, Vec3 rel) {
        double r = Math.toRadians(yaw);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        // +z goes the way yaw faces ((-sin, cos)), +x a quarter turn from it ((cos, sin)).
        return anchor.add(rel.x * cos - rel.z * sin, rel.y, rel.x * sin + rel.z * cos);
    }

    public static Builder of(String id) {
        return new Builder(id);
    }

    /**
     * Writes a script:
     * <pre>
     * Cutscene.of("dbz.example")
     *     .cast("goku", GOKU, 0, 0, 0, 180).bind().ends(End.FIGHT)
     *     .shot(40).cam(6, 3, 6).camTo(4, 2, 4).look(0, 1, 0).say(GOKU, "line")
     *         .at(10).move("goku", 0, 0, 3, 20).pose("goku", DbzAction.FIRE)
     *     .build();
     * </pre>
     * Acts belong to the current shot; {@link #at} is the tick within it.
     */
    public static final class Builder {
        private final String id;
        private final List<Cast> cast = new ArrayList<>();
        private final List<Shot> shots = new ArrayList<>();
        private final List<Act> acts = new ArrayList<>();
        private int shotStart;
        private int shotTicks = -1;
        private Vec3 camFrom = new Vec3(0, 3, -8);
        private Vec3 camTo = camFrom;
        private Vec3 lookFrom = new Vec3(0, 1.5, 0);
        private Vec3 lookTo = lookFrom;
        private boolean smooth = true;
        private Line line;
        private int local;

        private Builder(String id) {
            this.id = id;
        }

        /** Someone new, spawned for the scene (removed after it unless {@link #ends} says otherwise). */
        public Builder cast(String actor, DbzCharacter who, double x, double y, double z, float yaw) {
            cast.add(new Cast(actor, who, new Vec3(x, y, z), yaw, false, false, End.REMOVE));
            return this;
        }

        private Builder replaceLast(java.util.function.UnaryOperator<Cast> change) {
            cast.set(cast.size() - 1, change.apply(cast.get(cast.size() - 1)));
            return this;
        }

        /** The last cast member is the fight's fighter of that character if there is one (it goes back to fighting). */
        public Builder bind() {
            return replaceLast(c -> new Cast(c.id(), c.who(), c.at(), c.yaw(), true, c.hidden(), End.FIGHT));
        }

        /** The last cast member starts out of sight (until an APPEAR). */
        public Builder hidden() {
            return replaceLast(c -> new Cast(c.id(), c.who(), c.at(), c.yaw(), c.bind(), true, c.end()));
        }

        public Builder ends(End end) {
            return replaceLast(c -> new Cast(c.id(), c.who(), c.at(), c.yaw(), c.bind(), c.hidden(), end));
        }

        private void closeShot() {
            if (shotTicks >= 0) {
                shots.add(new Shot(shotStart, shotTicks, camFrom, camTo, lookFrom, lookTo, smooth, line));
                shotStart += shotTicks;
            }
        }

        /** A new shot of {@code ticks}; its camera starts where the last one ended. */
        public Builder shot(int ticks) {
            closeShot();
            shotTicks = ticks;
            camFrom = camTo;
            lookFrom = lookTo;
            smooth = true;
            line = null;
            local = 0;
            return this;
        }

        /** Camera here for the whole shot (until camTo). */
        public Builder cam(double x, double y, double z) {
            camFrom = new Vec3(x, y, z);
            camTo = camFrom;
            return this;
        }

        public Builder camTo(double x, double y, double z) {
            camTo = new Vec3(x, y, z);
            return this;
        }

        public Builder look(double x, double y, double z) {
            lookFrom = new Vec3(x, y, z);
            lookTo = lookFrom;
            return this;
        }

        public Builder lookTo(double x, double y, double z) {
            lookTo = new Vec3(x, y, z);
            return this;
        }

        /** Move at a steady speed instead of easing in and out. */
        public Builder linear() {
            smooth = false;
            return this;
        }

        /** The shot's subtitle: a line spoken (dbz.flightsuit.<voice>.<key>). */
        public Builder say(DbzCharacter speaker, String key) {
            line = new Line(speaker, key);
            return this;
        }

        /** The shot's subtitle: narration (cutscene.flightsuit.<scene id>.<key>). */
        public Builder narrate(String key) {
            line = new Line(null, key);
            return this;
        }

        /** Following acts start this many ticks into the shot. */
        public Builder at(int tick) {
            local = tick;
            return this;
        }

        private Builder act(String actor, Kind kind, Vec3 pos, @Nullable String target, int ticks, int value, @Nullable DbzCharacter as,
                            @Nullable String key) {
            acts.add(new Act(shotStart + local, actor, kind, pos, target, ticks, value, as, key));
            return this;
        }

        public Builder move(String actor, double x, double y, double z, int ticks) {
            return act(actor, Kind.MOVE, new Vec3(x, y, z), null, ticks, 0, null, null);
        }

        public Builder fly(String actor, double x, double y, double z, int ticks) {
            return act(actor, Kind.FLY, new Vec3(x, y, z), null, ticks, 10, null, null);
        }

        /** Fly with an arc {@code arc} blocks high. */
        public Builder fly(String actor, double x, double y, double z, int ticks, double arc) {
            return act(actor, Kind.FLY, new Vec3(x, y, z), null, ticks, (int) Math.round(arc * 10.0D), null, null);
        }

        public Builder face(String actor, String other) {
            return act(actor, Kind.FACE, Vec3.ZERO, other, 0, 0, null, null);
        }

        public Builder facePos(String actor, double x, double y, double z) {
            return act(actor, Kind.FACE, new Vec3(x, y, z), null, 0, 0, null, null);
        }

        public Builder pose(String actor, DbzAction action) {
            return act(actor, Kind.POSE, Vec3.ZERO, null, 0, action.ordinal(), null, null);
        }

        /** A beam from {@code actor} at another actor for {@code ticks}. */
        public Builder beam(String actor, String other, int colour, int ticks) {
            return act(actor, Kind.BEAM, Vec3.ZERO, other, ticks, colour, null, null);
        }

        /** A beam from {@code actor} at a spot. */
        public Builder beamAt(String actor, double x, double y, double z, int colour, int ticks) {
            return act(actor, Kind.BEAM, new Vec3(x, y, z), null, ticks, colour, null, null);
        }

        public Builder knock(String actor, double x, double y, double z, int ticks) {
            return act(actor, Kind.KNOCKBACK, new Vec3(x, y, z), null, ticks, 12, null, null);
        }

        public Builder fall(String actor) {
            return act(actor, Kind.FALL, Vec3.ZERO, null, 0, 0, null, null);
        }

        public Builder transform(String actor, DbzCharacter as) {
            return act(actor, Kind.TRANSFORM, Vec3.ZERO, null, 0, 0, as, null);
        }

        public Builder glow(String actor, boolean on) {
            return act(actor, Kind.GLOW, Vec3.ZERO, null, 0, on ? 1 : 0, null, null);
        }

        public Builder vanish(String actor) {
            return act(actor, Kind.VANISH, Vec3.ZERO, null, 0, 0, null, null);
        }

        /** Gone in a burst of light (blown away by a beam, teleported out). */
        public Builder vanishInLight(String actor) {
            return act(actor, Kind.VANISH, Vec3.ZERO, null, 0, 1, null, null);
        }

        public Builder appear(String actor, double x, double y, double z) {
            return act(actor, Kind.APPEAR, new Vec3(x, y, z), null, 0, 0, null, null);
        }

        /** A line spoken mid-shot (replaces the subtitle). */
        public Builder line(String actor, String key) {
            return act(actor, Kind.SAY, Vec3.ZERO, null, 0, 0, null, key);
        }

        public Builder burst(int kind, double x, double y, double z) {
            return act("", Kind.BURST, new Vec3(x, y, z), null, 0, kind, null, null);
        }

        public Builder sound(int kind) {
            return act("", Kind.SOUND, Vec3.ZERO, null, 0, kind, null, null);
        }

        public Builder shake(int ticks) {
            return act("", Kind.SHAKE, Vec3.ZERO, null, ticks, 0, null, null);
        }

        public Cutscene build() {
            closeShot();
            shotTicks = -1;
            if (shots.isEmpty()) {
                throw new IllegalStateException("cutscene " + id + " has no shots");
            }
            return new Cutscene(id, cast, shots, acts);
        }
    }
}
