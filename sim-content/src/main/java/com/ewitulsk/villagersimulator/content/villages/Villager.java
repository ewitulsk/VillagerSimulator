package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A villager's identity and place in the village.
 *
 * @param bed        index of the villager's bed point in {@code home}
 * @param workplace  building the villager works at, or {@link EntityId#NONE}
 * @param workSlot   index of the villager's work point at the workplace
 * @param number     position among the village's residents (stable, used for deterministic choices)
 */
public record Villager(String name, EntityId village, EntityId home, int bed, EntityId workplace, int workSlot,
                       int number, long seed) {
    public static final Codec<Villager> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(Villager::name),
            EntityId.CODEC.fieldOf("village").forGetter(Villager::village),
            EntityId.CODEC.fieldOf("home").forGetter(Villager::home),
            Codec.INT.fieldOf("bed").forGetter(Villager::bed),
            EntityId.CODEC.fieldOf("workplace").forGetter(Villager::workplace),
            Codec.INT.fieldOf("work_slot").forGetter(Villager::workSlot),
            Codec.INT.fieldOf("number").forGetter(Villager::number),
            Codec.LONG.fieldOf("seed").forGetter(Villager::seed)
    ).apply(i, Villager::new));

    public boolean employed() {
        return !workplace.isNone();
    }
}
