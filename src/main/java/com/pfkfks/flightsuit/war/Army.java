package com.pfkfks.flightsuit.war;

import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import com.pfkfks.flightsuit.village.Villages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The player's own army on the move (DESIGN.md 4-12 병사: 원정·침략, M12): "/village army follow" in your village
 * takes its soldiers and recruited generals with you; "/village army home" sends them back.
 */
public final class Army {
    private Army() {
    }

    public static int follow(ServerPlayer player) {
        VillageHallBlockEntity hall = Villages.containing(player.level(), player.blockPosition());
        if (hall == null || !hall.isOwner(player)) {
            player.sendSystemMessage(Component.translatable("army.flightsuit.in_village").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        int soldiers = 0;
        for (ResidentEntity resident : hall.residents()) {
            if (resident.getJob() == ResidentJob.SOLDIER && !resident.isDowned() && !resident.isBaby()) {
                resident.setCommander(player.getUUID());
                soldiers++;
            }
        }
        int generals = 0;
        for (GeneralEntity general : player.level().getEntitiesOfClass(GeneralEntity.class, hall.area().inflate(16.0D),
                general -> general.isRecruited() && player.getUUID().equals(general.getOwnerId()))) {
            general.setFollowing(true);
            generals++;
        }
        player.sendSystemMessage(Component.translatable("army.flightsuit.follow", soldiers, generals).withStyle(ChatFormatting.GREEN));
        return soldiers + generals;
    }

    public static int home(ServerPlayer player) {
        int count = 0;
        for (ResidentEntity resident : player.level().getEntitiesOfClass(ResidentEntity.class, player.getBoundingBox().inflate(160.0D),
                resident -> player.getUUID().equals(resident.getCommander()))) {
            resident.setCommander(null);
            count++;
        }
        for (GeneralEntity general : player.level().getEntitiesOfClass(GeneralEntity.class, player.getBoundingBox().inflate(160.0D),
                general -> general.isFollowing() && player.getUUID().equals(general.getOwnerId()))) {
            general.setFollowing(false);
            count++;
        }
        player.sendSystemMessage(Component.translatable("army.flightsuit.home", count).withStyle(ChatFormatting.YELLOW));
        return count;
    }
}
