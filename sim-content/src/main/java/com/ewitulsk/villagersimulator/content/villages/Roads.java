package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A village's road graph (docs/DESIGN.md §4.8): nodes at the village centre and each building's entrance. Each node
 * links to its {@link #NEIGHBOURS} nearest neighbours, plus a minimum spanning tree so everything is connected; that
 * keeps routes close to straight instead of zig-zagging through the whole village. Villagers away from players
 * travel along it, so travel times and plan positions follow the roads.
 */
public final class Roads {
    /**
     * Nodes as flattened {@code x, y, z}; edges as flattened node index pairs. Primitive arrays plus a prebuilt
     * adjacency list, since routes are computed on every walk (docs/ROADMAP.md Phase 6).
     */
    public static final class Graph {
        public static final Codec<Graph> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.listOf().fieldOf("nodes").forGetter(Graph::nodes),
                Codec.INT.listOf().fieldOf("edges").forGetter(Graph::edges)
        ).apply(i, (n, e) -> new Graph(n.stream().mapToDouble(Double::doubleValue).toArray(), e.stream().mapToInt(Integer::intValue).toArray())));

        private final double[] xyz;
        private final int[] edges;
        private final int[][] adjacent;
        /** Shortest-path trees (predecessor per node) by start node, computed on first use. */
        private final java.util.concurrent.atomic.AtomicReferenceArray<int[]> trees;

        public Graph(double[] xyz, int[] edges) {
            this.xyz = xyz;
            this.edges = edges;
            int n = xyz.length / 3;
            int[] degree = new int[n];
            for (int i = 0; i + 1 < edges.length; i += 2) {
                degree[edges[i]]++;
                degree[edges[i + 1]]++;
            }
            trees = new java.util.concurrent.atomic.AtomicReferenceArray<>(n);
            adjacent = new int[n][];
            for (int i = 0; i < n; i++) adjacent[i] = new int[degree[i]];
            int[] fill = new int[n];
            for (int i = 0; i + 1 < edges.length; i += 2) {
                adjacent[edges[i]][fill[edges[i]]++] = edges[i + 1];
                adjacent[edges[i + 1]][fill[edges[i + 1]]++] = edges[i];
            }
        }

        public List<Double> nodes() {
            return Arrays.stream(xyz).boxed().toList();
        }

        public List<Integer> edges() {
            return Arrays.stream(edges).boxed().toList();
        }

        public int size() {
            return xyz.length / 3;
        }

        double[] node(int i) {
            return new double[]{xyz[3 * i], xyz[3 * i + 1], xyz[3 * i + 2]};
        }

        /** Predecessors on shortest paths from {@code start} (Dijkstra; graphs are small, one node per building). */
        int[] tree(int start) {
            int[] prev = trees.get(start);
            if (prev != null) return prev;
            int n = size();
            double[] d = new double[n];
            prev = new int[n];
            boolean[] done = new boolean[n];
            Arrays.fill(d, Double.MAX_VALUE);
            Arrays.fill(prev, -1);
            d[start] = 0;
            for (int k = 0; k < n; k++) {
                int u = -1;
                for (int i = 0; i < n; i++) if (!done[i] && d[i] < Double.MAX_VALUE && (u < 0 || d[i] < d[u])) u = i;
                if (u < 0) break;
                done[u] = true;
                for (int v : adjacent[u]) {
                    double nd = d[u] + dist(u, v);
                    if (nd < d[v]) {
                        d[v] = nd;
                        prev[v] = u;
                    }
                }
            }
            trees.compareAndSet(start, null, prev);
            return trees.get(start);
        }

        double dist(int a, int b) {
            double dx = xyz[3 * a] - xyz[3 * b], dy = xyz[3 * a + 1] - xyz[3 * b + 1], dz = xyz[3 * a + 2] - xyz[3 * b + 2];
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        double dist(int a, double[] p) {
            double dx = xyz[3 * a] - p[0], dy = xyz[3 * a + 1] - p[1], dz = xyz[3 * a + 2] - p[2];
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Graph g && Arrays.equals(xyz, g.xyz) && Arrays.equals(edges, g.edges);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(xyz) + Arrays.hashCode(edges);
        }
    }

    public static final SparseComponent<Graph> COMPONENT = new SparseComponent<>(VS.id("roads"), 1, Graph.CODEC);
    /** Trips shorter than this skip the roads and walk straight there. */
    public static final double DIRECT = 10;
    /** Road links from each node to its nearest neighbours. */
    public static final int NEIGHBOURS = 4;

    private Roads() {}

    /** Builds the graph for a village from its centre and buildings. */
    public static Graph build(SimContext ctx, double[] centre, List<EntityId> buildings) {
        List<double[]> points = new ArrayList<>();
        points.add(centre);
        for (EntityId b : buildings) points.add(entrance(ctx, b));
        int n = points.size();
        List<Integer> edges = new ArrayList<>();
        // Prim's minimum spanning tree.
        boolean[] in = new boolean[n];
        double[] best = new double[n];
        int[] from = new int[n];
        Arrays.fill(best, Double.MAX_VALUE);
        best[0] = 0;
        for (int k = 0; k < n; k++) {
            int u = -1;
            for (int i = 0; i < n; i++) if (!in[i] && (u < 0 || best[i] < best[u])) u = i;
            in[u] = true;
            if (k > 0) {
                edges.add(from[u]);
                edges.add(u);
            }
            for (int v = 0; v < n; v++) {
                double d = dist(points.get(u), points.get(v));
                if (!in[v] && d < best[v]) {
                    best[v] = d;
                    from[v] = u;
                }
            }
        }
        java.util.Set<Long> linked = new java.util.HashSet<>();
        for (int i = 0; i + 1 < edges.size(); i += 2) linked.add(key(edges.get(i), edges.get(i + 1)));
        for (int u = 0; u < n; u++) {
            final int from0 = u;
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            Arrays.sort(order, java.util.Comparator.comparingDouble(i -> dist(points.get(from0), points.get(i))));
            for (int k = 1, added = 0; k < n && added < NEIGHBOURS; k++, added++) {
                int v = order[k];
                if (linked.add(key(u, v))) {
                    edges.add(u);
                    edges.add(v);
                }
            }
        }
        double[] flat = new double[n * 3];
        for (int i = 0; i < n; i++) System.arraycopy(points.get(i), 0, flat, 3 * i, 3);
        return new Graph(flat, edges.stream().mapToInt(Integer::intValue).toArray());
    }

    /** Where a building meets the road: its first wander point (outside the door), or the front of its footprint. */
    static double[] entrance(SimContext ctx, EntityId building) {
        BuildingType t = Buildings.type(ctx, building);
        if (!t.points(BuildingType.WANDER).isEmpty()) return Buildings.point(ctx, building, BuildingType.WANDER, 0);
        return Buildings.point(ctx, building, "entrance", 0);
    }

    /**
     * Waypoints from {@code a} to {@code b} along the roads, excluding both ends (empty for short trips or villages
     * without roads).
     */
    public static List<double[]> route(SimContext ctx, EntityId village, double[] a, double[] b) {
        Graph g = village.isNone() ? null : ctx.get(village, COMPONENT);
        if (g == null || g.size() < 2 || dist(a, b) < DIRECT) return List.of();
        int start = nearest(g, a), goal = nearest(g, b);
        if (start == goal) return List.of();
        int[] prev = g.tree(start);
        if (prev[goal] < 0) return List.of();
        List<double[]> path = new ArrayList<>();
        for (int v = goal; v >= 0; v = prev[v]) path.add(0, g.node(v));
        return path;
    }

    private static long key(int a, int b) {
        return ((long) Math.min(a, b) << 32) | Math.max(a, b);
    }

    private static int nearest(Graph g, double[] p) {
        int best = 0;
        double bestD = g.dist(0, p);
        for (int i = 1; i < g.size(); i++) {
            double d = g.dist(i, p);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
