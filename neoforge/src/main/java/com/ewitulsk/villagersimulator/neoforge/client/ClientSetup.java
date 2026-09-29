package com.ewitulsk.villagersimulator.neoforge.client;

import com.ewitulsk.villagersimulator.neoforge.ModContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only registration. */
public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        // Addons' client hooks (mod-api), collected once the mods have loaded.
        modBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, e -> e.enqueueWork(() -> {
            com.ewitulsk.villagersimulator.neoforge.AnimationKeys.load();
            DialoguePanels.load();
        }));
        com.ewitulsk.villagersimulator.neoforge.client.script.ClientScript.init();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class,
                DebugOverlayRenderer::render);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(ModContent.VILLAGER.get(), SimVillagerRenderer::new));
    }
}
