package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.SimRandom;

/** Placeholder villager names until cultures bring their own (Phase 19+). */
public final class Names {
    private static final String[] FIRST = {
            "Ada", "Aldric", "Bram", "Cedric", "Dara", "Edda", "Elric", "Fenna", "Garrick", "Hilda",
            "Ivo", "Jorun", "Kara", "Leif", "Maren", "Nils", "Odo", "Petra", "Quill", "Rhea",
            "Soren", "Tilda", "Ulric", "Vera", "Wynn", "Yara", "Zeno", "Brisa", "Corin", "Doran",
            "Elsa", "Finn", "Greta", "Hugo", "Isla", "Jasper", "Lotte", "Milo", "Nora", "Otto"};
    private static final String[] LAST = {
            "Miller", "Baker", "Thatcher", "Cooper", "Fletcher", "Mason", "Carter", "Weaver",
            "Brook", "Hale", "Ash", "Fenwick", "Holt", "Marsh", "Stone", "Wren"};

    private Names() {}

    public static String pick(long seed) {
        return FIRST[SimRandom.below(FIRST.length, seed, 1)] + " " + LAST[SimRandom.below(LAST.length, seed, 2)];
    }
}
