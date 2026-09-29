package com.ewitulsk.villagersimulator.core.storage;

import com.ewitulsk.villagersimulator.api.sim.task.TaskType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** The event queue of one shard. Order: time, then priority, then scheduling order (docs/ARCHITECTURE.md §6.2). */
public final class Scheduler {
    public record Task(long time, int priority, long seq, TaskType type, int target, long arg) {}

    public static final Comparator<Task> ORDER = Comparator.comparingLong(Task::time)
            .thenComparingInt(Task::priority)
            .thenComparingLong(Task::seq);

    private final PriorityQueue<Task> queue = new PriorityQueue<>(ORDER);
    private long nextSeq;

    public void schedule(long time, TaskType type, int target, long arg) {
        queue.add(new Task(time, type.priority(), nextSeq++, type, target, arg));
    }

    /** Re-adds a saved task, keeping its sequence number. */
    public void restore(Task task) {
        queue.add(task);
        nextSeq = Math.max(nextSeq, task.seq() + 1);
    }

    public Task peek() {
        return queue.peek();
    }

    public Task poll() {
        return queue.poll();
    }

    public int size() {
        return queue.size();
    }

    public long nextSeq() {
        return nextSeq;
    }

    public void setNextSeq(long seq) {
        nextSeq = Math.max(nextSeq, seq);
    }

    /** All pending tasks in execution order. */
    public List<Task> ordered() {
        List<Task> out = new ArrayList<>(queue);
        out.sort(ORDER);
        return out;
    }
}
