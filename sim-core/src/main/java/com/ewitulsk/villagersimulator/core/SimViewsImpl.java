package com.ewitulsk.villagersimulator.core;

import com.ewitulsk.villagersimulator.api.sim.view.SimViews;
import com.ewitulsk.villagersimulator.api.sim.view.ViewKey;

import java.util.Map;

/** An immutable published set of views. */
public record SimViewsImpl(long time, Map<ViewKey<?>, Object> values) implements SimViews {
    public static final SimViewsImpl EMPTY = new SimViewsImpl(0, Map.of());

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(ViewKey<T> key) {
        return (T) values.get(key);
    }
}
