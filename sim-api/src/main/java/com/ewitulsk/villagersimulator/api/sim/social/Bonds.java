package com.ewitulsk.villagersimulator.api.sim.social;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bond flags (docs/DESIGN.md §8.1). Bits 0-15 are reserved for the base game; addons get bits 16-31 later through
 * the modding surface. Pairs with {@link #LASTING} bonds never fade.
 */
public final class Bonds {
    public static final int FRIEND = 1;
    public static final int RIVAL = 1 << 1;
    public static final int COLLEAGUE = 1 << 2;
    public static final int FAMILY = 1 << 3;
    public static final int SPOUSE = 1 << 4;
    public static final int NEIGHBOUR = 1 << 5;

    /** Bonds that keep a relationship from fading. */
    public static final int LASTING = FAMILY | SPOUSE;

    private static final Map<Integer, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put(FRIEND, "friend");
        NAMES.put(RIVAL, "rival");
        NAMES.put(COLLEAGUE, "colleague");
        NAMES.put(FAMILY, "family");
        NAMES.put(SPOUSE, "spouse");
        NAMES.put(NEIGHBOUR, "neighbour");
    }

    private Bonds() {}

    /** {@code "friend, colleague"} */
    public static String describe(int bonds) {
        StringBuilder b = new StringBuilder();
        NAMES.forEach((bit, name) -> {
            if ((bonds & bit) != 0) {
                if (!b.isEmpty()) b.append(", ");
                b.append(name);
            }
        });
        return b.toString();
    }
}
