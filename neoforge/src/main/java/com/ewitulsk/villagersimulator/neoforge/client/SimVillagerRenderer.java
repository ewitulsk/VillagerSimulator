package com.ewitulsk.villagersimulator.neoforge.client;

import com.ewitulsk.villagersimulator.neoforge.SimVillagerEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Placeholder look for Phase 0: the vanilla villager model and base texture (referenced from the game's own assets,
 * not copied). Custom GeckoLib villagers arrive in Phase 4.
 */
public class SimVillagerRenderer extends MobRenderer<SimVillagerEntity, VillagerModel<SimVillagerEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");

    public SimVillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(SimVillagerEntity entity) {
        return TEXTURE;
    }
}
