package com.ewitulsk.villagersimulator.api.sim.component;

import com.ewitulsk.villagersimulator.api.sim.Id;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A component of hot numeric data, stored as structure-of-arrays primitive columns (docs/ARCHITECTURE.md §5.2).
 * Declare the fields in static initialisers, then register the component from a module:
 *
 * <pre>{@code
 * public static final DenseComponent HUNGER = new DenseComponent(Id.of("mymod", "hunger"), 1);
 * public static final FloatField HUNGER_VALUE = HUNGER.floatField("value");
 * }</pre>
 *
 * Fields are matched by name when a save is loaded, so adding or removing fields is a safe schema change.
 */
public final class DenseComponent {
    private final Id id;
    private final int version;
    private final List<String> floatNames = new ArrayList<>();
    private final List<String> longNames = new ArrayList<>();
    private final List<String> intNames = new ArrayList<>();
    private boolean frozen;

    public DenseComponent(Id id, int version) {
        this.id = id;
        this.version = version;
    }

    public FloatField floatField(String name) {
        checkOpen(name);
        floatNames.add(name);
        return new FloatField(this, floatNames.size() - 1, name);
    }

    public LongField longField(String name) {
        checkOpen(name);
        longNames.add(name);
        return new LongField(this, longNames.size() - 1, name);
    }

    public IntField intField(String name) {
        checkOpen(name);
        intNames.add(name);
        return new IntField(this, intNames.size() - 1, name);
    }

    private void checkOpen(String name) {
        if (frozen) throw new IllegalStateException("Component " + id + " is already registered; can't add field " + name);
        if (floatNames.contains(name) || longNames.contains(name) || intNames.contains(name)) {
            throw new IllegalArgumentException("Duplicate field " + name + " in " + id);
        }
    }

    /** Called by the engine at registration; no fields can be added afterwards. */
    public void freeze() {
        frozen = true;
    }

    public Id id() {
        return id;
    }

    public int version() {
        return version;
    }

    public List<String> floatNames() {
        return Collections.unmodifiableList(floatNames);
    }

    public List<String> longNames() {
        return Collections.unmodifiableList(longNames);
    }

    public List<String> intNames() {
        return Collections.unmodifiableList(intNames);
    }

    @Override
    public String toString() {
        return "DenseComponent[" + id + "]";
    }
}
