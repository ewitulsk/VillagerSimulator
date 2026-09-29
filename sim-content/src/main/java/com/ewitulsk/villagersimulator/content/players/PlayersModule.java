package com.ewitulsk.villagersimulator.content.players;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.activity.EmbodiedBehaviors;
import com.ewitulsk.villagersimulator.api.sim.command.SimCommand;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.api.sim.social.Relation;
import com.ewitulsk.villagersimulator.content.VS;
import com.ewitulsk.villagersimulator.content.plans.BasicActivities;
import com.ewitulsk.villagersimulator.content.plans.PlansModule;
import com.ewitulsk.villagersimulator.content.social.Memories;
import com.ewitulsk.villagersimulator.content.social.Personality;
import com.ewitulsk.villagersimulator.content.social.Social;
import com.ewitulsk.villagersimulator.content.social.SocialModule;
import com.ewitulsk.villagersimulator.content.villages.Villager;
import com.ewitulsk.villagersimulator.content.villages.Villages;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Players as sim agents: presence, dialogue, gifts and gossip about players. */
public final class PlayersModule implements SimModule {
    public static final Id ID = VS.id("players");
    /** How fast opinions about a player spread between villagers who talk (per interaction, before trust). */
    public static final double GOSSIP_RATE = 0.15;

    @Override
    public Id id() {
        return ID;
    }

    @Override
    public Set<Id> dependencies() {
        return Set.of(PlansModule.ID, SocialModule.ID);
    }

    @Override
    public void register(SimRegistrar r) {
        r.component(Players.PLAYER);
        r.component(Players.PRESENCE);
        r.component(Players.TALKS);
        r.registry(Players.GIFTS);
        r.activity(new BasicActivities.Simple(Players.VISIT, "Visiting", EmbodiedBehaviors.IDLE, false));
        r.activity(new BasicActivities.Simple(Players.LISTEN, "Talking with you", EmbodiedBehaviors.IDLE, false));
        r.subscribe(Social.Interacted.class, (ctx, e) -> gossip(ctx, e.a(), e.b()));
    }

    /**
     * Gossip v1 (docs/DESIGN.md §8.3): villagers who spend time together share what they think of players. Each
     * listener's opinion moves toward the teller's, weighted by how much they trust each other. Villagers who never
     * met a player end up with an opinion of them anyway: reputation spreads.
     */
    static void gossip(SimContext ctx, EntityId a, EntityId b) {
        if (Players.isPlayer(ctx, a) || Players.isPlayer(ctx, b)) return;
        float trust = ctx.relationships().friendship(a, b);
        if (trust <= -10) return;
        double weight = GOSSIP_RATE * Math.max(0.1, Math.min(1, (trust + 30) / 100));
        Map<EntityId, Float> fromA = playerOpinions(ctx, a), fromB = playerOpinions(ctx, b);
        Map<EntityId, Float> changesForB = new LinkedHashMap<>(), changesForA = new LinkedHashMap<>();
        fromA.forEach((p, op) -> changesForB.put(p, (float) ((op - ctx.relationships().friendship(b, p)) * weight)));
        fromB.forEach((p, op) -> changesForA.put(p, (float) ((op - ctx.relationships().friendship(a, p)) * weight)));
        changesForB.forEach((p, d) -> ctx.relationships().changeFriendship(b, p, d));
        changesForA.forEach((p, d) -> ctx.relationships().changeFriendship(a, p, d));
    }

    private static Map<EntityId, Float> playerOpinions(SimContext ctx, EntityId v) {
        Map<EntityId, Float> out = new LinkedHashMap<>();
        for (Relation r : ctx.relationships().of(v)) {
            if (Players.isPlayer(ctx, r.other()) && Math.abs(r.friendship()) >= 1) out.put(r.other(), r.friendship());
        }
        return out;
    }

    /** A player gives a villager an item. Calls back with the villager's reaction. */
    public record Gift(EntityId villager, EntityId player, String item, Consumer<String> reply) implements SimCommand {
        @Override
        public void apply(SimContext ctx) {
            reply.accept(give(ctx, villager, player, item));
        }
    }

    static String give(SimContext ctx, EntityId villager, EntityId player, String item) {
        Villager v = ctx.get(villager, Villages.VILLAGER);
        if (v == null || !Players.isPlayer(ctx, player)) return "...";
        double value = Players.giftValue(ctx, item);
        double taste = SimRandom.unit(v.seed(), SimRandom.salt(Players.giftCategory(ctx, item)));
        double kindness = Personality.get(ctx, villager, Personality.KINDNESS);
        double delta = value >= 0 ? value * (0.4 + 1.2 * taste) * (0.7 + 0.6 * kindness) : value * (1.3 - 0.6 * kindness);
        if (!fresh(ctx, villager, player)) delta *= 0.3;
        ctx.relationships().changeFriendship(villager, player, (float) delta);
        long record = ctx.events().record(Players.EVENT_GIFT, player, 0, List.of(villager),
                Players.name(ctx, player) + " gave " + v.name() + " " + item);
        Memories.add(ctx, villager, Players.MEM_GIFT, record, player, (float) Math.max(-6, Math.min(6, delta / 2)), SimTime.days(2));
        if (delta >= 8) return "I love it! Thank you so much!";
        if (delta >= 3) return "Thank you, that's lovely.";
        if (delta > 0) return "Oh... thanks.";
        return "Why would you give me this?";
    }

    /** Gifts more than once a day are worth less. */
    private static boolean fresh(SimContext ctx, EntityId villager, EntityId player) {
        Map<String, Long> talks = ctx.get(villager, Players.TALKS);
        Map<String, Long> next = new HashMap<>(talks == null ? Map.of() : talks);
        String key = player.raw() + ":gift";
        Long last = next.put(key, ctx.now());
        ctx.set(villager, Players.TALKS, Map.copyOf(next));
        return last == null || ctx.now() - last >= SimTime.TICKS_PER_DAY;
    }
}
