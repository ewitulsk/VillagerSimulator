package com.ewitulsk.villagersimulator.neoforge;

import com.ewitulsk.villagersimulator.content.ContentModules;
import com.ewitulsk.villagersimulator.neoforge.client.ClientSetup;
import com.ewitulsk.villagersimulator.neoforge.command.VsCommands;
import com.ewitulsk.villagersimulator.neoforge.server.SimDataReloadListener;
import com.ewitulsk.villagersimulator.neoforge.server.SimServer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Mod entry point. The simulation lives in the Minecraft-free {@code sim-*} projects; this project is the bridge
 * (docs/ARCHITECTURE.md §12).
 */
@Mod(VillagerSimulatorMod.MOD_ID)
public final class VillagerSimulatorMod {
    public static final String MOD_ID = "villagersimulator";

    public VillagerSimulatorMod(IEventBus modBus, ModContainer container) {
        ModContent.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, SimConfig.SPEC);

        modBus.addListener(EntityAttributeCreationEvent.class,
                e -> e.put(ModContent.VILLAGER.get(), SimVillagerEntity.createAttributes().build()));
        // Our own content registers exactly like an addon would (docs/ARCHITECTURE.md §3, "dogfood the API").
        modBus.addListener(RegisterSimModulesEvent.class, e -> ContentModules.all().forEach(e::register));
        if (FMLEnvironment.dist == Dist.CLIENT) ClientSetup.init(modBus);

        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, e -> e.addListener(new SimDataReloadListener()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, e -> VsCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerStartingEvent.class, e -> SimServer.start(e.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> SimServer.tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(LevelEvent.Save.class, SimServer::onLevelSave);
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> SimServer.stop());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
