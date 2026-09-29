package com.ewitulsk.villagersimulator.content.plans;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** A villager's itinerary for sim day {@code day}; entries are contiguous and cover the whole day. */
public record Plan(long day, List<PlanEntry> entries) {
    public static final Codec<Plan> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("day").forGetter(Plan::day),
            PlanEntry.CODEC.listOf().fieldOf("entries").forGetter(Plan::entries)
    ).apply(i, Plan::new));

    public Plan {
        entries = List.copyOf(entries);
    }

    /** Index of the entry containing {@code time}, or the last entry. */
    public int indexAt(long time) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).contains(time)) return i;
        return entries.size() - 1;
    }
}
