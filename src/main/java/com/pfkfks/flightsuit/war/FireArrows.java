package com.pfkfks.flightsuit.war;

import com.pfkfks.flightsuit.FlightSuitMod;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 화공 (DESIGN.md 4-11): a fire attack's burning arrows can set the village alight where they land. Only inside
 * a village (whose hall then watches the blaze and sends guards), and only some of the time.
 */
@Mod.EventBusSubscriber(modid = FlightSuitMod.MODID)
public final class FireArrows {
    private FireArrows() {
    }

    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        Level level = projectile.level();
        if (level.isClientSide || !projectile.getTags().contains(KingdomSoldierEntity.FIRE_ARROW_TAG)
                || !(event.getRayTraceResult() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        // One chance per arrow.
        projectile.removeTag(KingdomSoldierEntity.FIRE_ARROW_TAG);
        BlockPos firePos = hit.getBlockPos().relative(hit.getDirection());
        VillageHallBlockEntity hall = Villages.containing(level, firePos);
        if (hall == null || level.random.nextFloat() > WarTuning.FIRE_CHANCE) {
            return;
        }
        if (level.getBlockState(firePos).isAir() && BaseFireBlock.canBePlacedAt(level, firePos, hit.getDirection())) {
            level.setBlock(firePos, BaseFireBlock.getState(level, firePos), Block.UPDATE_ALL_IMMEDIATE);
            hall.watchFire(firePos);
        }
    }
}
