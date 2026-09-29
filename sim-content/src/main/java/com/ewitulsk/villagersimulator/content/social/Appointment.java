package com.ewitulsk.villagersimulator.content.social;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A shared plan entry between two villagers (docs/DESIGN.md §7.1): meet {@code other} at {@code venue} for its
 * advertisement {@code ad} during {@code [start, end)}. Each side holds a copy with the same {@code id}.
 */
public record Appointment(long id, EntityId other, EntityId venue, String ad, long start, long end, boolean resolved) {
    public static final Codec<Appointment> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("id").forGetter(Appointment::id),
            EntityId.CODEC.fieldOf("other").forGetter(Appointment::other),
            EntityId.CODEC.fieldOf("venue").forGetter(Appointment::venue),
            Codec.STRING.fieldOf("ad").forGetter(Appointment::ad),
            Codec.LONG.fieldOf("start").forGetter(Appointment::start),
            Codec.LONG.fieldOf("end").forGetter(Appointment::end),
            Codec.BOOL.fieldOf("resolved").forGetter(Appointment::resolved)
    ).apply(i, Appointment::new));

    /** Marks the appointment as over (kept or missed). */
    public Appointment resolve() {
        return new Appointment(id, other, venue, ad, start, end, true);
    }
}
