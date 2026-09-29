package com.ewitulsk.villagersimulator.bench;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprType;
import com.ewitulsk.villagersimulator.api.sim.expr.Expression;
import com.ewitulsk.villagersimulator.api.sim.task.TaskType;
import com.ewitulsk.villagersimulator.content.needs.Needs;
import com.ewitulsk.villagersimulator.content.plans.Choices;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.core.storage.RelationshipGraph;
import com.ewitulsk.villagersimulator.core.storage.Scheduler;
import com.ewitulsk.villagersimulator.harness.Scenario;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH micro-benchmarks for the sim's hot paths (docs/ROADMAP.md Phase 6): the event queue, dense component access,
 * compiled expressions, the relationship graph and a utility-AI choice. Run with {@code ./gradlew :sim-bench:jmh}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class HotPathBenchmarks {
    private static final TaskType NOOP = new TaskType(Id.of("bench", "noop"), 0, (ctx, e, arg) -> {});

    /** A queue holding 100k pending tasks, like a large shard. */
    @State(Scope.Thread)
    public static class Queue {
        Scheduler scheduler;
        long time;

        @Setup(Level.Trial)
        public void setup() {
            scheduler = new Scheduler();
            for (int i = 0; i < 100_000; i++) scheduler.schedule(i * 7L % 24_000, NOOP, i, 0);
            time = 24_000;
        }
    }

    /** Poll the earliest task and schedule a replacement: the steady-state cost of one task. */
    @Benchmark
    public Object schedulerPollAndSchedule(Queue q) {
        Scheduler.Task t = q.scheduler.poll();
        q.scheduler.schedule(t.time() + 24_000, NOOP, t.target(), 0);
        return t;
    }

    /** A hamlet-sized world for component, expression and choice benchmarks. */
    @State(Scope.Thread)
    public static class World {
        Scenario scenario;
        List<EntityId> villagers;
        Expression expression;
        ExprEnv env;
        double[] from;
        int next;

        @Setup(Level.Trial)
        public void setup() {
            scenario = Scenario.start();
            EntityId town = scenario.spawnTown("Benchford", 7, 200, 0, 0);
            scenario.warp(3_000); // mid-morning
            villagers = scenario.village(town).residents();
            expression = scenario.world().logic().expression(
                    "(hour() < 9 || (hour() >= 11.5 && hour() < 14)) && need('hunger') < 70 ? 150 : -40", ExprType.NUMBER);
            env = ExprEnv.of(scenario.world(), villagers.get(0), EntityId.NONE, new double[]{0, 64, 0});
            from = Plans.positionNow(scenario.world(), villagers.get(0));
        }

        EntityId nextVillager() {
            next = (next + 1) % villagers.size();
            return villagers.get(next);
        }
    }

    @Benchmark
    public float needValue(World w) {
        return Needs.HUNGER.value(w.scenario.world(), w.nextVillager());
    }

    @Benchmark
    public double compiledExpression(World w) {
        return w.expression.number(w.env);
    }

    /** One utility-AI decision over every advertisement in a 200-villager town (~85 buildings). */
    @Benchmark
    public Object choose(World w) {
        EntityId v = w.nextVillager();
        return Choices.choose(w.scenario.world(), v, w.from);
    }

    /** A graph with 10k villagers and 30 ties each. */
    @State(Scope.Thread)
    public static class Graph {
        RelationshipGraph graph;
        RelationshipGraph.View view;
        EntityId[] ids;
        int next;

        @Setup(Level.Trial)
        public void setup() {
            graph = new RelationshipGraph(e -> true);
            view = graph.view(() -> 0);
            ids = new EntityId[10_000];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = new EntityId(i + 1);
                graph.reserve(i + 1);
            }
            for (int i = 0; i < ids.length; i++) {
                for (int k = 1; k <= 15; k++) view.changeFriendship(ids[i], ids[(i + k * 37) % ids.length], k);
            }
        }
    }

    @Benchmark
    public float friendshipLookup(Graph g) {
        g.next = (g.next + 1) % g.ids.length;
        return g.view.friendship(g.ids[g.next], g.ids[(g.next + 5 * 37) % g.ids.length]);
    }

    @Benchmark
    public void friendshipChange(Graph g) {
        g.next = (g.next + 1) % g.ids.length;
        g.view.changeFriendship(g.ids[g.next], g.ids[(g.next + 3 * 37) % g.ids.length], 0.5f);
    }

    @Benchmark
    public void relationsForEach(Graph g, Blackhole bh) {
        g.next = (g.next + 1) % g.ids.length;
        g.view.forEach(g.ids[g.next], bh::consume);
    }
}
