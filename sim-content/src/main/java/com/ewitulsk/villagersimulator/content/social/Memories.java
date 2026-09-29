package com.ewitulsk.villagersimulator.content.social;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.content.VS;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Memories and the mood they add up to. Faded memories are dropped; at most {@link #MAX} are kept. */
public final class Memories {
    public static final SparseComponent<List<Memory>> COMPONENT = new SparseComponent<>(VS.id("memories"), 1, Memory.CODEC.listOf());
    public static final int MAX = 16;
    /** Memories whose mood effect has faded below this are forgotten. */
    private static final double FORGET_BELOW = 0.5;

    private Memories() {}

    public static void add(SimContext ctx, EntityId v, Id kind, long record, EntityId about, float mood, long halfLife) {
        long now = ctx.now();
        List<Memory> list = new ArrayList<>();
        for (Memory m : get(ctx, v)) if (Math.abs(m.effect(now)) >= FORGET_BELOW) list.add(m);
        list.add(new Memory(kind, record, about, mood, now, halfLife));
        if (list.size() > MAX) {
            list.sort(Comparator.comparingDouble((Memory m) -> -Math.abs(m.effect(now))).thenComparingLong(Memory::time));
            list = new ArrayList<>(list.subList(0, MAX));
        }
        ctx.set(v, COMPONENT, List.copyOf(list));
    }

    public static List<Memory> get(SimContext ctx, EntityId v) {
        List<Memory> list = ctx.get(v, COMPONENT);
        return list == null ? List.of() : list;
    }

    /** Sum of all memories' current mood effects. */
    public static double mood(SimContext ctx, EntityId v) {
        long now = ctx.now();
        double total = 0;
        for (Memory m : get(ctx, v)) total += m.effect(now);
        return total;
    }
}
