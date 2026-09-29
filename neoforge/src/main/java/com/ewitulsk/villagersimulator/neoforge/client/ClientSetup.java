package com.ewitulsk.villagersimulator.neoforge.client;

import com.ewitulsk.villagersimulator.neoforge.ModContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only registration. */
public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(ModContent.VILLAGER.get(), SimVillagerRenderer::new));
    }
}
