package com.ewitulsk.villagersimulator.api.mod;

import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/** Entry point to the running sim for addons and scripts. */
public final class VillagerSimApi {
    private static volatile SimAccess server;

    private VillagerSimApi() {}

    /** The sim of the running server, or empty while no server (or no overworld) is running. */
    public static Optional<SimAccess> server() {
        return Optional.ofNullable(server);
    }

    /** Set by Villager Simulator when its sim starts and stops. */
    @ApiStatus.Internal
    public static void setServer(SimAccess access) {
        server = access;
    }
}
