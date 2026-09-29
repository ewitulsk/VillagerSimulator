package com.ewitulsk.villagersimulator.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Where the sim spent its time since the profile was last reset (docs/ARCHITECTURE.md §18, {@code /vs profile}):
 * one entry per task type, view and engine phase. Task times are summed over worker threads, so with several
 * threads they can add up to more than the wall-clock time.
 *
 * @param simTicks sim ticks advanced
 * @param wallNanos wall-clock time spent advancing
 */
public record SimProfile(long simTicks, long wallNanos, List<Entry> entries) {
    /** @param kind "task", "view" or "engine" */
    public record Entry(String kind, String name, long calls, long nanos) {}

    public long tasks() {
        long n = 0;
        for (Entry e : entries) if (e.kind().equals("task")) n += e.calls();
        return n;
    }

    /** Entries sorted by time spent, most first. */
    public List<Entry> byTime() {
        List<Entry> out = new ArrayList<>(entries);
        out.sort(Comparator.comparingLong(Entry::nanos).reversed());
        return out;
    }

    /** Human-readable lines, most expensive first. */
    public List<String> lines(int max) {
        List<String> out = new ArrayList<>();
        double secs = wallNanos / 1e9;
        out.add(String.format(Locale.ROOT, "%,d sim ticks in %.2f s wall, %,d tasks (%,.0f/s)", simTicks, secs, tasks(),
                secs > 0 ? tasks() / secs : 0));
        for (Entry e : byTime()) {
            if (out.size() > max) break;
            out.add(String.format(Locale.ROOT, "%-6s %-40s %,12d calls %10.1f ms %8.2f us/call", e.kind(), e.name(),
                    e.calls(), e.nanos() / 1e6, e.calls() == 0 ? 0 : e.nanos() / 1e3 / e.calls()));
        }
        return out;
    }
}
