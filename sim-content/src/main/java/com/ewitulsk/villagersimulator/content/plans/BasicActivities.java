package com.ewitulsk.villagersimulator.content.plans;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.activity.Activity;
import com.ewitulsk.villagersimulator.api.sim.activity.ActivityContext;
import com.ewitulsk.villagersimulator.api.sim.activity.EmbodiedBehaviors;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.needs.Needs;

import java.util.List;

/**
 * The base game's generic activities. Their effects on needs come from the advertisement that was chosen (see
 * {@link Plans}), so these only set awake/asleep need rates, a label and an embodied behaviour.
 */
public final class BasicActivities {
    public static final Id SLEEP = VS.id("sleep");
    public static final Id NAP = VS.id("nap");
    public static final Id EAT = VS.id("eat");
    public static final Id DRINK = VS.id("drink");
    public static final Id SOCIALIZE = VS.id("socialize");
    public static final Id WASH = VS.id("wash");
    public static final Id REST = VS.id("rest");
    public static final Id BROWSE = VS.id("browse");
    public static final Id WANDER = VS.id("wander");
    public static final Id TRAVEL = VS.id("travel");
    /** Placeholder for unplanned time; replaced by a chosen activity when it begins. */
    public static final Id FREE_TIME = VS.id("free_time");

    /** Event log type: a villager wanted food that was gone by the time they got there. */
    public static final Id EVENT_NO_FOOD = VS.id("no_food");

    private BasicActivities() {}

    /** An activity with no logic of its own. */
    public record Simple(Id id, String label, Id embodied, boolean asleep) implements Activity {
        @Override
        public void begin(ActivityContext ctx) {
            if (asleep) Needs.asleep(ctx.sim(), ctx.actor());
            else Needs.awake(ctx.sim(), ctx.actor());
        }

        @Override
        public void simulateAbstract(ActivityContext ctx, long from, long to) {}
    }

    public static List<Activity> all() {
        return List.of(
                new Simple(SLEEP, "Sleeping", EmbodiedBehaviors.SLEEP, true),
                new Simple(NAP, "Napping", EmbodiedBehaviors.SLEEP, true),
                new Simple(EAT, "Eating", EmbodiedBehaviors.EAT, false),
                new Simple(DRINK, "Drinking", EmbodiedBehaviors.EAT, false),
                new Simple(SOCIALIZE, "Chatting", EmbodiedBehaviors.IDLE, false),
                new Simple(WASH, "Washing", EmbodiedBehaviors.WORK, false),
                new Simple(REST, "Resting", EmbodiedBehaviors.IDLE, false),
                new Simple(BROWSE, "Browsing", EmbodiedBehaviors.IDLE, false),
                new Simple(WANDER, "Relaxing", EmbodiedBehaviors.IDLE, false),
                new Simple(TRAVEL, "Walking", EmbodiedBehaviors.WALK, false),
                new Simple(FREE_TIME, "Free time", EmbodiedBehaviors.IDLE, false));
    }
}
