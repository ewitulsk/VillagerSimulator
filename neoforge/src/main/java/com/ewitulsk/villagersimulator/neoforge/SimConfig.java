package com.ewitulsk.villagersimulator.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code config/villagersimulator-common.toml} */
public final class SimConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue DEBUG_TIME_SCALE = BUILDER
            .comment("Sim ticks per game tick. 1.0 is normal speed. Raise it for manual playtesting,",
                    "e.g. 60 makes a sim day pass in 20 real seconds (docs/ROADMAP.md Phase 0, time control).")
            .defineInRange("sim.debugTimeScale", 1.0, 0.01, 10_000.0);

    public static final ModConfigSpec.IntValue T0_RADIUS = BUILDER
            .comment("Villagers within this many blocks of a player are embodied as entities (tier T0).")
            .defineInRange("tiers.t0Radius", 48, 8, 128);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SimConfig() {}
}
