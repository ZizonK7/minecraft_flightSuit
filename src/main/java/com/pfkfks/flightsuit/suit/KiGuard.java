package com.pfkfks.flightsuit.suit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Asked before a ki beam or blast lands (SuitSkills.beam, the Dragon Ball fighters' blasts): Trunks cutting with
 * his sword cuts it out of the air (SwordArts.cutsKi), and a Dragon Ball boss may guard or dodge a player's beam
 * (DbzFighterEntity.guardsBeam).
 */
public final class KiGuard {
    private KiGuard() {
    }

    /** True if {@code victim} takes nothing from the ki that {@code shooter} sent from {@code from}. */
    public static boolean blocks(LivingEntity victim, LivingEntity shooter, Vec3 from) {
        if (SwordArts.cutsKi(victim, from)) {
            return true;
        }
        return victim instanceof com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity fighter && fighter.dodgesBeam(shooter);
    }
}
