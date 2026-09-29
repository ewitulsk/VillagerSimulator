package com.ewitulsk.villagersimulator.api.sim;

import com.mojang.serialization.Codec;

/**
 * A 32-bit entity handle: a 24-bit index plus an 8-bit generation. Stale handles (to a destroyed entity whose index
 * was reused) are detectable because the generation no longer matches. Index 0 is never used, so raw value 0 is
 * {@link #NONE}.
 */
public record EntityId(int raw) {
    public static final int INDEX_BITS = 24;
    public static final int MAX_INDEX = (1 << INDEX_BITS) - 1;
    public static final EntityId NONE = new EntityId(0);
    public static final Codec<EntityId> CODEC = Codec.INT.xmap(EntityId::new, EntityId::raw);

    public static EntityId of(int index, int generation) {
        if (index <= 0 || index > MAX_INDEX) throw new IllegalArgumentException("Bad entity index " + index);
        return new EntityId(((generation & 0xFF) << INDEX_BITS) | index);
    }

    public int index() {
        return raw & MAX_INDEX;
    }

    public int generation() {
        return raw >>> INDEX_BITS;
    }

    public boolean isNone() {
        return raw == 0;
    }

    @Override
    public String toString() {
        return isNone() ? "#none" : "#" + index() + "g" + generation();
    }
}
