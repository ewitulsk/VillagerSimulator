package com.ewitulsk.villagersimulator.api.sim.event;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;

import java.util.List;

public interface EventLog {
    /** Records a notable event at the current sim time and returns its id. */
    long record(Id type, EntityId actor, long cause, List<EntityId> witnesses, String detail);

    default long record(Id type, EntityId actor, String detail) {
        return record(type, actor, 0, List.of(), detail);
    }

    /** All records of {@code type}, oldest first. */
    List<EventRecord> ofType(Id type);

    /** All records with {@code actor}, oldest first. */
    List<EventRecord> byActor(EntityId actor);

    int size();
}
