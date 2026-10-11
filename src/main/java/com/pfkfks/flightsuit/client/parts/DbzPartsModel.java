package com.pfkfks.flightsuit.client.parts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pfkfks.flightsuit.planet.dbz.DbzFighterEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * M18 the Dragon Ball cast's 3D parts (DESIGN.md 5차 피드백 "인물별 입체 부품"): tails, antennae, horns, shoulder pads,
 * wings - drawn by DbzPartsLayer over the skinned body, so the body's posing (DbzFighterModel) stays untouched.
 * A parts model has two roots, {@code head} and {@code body} (pivots where the player model's are), whose direct
 * children are named groups ({@link #groups()}; DbzParts says which each character turns on); the layer copies the
 * body model's head and body transforms before drawing each root's groups. Boxes, UVs and the texture come from
 * tools/ModelGen.java ("parts_*" - the generated block in each subclass).
 */
public abstract class DbzPartsModel extends Model {
    protected final ModelPart head;
    protected final ModelPart body;

    protected DbzPartsModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.head = root.getChild("head");
        this.body = root.getChild("body");
    }

    public abstract ResourceLocation texture();

    /** Every group name this model has (direct children of head or body), in the order they're drawn. */
    public abstract List<String> groups();

    /** Shows the named groups and hides the rest (names it doesn't have are simply skipped). */
    public void show(Set<String> on) {
        for (String name : groups()) {
            ModelPart group = group(name);
            if (group != null) {
                group.visible = on.contains(name);
            }
        }
    }

    /** The group of that name under the head or the body, or null. */
    protected @Nullable ModelPart group(String name) {
        if (head.hasChild(name)) {
            return head.getChild(name);
        }
        return body.hasChild(name) ? body.getChild(name) : null;
    }

    /** Per-frame motion: a tail swaying, antennae bobbing. The head's and body's own transforms are the layer's. */
    public void animate(DbzFighterEntity fighter, float ageInTicks) {
    }

    /** Draws the head's groups (the layer has applied the body model's head transform). */
    public void renderHead(PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
        renderGroups(head, poseStack, buffer, light, overlay);
    }

    /** Draws the body's groups (the layer has applied the body model's body transform). */
    public void renderBody(PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
        renderGroups(body, poseStack, buffer, light, overlay);
    }

    private void renderGroups(ModelPart parent, PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
        for (String name : groups()) {
            if (parent.hasChild(name)) {
                ModelPart group = parent.getChild(name);
                if (group.visible) {
                    group.render(poseStack, buffer, light, overlay);
                }
            }
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, float r, float g, float b, float a) {
        renderHead(poseStack, buffer, light, overlay);
        renderBody(poseStack, buffer, light, overlay);
    }
}
