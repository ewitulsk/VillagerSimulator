package com.ewitulsk.villagersimulator.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Minecraft registrations. */
public final class ModContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VillagerSimulatorMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VillagerSimulatorMod.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, VillagerSimulatorMod.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VillagerSimulatorMod.MOD_ID);

    /**
     * A player's sim entity handle, cached on the player (docs/ARCHITECTURE.md §14). Not saved: the sim looks
     * players up by UUID, so the cache is rebuilt on login.
     */
    public static final Supplier<AttachmentType<Integer>> PLAYER_HANDLE = ATTACHMENTS.register("player_handle",
            () -> AttachmentType.builder(() -> 0).build());

    public static final DeferredBlock<BuildingAnchorBlock> BUILDING_ANCHOR = BLOCKS.register("building_anchor",
            () -> new BuildingAnchorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 6.0f)
                    .sound(SoundType.LODESTONE)));
    public static final DeferredItem<BlockItem> BUILDING_ANCHOR_ITEM = ITEMS.registerSimpleBlockItem(BUILDING_ANCHOR);

    /**
     * Villager puppets. {@code noSave()}: the sim is the truth, so these are never written to chunks
     * (docs/ARCHITECTURE.md §12.4). {@code noSummon()}: they only exist when the sim embodies someone.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SimVillagerEntity>> VILLAGER = ENTITIES.register("villager",
            () -> EntityType.Builder.<SimVillagerEntity>of(SimVillagerEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .noSave()
                    .noSummon()
                    .build("villager"));

    private ModContent() {}

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
    }
}
