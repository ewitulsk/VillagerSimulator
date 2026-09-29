package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;
import com.ewitulsk.villagersimulator.api.sim.command.SimQuery;
import com.ewitulsk.villagersimulator.api.sim.view.SimViews;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Runs a {@link SimWorld} on its own thread (docs/ARCHITECTURE.md §6.4). Other threads never touch the world: they
 * enqueue commands and queries, move the target time forward, and read the latest published views. Nothing here ever
 * blocks the caller, so Minecraft never waits for the sim.
 */
public final class SimRuntime implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("VillagerSim");
    /** Longest stretch of sim time run before commands are applied and views published again. */
    public static final long WINDOW = 1_000;
    private static final long PUBLISH_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

    private final SimWorld world;
    private final ConcurrentLinkedQueue<Consumer<SimWorld>> inbox = new ConcurrentLinkedQueue<>();
    private final AtomicLong target;
    private final Thread thread;
    private volatile SimViewsImpl views = SimViewsImpl.EMPTY;
    private volatile boolean running = true;
    // Outbox (docs/ARCHITECTURE.md §10.2): new event records for the server thread, filled on the sim thread.
    private static final int OUTBOX_LIMIT = 100_000;
    private final ConcurrentLinkedQueue<com.ewitulsk.villagersimulator.api.sim.event.EventRecord> outbox = new ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicInteger outboxSize = new java.util.concurrent.atomic.AtomicInteger();
    private volatile boolean outboxEnabled;
    private int outboxIndex;
    private long outboxDropped;
    private volatile Throwable lastError;

    public SimRuntime(SimWorld world, String threadName) {
        this.world = world;
        this.target = new AtomicLong(world.now());
        this.views = world.snapshotViews();
        this.thread = new Thread(this::loop, threadName);
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    /** Moves the target time forward by {@code ticks}; the sim catches up as fast as it can. */
    public void advanceTarget(long ticks) {
        if (ticks <= 0) return;
        target.addAndGet(ticks);
        LockSupport.unpark(thread);
    }

    public long targetTime() {
        return target.get();
    }

    /** The latest published views. Safe on any thread. */
    public SimViews views() {
        return views;
    }

    /** True while the sim is behind its target time. */
    public boolean catchingUp() {
        return views.time() < target.get();
    }

    public Throwable lastError() {
        return lastError;
    }

    /**
     * Starts copying new event records to the outbox for {@link #pollOutbox}. Records logged before this call are
     * skipped. If nobody drains it, the outbox keeps the latest records only.
     */
    public void enableOutbox() {
        run(w -> {
            outboxIndex = w.events().size();
            outboxEnabled = true;
        });
    }

    /** The next new event record, or {@code null}. Safe on any thread. */
    public com.ewitulsk.villagersimulator.api.sim.event.EventRecord pollOutbox() {
        var r = outbox.poll();
        if (r != null) outboxSize.decrementAndGet();
        return r;
    }

    private void fillOutbox() {
        if (!outboxEnabled) return;
        int size = world.events().size();
        if (size <= outboxIndex) return;
        for (var r : world.eventsSince(outboxIndex)) {
            outbox.add(r);
            if (outboxSize.incrementAndGet() > OUTBOX_LIMIT && outbox.poll() != null) {
                outboxSize.decrementAndGet();
                if (outboxDropped++ % 10_000 == 0) LOG.warn("Sim event outbox full; dropping the oldest records");
            }
        }
        outboxIndex = size;
    }

    public void submit(SimCommand command) {
        run(w -> w.apply(command));
    }

    /** Reads engine-level state on the sim thread, e.g. {@link SimWorld#problems()}. */
    public <T> CompletableFuture<T> queryWorld(java.util.function.Function<SimWorld, T> read) {
        CompletableFuture<T> future = new CompletableFuture<>();
        run(w -> {
            try {
                future.complete(read.apply(w));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /** Runs an engine-level action on the sim thread, e.g. {@link SimWorld#reloadData}. */
    public void submitWorld(Consumer<SimWorld> action) {
        run(action);
    }

    public <T> CompletableFuture<T> query(SimQuery<T> query) {
        CompletableFuture<T> future = new CompletableFuture<>();
        run(w -> {
            try {
                future.complete(w.query(query));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public CompletableFuture<SimWorld.Snapshot> snapshot(int savedEvents) {
        return query(ctx -> world.snapshot(savedEvents));
    }

    private void run(Consumer<SimWorld> action) {
        inbox.add(action);
        LockSupport.unpark(thread);
    }

    private void loop() {
        long lastPublish = System.nanoTime();
        while (running) {
            boolean changed = drain();
            long goal = target.get();
            if (world.now() < goal) {
                long next = Math.min(goal, world.now() + WINDOW);
                try {
                    world.advanceTo(next);
                } catch (Throwable t) {
                    fail("advancing the sim", t);
                }
                changed = true;
                if (next == goal || System.nanoTime() - lastPublish > PUBLISH_NANOS) {
                    publish();
                    lastPublish = System.nanoTime();
                }
                continue;
            }
            if (changed) {
                publish();
                lastPublish = System.nanoTime();
            }
            if (inbox.isEmpty() && world.now() >= target.get()) LockSupport.parkNanos(this, TimeUnit.MILLISECONDS.toNanos(20));
        }
        drain();
    }

    private boolean drain() {
        boolean any = false;
        Consumer<SimWorld> action;
        while ((action = inbox.poll()) != null) {
            any = true;
            try {
                action.accept(world);
            } catch (Throwable t) {
                fail("applying a command", t);
            }
        }
        return any;
    }

    private void publish() {
        try {
            views = world.snapshotViews();
        } catch (Throwable t) {
            fail("publishing views", t);
        }
        // After the views, so a listener reacting to a record already sees the world it happened in.
        fillOutbox();
    }

    private void fail(String what, Throwable t) {
        lastError = t;
        LOG.error("Sim error while {}", what, t);
    }

    /** Stops the thread after it has applied everything already queued. */
    @Override
    public void close() {
        running = false;
        LockSupport.unpark(thread);
        try {
            thread.join(TimeUnit.SECONDS.toMillis(30));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        world.close();
    }
}
