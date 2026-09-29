package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;
import com.ewitulsk.villagersimulator.api.sim.command.SimQuery;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.FloatField;
import com.ewitulsk.villagersimulator.api.sim.component.IntField;
import com.ewitulsk.villagersimulator.api.sim.component.LongField;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.core.CoreComponents;
import com.ewitulsk.villagersimulator.api.sim.core.DataReloaded;
import com.ewitulsk.villagersimulator.api.sim.event.EventHandler;
import com.ewitulsk.villagersimulator.api.sim.event.EventLog;
import com.ewitulsk.villagersimulator.api.sim.event.EventRecord;
import com.ewitulsk.villagersimulator.api.sim.event.SimEvent;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.api.sim.logic.Logic;
import com.ewitulsk.villagersimulator.api.sim.logic.LogicFactories;
import com.ewitulsk.villagersimulator.api.sim.module.ExtensionPoint;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.api.sim.module.Validator;
import com.ewitulsk.villagersimulator.api.sim.registry.RegistryKey;
import com.ewitulsk.villagersimulator.api.sim.registry.SimRegistry;
import com.ewitulsk.villagersimulator.api.sim.social.Relationships;
import com.ewitulsk.villagersimulator.api.sim.stat.StatType;
import com.ewitulsk.villagersimulator.api.sim.stat.Stats;
import com.ewitulsk.villagersimulator.api.sim.task.TaskType;
import com.ewitulsk.villagersimulator.api.sim.view.ViewKey;
import com.ewitulsk.villagersimulator.api.sim.view.ViewProvider;
import com.ewitulsk.villagersimulator.core.data.DataSource;
import com.ewitulsk.villagersimulator.core.logic.LogicImpl;
import com.ewitulsk.villagersimulator.core.logic.StatsImpl;
import com.ewitulsk.villagersimulator.core.storage.DenseStore;
import com.ewitulsk.villagersimulator.core.storage.EntityAllocator;
import com.ewitulsk.villagersimulator.core.storage.RelationshipGraph;
import com.ewitulsk.villagersimulator.core.storage.Scheduler;
import com.ewitulsk.villagersimulator.core.storage.SparseStore;
import com.mojang.serialization.JsonOps;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/**
 * One simulation: entities, components, the event queues, registries and modules.
 *
 * <p><b>Shards and windows</b> (docs/ARCHITECTURE.md §6.3). Every entity belongs to a shard (in practice one per
 * village; shard 0 is the world). Time advances in windows of {@link #WINDOW} ticks. Within a window, each shard
 * runs its own events up to the window's end, in parallel on {@code threads} workers, through a {@link ShardContext}
 * that owns that shard's entities: writes to other shards are captured and applied at the boundary in shard order,
 * and new event records are merged in (time, shard) order. So the result never depends on the number of threads.
 *
 * <p>Outside windows (commands, queries, views) the world itself is the context and owns everything.
 */
public final class SimWorld implements SimContext, AutoCloseable {
    /** Format version of {@link #snapshot} data. */
    public static final int SNAPSHOT_FORMAT = SnapshotCodec.FORMAT;
    /** Length of a synchronisation window in ticks; also the delay of cross-shard effects. Divides a day. */
    public static final long WINDOW = 200;
    /** Saved event records older than this (3 sim-days) are dropped from memory at the next snapshot. */
    public static final long DEFAULT_EVENT_RETENTION = 3 * 24_000L;

    private final List<SimModule> modules;
    final Map<Id, DenseStore> dense = new LinkedHashMap<>();
    private final Map<DenseComponent, DenseStore> denseByComponent = new IdentityHashMap<>();
    final Map<Id, SparseStore<?>> sparse = new LinkedHashMap<>();
    private final Map<SparseComponent<?>, SparseStore<?>> sparseByComponent = new IdentityHashMap<>();
    // The same, indexed by component serial: store lookup is on every component access.
    private DenseStore[] denseBySerial = new DenseStore[0];
    private SparseStore<?>[] sparseBySerial = new SparseStore<?>[0];
    final Map<Id, TaskType> tasks = new LinkedHashMap<>();
    private final Map<Id, Activity> activities = new LinkedHashMap<>();
    private final Map<Id, SimRegistryImpl<?>> registries = new LinkedHashMap<>();
    private final Map<Id, RegistryKey<?>> registryKeys = new LinkedHashMap<>();
    private final LogicImpl logic = new LogicImpl();
    private final Map<Id, StatType> statTypes = new LinkedHashMap<>();
    private final StatsImpl stats = new StatsImpl(this, statTypes);
    private final List<Validator> validators = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final Map<Id, List<Object>> extensions = new LinkedHashMap<>();
    final RelationshipGraph relationships = new RelationshipGraph(this::alive);
    private final Relationships relationshipView = relationships.view(this::now);
    private final Map<Class<?>, List<EventHandler<?>>> handlers = new HashMap<>();
    private final Map<ViewKey<?>, ViewProvider<?>> views = new LinkedHashMap<>();
    final EntityAllocator entities = new EntityAllocator();
    /** Event queues, one per shard. */
    final List<Scheduler> queues = new ArrayList<>(List.of(new Scheduler()));
    /** Shard of each entity index. */
    int[] shardOf = new int[64];
    private final List<ShardContext> contexts = new ArrayList<>();
    final SimEventLog eventLog = new SimEventLog();
    private final SimEventLog.Writer worldEvents = eventLog.new Writer(0, this::now, true);
    /** Sections of a loaded save that no registered component claims (e.g. a removed addon); written back as-is. */
    final Map<Id, byte[]> unknownSections = new LinkedHashMap<>();
    private final int threads;
    /** How long saved event records stay in memory (docs/ROADMAP.md Phase 6). */
    private long eventRetention = DEFAULT_EVENT_RETENTION;
    private ExecutorService pool;
    private DataSource data;
    private long now;
    // Profiling (SimProfile): engine phases and views, measured on the thread that owns the world.
    private final Map<String, long[]> engineProfile = new LinkedHashMap<>();
    private long profileTicks, profileWall;

    private SimWorld(List<SimModule> modules, DataSource data, int threads) {
        this.modules = modules;
        this.data = data;
        this.threads = Math.max(1, threads);
        contexts.add(new ShardContext(0));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<SimModule> modules = new ArrayList<>();
        private DataSource data = folder -> Map.of();
        private long startTime;
        private int threads = 1;
        private long eventRetention = DEFAULT_EVENT_RETENTION;

        /** How long saved event records stay in memory; older ones only live in the save. */
        public Builder eventRetention(long ticks) {
            this.eventRetention = ticks;
            return this;
        }

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

        /** Worker threads for parallel windows. Results are identical for any value. */
        public Builder threads(int threads) {
            this.threads = threads;
            return this;
        }

        public SimWorld build() {
            SimWorld world = new SimWorld(List.copyOf(sortModules(modules)), data, threads);
            world.now = startTime;
            world.eventRetention = eventRetention;
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
            if (component.serial() >= denseBySerial.length) denseBySerial = Arrays.copyOf(denseBySerial, component.serial() + 16);
            denseBySerial[component.serial()] = store;
        }

        @Override
        public void component(SparseComponent<?> component) {
            if (dense.containsKey(component.id()) || sparse.containsKey(component.id())) {
                throw new IllegalArgumentException("Duplicate component " + component.id());
            }
            SparseStore<?> store = new SparseStore<>(component);
            sparse.put(component.id(), store);
            sparseByComponent.put(component, store);
            if (component.serial() >= sparseBySerial.length) sparseBySerial = Arrays.copyOf(sparseBySerial, component.serial() + 16);
            sparseBySerial[component.serial()] = store;
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

        @Override
        public <T> void extend(ExtensionPoint<T> point, T value) {
            if (!point.type().isInstance(value)) throw new IllegalArgumentException(value + " is not a " + point.type().getName());
            extensions.computeIfAbsent(point.id(), k -> new ArrayList<>()).add(value);
        }
    }

    // ------------------------------------------------------------------------------------------------ driving

    /**
     * Runs every task due at or before {@code time}, window by window, then sets the clock to {@code time}.
     * Windows end on multiples of {@link #WINDOW} (and at {@code time}).
     */
    public void advanceTo(long time) {
        long start = System.nanoTime(), from = now;
        while (true) {
            long end = Math.min(time, (Math.floorDiv(now, WINDOW) + 1) * WINDOW);
            runWindow(end);
            if (end > now) now = end;
            if (end >= time) break;
        }
        profileTicks += Math.max(0, now - from);
        profileWall += System.nanoTime() - start;
    }

    private void profileEngine(String name, long nanos) {
        long[] a = engineProfile.computeIfAbsent(name, k -> new long[2]);
        a[0]++;
        a[1] += nanos;
    }

    /** Where time went since the last {@link #resetProfile()}. */
    public SimProfile profile() {
        Map<String, long[]> tasksByName = new java.util.TreeMap<>();
        for (ShardContext c : contexts) {
            c.profile.forEach((type, a) -> {
                long[] t = tasksByName.computeIfAbsent(type.id().toString(), k -> new long[2]);
                t[0] += a[0];
                t[1] += a[1];
            });
        }
        List<SimProfile.Entry> out = new ArrayList<>();
        tasksByName.forEach((n, a) -> out.add(new SimProfile.Entry("task", n, a[0], a[1])));
        engineProfile.forEach((n, a) -> out.add(new SimProfile.Entry(n.startsWith("view ") ? "view" : "engine",
                n.startsWith("view ") ? n.substring(5) : n, a[0], a[1])));
        return new SimProfile(profileTicks, profileWall, List.copyOf(out));
    }

    public void resetProfile() {
        for (ShardContext c : contexts) c.profile.clear();
        engineProfile.clear();
        profileTicks = 0;
        profileWall = 0;
    }

    private void runWindow(long end) {
        while (true) {
            List<ShardContext> active = new ArrayList<>();
            for (int s = 0; s < queues.size(); s++) {
                Scheduler.Task t = queues.get(s).peek();
                if (t != null && t.time() <= end) active.add(contexts.get(s));
            }
            if (active.isEmpty()) return;
            long t0 = System.nanoTime();
            if (threads == 1 || active.size() == 1) {
                for (ShardContext c : active) c.run(end);
            } else {
                if (pool == null) pool = Executors.newFixedThreadPool(threads, r -> {
                    Thread t = new Thread(r, "VillagerSim-Worker");
                    t.setDaemon(true);
                    return t;
                });
                List<Callable<Void>> jobs = new ArrayList<>();
                for (ShardContext c : active) jobs.add(() -> {
                    c.run(end);
                    return null;
                });
                try {
                    for (Future<Void> f : pool.invokeAll(jobs)) f.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while simulating", e);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException re) throw re;
                    if (cause instanceof Error err) throw err;
                    throw new IllegalStateException(cause);
                }
            }
            long t1 = System.nanoTime();
            profileEngine("shards (wall)", t1 - t0);
            // Boundary: apply deferred cross-shard writes in shard order, then merge event records.
            long saved = now;
            now = end;
            for (ShardContext c : contexts) c.flushDeferred();
            List<SimEventLog.Writer> writers = new ArrayList<>(contexts.size());
            for (ShardContext c : contexts) writers.add(c.events);
            eventLog.merge(writers);
            now = Math.max(saved, now);
            profileEngine("boundary", System.nanoTime() - t1);
        }
    }

    public void apply(SimCommand command) {
        command.apply(this);
    }

    public <T> T query(SimQuery<T> query) {
        return query.run(this);
    }

    public SimViewsImpl snapshotViews() {
        Map<ViewKey<?>, Object> out = new LinkedHashMap<>();
        views.forEach((key, provider) -> {
            long t = System.nanoTime();
            out.put(key, provider.snapshot(this));
            profileEngine("view " + key.id(), System.nanoTime() - t);
        });
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
        int n = 0;
        for (Scheduler q : queues) n += q.size();
        return n;
    }

    public int shardCount() {
        return queues.size();
    }

    @Override
    public void close() {
        if (pool != null) pool.shutdownNow();
    }

    // ------------------------------------------------------------------------------------------------ storage access

    private DenseStore store(DenseComponent c) {
        int k = c.serial();
        DenseStore s = k < denseBySerial.length ? denseBySerial[k] : null;
        if (s == null) throw new IllegalArgumentException("Component " + c.id() + " is not registered");
        return s;
    }

    @SuppressWarnings("unchecked")
    private <T> SparseStore<T> store(SparseComponent<T> c) {
        int k = c.serial();
        SparseStore<?> s = k < sparseBySerial.length ? sparseBySerial[k] : null;
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

    int shardIndex(EntityId e) {
        int i = e.index();
        return i > 0 && i < shardOf.length ? shardOf[i] : 0;
    }

    private void doSchedule(long time, TaskType type, EntityId target, long arg) {
        if (!tasks.containsKey(type.id())) throw new IllegalArgumentException("Task " + type.id() + " is not registered");
        queues.get(target.isNone() ? 0 : shardIndex(target)).schedule(time, type, target.raw(), arg);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doPublish(SimContext ctx, SimEvent event) {
        List<EventHandler<?>> list = handlers.get(event.getClass());
        if (list == null) return;
        for (EventHandler h : list) h.handle(ctx, event);
    }

    // ------------------------------------------------------------------------------------------------ SimContext (world)

    @Override
    public long now() {
        return now;
    }

    @Override
    public EntityId create() {
        EntityId e = entities.allocate();
        int i = e.index();
        if (i >= shardOf.length) shardOf = Arrays.copyOf(shardOf, Math.max(i + 1, shardOf.length * 2));
        shardOf[i] = 0;
        for (DenseStore s : dense.values()) s.reserve(i);
        relationships.reserve(i);
        return e;
    }

    @Override
    public void destroy(EntityId entity) {
        if (!entities.isAlive(entity)) return;
        int i = entity.index();
        for (DenseStore s : dense.values()) s.remove(i);
        for (SparseStore<?> s : sparse.values()) s.remove(i);
        relationships.forget(entity);
        entities.free(entity);
    }

    @Override
    public boolean alive(EntityId entity) {
        return entities.isAlive(entity);
    }

    @Override
    public int newShard() {
        queues.add(new Scheduler());
        contexts.add(new ShardContext(queues.size() - 1));
        eventLog.reserveShard(queues.size() - 1);
        return queues.size() - 1;
    }

    @Override
    public void setShard(EntityId entity, int shard) {
        int i = live(entity);
        if (shard < 0 || shard >= queues.size()) throw new IllegalArgumentException("No shard " + shard);
        int old = shardOf[i];
        if (old == shard) return;
        shardOf[i] = shard;
        for (Scheduler.Task t : queues.get(old).removeTarget(entity.raw())) queues.get(shard).schedule(t.time(), t.type(), t.target(), t.arg());
    }

    @Override
    public int shardOf(EntityId entity) {
        return shardIndex(entity);
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
        synchronized (s) {
            for (int i = s.present().nextSetBit(0); i >= 0; i = s.present().nextSetBit(i + 1)) out.add(entities.idOf(i));
        }
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
        for (Map.Entry<Integer, T> entry : store(component).sorted()) action.accept(entities.idOf(entry.getKey()), entry.getValue());
    }

    @Override
    public void schedule(long time, TaskType type, EntityId target, long arg) {
        if (time < now) throw new IllegalArgumentException("Can't schedule in the past: " + time + " < " + now);
        doSchedule(time, type, target, arg);
    }

    @Override
    public void publish(SimEvent event) {
        doPublish(this, event);
    }

    @Override
    public EventLog events() {
        return worldEvents;
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

    @Override
    public Relationships relationships() {
        return relationshipView;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> extensions(ExtensionPoint<T> point) {
        return (List<T>) Collections.unmodifiableList(extensions.getOrDefault(point.id(), List.of()));
    }

    // ------------------------------------------------------------------------------------------------ shard context

    /**
     * The sim as seen by one shard during a window. Reads go straight to storage; writes to entities this shard
     * doesn't own are deferred to the boundary; creating, destroying and re-sharding entities isn't allowed.
     */
    final class ShardContext implements SimContext {
        private final int shard;
        private final List<Runnable> deferred = new ArrayList<>();
        /** Per task type: calls, nanos. Only this shard's worker writes it. */
        final Map<TaskType, long[]> profile = new IdentityHashMap<>();
        final SimEventLog.Writer events;
        private final StatsImpl shardStats;
        private final Relationships shardRelationships;
        private long time;

        ShardContext(int shard) {
            this.shard = shard;
            this.events = eventLog.new Writer(shard, this::now, false);
            this.shardStats = stats.with(this);
            this.shardRelationships = relationships.view(this::now, i -> i > 0 && i < shardOf.length && shardOf[i] == shard,
                    deferred::add);
        }

        void run(long end) {
            Scheduler q = queues.get(shard);
            Scheduler.Task t;
            while ((t = q.peek()) != null && t.time() <= end) {
                q.poll();
                time = Math.max(t.time(), SimWorld.this.now);
                long start = System.nanoTime();
                t.type().handler().run(this, new EntityId(t.target()), t.arg());
                long[] a = profile.computeIfAbsent(t.type(), k -> new long[2]);
                a[0]++;
                a[1] += System.nanoTime() - start;
            }
        }

        void flushDeferred() {
            if (deferred.isEmpty()) return;
            List<Runnable> ops = new ArrayList<>(deferred);
            deferred.clear();
            for (Runnable r : ops) r.run();
        }

        private boolean owns(EntityId e) {
            return !e.isNone() && shardIndex(e) == shard;
        }

        private void write(EntityId e, Runnable op) {
            if (owns(e)) op.run();
            else deferred.add(op);
        }

        @Override
        public long now() {
            return time;
        }

        @Override
        public EntityId create() {
            throw new IllegalStateException("Entities are created by commands, not during a window");
        }

        @Override
        public void destroy(EntityId entity) {
            deferred.add(() -> SimWorld.this.destroy(entity));
        }

        @Override
        public boolean alive(EntityId entity) {
            return SimWorld.this.alive(entity);
        }

        @Override
        public int newShard() {
            throw new IllegalStateException("Shards are created by commands, not during a window");
        }

        @Override
        public void setShard(EntityId entity, int s) {
            deferred.add(() -> SimWorld.this.setShard(entity, s));
        }

        @Override
        public int shardOf(EntityId entity) {
            return shardIndex(entity);
        }

        @Override
        public void add(EntityId entity, DenseComponent component) {
            write(entity, () -> SimWorld.this.add(entity, component));
        }

        @Override
        public boolean has(EntityId entity, DenseComponent component) {
            return SimWorld.this.has(entity, component);
        }

        @Override
        public void remove(EntityId entity, DenseComponent component) {
            write(entity, () -> SimWorld.this.remove(entity, component));
        }

        @Override
        public float get(EntityId entity, FloatField field) {
            return SimWorld.this.get(entity, field);
        }

        @Override
        public void set(EntityId entity, FloatField field, float value) {
            write(entity, () -> SimWorld.this.set(entity, field, value));
        }

        @Override
        public long get(EntityId entity, LongField field) {
            return SimWorld.this.get(entity, field);
        }

        @Override
        public void set(EntityId entity, LongField field, long value) {
            write(entity, () -> SimWorld.this.set(entity, field, value));
        }

        @Override
        public int get(EntityId entity, IntField field) {
            return SimWorld.this.get(entity, field);
        }

        @Override
        public void set(EntityId entity, IntField field, int value) {
            write(entity, () -> SimWorld.this.set(entity, field, value));
        }

        @Override
        public List<EntityId> with(DenseComponent component) {
            List<EntityId> out = new ArrayList<>();
            for (EntityId e : SimWorld.this.with(component)) if (owns(e)) out.add(e);
            return out;
        }

        @Override
        public <T> T get(EntityId entity, SparseComponent<T> component) {
            return SimWorld.this.get(entity, component);
        }

        @Override
        public <T> void set(EntityId entity, SparseComponent<T> component, T value) {
            write(entity, () -> SimWorld.this.set(entity, component, value));
        }

        @Override
        public boolean has(EntityId entity, SparseComponent<?> component) {
            return SimWorld.this.has(entity, component);
        }

        @Override
        public void remove(EntityId entity, SparseComponent<?> component) {
            write(entity, () -> SimWorld.this.remove(entity, component));
        }

        /** Only this shard's entities: other shards may be changing theirs. */
        @Override
        public <T> void forEach(SparseComponent<T> component, BiConsumer<EntityId, T> action) {
            for (Map.Entry<Integer, T> entry : store(component).sorted()) {
                EntityId e = entities.idOf(entry.getKey());
                if (owns(e)) action.accept(e, entry.getValue());
            }
        }

        @Override
        public void schedule(long when, TaskType type, EntityId target, long arg) {
            if (when < time) throw new IllegalArgumentException("Can't schedule in the past: " + when + " < " + time);
            if (target.isNone() ? shard == 0 : owns(target)) doSchedule(when, type, target, arg);
            else deferred.add(() -> doSchedule(Math.max(when, SimWorld.this.now), type, target, arg));
        }

        @Override
        public void publish(SimEvent event) {
            doPublish(this, event);
        }

        @Override
        public EventLog events() {
            return events;
        }

        @Override
        public <T> SimRegistry<T> registry(RegistryKey<T> key) {
            return SimWorld.this.registry(key);
        }

        @Override
        public Activity activity(Id id) {
            return SimWorld.this.activity(id);
        }

        @Override
        public Logic logic() {
            return logic;
        }

        @Override
        public Stats stats() {
            return shardStats;
        }

        @Override
        public Relationships relationships() {
            return shardRelationships;
        }

        @Override
        public <T> List<T> extensions(ExtensionPoint<T> point) {
            return SimWorld.this.extensions(point);
        }
    }

    // ------------------------------------------------------------------------------------------------ state

    /**
     * A hash of the whole world state: clock, entities and shards, every component value, every pending task, the
     * relationship graph and the event log size. Used by determinism and save/load tests.
     */
    public long stateHash() {
        Hasher h = new Hasher();
        h.add(now);
        h.add(entities.indexLimit());
        for (int i = 1; i < entities.indexLimit(); i++) {
            h.add(entities.aliveAt(i) ? 1 : 0);
            h.add(entities.generation(i));
            h.add(i < shardOf.length ? shardOf[i] : 0);
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
        for (int q = 0; q < queues.size(); q++) {
            h.add(q);
            for (Scheduler.Task t : queues.get(q).ordered()) {
                h.add(t.time());
                h.add(t.priority());
                h.add(t.seq());
                h.add(t.type().id().toString());
                h.add(t.target());
                h.add(t.arg());
            }
        }
        relationships.hash(h::add);
        h.add(eventLog.size());
        return h.value;
    }

    private static <T> void hashSparse(Hasher h, SparseStore<T> s) {
        for (Map.Entry<Integer, T> e : s.sorted()) {
            h.add(e.getKey());
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
            value = SimRandom.mix(value ^ v) * 31;
        }

        void add(String s) {
            for (byte b : s.getBytes(StandardCharsets.UTF_8)) add(b);
        }
    }

    // ------------------------------------------------------------------------------------------------ persistence

    /** A saved snapshot: world bytes plus the event records not saved yet. */
    public record Snapshot(long time, byte[] data, List<EventRecord> newEvents) {}

    /**
     * Takes a snapshot; call on the thread that owns the world. {@code savedEvents} is how many event records are
     * already saved (the log's first {@code savedEvents} records).
     */
    public Snapshot snapshot(int savedEvents) {
        Snapshot s = new Snapshot(now, SnapshotCodec.write(this), eventLog.from(savedEvents));
        eventLog.forget(savedEvents, now - eventRetention);
        return s;
    }

    /** Records from absolute log position {@code index} on (see {@code EventLog.size()}), in log order. */
    public List<EventRecord> eventsSince(int index) {
        return eventLog.from(index);
    }

    /** Event records held in memory (the rest are only in the save). */
    public int retainedEvents() {
        return eventLog.retained();
    }

    public long eventRetention() {
        return eventRetention;
    }

    /** Restores a snapshot into this freshly built world. */
    public void restore(byte[] data, List<EventRecord> events) {
        restore(data, events, 0);
    }

    /** Restores a snapshot whose first {@code forgotten} event records were left in the save. */
    public void restore(byte[] data, List<EventRecord> events, int forgotten) {
        if (entities.count() != 0 || pendingTasks() != 0) throw new IllegalStateException("Restore into a fresh world only");
        eventLog.startAfter(forgotten);
        SnapshotCodec.read(this, data);
        for (EventRecord r : events) eventLog.add(r);
    }

    void setNow(long time) {
        this.now = time;
    }

    /** Restores shard assignments (snapshot loading). */
    void restoreShards(int count, int[] assignment) {
        while (queues.size() < count) newShard();
        shardOf = Arrays.copyOf(assignment, Math.max(64, assignment.length));
        for (int i = 0; i < entities.indexLimit(); i++) {
            for (DenseStore s : dense.values()) s.reserve(i);
            relationships.reserve(i);
        }
    }
}
