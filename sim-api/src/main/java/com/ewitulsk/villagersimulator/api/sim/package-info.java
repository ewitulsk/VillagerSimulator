/**
 * Villager Simulator's public, Minecraft-free sim API (docs/ARCHITECTURE.md §11): entities and components, tasks,
 * events and the event log, commands and queries, registries, modules and extension points, activities, the VS
 * expression language, conditions, effects and stats, relationships, views and the scenario DSL. Addons build sim
 * modules against these types only.
 *
 * <p>Types here follow semantic versioning (with a pre-1.0 grace period) and are checked for compatibility in CI
 * against {@code api/baseline/}. {@link org.jetbrains.annotations.ApiStatus.Experimental} types may still change;
 * {@link org.jetbrains.annotations.ApiStatus.Internal} members are for the engine and base game only.
 */
package com.ewitulsk.villagersimulator.api.sim;
