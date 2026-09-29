package com.ewitulsk.villagersimulator.neoforge.client;

import com.ewitulsk.villagersimulator.content.villages.Appearance;
import com.ewitulsk.villagersimulator.neoforge.SimVillagerEntity;
import com.ewitulsk.villagersimulator.neoforge.VillagerSimulatorMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Villagers with a face (docs/ROADMAP.md Phase 4): the GeckoLib villager model, a texture painted from the
 * villager's appearance genes ({@link VillagerTextures}), and hair bones shown per hair style.
 */
public class SimVillagerRenderer extends GeoEntityRenderer<SimVillagerEntity> {
    private static final ResourceLocation MODEL = VillagerSimulatorMod.id("geo/entity/villager.geo.json");
    private static final ResourceLocation ANIMATIONS = VillagerSimulatorMod.id("animations/entity/villager.animation.json");

    public SimVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(SimVillagerEntity animatable) {
                return MODEL;
            }

            @Override
            public ResourceLocation getTextureResource(SimVillagerEntity animatable) {
                return VillagerTextures.texture(animatable.appearance());
            }

            @Override
            public ResourceLocation getAnimationResource(SimVillagerEntity animatable) {
                return ANIMATIONS;
            }
        });
        this.shadowRadius = 0.5f;
    }

    @Override
    public void preRender(PoseStack poseStack, SimVillagerEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        int style = Appearance.style(animatable.appearance());
        model.getBone("hair").ifPresent(b -> b.setHidden(style == Appearance.BALD));
        model.getBone("hair_long").ifPresent(b -> b.setHidden(style != Appearance.LONG && style != Appearance.HOOD));
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
