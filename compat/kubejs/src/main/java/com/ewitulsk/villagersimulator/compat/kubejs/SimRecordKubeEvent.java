package com.ewitulsk.villagersimulator.compat.kubejs;

import com.ewitulsk.villagersimulator.api.mod.event.SimRecordEvent;
import com.ewitulsk.villagersimulator.api.sim.EntityId;
import dev.latvian.mods.kubejs.event.KubeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A sim event record, for {@code VillagerSimEvents.recorded}. Entity ids are plain numbers that the
 * {@code VillagerSim} binding accepts back.
 *
 * <pre>{@code
 * VillagerSimEvents.recorded(e => {
 *   if (e.type == 'villagersimulator:village_founded') console.info('New village: ' + e.detail)
 * })
 * }</pre>
 */
public final class SimRecordKubeEvent implements KubeEvent {
    private final SimRecordEvent event;

    SimRecordKubeEvent(SimRecordEvent event) {
        this.event = event;
    }

    /** e.g. {@code villagersimulator:village_founded}. */
    public String getType() {
        return event.type();
    }

    /** The entity the record is about. */
    public int getActor() {
        return event.record().actor().raw();
    }

    public String getDetail() {
        return event.record().detail();
    }

    /** Sim time, in sim ticks. */
    public long getTime() {
        return event.record().time();
    }

    /** The record id, which other records can name as their cause. */
    public long getId() {
        return event.record().id();
    }

    public List<Integer> getWitnesses() {
        List<Integer> out = new ArrayList<>();
        for (EntityId w : event.record().witnesses()) out.add(w.raw());
        return out;
    }
}
