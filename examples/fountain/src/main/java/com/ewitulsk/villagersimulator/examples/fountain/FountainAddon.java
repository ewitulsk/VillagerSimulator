package com.ewitulsk.villagersimulator.examples.fountain;

import com.ewitulsk.villagersimulator.api.mod.RegisterSimModulesEvent;
import com.ewitulsk.villagersimulator.api.mod.animation.RegisterAnimationKeysEvent;
import com.ewitulsk.villagersimulator.api.mod.blueprint.RegisterPointBlocksEvent;
import com.ewitulsk.villagersimulator.api.mod.embodiment.RegisterEmbodiedBehavioursEvent;
import com.ewitulsk.villagersimulator.api.mod.ui.RegisterDialoguePanelsEvent;
import com.ewitulsk.villagersimulator.api.mod.ui.RegisterVillagerFactsEvent;
import com.ewitulsk.villagersimulator.api.sim.Id;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import java.util.List;

/**
 * The reference addon (docs/ROADMAP.md Phase 7): a fountain where villagers make wishes. It only uses the public
 * APIs, and every extension point it touches is one line here:
 * <ul>
 *   <li>data: the fountain building type and blueprint, with its advertisements and a {@code layout} so generated
 *       villages include it ({@code data/villagersimulator_fountain/});</li>
 *   <li>sim: a module with the {@link MakeWish} Activity and the {@code wishes()} expression function;</li>
 *   <li>blueprint metadata: the water cauldron becomes the fountain's {@code wish} point;</li>
 *   <li>embodiment: puppets making a wish hold a gold nugget and toss it with a splash ({@link WishBehaviour});</li>
 *   <li>animation: making a wish plays the villager's work animation;</li>
 *   <li>UI: the dialogue screen shows how many wishes a villager has made.</li>
 * </ul>
 */
@Mod(FountainAddon.MOD_ID)
public final class FountainAddon {
    public static final String MOD_ID = "villagersimulator_fountain";
    public static final String WISHES_FACT = MOD_ID + ":wishes";

    public static Id id(String path) {
        return Id.of(MOD_ID, path);
    }

    public FountainAddon(IEventBus modBus) {
        modBus.addListener(RegisterSimModulesEvent.class, e -> e.register(new FountainModule()));
        modBus.addListener(RegisterPointBlocksEvent.class, e -> e.register(Blocks.WATER_CAULDRON, "wish"));
        modBus.addListener(RegisterEmbodiedBehavioursEvent.class, e -> e.register(MakeWish.ID, new WishBehaviour()));
        modBus.addListener(RegisterVillagerFactsEvent.class, e -> e.register(WISHES_FACT, (sim, villager) -> {
            int n = FountainModule.wishes(sim, villager);
            return n > 0 ? String.valueOf(n) : null;
        }));
        // Client-side hooks. Their events are only fired on the client, so registering them everywhere is safe.
        modBus.addListener(RegisterAnimationKeysEvent.class, e -> e.register(MakeWish.ID, RegisterAnimationKeysEvent.WORK));
        modBus.addListener(RegisterDialoguePanelsEvent.class, e -> e.register(ctx -> {
            String wishes = ctx.facts().get(WISHES_FACT);
            if (wishes == null) return List.of();
            return List.of(Component.literal("Wishes made at the fountain: " + wishes).withStyle(ChatFormatting.AQUA));
        }));
    }
}
