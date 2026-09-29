package com.ewitulsk.villagersimulator.content.plans;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.activity.ActivityContext;
import com.ewitulsk.villagersimulator.api.sim.activity.EmbodiedBehaviors;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.needs.Needs;

import java.util.List;

/** Phase 0 activities: sleep, eat, wander and travel. */
public final class BasicActivities {
    public static final Id SLEEP = VS.id("sleep");
    public static final Id EAT = VS.id("eat");
    public static final Id WANDER = VS.id("wander");
    public static final Id TRAVEL = VS.id("travel");

    /** Event log type: a villager found no food. */
    public static final Id EVENT_NO_FOOD = VS.id("no_food");
    /** Hunger restored by one bread. */
    public static final float BREAD_HUNGER = 55f;

    private BasicActivities() {}

    public static List<Activity> all() {
        return List.of(new Sleep(), new Eat(), new Wander(), new Travel());
    }

    static final class Sleep implements Activity {
        @Override
        public Id id() {
            return SLEEP;
        }

        @Override
        public void begin(ActivityContext ctx) {
            Needs.asleep(ctx.sim(), ctx.actor());
        }

        @Override
        public void simulateAbstract(ActivityContext ctx, long from, long to) {
            // Needs change lazily through the rates set in begin().
        }

        @Override
        public Id embodied() {
            return EmbodiedBehaviors.SLEEP;
        }

        @Override
        public String label() {
            return "Sleeping";
        }
    }

    /** Eating a bread from the venue's stock at the end of the meal. */
    static final class Eat implements Activity {
        @Override
        public Id id() {
            return EAT;
        }

        @Override
        public void begin(ActivityContext ctx) {
            Needs.awake(ctx.sim(), ctx.actor());
        }

        @Override
        public void simulateAbstract(ActivityContext ctx, long from, long to) {
            if (to <= from) return;
            var sim = ctx.sim();
            int bread = ctx.venue().isNone() || !sim.alive(ctx.venue()) ? 0 : Buildings.stock(sim, ctx.venue(), Buildings.BREAD);
            if (bread > 0) {
                Buildings.setStock(sim, ctx.venue(), Buildings.BREAD, bread - 1);
                Needs.HUNGER.add(sim, ctx.actor(), BREAD_HUNGER);
            } else {
                sim.events().record(EVENT_NO_FOOD, ctx.actor(), "No bread left");
            }
        }

        @Override
        public Id embodied() {
            return EmbodiedBehaviors.EAT;
        }

        @Override
        public String label() {
            return "Eating";
        }
    }

    static final class Wander implements Activity {
        @Override
        public Id id() {
            return WANDER;
        }

        @Override
        public void begin(ActivityContext ctx) {
            Needs.awake(ctx.sim(), ctx.actor());
        }

        @Override
        public void simulateAbstract(ActivityContext ctx, long from, long to) {}

        @Override
        public String label() {
            return "Relaxing";
        }
    }

    static final class Travel implements Activity {
        @Override
        public Id id() {
            return TRAVEL;
        }

        @Override
        public void begin(ActivityContext ctx) {
            Needs.awake(ctx.sim(), ctx.actor());
        }

        @Override
        public void simulateAbstract(ActivityContext ctx, long from, long to) {}

        @Override
        public Id embodied() {
            return EmbodiedBehaviors.WALK;
        }

        @Override
        public String label() {
            return "Walking";
        }
    }
}
