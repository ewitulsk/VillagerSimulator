package com.ewitulsk.villagersimulator.api.sim;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sim time helpers. The sim clock counts <b>sim ticks</b>, one per Minecraft tick at normal speed, so a sim day is
 * 24,000 ticks and an hour is 1,000. Like Minecraft, tick 0 of each day is 06:00.
 */
public final class SimTime {
    public static final long TICKS_PER_HOUR = 1_000;
    public static final long TICKS_PER_DAY = 24 * TICKS_PER_HOUR;
    /** Clock hour at tick 0 of a day. */
    public static final int DAY_START_HOUR = 6;

    private static final Pattern PART = Pattern.compile("(\\d+)([dhmt])");

    private SimTime() {}

    public static long days(long n) {
        return n * TICKS_PER_DAY;
    }

    public static long hours(long n) {
        return n * TICKS_PER_HOUR;
    }

    public static long minutes(long n) {
        return n * TICKS_PER_HOUR / 60;
    }

    public static long day(long time) {
        return Math.floorDiv(time, TICKS_PER_DAY);
    }

    public static long dayStart(long time) {
        return day(time) * TICKS_PER_DAY;
    }

    /** Clock hour (0-23) at {@code time}. */
    public static int hourOfDay(long time) {
        long ticksIntoDay = Math.floorMod(time, TICKS_PER_DAY);
        return (int) ((ticksIntoDay / TICKS_PER_HOUR + DAY_START_HOUR) % 24);
    }

    /** {@code "Day 3, 14:30"} */
    public static String describe(long time) {
        long ticksIntoDay = Math.floorMod(time, TICKS_PER_DAY);
        long minute = (ticksIntoDay % TICKS_PER_HOUR) * 60 / TICKS_PER_HOUR;
        return String.format("Day %d, %02d:%02d", day(time), hourOfDay(time), minute);
    }

    /**
     * Parses a duration such as {@code 1d}, {@code 3h}, {@code 30m}, {@code 200t} or combinations like {@code 1d12h}.
     *
     * @throws IllegalArgumentException if the text isn't a duration
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> DURATIONS = new java.util.concurrent.ConcurrentHashMap<>();

    public static long parseDuration(String text) {
        Long cached = DURATIONS.get(text);
        if (cached != null) return cached;
        long ticks = parse(text);
        if (DURATIONS.size() < 4096) DURATIONS.put(text, ticks);
        return ticks;
    }

    private static long parse(String text) {
        String s = text.trim().toLowerCase();
        if (s.isEmpty()) throw new IllegalArgumentException("Empty duration");
        Matcher m = PART.matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) throw new IllegalArgumentException("Bad duration: " + text);
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> days(n);
                case "h" -> hours(n);
                case "m" -> minutes(n);
                default -> n;
            };
            end = m.end();
        }
        if (end != s.length()) throw new IllegalArgumentException("Bad duration: " + text);
        return total;
    }
}
