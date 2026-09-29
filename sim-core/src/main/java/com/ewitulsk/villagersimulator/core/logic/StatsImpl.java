package com.ewitulsk.villagersimulator.core.logic;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.stat.Modifier;
import com.ewitulsk.villagersimulator.api.sim.stat.StatType;
import com.ewitulsk.villagersimulator.api.sim.stat.Stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stats with modifiers stored in a sparse component on each entity. */
public final class StatsImpl implements Stats {
    public static final SparseComponent<List<Modifier>> MODIFIERS =
            new SparseComponent<>(Id.of(LogicImpl.NAMESPACE, "modifiers"), 1, Modifier.CODEC.listOf());

    private final SimContext ctx;
    private final Map<Id, StatType> types;

    /** Stats read and written through {@code ctx}, so a shard's writes to other shards are deferred. */
    public StatsImpl(SimContext ctx, Map<Id, StatType> types) {
        this.ctx = ctx;
        this.types = types;
    }

    public StatsImpl(SimContext ctx) {
        this(ctx, new LinkedHashMap<>());
    }

    /** The same stat types, used through another context. */
    public StatsImpl with(SimContext other) {
        return new StatsImpl(other, types);
    }

    public void register(StatType type) {
        if (types.putIfAbsent(type.id(), type) != null) throw new IllegalArgumentException("Duplicate stat " + type.id());
    }

    public boolean known(Id stat) {
        return types.containsKey(stat);
    }

    @Override
    public double value(EntityId entity, Id stat) {
        StatType type = types.get(stat);
        if (type == null) throw new IllegalArgumentException("Unknown stat " + stat);
        double add = 0, mult = 0;
        List<Modifier> list = ctx.get(entity, MODIFIERS);
        if (list != null) {
            long now = ctx.now();
            for (Modifier m : list) {
                if (m.stat().equals(stat) && m.activeAt(now)) {
                    add += m.add();
                    mult += m.mult();
                }
            }
        }
        return (type.base() + add) * (1 + mult);
    }

    @Override
    public void add(EntityId entity, Modifier modifier) {
        if (!types.containsKey(modifier.stat())) throw new IllegalArgumentException("Unknown stat " + modifier.stat());
        List<Modifier> out = new ArrayList<>();
        long now = ctx.now();
        List<Modifier> list = ctx.get(entity, MODIFIERS);
        if (list != null) {
            for (Modifier m : list) {
                boolean replaced = m.stat().equals(modifier.stat()) && m.source().equals(modifier.source());
                if (!replaced && m.activeAt(now)) out.add(m);
            }
        }
        out.add(modifier);
        ctx.set(entity, MODIFIERS, List.copyOf(out));
    }

    @Override
    public void remove(EntityId entity, Id stat, Id source) {
        List<Modifier> list = ctx.get(entity, MODIFIERS);
        if (list == null) return;
        long now = ctx.now();
        List<Modifier> out = list.stream()
                .filter(m -> !(m.stat().equals(stat) && m.source().equals(source)) && m.activeAt(now)).toList();
        if (out.isEmpty()) ctx.remove(entity, MODIFIERS);
        else ctx.set(entity, MODIFIERS, out);
    }

    @Override
    public List<Modifier> modifiers(EntityId entity) {
        List<Modifier> list = ctx.get(entity, MODIFIERS);
        if (list == null) return List.of();
        long now = ctx.now();
        return list.stream().filter(m -> m.activeAt(now)).toList();
    }
}
