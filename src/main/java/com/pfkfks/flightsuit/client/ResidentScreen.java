package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.network.ModNetwork;
import com.pfkfks.flightsuit.network.ResidentActionC2SPacket;
import com.pfkfks.flightsuit.network.ResidentScreenS2CPacket;
import com.pfkfks.flightsuit.village.Blueprint;
import com.pfkfks.flightsuit.village.ResidentEntity;
import com.pfkfks.flightsuit.village.ResidentJob;
import com.pfkfks.flightsuit.village.VillageTuning;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Talking to a resident (right-click): who they are, how they're doing and what they're good at (talent
 * stars per job), and - for a wanderer - whether to take them in, or - for a resident - which job they do.
 * An architect also has a "건축" tab: their suggestions (건의) to approve, blueprints to take, and the building
 * queue. The village numbers stay on the hall's board.
 */
public class ResidentScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 252;
    private static final int PANEL = 0xE0141A20;
    private static final int EDGE = 0xFF5FE3FF;
    private static final int GIFTED = 0xFFD54A;
    private static final int PLAIN = 0xE8E8E8;
    private static final int WEAK = 0x8C96A0;
    private static final int CELL_WIDTH = 68;

    private final int entityId;
    private int mood;
    private boolean fed;
    private boolean hasBed;
    private int[] talents;
    private @Nullable CompoundTag works;
    private ResidentJob shownJob;
    private boolean shownWanderer;
    private boolean shownChild;
    private boolean buildTab;

    private ResidentScreen(ResidentScreenS2CPacket packet) {
        super(Component.translatable("screen.flightsuit.resident"));
        this.entityId = packet.entityId;
        update(packet);
    }

    /** A fresh packet for the screen already open (after a choice) refreshes it in place, same tab. */
    public static void open(ResidentScreenS2CPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ResidentScreen screen && screen.entityId == packet.entityId) {
            screen.update(packet);
            screen.rebuildWidgets();
        } else {
            minecraft.setScreen(new ResidentScreen(packet));
        }
    }

    private void update(ResidentScreenS2CPacket packet) {
        this.mood = packet.mood;
        this.fed = packet.fed;
        this.hasBed = packet.hasBed;
        this.talents = packet.talents;
        this.works = packet.works;
    }

    private @Nullable ResidentEntity resident() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getEntity(entityId) instanceof ResidentEntity resident ? resident : null;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    private int talent(ResidentJob job) {
        return job.ordinal() < talents.length ? Math.max(1, Math.min(5, talents[job.ordinal()])) : 1;
    }

    private Component stars(ResidentJob job) {
        int stars = talent(job);
        return Component.literal("★" + stars).withStyle(style -> style.withColor(stars >= 4 ? GIFTED : stars == 3 ? PLAIN : WEAK));
    }

    @Override
    protected void init() {
        ResidentEntity resident = resident();
        if (resident == null) {
            onClose();
            return;
        }
        shownJob = resident.getJob();
        shownWanderer = resident.isWanderer();
        shownChild = resident.isBaby();
        if (shownChild) {
            // Children don't work yet: no job buttons, just who they are (render).
            return;
        }
        if (works == null) {
            buildTab = false;
        }
        int x = left() + 92;
        if (shownWanderer) {
            addRenderableWidget(Button.builder(Component.translatable("screen.flightsuit.resident.accept"),
                    button -> send(ResidentEntity.ACTION_ACCEPT, 0)).bounds(x, top() + 148, 96, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.flightsuit.resident.dismiss"),
                    button -> {
                        send(ResidentEntity.ACTION_DISMISS, 0);
                        onClose();
                    }).bounds(x + 102, top() + 148, 96, 20).build());
            return;
        }
        if (works != null) {
            Button jobs = Button.builder(Component.translatable("screen.flightsuit.tab.job"), b -> {
                buildTab = false;
                rebuildWidgets();
            }).bounds(left() + WIDTH - 104, top() + 6, 48, 16).build();
            Button build = Button.builder(Component.translatable("screen.flightsuit.tab.build"), b -> {
                buildTab = true;
                rebuildWidgets();
            }).bounds(left() + WIDTH - 54, top() + 6, 48, 16).build();
            jobs.active = buildTab;
            build.active = !buildTab;
            addRenderableWidget(jobs);
            addRenderableWidget(build);
        }
        if (buildTab) {
            initBuildTab(x);
        } else {
            initJobTab(x);
        }
    }

    private void initJobTab(int x) {
        ResidentJob[] jobs = ResidentJob.values();
        for (int i = 0; i < jobs.length; i++) {
            ResidentJob job = jobs[i];
            MutableComponent label = Component.translatable(job.translationKey()).copy();
            if (job != ResidentJob.NONE) {
                label.append(" ").append(stars(job));
            }
            Button button = Button.builder(label, b -> send(ResidentEntity.ACTION_SET_JOB, job.ordinal()))
                    .bounds(x + (i % 3) * CELL_WIDTH, top() + 76 + (i / 3) * 22, CELL_WIDTH - 2, 20)
                    .tooltip(Tooltip.create(effect(job)))
                    .build();
            button.active = job != shownJob;
            addRenderableWidget(button);
        }
    }

    private void initBuildTab(int x) {
        int top = top();
        ListTag proposals = works.getList("Proposals", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(3, proposals.size()); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.translatable("screen.flightsuit.build.approve"),
                    b -> send(ResidentEntity.ACTION_APPROVE, index)).bounds(x + 152, top + 83 + i * 19, 48, 18).build());
        }
        int stage = works.getInt("Stage");
        int slot = 0;
        for (Blueprint blueprint : Blueprint.values()) {
            if (blueprint.stage() > stage || blueprint.isHall()) {
                continue;
            }
            addRenderableWidget(Button.builder(Component.translatable(blueprint.translationKey()),
                            b -> send(ResidentEntity.ACTION_TAKE_BLUEPRINT, blueprint.ordinal()))
                    .bounds(x + (slot % 3) * CELL_WIDTH, top + 155 + (slot / 3) * 20, CELL_WIDTH - 2, 18)
                    .tooltip(Tooltip.create(Component.translatable("screen.flightsuit.build.take_hint")))
                    .build());
            slot++;
        }
        ListTag queue = works.getList("Queue", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(2, queue.size()); i++) {
            addCancel(x, top + 208 + i * 19, i);
        }
    }

    private void addCancel(int x, int y, int index) {
        addRenderableWidget(Button.builder(Component.translatable("screen.flightsuit.build.cancel"),
                b -> send(ResidentEntity.ACTION_CANCEL, index)).bounds(x + 152, y, 48, 18).build());
    }

    /** What the stars mean for this job right now. */
    private Component effect(ResidentJob job) {
        if (job == ResidentJob.NONE) {
            return Component.translatable("screen.flightsuit.job_none");
        }
        int stars = talent(job);
        if (job.isFighter()) {
            return Component.translatable("screen.flightsuit.job_guard", stars,
                    (int) (VillageTuning.GUARD_HEALTH_PER_STAR * (stars - 1) / 2), (int) (VillageTuning.GUARD_DAMAGE_PER_STAR * (stars - 1)));
        }
        if (job.works()) {
            return Component.translatable("screen.flightsuit.job_speed", stars, Math.round(VillageTuning.talentSpeed(stars) * 100));
        }
        return Component.translatable("screen.flightsuit.job_later", stars);
    }

    /** "건축가 ★5 · 경비병 ★4" - the gifts (or the best they have). */
    private Component gifts() {
        MutableComponent line = Component.empty();
        int best = 0;
        for (ResidentJob job : ResidentJob.values()) {
            if (job != ResidentJob.NONE) {
                best = Math.max(best, talent(job));
            }
        }
        boolean first = true;
        for (ResidentJob job : ResidentJob.values()) {
            if (job != ResidentJob.NONE && talent(job) >= Math.min(4, best)) {
                if (!first) {
                    line.append(" · ");
                }
                line.append(Component.translatable(job.translationKey())).append(" ").append(stars(job));
                first = false;
            }
        }
        return Component.translatable("screen.flightsuit.resident.gifts").append(line);
    }

    private void send(int action, int arg) {
        ModNetwork.sendToServer(new ResidentActionC2SPacket(entityId, action, arg));
    }

    @Override
    public void tick() {
        ResidentEntity resident = resident();
        Minecraft minecraft = Minecraft.getInstance();
        if (resident == null || resident.isRemoved() || resident.isDowned()
                || minecraft.player == null || resident.distanceToSqr(minecraft.player) > 144.0D) {
            onClose();
        } else if (resident.getJob() != shownJob || resident.isWanderer() != shownWanderer || resident.isBaby() != shownChild) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int left = left();
        int top = top();
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, EDGE);
        ResidentEntity resident = resident();
        if (resident != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, left + 46, top + 150, 52,
                    left + 46 - mouseX, top + 62 - mouseY, resident);
            int x = left + 92;
            graphics.drawString(font, resident.getName().copy().withStyle(ChatFormatting.BOLD), x, top + 10, 0xFFFFFF);
            Component role = shownChild ? Component.translatable("job.flightsuit.child", resident.getChildDaysLeft())
                    : Component.translatable(shownWanderer ? "job.flightsuit.wanderer" : shownJob.translationKey());
            graphics.drawString(font, role, x, top + 22, 0xA8B4BE);
            graphics.drawString(font, Component.translatable("screen.flightsuit.resident.mood", mood), x, top + 34, moodColor());
            if (!shownWanderer) {
                Component meal = Component.translatable(fed ? "screen.flightsuit.resident.fed" : "screen.flightsuit.resident.hungry");
                Component bed = Component.translatable(hasBed ? "screen.flightsuit.resident.bed" : "screen.flightsuit.resident.no_bed");
                graphics.drawString(font, meal.copy().append(" · ").append(bed), x, top + 45, 0xC8C8C8);
            }
            graphics.drawString(font, gifts(), x, top + 58, 0xC8C8C8);
            if (shownChild) {
                renderTalentTable(graphics, x, top + 74);
                Component parents = resident.getParents().isEmpty() ? Component.literal("-") : Component.literal(resident.getParents());
                graphics.drawString(font, Component.translatable("screen.flightsuit.child.parents", parents), x, top + 124, PLAIN);
                graphics.drawString(font, Component.translatable("screen.flightsuit.child.school", resident.getLessons(),
                        Component.translatable(resident.getFavorite().translationKey())), x, top + 136, PLAIN);
                graphics.drawWordWrap(font, Component.translatable("screen.flightsuit.child.hint"), x, top + 152, WIDTH - 100, WEAK);
            } else if (shownWanderer) {
                renderTalentTable(graphics, x, top + 74);
                graphics.drawWordWrap(font, Component.translatable("screen.flightsuit.resident.wanderer_ask", resident.getName()),
                        x, top + 124, WIDTH - 100, PLAIN);
            } else if (buildTab && works != null) {
                renderBuildTab(graphics, x, top);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderBuildTab(GuiGraphics graphics, int x, int top) {
        graphics.drawString(font, Component.translatable("screen.flightsuit.build.proposals"), x, top + 72, EDGE);
        ListTag proposals = works.getList("Proposals", Tag.TAG_COMPOUND);
        if (proposals.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.flightsuit.build.no_proposals"), x, top + 88, WEAK);
        }
        for (int i = 0; i < Math.min(3, proposals.size()); i++) {
            graphics.drawString(font, proposalText(proposals.getCompound(i)), x, top + 88 + i * 19, PLAIN);
        }
        Component stage = Component.translatable("stage.flightsuit." + works.getInt("Stage"));
        graphics.drawString(font, Component.translatable("screen.flightsuit.build.blueprints", stage), x, top + 144, EDGE);
        graphics.drawString(font, Component.translatable("screen.flightsuit.build.queue"), x, top + 197, EDGE);
        ListTag queue = works.getList("Queue", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(2, queue.size()); i++) {
            CompoundTag order = queue.getCompound(i);
            Component name = Component.translatable(Blueprint.values()[order.getInt("Blueprint")].translationKey());
            String key = order.getBoolean("Rebuild") ? "screen.flightsuit.build.order_rebuild" : "screen.flightsuit.build.order";
            graphics.drawString(font, Component.translatable(key, name, order.getInt("Placed"), order.getInt("Total"), order.getInt("Crew")),
                    x, top + 213 + i * 19, PLAIN);
        }
        if (queue.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.flightsuit.build.no_orders"), x, top + 213, WEAK);
        } else if (queue.size() > 2) {
            graphics.drawString(font, Component.translatable("screen.flightsuit.build.more", queue.size() - 2), x + 100, top + 197, WEAK);
        }
    }

    /** "천막 숙소 · 빈 침대가 없음", "천막 숙소 → 집 개축", "석조 회관 · 인구 6명: 마을로 성장 가능". */
    private static Component proposalText(CompoundTag proposal) {
        String kind = proposal.getString("Kind");
        Component reason = Component.translatable(proposal.getString("Reason"));
        Component name = Component.translatable(Blueprint.values()[proposal.getInt("Blueprint")].translationKey());
        if ("REBUILD".equals(kind)) {
            Component from = Component.translatable(Blueprint.values()[proposal.getInt("From")].translationKey());
            return Component.translatable("screen.flightsuit.build.rebuild", from, name);
        }
        return name.copy().append(" · ").append(reason);
    }

    /** A wanderer can't be given a job yet, but you see what they'd be good at before taking them in. */
    private void renderTalentTable(GuiGraphics graphics, int x, int y) {
        int i = 0;
        for (ResidentJob job : ResidentJob.values()) {
            if (job == ResidentJob.NONE) {
                continue;
            }
            Component cell = Component.translatable(job.translationKey()).copy().append(" ").append(stars(job));
            graphics.drawString(font, cell, x + (i % 3) * CELL_WIDTH, y + (i / 3) * 11, 0xB8C2CA);
            i++;
        }
    }

    private int moodColor() {
        return mood >= 70 ? 0x7CE07C : mood >= 40 ? 0xE8D35A : 0xE06A5A;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
