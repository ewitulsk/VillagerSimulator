package com.ewitulsk.villagersimulator.api.mod.embodiment;

/**
 * What a villager puppet does in the world while performing an activity at T0 (docs/ARCHITECTURE.md §12.4). The sim
 * decides <em>what</em> happens; an embodied behaviour only shows it: particles, a held item, looking at something,
 * a gesture. It runs every server tick for puppets whose activity has this behaviour's key
 * ({@code Activity.embodied()}), after the puppet has moved.
 */
@FunctionalInterface
public interface EmbodiedBehaviour {
    void tick(PuppetContext ctx);

    /** Called once when the puppet stops performing the behaviour, e.g. to put away a held item. */
    default void stop(PuppetContext ctx) {}
}
