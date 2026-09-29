package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** A village: its name, seed, centre and members, in creation order. */
public record Village(String name, long seed, int x, int y, int z, long founded,
                      List<EntityId> buildings, List<EntityId> residents) {
    public static final Codec<Village> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Village::name),
            Codec.LONG.fieldOf("seed").forGetter(Village::seed),
            Codec.INT.fieldOf("x").forGetter(Village::x),
            Codec.INT.fieldOf("y").forGetter(Village::y),
            Codec.INT.fieldOf("z").forGetter(Village::z),
            Codec.LONG.fieldOf("founded").forGetter(Village::founded),
            EntityId.CODEC.listOf().fieldOf("buildings").forGetter(Village::buildings),
            EntityId.CODEC.listOf().fieldOf("residents").forGetter(Village::residents)
    ).apply(i, Village::new));

    public Village {
        buildings = List.copyOf(buildings);
        residents = List.copyOf(residents);
    }
}
