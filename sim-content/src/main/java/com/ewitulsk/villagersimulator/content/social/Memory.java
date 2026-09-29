package com.ewitulsk.villagersimulator.content.social;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Something a villager remembers (docs/DESIGN.md §6, DF-style thoughts). {@code record} references the shared event
 * record, if any, so many villagers can remember the same event without copying it. The mood effect halves every
 * {@code halfLife} ticks.
 */
public record Memory(Id kind, long record, EntityId about, float mood, long time, long halfLife) {
    public static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
            Id.CODEC.fieldOf("kind").forGetter(Memory::kind),
            Codec.LONG.fieldOf("record").forGetter(Memory::record),
            EntityId.CODEC.fieldOf("about").forGetter(Memory::about),
            Codec.FLOAT.fieldOf("mood").forGetter(Memory::mood),
            Codec.LONG.fieldOf("time").forGetter(Memory::time),
            Codec.LONG.fieldOf("half_life").forGetter(Memory::halfLife)
    ).apply(i, Memory::new));

    /** Mood effect at {@code now}. */
    public double effect(long now) {
        if (halfLife <= 0) return mood;
        return mood * Math.pow(0.5, (double) (now - time) / halfLife);
    }
}
