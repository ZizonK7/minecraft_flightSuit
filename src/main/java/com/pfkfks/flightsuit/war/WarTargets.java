package com.pfkfks.flightsuit.war;

import com.pfkfks.flightsuit.entity.RemoteBodyEntity;
import com.pfkfks.flightsuit.entity.SuitCompanionEntity;
import com.pfkfks.flightsuit.village.ResidentEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who fights whom in the Three Kingdoms war (M12), in one place:
 * - a raid on the player's village fights its defenders and anyone on the player's side;
 * - a storm on a fortress fights that fortress's garrison, the player's side, and players at odds with it;
 * - a garrison fights storms, monsters at home, and players (with their suits and soldiers) it's hostile to;
 * - the player's side (support troops, allied armies, recruited generals, the player's own soldiers) fights
 *   raids and storms, monsters, and garrisons of a kingdom being invaded or hostile to their commander.
 * Kneeling prisoners are never fought.
 */
public final class WarTargets {
    private WarTargets() {
    }

    public static boolean isEnemy(RaidMember self, Mob selfMob, LivingEntity other) {
        if (other == selfMob || !other.isAlive()) {
            return false;
        }
        boolean selfSide = self.isPlayerSide();
        if (other instanceof RaidMember them) {
            boolean themSide = them.isPlayerSide();
            if (them.isNoThreat() && !themSide) {
                return false;
            }
            if (selfSide) {
                return !themSide && isPlayerSideFoe(selfMob, self.commander(), self.foe(), them);
            }
            if (themSide) {
                return self.role() != WarRole.GARRISON || isPlayerSideFoe(selfMob, them.commander(), them.foe(), self);
            }
            if (self.kingdom() == them.kingdom()) {
                return false;
            }
            if (self.role() == WarRole.RAID) {
                return them.role() == WarRole.GARRISON && self.foe() == them.kingdom();
            }
            // Garrison: anyone storming (any raiding army of another kingdom near home).
            return self.role() == WarRole.GARRISON && them.role() == WarRole.RAID;
        }
        if (selfSide) {
            return other instanceof Enemy && !(other instanceof RaidMember) && near(selfMob, other, 16.0D);
        }
        UUID backer = backerOf(other);
        boolean villageRaid = self.role() == WarRole.RAID && self.foe() == null;
        if (other instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            if (villageRaid) {
                return !RaidManager.isDueling(self.raidId(), player, selfMob);
            }
            int trust = trust(selfMob, player.getUUID(), self.kingdom());
            return self.role() == WarRole.RAID ? trust < 0 : trust <= -50;
        }
        if (other instanceof ResidentEntity resident) {
            if (resident.isDowned()) {
                return false;
            }
            if (villageRaid) {
                return true;
            }
            return backer != null && hostile(selfMob, backer, self);
        }
        if (other instanceof SuitCompanionEntity || other instanceof RemoteBodyEntity) {
            if (villageRaid) {
                return true;
            }
            return backer != null && hostile(selfMob, backer, self);
        }
        if (other instanceof IronGolem) {
            return villageRaid;
        }
        if (other instanceof Enemy && self.role() == WarRole.GARRISON && self.home() != null) {
            return other.blockPosition().closerThan(self.home(), 32.0D);
        }
        return false;
    }

    /** The player behind a suit, a body, or a soldier resident marching with them (null = nobody). */
    private static @Nullable UUID backerOf(LivingEntity entity) {
        if (entity instanceof SuitCompanionEntity suit) {
            return suit.getOwnerId();
        }
        if (entity instanceof RemoteBodyEntity body) {
            return body.getOwnerId();
        }
        if (entity instanceof ResidentEntity resident) {
            return resident.getCommander();
        }
        return null;
    }

    private static boolean hostile(Mob selfMob, UUID player, RaidMember self) {
        int trust = trust(selfMob, player, self.kingdom());
        return self.role() == WarRole.RAID ? trust < 0 : trust <= -50;
    }

    /** Does the player's side (led by {@code commander}, invading {@code foe}) fight this member? */
    public static boolean isPlayerSideFoe(Mob anyMob, @Nullable UUID commander, @Nullable Kingdom foe, RaidMember them) {
        if (them.isNoThreat()) {
            return false;
        }
        if (them.role() == WarRole.RAID) {
            return true;
        }
        if (them.role() == WarRole.GARRISON) {
            return them.kingdom() == foe || commander != null && trust(anyMob, commander, them.kingdom()) <= -50;
        }
        return false;
    }

    public static int trust(Mob anyMob, UUID player, Kingdom kingdom) {
        if (anyMob.level() instanceof ServerLevel server) {
            return WarData.get(server.getServer()).trust(player, kingdom);
        }
        return 0;
    }

    private static boolean near(Mob selfMob, LivingEntity other, double range) {
        return selfMob.distanceToSqr(other) < range * range;
    }
}
