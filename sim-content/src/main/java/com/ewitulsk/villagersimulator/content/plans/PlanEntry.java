package com.ewitulsk.villagersimulator.content.plans;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * One step of a daily plan: do {@code activity} at {@code venue} during {@code [start, end)}, moving from
 * {@code from} to {@code to}. Only travel entries have {@code from != to}.
 *
 * @param ad the venue's advertisement this entry fulfils, if it was chosen by the utility AI
 */
public record PlanEntry(long start, long end, Id activity, EntityId venue,
                        double fx, double fy, double fz, double tx, double ty, double tz, Optional<String> ad) {
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
            Codec.STRING.optionalFieldOf("ad").forGetter(PlanEntry::ad)
    ).apply(i, PlanEntry::new));

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
        return new PlanEntry(newStart, newEnd, activity, venue, fx, fy, fz, tx, ty, tz, ad);
    }

    /** Plan position at {@code time}: interpolated along a travel entry, otherwise the destination. */
    public double[] positionAt(long time) {
        if (end <= start) return target();
        double f = Math.max(0, Math.min(1, (double) (time - start) / (end - start)));
        return new double[]{fx + (tx - fx) * f, fy + (ty - fy) * f, fz + (tz - fz) * f};
    }
}
