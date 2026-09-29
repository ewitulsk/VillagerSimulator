package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;
import com.ewitulsk.villagersimulator.api.sim.command.SimQuery;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.FloatField;
import com.ewitulsk.villagersimulator.api.sim.component.IntField;
import com.ewitulsk.villagersimulator.api.sim.component.LongField;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.core.CoreComponents;
import com.ewitulsk.villagersimulator.api.sim.event.EventHandler;
import com.ewitulsk.villagersimulator.api.sim.event.EventLog;
import com.ewitulsk.villagersimulator.api.sim.event.EventRecord;
import com.ewitulsk.villagersimulator.api.sim.event.SimEvent;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.api.sim.module.Validator;
import com.ewitulsk.villagersimulator.api.sim.core.DataReloaded;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.api.sim.logic.Logic;
import com.ewitulsk.villagersimulator.api.sim.logic.LogicFactories;
import com.ewitulsk.villagersimulator.api.sim.stat.StatType;
import com.ewitulsk.villagersimulator.api.sim.stat.Stats;
import com.ewitulsk.villagersimulator.core.logic.LogicImpl;
import com.ewitulsk.villagersimulator.core.logic.StatsImpl;
import com.ewitulsk.villagersimulator.api.sim.registry.RegistryKey;
import com.ewitulsk.villagersimulator.api.sim.registry.SimRegistry;
import com.ewitulsk.villagersimulator.api.sim.task.TaskType;
import com.ewitulsk.villagersimulator.api.sim.view.ViewKey;
import com.ewitulsk.villagersimulator.api.sim.view.ViewProvider;
import com.ewitulsk.villagersimulator.core.data.DataSource;
import com.ewitulsk.villagersimulator.core.storage.DenseStore;
import com.ewitulsk.villagersimulator.core.storage.EntityAllocator;
import com.ewitulsk.villagersimulator.core.storage.Scheduler;
import com.ewitulsk.villagersimulator.core.storage.SparseStore;
import com.mojang.serialization.JsonOps;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * One simulation: entities, components, the event queue, registries and modules. Single-threaded; in game it is
 * driven by {@link SimRuntime} on its own thread, headless it is driven directly (virtual time).
 */
public final class SimWorld implements SimContext {
    /** Format version of {@link #snapshot} data. */
    public static final int SNAPSHOT_FORMAT = SnapshotCodec.FORMAT;

    private final List<SimModule> modules;
    final Map<Id, DenseStore> dense = new LinkedHashMap<>();
    private final Map<DenseComponent, DenseStore> denseByComponent = new IdentityHashMap<>();
    final Map<Id, SparseStore<?>> sparse = new LinkedHashMap<>();
    private final Map<SparseComponent<?>, SparseStore<?>> sparseByComponent = new IdentityHashMap<>();
    final Map<Id, TaskType> tasks = new LinkedHashMap<>();
    private final Map<Id, Activity> activities = new LinkedHashMap<>();
    private final Map<Id, SimRegistryImpl<?>> registries = new LinkedHashMap<>();
    private final Map<Id, RegistryKey<?>> registryKeys = new LinkedHashMap<>();
    private final LogicImpl logic = new LogicImpl();
    private final StatsImpl stats = new StatsImpl(this);
    private final List<Validator> validators = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final Map<Class<?>, List<EventHandler<?>>> handlers = new HashMap<>();
    private final Map<ViewKey<?>, ViewProvider<?>> views = new LinkedHashMap<>();
    final EntityAllocator entities = new EntityAllocator();
    final Scheduler scheduler = new Scheduler();
    final SimEventLog eventLog = new SimEventLog(this::now);
    /** Sections of a loaded save that no registered component claims (e.g. a removed addon); written back as-is. */
    final Map<Id, byte[]> unknownSections = new LinkedHashMap<>();
    private DataSource data;
    private long now;

    private SimWorld(List<SimModule> modules, DataSource data) {
        this.modules = modules;
        this.data = data;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<SimModule> modules = new ArrayList<>();
        private DataSource data = folder -> Map.of();
        private long startTime;

        public Builder module(SimModule module) {
            modules.add(module);
            return this;
        }

        public Builder modules(Collection<? extends SimModule> list) {
            modules.addAll(list);
            return this;
        }

        public Builder data(DataSource source) {
            this.data = source;
            return this;
        }

        public Builder startTime(long time) {
            this.startTime = time;
            return this;
        }

        public SimWorld build() {
            SimWorld world = new SimWorld(List.copyOf(sortModules(modules)), data);
            world.now = startTime;
            world.registerAll();
            return world;
        }
    }

    /** Orders modules so dependencies come first; otherwise keeps the given order. */
    static List<SimModule> sortModules(List<SimModule> input) {
        Map<Id, SimModule> byId = new LinkedHashMap<>();
        for (SimModule m : input) {
            if (byId.put(m.id(), m) != null) throw new IllegalArgumentException("Duplicate module " + m.id());
        }
        List<SimModule> out = new ArrayList<>();
        Set<Id> done = new HashSet<>();
        Set<Id> visiting = new HashSet<>();
        for (SimModule m : input) visit(m, byId, done, visiting, out);
        return out;
    }

    private static void visit(SimModule m, Map<Id, SimModule> byId, Set<Id> done, Set<Id> visiting, List<SimModule> out) {
        if (done.contains(m.id())) return;
        if (!visiting.add(m.id())) throw new IllegalArgumentException("Module dependency cycle at " + m.id());
        for (Id dep : m.dependencies()) {
            SimModule d = byId.get(dep);
            if (d == null) throw new IllegalArgumentException("Module " + m.id() + " needs missing module " + dep);
            visit(d, byId, done, visiting, out);
        }
        visiting.remove(m.id());
        done.add(m.id());
        out.add(m);
    }

    private void registerAll() {
        Registrar r = new Registrar();
        r.component(CoreComponents.TIER);
        r.component(StatsImpl.MODIFIERS);
        for (SimModule m : modules) m.register(r);
        validate();
    }

    @SuppressWarnings("unchecked")
    private <T> void reloadRegistry(RegistryKey<T> key, DataSource source) {
        SimRegistryImpl<T> fresh = SimRegistryImpl.parse(key, source.load(key.folder()), problems::add);
        registries.put(key.id(), fresh.keepingMissingFrom((SimRegistryImpl<T>) registries.get(key.id()), problems::add));
    }

    private void validate() {
        for (Validator v : validators) {
            try {
                v.validate(this, problems::add);
            } catch (RuntimeException e) {
                problems.add("Validator failed: " + e);
            }
        }
    }

    /**
     * Problems found in data (bad definitions, expressions that do not compile). Bad data is skipped rather than
     * crashing the sim; this list says what was skipped.
     */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /**
     * Re-reads every registry from {@code source} ({@code /reload}). Definitions still used by the world but missing
     * from the new data are kept. Compiled logic is recompiled lazily, validators run again, and
     * {@link DataReloaded} is published.
     */
    public void reloadData(DataSource source) {
        this.data = source;
        problems.clear();
        for (RegistryKey<?> key : registryKeys.values()) reloadRegistry(key, source);
        logic.clearCaches();
        validate();
        publish(new DataReloaded());
    }

    private final class Registrar implements SimRegistrar {
        @Override
        public void component(DenseComponent component) {
            if (dense.containsKey(component.id()) || sparse.containsKey(component.id())) {
                throw new IllegalArgumentException("Duplicate component " + component.id());
            }
            component.freeze();
            DenseStore store = new DenseStore(component);
            dense.put(component.id(), store);
            denseByComponent.put(component, store);
        }

        @Override
        public void component(SparseComponent<?> component) {
            if (dense.containsKey(component.id()) || sparse.containsKey(component.id())) {
                throw new IllegalArgumentException("Duplicate component " + component.id());
            }
            SparseStore<?> store = new SparseStore<>(component);
            sparse.put(component.id(), store);
            sparseByComponent.put(component, store);
        }

        @Override
        public void task(TaskType type) {
            if (tasks.putIfAbsent(type.id(), type) != null) throw new IllegalArgumentException("Duplicate task " + type.id());
        }

        @Override
        public <T> void registry(RegistryKey<T> key) {
            if (registries.containsKey(key.id())) throw new IllegalArgumentException("Duplicate registry " + key.id());
            registries.put(key.id(), SimRegistryImpl.parse(key, data.load(key.folder()), problems::add));
            registryKeys.put(key.id(), key);
        }

        @Override
        public void activity(Activity activity) {
            if (activities.putIfAbsent(activity.id(), activity) != null) {
                throw new IllegalArgumentException("Duplicate activity " + activity.id());
            }
        }

        @Override
        public <E extends SimEvent> void subscribe(Class<E> type, EventHandler<E> handler) {
            handlers.computeIfAbsent(type, k -> new ArrayList<>()).add(handler);
        }

        @Override
        public <T> void view(ViewKey<T> key, ViewProvider<T> provider) {
            if (views.putIfAbsent(key, provider) != null) throw new IllegalArgumentException("Duplicate view " + key.id());
        }

        @Override
        public void function(ExpressionFunction function) {
            logic.function(function);
        }

        @Override
        public void condition(Id type, LogicFactories.ConditionFactory factory) {
            logic.condition(type, factory);
        }

        @Override
        public void effect(Id type, LogicFactories.EffectFactory factory) {
            logic.effect(type, factory);
        }

        @Override
        public void stat(StatType stat) {
            stats.register(stat);
        }

        @Override
        public void validator(Validator validator) {
            validators.add(validator);
        }
    }

    // ------------------------------------------------------------------------------------------------ driving

    /** Runs every task due at or before {@code time}, then sets the clock to {@code time}. */
    public void advanceTo(long time) {
        Scheduler.Task t;
        while ((t = scheduler.peek()) != null && t.time() <= time) {
            scheduler.poll();
            now = t.time();
            t.type().handler().run(this, new EntityId(t.target()), t.arg());
        }
        if (time > now) now = time;
    }

    public void apply(SimCommand command) {
        command.apply(this);
    }

    public <T> T query(SimQuery<T> query) {
        return query.run(this);
    }

    public SimViewsImpl snapshotViews() {
        Map<ViewKey<?>, Object> out = new LinkedHashMap<>();
        views.forEach((key, provider) -> out.put(key, provider.snapshot(this)));
        return new SimViewsImpl(now, out);
    }

    public List<SimModule> modules() {
        return modules;
    }

    public DataSource data() {
        return data;
    }

    public int entityCount() {
        return entities.count();
    }

    public int pendingTasks() {
        return scheduler.size();
    }

    // ------------------------------------------------------------------------------------------------ SimContext

    @Override
    public long now() {
        return now;
    }

    @Override
    public EntityId create() {
        return entities.allocate();
    }

    @Override
    public void destroy(EntityId entity) {
        if (!entities.isAlive(entity)) return;
        int i = entity.index();
        for (DenseStore s : dense.values()) s.remove(i);
        for (SparseStore<?> s : sparse.values()) s.remove(i);
        entities.free(entity);
    }

    @Override
    public boolean alive(EntityId entity) {
        return entities.isAlive(entity);
    }

    private DenseStore store(DenseComponent c) {
        DenseStore s = denseByComponent.get(c);
        if (s == null) throw new IllegalArgumentException("Component " + c.id() + " is not registered");
        return s;
    }

    @SuppressWarnings("unchecked")
    private <T> SparseStore<T> store(SparseComponent<T> c) {
        SparseStore<?> s = sparseByComponent.get(c);
        if (s == null) throw new IllegalArgumentException("Component " + c.id() + " is not registered");
        return (SparseStore<T>) s;
    }

    private int live(EntityId e) {
        if (!entities.isAlive(e)) throw new IllegalArgumentException("Entity " + e + " is not alive");
        return e.index();
    }

    private int with(EntityId e, DenseComponent c, DenseStore s) {
        int i = live(e);
        if (!s.has(i)) throw new IllegalArgumentException("Entity " + e + " has no " + c.id());
        return i;
    }

    @Override
    public void add(EntityId entity, DenseComponent component) {
        store(component).add(live(entity));
    }

    @Override
    public boolean has(EntityId entity, DenseComponent component) {
        return entities.isAlive(entity) && store(component).has(entity.index());
    }

    @Override
    public void remove(EntityId entity, DenseComponent component) {
        if (entities.isAlive(entity)) store(component).remove(entity.index());
    }

    @Override
    public float get(EntityId entity, FloatField field) {
        DenseStore s = store(field.component());
        return s.getFloat(field.slot(), with(entity, field.component(), s));
    }

    @Override
    public void set(EntityId entity, FloatField field, float value) {
        DenseStore s = store(field.component());
        s.setFloat(field.slot(), with(entity, field.component(), s), value);
    }

    @Override
    public long get(EntityId entity, LongField field) {
        DenseStore s = store(field.component());
        return s.getLong(field.slot(), with(entity, field.component(), s));
    }

    @Override
    public void set(EntityId entity, LongField field, long value) {
        DenseStore s = store(field.component());
        s.setLong(field.slot(), with(entity, field.component(), s), value);
    }

    @Override
    public int get(EntityId entity, IntField field) {
        DenseStore s = store(field.component());
        return s.getInt(field.slot(), with(entity, field.component(), s));
    }

    @Override
    public void set(EntityId entity, IntField field, int value) {
        DenseStore s = store(field.component());
        s.setInt(field.slot(), with(entity, field.component(), s), value);
    }

    @Override
    public List<EntityId> with(DenseComponent component) {
        DenseStore s = store(component);
        List<EntityId> out = new ArrayList<>();
        for (int i = s.present().nextSetBit(0); i >= 0; i = s.present().nextSetBit(i + 1)) out.add(entities.idOf(i));
        return out;
    }

    @Override
    public <T> T get(EntityId entity, SparseComponent<T> component) {
        return entities.isAlive(entity) ? store(component).get(entity.index()) : null;
    }

    @Override
    public <T> void set(EntityId entity, SparseComponent<T> component, T value) {
        store(component).set(live(entity), value);
    }

    @Override
    public boolean has(EntityId entity, SparseComponent<?> component) {
        return entities.isAlive(entity) && store(component).has(entity.index());
    }

    @Override
    public void remove(EntityId entity, SparseComponent<?> component) {
        if (entities.isAlive(entity)) store(component).remove(entity.index());
    }

    @Override
    public <T> void forEach(SparseComponent<T> component, BiConsumer<EntityId, T> action) {
        // Copy first so the action may modify the component.
        for (var entry : new ArrayList<>(store(component).values().int2ObjectEntrySet())) {
            action.accept(entities.idOf(entry.getIntKey()), entry.getValue());
        }
    }

    @Override
    public void schedule(long time, TaskType type, EntityId target, long arg) {
        if (time < now) throw new IllegalArgumentException("Can't schedule in the past: " + time + " < " + now);
        if (!tasks.containsKey(type.id())) throw new IllegalArgumentException("Task " + type.id() + " is not registered");
        scheduler.schedule(time, type, target.raw(), arg);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void publish(SimEvent event) {
        List<EventHandler<?>> list = handlers.get(event.getClass());
        if (list == null) return;
        for (EventHandler h : list) h.handle(this, event);
    }

    @Override
    public EventLog events() {
        return eventLog;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> SimRegistry<T> registry(RegistryKey<T> key) {
        SimRegistry<?> r = registries.get(key.id());
        if (r == null) throw new IllegalArgumentException("Registry " + key.id() + " is not registered");
        return (SimRegistry<T>) r;
    }

    @Override
    public Activity activity(Id id) {
        Activity a = activities.get(id);
        if (a == null) throw new IllegalArgumentException("No activity " + id);
        return a;
    }

    public boolean hasActivity(Id id) {
        return activities.containsKey(id);
    }

    @Override
    public Logic logic() {
        return logic;
    }

    @Override
    public Stats stats() {
        return stats;
    }

    // ------------------------------------------------------------------------------------------------ state

    /**
     * A hash of the whole world state: clock, entities, every component value and every pending task. Used by
     * determinism and save/load tests. Equal hashes mean equal state.
     */
    public long stateHash() {
        Hasher h = new Hasher();
        h.add(now);
        h.add(entities.indexLimit());
        for (int i = 1; i < entities.indexLimit(); i++) {
            h.add(entities.aliveAt(i) ? 1 : 0);
            h.add(entities.generation(i));
        }
        for (DenseStore s : dense.values()) {
            h.add(s.component().id().toString());
            for (int i = s.present().nextSetBit(0); i >= 0; i = s.present().nextSetBit(i + 1)) {
                h.add(i);
                for (int f = 0; f < s.component().floatNames().size(); f++) h.add(Float.floatToIntBits(s.getFloat(f, i)));
                for (int f = 0; f < s.component().longNames().size(); f++) h.add(s.getLong(f, i));
                for (int f = 0; f < s.component().intNames().size(); f++) h.add(s.getInt(f, i));
            }
        }
        for (SparseStore<?> s : sparse.values()) {
            h.add(s.component().id().toString());
            hashSparse(h, s);
        }
        for (Scheduler.Task t : scheduler.ordered()) {
            h.add(t.time());
            h.add(t.priority());
            h.add(t.seq());
            h.add(t.type().id().toString());
            h.add(t.target());
            h.add(t.arg());
        }
        h.add(eventLog.size());
        return h.value;
    }

    private static <T> void hashSparse(Hasher h, SparseStore<T> s) {
        for (var e : s.values().int2ObjectEntrySet()) {
            h.add(e.getIntKey());
            h.add(encodeJson(s.component(), e.getValue()));
        }
    }

    static <T> String encodeJson(SparseComponent<T> c, T value) {
        return c.codec().encodeStart(JsonOps.INSTANCE, value).result()
                .orElseThrow(() -> new IllegalStateException("Can't encode " + c.id()))
                .toString();
    }

    private static final class Hasher {
        long value = 0xCBF29CE484222325L;

        void add(long v) {
            value = com.ewitulsk.villagersimulator.api.sim.SimRandom.mix(value ^ v) * 31;
        }

        void add(String s) {
            for (byte b : s.getBytes(StandardCharsets.UTF_8)) add(b);
        }
    }

    // ------------------------------------------------------------------------------------------------ persistence

    /** A saved snapshot: world bytes plus event records newer than the last saved one. */
    public record Snapshot(long time, byte[] data, List<EventRecord> newEvents) {}

    /** Takes a snapshot; call on the thread that owns the world. */
    public Snapshot snapshot(long lastSavedEventId) {
        return new Snapshot(now, SnapshotCodec.write(this), eventLog.after(lastSavedEventId));
    }

    /** Restores a snapshot into this freshly built world. */
    public void restore(byte[] data, List<EventRecord> events) {
        if (entities.count() != 0 || scheduler.size() != 0) throw new IllegalStateException("Restore into a fresh world only");
        SnapshotCodec.read(this, data);
        for (EventRecord r : events) eventLog.add(r);
    }

    void setNow(long time) {
        this.now = time;
    }
}
