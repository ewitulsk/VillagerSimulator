package com.ewitulsk.villagersimulator.api.sim;

/**
 * Stateless, deterministic randomness. Every value is a hash of a seed and "salt" values (day number, purpose, index),
 * so results never depend on the order in which systems run or on unrelated entities. This is what keeps the sim
 * deterministic (docs/ARCHITECTURE.md §3, principle 7).
 */
public final class SimRandom {
    private SimRandom() {}

    /** SplitMix64 finaliser. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long hash(long seed, long... salt) {
        long h = mix(seed + 0x9E3779B97F4A7C15L);
        for (long s : salt) h = mix(h ^ (s + 0x9E3779B97F4A7C15L));
        return h;
    }

    /** Uniform in [0, 1). */
    public static double unit(long seed, long... salt) {
        return (hash(seed, salt) >>> 11) * 0x1.0p-53;
    }

    /** Uniform in [0, bound). */
    public static int below(int bound, long seed, long... salt) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
        return (int) Math.floorMod(hash(seed, salt), (long) bound);
    }

    /** A stable salt for a string purpose, e.g. {@code salt("wake_time")}. */
    public static long salt(String purpose) {
        long h = 1125899906842597L;
        for (int i = 0; i < purpose.length(); i++) h = 31 * h + purpose.charAt(i);
        return h;
    }
}
