package com.ewitulsk.villagersimulator.api.sim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.regex.Pattern;

/**
 * A namespaced identifier ({@code namespace:path}), the sim's Minecraft-free equivalent of a resource location.
 * Used for components, registries, activities, events and data definitions.
 */
public record Id(String namespace, String path) implements Comparable<Id> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    public static final Codec<Id> CODEC = Codec.STRING.comapFlatMap(Id::read, Id::toString);

    public Id {
        if (!NAMESPACE.matcher(namespace).matches()) throw new IllegalArgumentException("Bad namespace: " + namespace);
        if (!PATH.matcher(path).matches()) throw new IllegalArgumentException("Bad path: " + path);
    }

    public static Id of(String namespace, String path) {
        return new Id(namespace, path);
    }

    /** Parses {@code namespace:path}; a bare path gets the {@code minecraft} namespace, like vanilla. */
    public static Id parse(String text) {
        int colon = text.indexOf(':');
        return colon < 0 ? new Id("minecraft", text) : new Id(text.substring(0, colon), text.substring(colon + 1));
    }

    private static DataResult<Id> read(String text) {
        try {
            return DataResult.success(parse(text));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    @Override
    public int compareTo(Id other) {
        int c = namespace.compareTo(other.namespace);
        return c != 0 ? c : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
