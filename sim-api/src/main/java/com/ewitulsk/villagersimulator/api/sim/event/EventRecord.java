package com.ewitulsk.villagersimulator.api.sim.event;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;

import java.util.List;

/**
 * A notable event, stored once in the event log (docs/ARCHITECTURE.md §5.4). Records carry who did it
 * ({@code actor}), what caused it ({@code cause}, another record's id or 0) and who saw it ({@code witnesses}).
 * Memories, gossip, the chronicle and intrigue all build on these fields.
 */
public record EventRecord(long id, long time, Id type, EntityId actor, long cause, List<EntityId> witnesses, String detail) {}
