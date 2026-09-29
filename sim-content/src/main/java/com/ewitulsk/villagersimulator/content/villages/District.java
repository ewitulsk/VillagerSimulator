package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A semantic district of a village (docs/DESIGN.md §12.1): a named area with a purpose, e.g. "Market Quarter"
 * (market) or "Old Town" (residential). The unit for statistics, government and gossip in later phases.
 */
public record District(String name, String purpose, int x0, int z0, int x1, int z1, EntityId village) {
    public static final Codec<District> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(District::name),
            Codec.STRING.fieldOf("purpose").forGetter(District::purpose),
            Codec.INT.fieldOf("x0").forGetter(District::x0),
            Codec.INT.fieldOf("z0").forGetter(District::z0),
            Codec.INT.fieldOf("x1").forGetter(District::x1),
            Codec.INT.fieldOf("z1").forGetter(District::z1),
            EntityId.CODEC.fieldOf("village").forGetter(District::village)
    ).apply(i, District::new));

    /** A district to create with a village: its area is given in world block coordinates. */
    public record Spec(String name, String purpose, int x0, int z0, int x1, int z1) {}

    public boolean contains(double x, double z) {
        return x >= x0 && x < x1 && z >= z0 && z < z1;
    }

    public double distanceTo(double x, double z) {
        double dx = Math.max(Math.max(x0 - x, 0), x - x1);
        double dz = Math.max(Math.max(z0 - z, 0), z - z1);
        return Math.sqrt(dx * dx + dz * dz);
    }
}
