package com.ewitulsk.villagersimulator.content.players;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;
import com.ewitulsk.villagersimulator.api.sim.command.SimQuery;
import com.ewitulsk.villagersimulator.content.buildings.Advertisement;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.needs.Needs;
import com.ewitulsk.villagersimulator.content.plans.BasicActivities;
import com.ewitulsk.villagersimulator.content.plans.Choices;
import com.ewitulsk.villagersimulator.content.plans.Plan;
import com.ewitulsk.villagersimulator.content.plans.PlanEntry;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.content.social.Appointment;
import com.ewitulsk.villagersimulator.content.social.Appointments;
import com.ewitulsk.villagersimulator.content.social.Memories;
import com.ewitulsk.villagersimulator.content.social.Memory;
import com.ewitulsk.villagersimulator.content.social.Personality;
import com.ewitulsk.villagersimulator.content.social.Social;
import com.ewitulsk.villagersimulator.content.villages.Villager;
import com.ewitulsk.villagersimulator.content.villages.Villages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Talking with villagers (docs/DESIGN.md §11.3). The options and what they do come from sim state: how well the
 * villager knows the player, their personality and mood, what they're doing and whether there's a tavern to invite
 * them to.
 */
public final class Dialogue {
    public static final String TALK = "talk", COMPLIMENT = "compliment", JOKE = "joke", ASK_DAY = "ask_day", INVITE = "invite";
    /** Friendship needed before a villager accepts an invitation. */
    public static final float INVITE_FRIENDSHIP = 10;
    /** Options used again within this long have little effect. */
    private static final long COOLDOWN = SimTime.hours(1);
    private static final long H = SimTime.TICKS_PER_HOUR;

    /** One choice in the dialogue screen. {@code reason} explains why a disabled option is unavailable. */
    public record Option(String id, String label, boolean enabled, String reason) {}

    /** Everything the dialogue screen shows. */
    public record View(EntityId villager, String name, String header, String greeting, List<Option> options, String response) {}

    private Dialogue() {}

    /** Builds the dialogue for {@code player} talking to {@code villager}, with {@code response} as the last reply. */
    public static SimQuery<View> view(EntityId villager, EntityId player, String response) {
        return ctx -> build(ctx, villager, player, response);
    }

    static View build(SimContext ctx, EntityId villager, EntityId player, String response) {
        Villager v = ctx.get(villager, Villages.VILLAGER);
        if (v == null) return new View(villager, "?", "", "...", List.of(), response);
        float f = ctx.relationships().friendship(villager, player);
        PlanEntry now = Plans.current(ctx, villager);
        String doing = now == null ? "" : ctx.activity(now.activity()).label();
        String header = String.format("Friendship %.0f   Mood %+.0f   %s", f, Memories.mood(ctx, villager), doing);
        String first = Players.name(ctx, player).split(" ")[0];
        String greeting = f <= -30 ? "What do you want?"
                : f < 10 ? "Hello, stranger."
                : f < 40 ? "Good to see you, " + first + "!"
                : "My friend " + first + "! How are you?";

        List<Option> options = new ArrayList<>();
        options.add(new Option(TALK, "Chat", true, ""));
        options.add(new Option(COMPLIMENT, "Pay a compliment", true, ""));
        options.add(new Option(JOKE, "Tell a joke", true, ""));
        options.add(new Option(ASK_DAY, "How's your day been?", true, ""));
        Optional<EntityId> tavern = Villages.findService(ctx, v.village(), "drink");
        String inviteReason = tavern.isEmpty() ? "There's no tavern here"
                : f < INVITE_FRIENDSHIP ? "They don't know you well enough"
                : hasAppointmentToday(ctx, villager, player) ? "You're already meeting today" : "";
        options.add(new Option(INVITE, "Meet me at the tavern later?", inviteReason.isEmpty(), inviteReason));
        return new View(villager, v.name(), header, greeting, List.copyOf(options), response);
    }

    private static boolean hasAppointmentToday(SimContext ctx, EntityId villager, EntityId player) {
        long day = SimTime.day(ctx.now());
        for (Appointment a : Appointments.of(ctx, villager)) {
            if (a.other().equals(player) && !a.resolved() && SimTime.day(a.start()) == day) return true;
        }
        return false;
    }

    /** The player opened the dialogue: the villager stops for a minute to talk. */
    public record Open(EntityId villager, EntityId player) implements SimCommand {
        @Override
        public void apply(SimContext ctx) {
            if (!ctx.alive(villager) || ctx.get(villager, Villages.VILLAGER) == null) return;
            PlanEntry current = Plans.current(ctx, villager);
            if (current == null || current.activity().equals(BasicActivities.SLEEP)) return;
            double[] here = Plans.positionNow(ctx, villager);
            Plans.interrupt(ctx, villager, PlanEntry.stay(ctx.now(), ctx.now() + SimTime.minutes(2), Players.LISTEN,
                    EntityId.NONE, here, Optional.empty()));
        }
    }

    /** The player picked an option. Calls back with the villager's reply. */
    public record Choose(EntityId villager, EntityId player, String option, Consumer<String> reply) implements SimCommand {
        @Override
        public void apply(SimContext ctx) {
            reply.accept(choose(ctx, villager, player, option));
        }
    }

    static String choose(SimContext ctx, EntityId villager, EntityId player, String option) {
        Villager v = ctx.get(villager, Villages.VILLAGER);
        if (v == null || !Players.isPlayer(ctx, player)) return "...";
        double roll = SimRandom.unit(v.seed(), ctx.now(), SimRandom.salt(option));
        boolean fresh = fresh(ctx, villager, player, option);
        float kind = Personality.get(ctx, villager, Personality.KINDNESS);
        float social = Personality.get(ctx, villager, Personality.SOCIABILITY);
        String first = Players.name(ctx, player).split(" ")[0];
        switch (option) {
            case TALK -> {
                if (!fresh) return befriend(ctx, villager, player, 0.5f, "We were just talking, " + first + ".");
                Needs.SOCIAL.add(ctx, villager, 10);
                return befriend(ctx, villager, player, 2 + 3 * social, pick(roll, "It's good to have someone to talk to.",
                        "Did you hear about the bread shortage? Terrible.", "The tavern's the place to be tonight."));
            }
            case COMPLIMENT -> {
                if (!fresh) return befriend(ctx, villager, player, -1, "You said that already...");
                return befriend(ctx, villager, player, 3 + 4 * kind, pick(roll, "Oh! Thank you, that's kind.",
                        "You're too nice.", "Flattery will get you everywhere."));
            }
            case JOKE -> {
                if (!fresh) return befriend(ctx, villager, player, -1, "I heard that one already.");
                boolean laughs = roll < 0.35 + 0.5 * social;
                Needs.FUN.add(ctx, villager, laughs ? 15 : 0);
                return laughs ? befriend(ctx, villager, player, 5, "Ha! That's a good one.")
                        : befriend(ctx, villager, player, -3, "...I don't get it.");
            }
            case ASK_DAY -> {
                befriend(ctx, villager, player, fresh ? 1.5f : 0, "");
                return describeDay(ctx, villager);
            }
            case INVITE -> {
                return invite(ctx, villager, player);
            }
            default -> {
                return "...";
            }
        }
    }

    private static String befriend(SimContext ctx, EntityId villager, EntityId player, float delta, String reply) {
        ctx.relationships().changeFriendship(villager, player, delta);
        if (delta > 1) Memories.add(ctx, villager, Players.MEM_CHAT, 0, player, Math.min(4, delta), SimTime.days(1));
        return reply;
    }

    private static String pick(double roll, String... lines) {
        return lines[Math.min(lines.length - 1, (int) (roll * lines.length))];
    }

    /** Records the use and returns whether the option was last used by this player more than an hour ago. */
    private static boolean fresh(SimContext ctx, EntityId villager, EntityId player, String option) {
        Map<String, Long> talks = ctx.get(villager, Players.TALKS);
        Map<String, Long> next = new HashMap<>(talks == null ? Map.of() : talks);
        String key = player.raw() + ":" + option;
        Long last = next.get(key);
        next.put(key, ctx.now());
        next.values().removeIf(t -> ctx.now() - t > SimTime.TICKS_PER_DAY);
        ctx.set(villager, Players.TALKS, Map.copyOf(next));
        return last == null || ctx.now() - last >= COOLDOWN;
    }

    private static String describeDay(SimContext ctx, EntityId villager) {
        long now = ctx.now();
        Memory strongest = null;
        for (Memory m : Memories.get(ctx, villager)) {
            if (strongest == null || Math.abs(m.effect(now)) > Math.abs(strongest.effect(now))) strongest = m;
        }
        String mood = Memories.mood(ctx, villager) >= 0 ? "Not bad at all." : "Honestly, it's been rough.";
        if (strongest == null || Math.abs(strongest.effect(now)) < 1) return mood + " Quiet day.";
        Villager about = strongest.about().isNone() ? null : ctx.get(strongest.about(), Villages.VILLAGER);
        String who = about != null ? about.name().split(" ")[0] : Players.isPlayer(ctx, strongest.about()) ? "you" : "someone";
        String what = switch (strongest.kind().path()) {
            case "argued" -> "I had words with " + who + ".";
            case "made_a_friend" -> who + " and I have become good friends.";
            case "made_a_rival" -> "I can't stand " + who + ".";
            case "met_a_friend" -> "I had a lovely evening with " + who + ".";
            case "was_stood_up" -> who + " didn't show up when we'd arranged to meet.";
            case "got_a_gift" -> "I got a lovely gift from " + who + ".";
            default -> "Talked with " + who + ".";
        };
        return mood + " " + what;
    }

    /** Books a tavern visit with the player later today, if the villager is free and likes them enough. */
    private static String invite(SimContext ctx, EntityId villager, EntityId player) {
        Villager v = ctx.get(villager, Villages.VILLAGER);
        if (ctx.relationships().friendship(villager, player) < INVITE_FRIENDSHIP) return "I don't really know you...";
        if (hasAppointmentToday(ctx, villager, player)) return "We're already meeting today, remember?";
        Optional<EntityId> tavern = Villages.findService(ctx, v.village(), "drink");
        if (tavern.isEmpty()) return "Where would we even go?";
        Optional<Advertisement> drink = Buildings.type(ctx, tavern.get()).advertisement("drink");
        if (drink.isEmpty()) return "Where would we even go?";
        double[] point = Choices.point(ctx, v, tavern.get(), drink.get());
        long now = ctx.now();
        long first = (now / H + 1) * H;
        for (int tries = 0; tries < 6; tries++) {
            long start = first + tries * H;
            PlanEntry entry = PlanEntry.stay(start, start + H + H / 2, drink.get().activity(), tavern.get(), point, Optional.of("drink"));
            Optional<Plan> booked = Plans.commit(ctx.get(villager, Plans.PLAN), entry, now);
            if (booked.isPresent()) {
                ctx.set(villager, Plans.PLAN, booked.get());
                List<Appointment> list = new ArrayList<>(Appointments.of(ctx, villager));
                list.add(new Appointment(SimRandom.hash(v.seed(), player.raw(), start), player, tavern.get(), "drink",
                        entry.start(), entry.end(), false));
                ctx.set(villager, Social.APPOINTMENTS, List.copyOf(list));
                ctx.events().record(Social.EVENT_APPOINTMENT_MADE, villager, 0, List.of(player),
                        "Agreed to meet " + Players.name(ctx, player) + " at the tavern");
                return "I'd love to! See you at the tavern at " + SimTime.describe(start).substring(SimTime.describe(start).indexOf(',') + 2) + ".";
            }
        }
        return "Sorry, I'm busy today. Another time?";
    }
}
