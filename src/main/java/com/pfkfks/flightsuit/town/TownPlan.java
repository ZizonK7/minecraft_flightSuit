package com.pfkfks.flightsuit.town;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where a town's people live and spend their day, read off its builder's layout (FortressBuilder, HeroCityBuilder):
 * homes, and the spots of each kind of place - fields, shops, the smithy, the square, the park, offices, the
 * lab, the streets the police walk. Plus who lives there (the roster). All positions are where one stands (y + 1
 * over the town's ground).
 */
public final class TownPlan {
    private final List<BlockPos> homes = new ArrayList<>();
    private final Map<TownRole.Place, List<BlockPos>> places = new EnumMap<>(TownRole.Place.class);
    private final List<TownRole> roster = new ArrayList<>();

    private TownPlan() {
    }

    public List<TownRole> roster() {
        return roster;
    }

    public List<BlockPos> homes() {
        return homes;
    }

    /** The spots of one kind (never empty: falls back to the square). */
    public List<BlockPos> spots(TownRole.Place place) {
        List<BlockPos> spots = places.get(place);
        return spots == null || spots.isEmpty() ? places.get(TownRole.Place.SQUARE) : spots;
    }

    /** The {@code index}th person's spot of a kind (shops and fields are shared out in turn). */
    public BlockPos spot(TownRole.Place place, int index) {
        List<BlockPos> spots = spots(place);
        return spots.get(Math.floorMod(index, spots.size()));
    }

    public BlockPos randomSpot(TownRole.Place place, RandomSource random) {
        List<BlockPos> spots = spots(place);
        return spots.get(random.nextInt(spots.size()));
    }

    public BlockPos home(int index) {
        return homes.get(Math.floorMod(index, homes.size()));
    }

    private void add(TownRole.Place place, BlockPos pos) {
        places.computeIfAbsent(place, p -> new ArrayList<>()).add(pos);
    }

    private void roles(TownRole role, int count) {
        for (int i = 0; i < count; i++) {
            roster.add(role);
        }
    }

    /** A Three Kingdoms fortress town (FortressBuilder) around its centre on the ground. */
    public static TownPlan fortress(BlockPos center) {
        TownPlan plan = new TownPlan();
        BlockPos base = center.above();
        for (int cx : new int[]{-33, -23, -13}) {
            plan.homes.add(base.offset(cx, 0, 20));
            plan.homes.add(base.offset(cx, 0, 30));
        }
        // The wheat fields along the south wall, walked between the rows.
        for (int x = -34; x <= -8; x += 4) {
            plan.add(TownRole.Place.FIELD, base.offset(x, 0, 36));
        }
        // Behind the market stalls' counters.
        for (int cx : new int[]{12, 20, 28}) {
            plan.add(TownRole.Place.SHOP, base.offset(cx, 0, 21));
            plan.add(TownRole.Place.SHOP, base.offset(cx, 0, 31));
        }
        // The barracks' smithing tables; the granary's store for the cook.
        plan.add(TownRole.Place.SMITHY, base.offset(-30, 0, 11));
        plan.add(TownRole.Place.SMITHY, base.offset(30, 0, 11));
        plan.add(TownRole.Place.KITCHEN, base.offset(-30, 0, -30));
        // The square round the bell pavilion, and the training yard for the children.
        for (int[] at : new int[][]{{-7, 6}, {7, 6}, {-7, -6}, {7, -6}, {0, 7}, {0, -7}, {-8, 0}, {8, 0}}) {
            plan.add(TownRole.Place.SQUARE, base.offset(at[0], 0, at[1]));
        }
        for (int[] at : new int[][]{{-18, 6}, {-18, -6}, {-20, 0}, {-16, 0}}) {
            plan.add(TownRole.Place.PLAY, base.offset(at[0], 0, at[1]));
        }
        plan.roles(TownRole.FORT_FARMER, 4);
        plan.roles(TownRole.FORT_MERCHANT, 3);
        plan.roles(TownRole.FORT_SMITH, 1);
        plan.roles(TownRole.FORT_COOK, 1);
        plan.roles(TownRole.FORT_ELDER, 2);
        plan.roles(TownRole.FORT_CHILD, 3);
        return plan;
    }

    /** Hero City (HeroCityBuilder) around its centre on the ground. */
    public static TownPlan city(BlockPos center) {
        TownPlan plan = new TownPlan();
        BlockPos base = center.above();
        // The outer blocks: the corner and east/west ones are flats, the north/south ones shops.
        for (int[] at : new int[][]{{-48, -48}, {48, -48}, {-48, 48}, {48, 48}, {-48, -20}, {-48, 20}, {48, -20}, {48, 20}}) {
            plan.homes.add(base.offset(at[0], 0, at[1]));
        }
        for (int[] at : new int[][]{{-20, -48}, {20, -48}, {-20, 48}, {20, 48}}) {
            plan.add(TownRole.Place.SHOP, base.offset(at[0], 0, at[1]));
        }
        // The towers' ground floors.
        for (int[] at : new int[][]{{24, -24}, {-26, -26}, {-14, -27}, {25, 25}}) {
            plan.add(TownRole.Place.OFFICE, base.offset(at[0], 0, at[1]));
        }
        // The science centre, beside the beacon in the middle.
        plan.add(TownRole.Place.LAB, base.offset(12, 0, 22));
        plan.add(TownRole.Place.LAB, base.offset(10, 0, 25));
        // The plaza round the HQ, and the park.
        for (int[] at : new int[][]{{-9, 8}, {9, 8}, {-9, -8}, {9, -8}, {0, 9}, {-8, 9}, {8, 9}}) {
            plan.add(TownRole.Place.SQUARE, base.offset(at[0], 0, at[1]));
        }
        for (int[] at : new int[][]{{-26, 12}, {-12, 26}, {-26, 26}, {-12, 12}, {-19, 26}}) {
            plan.add(TownRole.Place.PLAY, base.offset(at[0], 0, at[1]));
            plan.add(TownRole.Place.SQUARE, base.offset(at[0], 0, at[1]));
        }
        // The avenues and streets the police walk.
        for (int d = 14; d <= 54; d += 10) {
            for (int s = -1; s <= 1; s += 2) {
                plan.add(TownRole.Place.PATROL, base.offset(s * d, 0, 0));
                plan.add(TownRole.Place.PATROL, base.offset(0, 0, s * d));
                plan.add(TownRole.Place.PATROL, base.offset(s * d, 0, 36));
                plan.add(TownRole.Place.PATROL, base.offset(36, 0, s * d));
            }
        }
        plan.roles(TownRole.CITY_WORKER, 5);
        plan.roles(TownRole.CITY_SCIENTIST, 2);
        plan.roles(TownRole.CITY_POLICE, 3);
        plan.roles(TownRole.CITY_SHOPKEEPER, 4);
        plan.roles(TownRole.CITY_CITIZEN, 4);
        plan.roles(TownRole.CITY_CHILD, 3);
        return plan;
    }

    /** M17: West City round Capsule Corp ({@code cc}: its centre at standing height - planet.dbz.WestCity). */
    public static TownPlan westCity(BlockPos cc) {
        TownPlan plan = new TownPlan();
        for (int[] at : com.pfkfks.flightsuit.planet.dbz.WestCity.HOUSES) {
            plan.homes.add(cc.offset(at[0], 0, at[1]));
        }
        for (int[] at : com.pfkfks.flightsuit.planet.dbz.WestCity.SHOPS) {
            // Behind the counter inside the door.
            net.minecraft.core.Direction door = com.pfkfks.flightsuit.planet.dbz.WestCity.doorFacing(at);
            plan.add(TownRole.Place.SHOP, cc.offset(at[0], 0, at[1]).relative(door.getOpposite()));
        }
        for (int[] at : com.pfkfks.flightsuit.planet.dbz.WestCity.SQUARE) {
            plan.add(TownRole.Place.SQUARE, cc.offset(at[0], 0, at[1]));
        }
        int[] park = com.pfkfks.flightsuit.planet.dbz.WestCity.PARK;
        for (int[] at : new int[][]{{-3, 0}, {3, 1}, {0, 3}, {1, -3}}) {
            plan.add(TownRole.Place.PLAY, cc.offset(park[0] + at[0], 0, park[1] + at[1]));
            plan.add(TownRole.Place.SQUARE, cc.offset(park[0] + at[0], 0, park[1] + at[1]));
        }
        plan.roles(TownRole.DBZ_SHOPKEEPER, 3);
        plan.roles(TownRole.DBZ_CITIZEN, 7);
        plan.roles(TownRole.DBZ_CHILD, 3);
        return plan;
    }

    /** M17: the Namekian village ({@code center}: on the ground, as DbzSaga keeps it) - six Namekians among the domes. */
    public static TownPlan namek(BlockPos center) {
        TownPlan plan = new TownPlan();
        BlockPos base = center.above();
        for (int[] at : com.pfkfks.flightsuit.planet.dbz.DbzSaga.NAMEK_DOMES) {
            plan.homes.add(base.offset(at[0], 0, at[1]));
        }
        // The green between the domes (the middle is kept for Dende, and for fights).
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45.0D);
            plan.add(TownRole.Place.SQUARE, base.offset((int) Math.round(Math.cos(angle) * 9.0D), 0, (int) Math.round(Math.sin(angle) * 9.0D)));
        }
        plan.roles(TownRole.NAMEKIAN, 6);
        return plan;
    }
}
