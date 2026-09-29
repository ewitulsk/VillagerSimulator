package com.ewitulsk.villagersimulator.content.social;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A villager's stay at a building, for working out who was there together.
 *
 * @param intensity how social the stay was (1 for socialising, lower for working or eating)
 * @param ended     false while the stay is ongoing ({@code to} is then the planned end)
 */
public record Visit(EntityId villager, long from, long to, float intensity, boolean work, boolean ended) {
    public static final Codec<Visit> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityId.CODEC.fieldOf("villager").forGetter(Visit::villager),
            Codec.LONG.fieldOf("from").forGetter(Visit::from),
            Codec.LONG.fieldOf("to").forGetter(Visit::to),
            Codec.FLOAT.fieldOf("intensity").forGetter(Visit::intensity),
            Codec.BOOL.fieldOf("work").forGetter(Visit::work),
            Codec.BOOL.fieldOf("ended").forGetter(Visit::ended)
    ).apply(i, Visit::new));

    public long overlap(long otherFrom, long otherTo) {
        return Math.max(0, Math.min(to, otherTo) - Math.max(from, otherFrom));
    }
}
