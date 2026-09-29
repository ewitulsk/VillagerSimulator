package com.ewitulsk.villagersimulator.api.sim.stat;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A change to a stat from one source. Adding a modifier with the same stat and source replaces the old one.
 *
 * @param expires sim time the modifier stops applying, or {@link Long#MAX_VALUE} for permanent
 */
public record Modifier(Id stat, Id source, double add, double mult, long expires) {
    public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(i -> i.group(
            Id.CODEC.fieldOf("stat").forGetter(Modifier::stat),
            Id.CODEC.fieldOf("source").forGetter(Modifier::source),
            Codec.DOUBLE.optionalFieldOf("add", 0.0).forGetter(Modifier::add),
            Codec.DOUBLE.optionalFieldOf("mult", 0.0).forGetter(Modifier::mult),
            Codec.LONG.optionalFieldOf("expires", Long.MAX_VALUE).forGetter(Modifier::expires)
    ).apply(i, Modifier::new));

    public boolean activeAt(long time) {
        return time < expires;
    }
}
