package com.ewitulsk.villagersimulator.content.plans;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * One step of a daily plan: do {@code activity} at {@code venue} during {@code [start, end)}, moving from
 * {@code from} to {@code to}. Only travel entries have {@code from != to}; they may pass through road waypoints.
 *
 * @param ad  the venue's advertisement this entry fulfils, if it was chosen by the utility AI
 * @param via road waypoints between from and to, flattened {@code x, y, z, ...} (travel only)
 */
public record PlanEntry(long start, long end, Id activity, EntityId venue,
                        double fx, double fy, double fz, double tx, double ty, double tz, Optional<String> ad,
                        Optional<List<Double>> via) {
    public static final Codec<PlanEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("start").forGetter(PlanEntry::start),
            Codec.LONG.fieldOf("end").forGetter(PlanEntry::end),
            Id.CODEC.fieldOf("activity").forGetter(PlanEntry::activity),
            EntityId.CODEC.fieldOf("venue").forGetter(PlanEntry::venue),
            Codec.DOUBLE.fieldOf("fx").forGetter(PlanEntry::fx),
            Codec.DOUBLE.fieldOf("fy").forGetter(PlanEntry::fy),
            Codec.DOUBLE.fieldOf("fz").forGetter(PlanEntry::fz),
            Codec.DOUBLE.fieldOf("tx").forGetter(PlanEntry::tx),
            Codec.DOUBLE.fieldOf("ty").forGetter(PlanEntry::ty),
            Codec.DOUBLE.fieldOf("tz").forGetter(PlanEntry::tz),
            Codec.STRING.optionalFieldOf("ad").forGetter(PlanEntry::ad),
            Codec.DOUBLE.listOf().optionalFieldOf("via").forGetter(PlanEntry::via)
    ).apply(i, PlanEntry::new));

    public PlanEntry(long start, long end, Id activity, EntityId venue, double fx, double fy, double fz,
                     double tx, double ty, double tz, Optional<String> ad) {
        this(start, end, activity, venue, fx, fy, fz, tx, ty, tz, ad, Optional.empty());
    }

    /** An entry that stays at one place. */
    public static PlanEntry stay(long start, long end, Id activity, EntityId venue, double[] p, Optional<String> ad) {
        return new PlanEntry(start, end, activity, venue, p[0], p[1], p[2], p[0], p[1], p[2], ad);
    }

    public boolean contains(long time) {
        return time >= start && time < end;
    }

    public double[] target() {
        return new double[]{tx, ty, tz};
    }

    public PlanEntry withTimes(long newStart, long newEnd) {
        return new PlanEntry(newStart, newEnd, activity, venue, fx, fy, fz, tx, ty, tz, ad, via);
    }

    /** All points of the path: from, the waypoints, to. */
    public double[][] path() {
        List<Double> v = via.orElse(List.of());
        double[][] out = new double[2 + v.size() / 3][];
        out[0] = new double[]{fx, fy, fz};
        for (int i = 0; i < v.size() / 3; i++) out[1 + i] = new double[]{v.get(3 * i), v.get(3 * i + 1), v.get(3 * i + 2)};
        out[out.length - 1] = target();
        return out;
    }

    public double length() {
        double[][] p = path();
        double total = 0;
        for (int i = 1; i < p.length; i++) total += dist(p[i - 1], p[i]);
        return total;
    }

    /** Plan position at {@code time}: along the path for travel, otherwise the destination. */
    public double[] positionAt(long time) {
        if (end <= start) return target();
        double f = Math.max(0, Math.min(1, (double) (time - start) / (end - start)));
        return along(f * length());
    }

    /** Index in {@link #path()} of the next point ahead at {@code time}. */
    public int nextPointAt(long time) {
        if (end <= start) return path().length - 1;
        double f = Math.max(0, Math.min(1, (double) (time - start) / (end - start)));
        double target = f * length();
        double[][] p = path();
        double walked = 0;
        for (int i = 1; i < p.length; i++) {
            walked += dist(p[i - 1], p[i]);
            if (walked > target) return i;
        }
        return p.length - 1;
    }

    private double[] along(double distance) {
        double[][] p = path();
        for (int i = 1; i < p.length; i++) {
            double seg = dist(p[i - 1], p[i]);
            if (distance <= seg || i == p.length - 1) {
                double f = seg <= 0 ? 1 : Math.min(1, distance / seg);
                return new double[]{p[i - 1][0] + (p[i][0] - p[i - 1][0]) * f, p[i - 1][1] + (p[i][1] - p[i - 1][1]) * f,
                        p[i - 1][2] + (p[i][2] - p[i - 1][2]) * f};
            }
            distance -= seg;
        }
        return target();
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
