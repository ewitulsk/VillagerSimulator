package com.ewitulsk.villagersimulator.content.buildings;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A placed building: its type, blueprint origin (min corner) and village. */
public record Building(Id type, int x, int y, int z, EntityId village) {
    public static final Codec<Building> CODEC = RecordCodecBuilder.create(i -> i.group(
            Id.CODEC.fieldOf("type").forGetter(Building::type),
            Codec.INT.fieldOf("x").forGetter(Building::x),
            Codec.INT.fieldOf("y").forGetter(Building::y),
            Codec.INT.fieldOf("z").forGetter(Building::z),
            EntityId.CODEC.fieldOf("village").forGetter(Building::village)
    ).apply(i, Building::new));
}
