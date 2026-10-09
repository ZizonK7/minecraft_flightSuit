package com.pfkfks.flightsuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.pfkfks.flightsuit.village.Blueprint;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageHallBlock;
import com.pfkfks.flightsuit.village.VillageHallBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The hall's notice board: two posts and a plank board above the block, facing the way the hall was
 * placed, with the village's numbers written on it (DESIGN.md 4-12: the numbers live on the building, not
 * on the player's screen). Drawn in the hall's frame: +z = the board's front.
 */
public class VillageHallRenderer implements BlockEntityRenderer<VillageHallBlockEntity> {
    private static final ResourceLocation PLANKS = new ResourceLocation("minecraft", "textures/block/birch_planks.png");
    private static final ResourceLocation POSTS = new ResourceLocation("minecraft", "textures/block/dark_oak_planks.png");
    private static final ResourceLocation STONE_POSTS = new ResourceLocation("minecraft", "textures/block/stone_bricks.png");
    private static final int[] WHITE = {255, 255, 255};
    private static final float BOARD_HALF_WIDTH = 1.05F;
    private static final float BOARD_BOTTOM = 1.25F;
    private static final float BOARD_TOP = 2.55F;
    private static final float TEXT_SCALE = 0.0085F;
    /** Board height the text may fill; with more lines than fit, the writing gets smaller. */
    private static final float TEXT_ROOM = 1.18F;
    private static final int LINE_HEIGHT = 10;

    private static final int INK = 0x2B1D0E;
    private static final int TITLE = 0x6A3D00;
    private static final int ALARM = 0xB00000;
    private static final int NEWS = 0x5A4E40;

    private final Font font;

    public VillageHallRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(VillageHallBlockEntity hall, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = hall.getBlockState().getValue(VillageHallBlock.FACING);
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));

        // Stage 1 (camp): timber posts. Stage 2 (village) on: stone posts and a stone cap over the board.
        boolean stone = hall.getStage() >= 2;
        VertexConsumer posts = buffers.getBuffer(RenderType.entityCutoutNoCull(stone ? STONE_POSTS : POSTS));
        if (stone) {
            BoxDraw.box(pose, posts, -BOARD_HALF_WIDTH - 0.06F, BOARD_TOP, -0.08F, BOARD_HALF_WIDTH + 0.06F, BOARD_TOP + 0.14F, 0.08F, WHITE, light);
        }
        BoxDraw.box(pose, posts, -BOARD_HALF_WIDTH + 0.02F, 1.0F, -0.06F, -BOARD_HALF_WIDTH + 0.14F, BOARD_TOP + 0.1F, 0.06F, WHITE, light);
        BoxDraw.box(pose, posts, BOARD_HALF_WIDTH - 0.14F, 1.0F, -0.06F, BOARD_HALF_WIDTH - 0.02F, BOARD_TOP + 0.1F, 0.06F, WHITE, light);
        VertexConsumer board = buffers.getBuffer(RenderType.entityCutoutNoCull(PLANKS));
        BoxDraw.box(pose, board, -BOARD_HALF_WIDTH, BOARD_BOTTOM, -0.04F, BOARD_HALF_WIDTH, BOARD_TOP, 0.04F, WHITE, light);

        List<Line> lines = lines(hall);
        float scale = Math.min(TEXT_SCALE, TEXT_ROOM / (lines.size() * LINE_HEIGHT));
        pose.translate(0.0D, BOARD_TOP - 0.06F, 0.045D);
        pose.scale(scale, -scale, scale);
        Matrix4f matrix = pose.last().pose();
        int maxWidth = (int) ((BOARD_HALF_WIDTH * 2.0F - 0.12F) / scale);
        int y = 0;
        for (Line line : lines) {
            FormattedText fitted = font.substrByWidth(line.text, maxWidth);
            float width = font.width(fitted);
            font.drawInBatch(Language.getInstance().getVisualOrder(fitted), -width / 2.0F, y, line.color, false, matrix, buffers,
                    Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
            y += LINE_HEIGHT;
        }
        pose.popPose();
    }

    private record Line(Component text, int color) {
    }

    private static List<Line> lines(VillageHallBlockEntity hall) {
        List<Line> lines = new ArrayList<>();
        Component owner = Component.literal(hall.getOwnerName().isEmpty() ? "?" : hall.getOwnerName());
        Component stage = Component.translatable("stage.flightsuit." + hall.getStage());
        lines.add(new Line(Component.translatable("board.flightsuit.title", owner, stage).withStyle(ChatFormatting.BOLD), TITLE));
        lines.add(new Line(Component.translatable("board.flightsuit.population", hall.getPopulation()), INK));
        if (hall.getPopulation() > 0) {
            lines.add(new Line(jobSummary(hall), INK));
        }
        lines.add(new Line(Component.translatable("board.flightsuit.food_housing",
                hall.getPopulation() == 0 ? hall.getFood() : hall.getFood() / hall.getPopulation(),
                hall.getBeds() - hall.getFreeBeds(), hall.getBeds()), INK));
        Component happiness = hall.getHappiness() < 0 ? Component.literal("-") : Component.literal(hall.getHappiness() + "%");
        lines.add(new Line(Component.translatable("board.flightsuit.mood_safety", happiness, hall.getSafety()), INK));
        Component buildings = buildingSummary(hall);
        if (buildings != null) {
            lines.add(new Line(Component.translatable("board.flightsuit.buildings", buildings), INK));
        }
        Blueprint order = hall.getOrder();
        if (order != null) {
            MutableComponent building = Component.translatable(hall.isOrderRebuild() ? "board.flightsuit.rebuilding" : "board.flightsuit.building",
                    Component.translatable(order.translationKey()), hall.getOrderPlaced(), hall.getOrderTotal());
            if (hall.getQueueSize() > 1) {
                building.append(" ").append(Component.translatable("board.flightsuit.queue_more", hall.getQueueSize() - 1));
            }
            lines.add(new Line(building, INK));
        }
        if (hall.isNoSeeds()) {
            lines.add(new Line(Component.translatable("board.flightsuit.no_seeds"), ALARM));
        }
        if (hall.getRepairs() > 0) {
            lines.add(new Line(Component.translatable("board.flightsuit.repairs", hall.getRepairs()), INK));
        }
        List<ItemStack> missing = hall.getMissing();
        if (!missing.isEmpty()) {
            MutableComponent items = Component.empty();
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) {
                    items.append(", ");
                }
                items.append(missing.get(i).getHoverName()).append(" ×" + missing.get(i).getCount());
            }
            lines.add(new Line(Component.translatable("board.flightsuit.missing", items), ALARM));
        }
        Component proposal = proposalLine(hall);
        if (proposal != null) {
            lines.add(new Line(proposal, TITLE));
        }
        if (hall.isAlarmShown()) {
            lines.add(new Line(Component.translatable("board.flightsuit.alarm", hall.getAlarmCause()).withStyle(ChatFormatting.BOLD), ALARM));
            lines.add(new Line(Component.translatable("board.flightsuit.alarm_hint"), ALARM));
        } else if (hall.getFreeBeds() == 0 && order == null && proposal == null) {
            lines.add(new Line(Component.translatable("board.flightsuit.need_beds"), NEWS));
        }
        for (Component news : hall.getNews()) {
            lines.add(new Line(Component.literal("· ").append(news), NEWS));
        }
        return lines;
    }

    /** "건의: 천막 숙소 (빈 침대가 없음) 외 1건" - talk to an architect to act on it. */
    private static @Nullable Component proposalLine(VillageHallBlockEntity hall) {
        int type = hall.getProposalType();
        if (type < 0) {
            return null;
        }
        Component what = Component.translatable(Blueprint.values()[Math.min(type, Blueprint.values().length - 1)].translationKey());
        MutableComponent line = Component.translatable("board.flightsuit.proposal", what, Component.translatable(hall.getProposalReason()));
        if (hall.getProposalCount() > 1) {
            line.append(" ").append(Component.translatable("board.flightsuit.queue_more", hall.getProposalCount() - 1));
        }
        return line;
    }

    /** "집 2 · 밭 1", or null before the architects built anything. */
    private static @Nullable Component buildingSummary(VillageHallBlockEntity hall) {
        MutableComponent summary = Component.empty();
        boolean any = false;
        for (Blueprint blueprint : Blueprint.values()) {
            int count = hall.getBuildingCount(blueprint);
            if (count > 0) {
                if (any) {
                    summary.append(" · ");
                }
                summary.append(Component.translatable(blueprint.translationKey())).append(" " + count);
                any = true;
            }
        }
        return any ? summary : null;
    }

    /** "농부 2 · 건축가 1 · 경비병 1" - only the jobs someone has. */
    private static Component jobSummary(VillageHallBlockEntity hall) {
        MutableComponent summary = Component.empty();
        boolean first = true;
        for (ResidentJob job : ResidentJob.values()) {
            int count = hall.getJobCount(job);
            if (count > 0) {
                if (!first) {
                    summary.append(" · ");
                }
                summary.append(Component.translatable(job.translationKey())).append(" " + count);
                first = false;
            }
        }
        return summary;
    }
}
