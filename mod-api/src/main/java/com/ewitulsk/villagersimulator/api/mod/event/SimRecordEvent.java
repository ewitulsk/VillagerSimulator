package com.ewitulsk.villagersimulator.api.mod.event;

import com.ewitulsk.villagersimulator.api.mod.SimAccess;
import com.ewitulsk.villagersimulator.api.sim.event.EventRecord;
import net.neoforged.bus.api.Event;

/**
 * A notable sim event (an event-log record: a village founded, two villagers becoming friends, an argument) was
 * recorded. Posted on the game bus ({@code NeoForge.EVENT_BUS}), on the server thread, shortly after the sim logged
 * it: the sim never waits for listeners (docs/ARCHITECTURE.md §10.2). React by submitting commands through
 * {@link #sim()}.
 */
public final class SimRecordEvent extends Event {
    private final EventRecord record;
    private final SimAccess sim;

    public SimRecordEvent(EventRecord record, SimAccess sim) {
        this.record = record;
        this.sim = sim;
    }

    public EventRecord record() {
        return record;
    }

    /** The record's type, e.g. {@code "villagersimulator:village_founded"}. */
    public String type() {
        return record.type().toString();
    }

    public SimAccess sim() {
        return sim;
    }
}
