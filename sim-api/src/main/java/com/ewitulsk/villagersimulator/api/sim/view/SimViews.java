package com.ewitulsk.villagersimulator.api.sim.view;

/** A published set of views, all taken at the same sim time. */
public interface SimViews {
    long time();

    /** @return the view, or {@code null} if no module provides it */
    <T> T get(ViewKey<T> key);
}
