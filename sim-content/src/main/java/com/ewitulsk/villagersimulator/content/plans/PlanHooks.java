package com.ewitulsk.villagersimulator.content.plans;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.event.SimEvent;
import com.ewitulsk.villagersimulator.api.sim.module.ExtensionPoint;
import com.ewitulsk.villagersimulator.content.VS;

import java.util.Optional;

/** Extension points and events of the plans module, for other modules to hook into daily life. */
public final class PlanHooks {
    /**
     * Adds to a villager's day right after it is planned (docs/ARCHITECTURE.md §8.5, PlanContributors), e.g.
     * appointments. Use {@link Plans#commit} to book time; return the plan unchanged if nothing applies.
     */
    @FunctionalInterface
    public interface PlanContributor {
        Plan contribute(SimContext ctx, EntityId villager, Plan plan);
    }

    public static final ExtensionPoint<PlanContributor> CONTRIBUTORS =
            new ExtensionPoint<>(VS.id("plan_contributors"), PlanContributor.class);

    /** Extra utility-AI considerations, summed with the base ones in {@link Choices#CONSIDERATIONS}. */
    public static final ExtensionPoint<Choices.Consideration> CONSIDERATIONS =
            new ExtensionPoint<>(VS.id("considerations"), Choices.Consideration.class);

    /** A villager started a stay at a building (not travel). {@code plannedEnd} may change if plans do. */
    public record VisitStarted(EntityId villager, EntityId venue, Id activity, Optional<String> ad, long start,
                               long plannedEnd) implements SimEvent {}

    /** A villager's stay at a building ended; {@code from}/{@code to} are the actual times. */
    public record VisitEnded(EntityId villager, EntityId venue, Id activity, Optional<String> ad, long from,
                             long to) implements SimEvent {}

    /** A villager's plan for a new day was just set. */
    public record DayPlanned(EntityId villager, long day) implements SimEvent {}

    private PlanHooks() {}
}
