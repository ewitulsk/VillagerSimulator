package com.ewitulsk.villagersimulator.api.mod.embodiment;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

/** What an {@link EmbodiedBehaviour} sees of one puppet. */
public interface PuppetContext {
    /** The puppet entity. Never saved; don't keep references to it. */
    PathfinderMob entity();

    ServerLevel level();

    /** The sim entity the puppet embodies. */
    EntityId villager();

    /** The activity the villager is doing, e.g. {@code villagersimulator_fountain:make_wish}. */
    Id activity();

    /** The embodied behaviour key. */
    Id behaviour();

    /** Where the plan puts the villager for this activity. */
    Vec3 target();

    /** True once the puppet has reached {@link #target()}. */
    boolean arrived();

    /** Server ticks since the puppet started this behaviour. */
    int ticks();
}
