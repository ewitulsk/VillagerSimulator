package com.ewitulsk.villagersimulator.content;

import com.ewitulsk.villagersimulator.api.sim.Id;

/** Ids in the {@code villagersimulator} namespace. */
public final class VS {
    public static final String NAMESPACE = "villagersimulator";

    private VS() {}

    public static Id id(String path) {
        return Id.of(NAMESPACE, path);
    }
}
