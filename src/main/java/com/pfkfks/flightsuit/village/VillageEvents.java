package com.pfkfks.flightsuit.village;

import com.pfkfks.flightsuit.FlightSuitMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** World hooks for villages: fight damage goes into the hall's ledger, and monsters go after residents. */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class VillageEvents {
    private VillageEvents() {
    }

    /**
     * Anything blown up inside a village - creepers, the suits' missiles - is fight damage the builders put
     * back. TNT someone lit is deliberate digging, so it is left alone.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide || event.getExplosion().getDirectSourceEntity() instanceof PrimedTnt) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            Villages.recordDamage(level, pos, level.getBlockState(pos));
        }
    }

    /** Hostile monsters treat residents like vanilla villagers - fair game (downed ones are left be). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Monster monster && !(monster instanceof NeutralMob)) {
            monster.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(monster, ResidentEntity.class, 10, true, false,
                    target -> !((ResidentEntity) target).isDowned()));
        }
    }
}
