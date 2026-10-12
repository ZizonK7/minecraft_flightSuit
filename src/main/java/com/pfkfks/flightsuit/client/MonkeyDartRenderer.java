package com.pfkfks.flightsuit.client;

import com.pfkfks.flightsuit.entity.MonkeyDartEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** A monkey tower's dart (MonkeyDartEntity): drawn as a plain arrow. */
public class MonkeyDartRenderer extends ArrowRenderer<MonkeyDartEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/projectiles/arrow.png");

    public MonkeyDartRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MonkeyDartEntity dart) {
        return TEXTURE;
    }
}
