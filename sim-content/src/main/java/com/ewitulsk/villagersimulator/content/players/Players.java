package com.ewitulsk.villagersimulator.content.players;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.component.SparseComponent;
import com.ewitulsk.villagersimulator.api.sim.registry.RegistryKey;
import com.ewitulsk.villagersimulator.content.VS;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Players as sim agents (docs/DESIGN.md §11): a record per player that villagers can know, like, gossip about and
 * meet. Players have no plan; the bridge reports where they are.
 */
public final class Players {
    /** A player's identity. */
    public record PlayerRecord(String uuid, String name) {
        public static final Codec<PlayerRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("uuid").forGetter(PlayerRecord::uuid),
                Codec.STRING.fieldOf("name").forGetter(PlayerRecord::name)
        ).apply(i, PlayerRecord::new));
    }

    /** Which building the player is in, since when. */
    public record Presence(EntityId venue, long since, String ad) {
        public static final Codec<Presence> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityId.CODEC.fieldOf("venue").forGetter(Presence::venue),
                Codec.LONG.fieldOf("since").forGetter(Presence::since),
                Codec.STRING.fieldOf("ad").forGetter(Presence::ad)
        ).apply(i, Presence::new));
    }

    /**
     * What an item is worth as a gift ({@code data/<ns>/villagersimulator/gift_values/*.json}). Items not listed are
     * worth {@link #DEFAULT_GIFT}. Each villager's taste scales the value.
     */
    public record GiftValue(List<String> items, double value) {
        public static final Codec<GiftValue> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("items").forGetter(GiftValue::items),
                Codec.DOUBLE.fieldOf("value").forGetter(GiftValue::value)
        ).apply(i, GiftValue::new));
    }

    public static final SparseComponent<PlayerRecord> PLAYER = new SparseComponent<>(VS.id("player"), 1, PlayerRecord.CODEC);
    public static final SparseComponent<Presence> PRESENCE = new SparseComponent<>(VS.id("presence"), 1, Presence.CODEC);
    /** On villagers: when each player last used each dialogue option ({@code "<player>:<option>"} → time). */
    public static final SparseComponent<Map<String, Long>> TALKS =
            new SparseComponent<>(VS.id("player_talks"), 1, Codec.unboundedMap(Codec.STRING, Codec.LONG));
    public static final RegistryKey<GiftValue> GIFTS = new RegistryKey<>(VS.id("gift_value"), "gift_values", GiftValue.CODEC);

    public static final Id VISIT = VS.id("player_visit");
    public static final Id LISTEN = VS.id("listen");
    public static final double DEFAULT_GIFT = 1;

    public static final Id EVENT_GIFT = VS.id("gift");
    public static final Id MEM_GIFT = VS.id("got_a_gift");
    public static final Id MEM_CHAT = VS.id("chatted_with_player");

    private Players() {}

    public static boolean isPlayer(SimContext ctx, EntityId e) {
        return !e.isNone() && ctx.has(e, PLAYER);
    }

    /** The sim entity of the player with {@code uuid}, or {@link EntityId#NONE}. */
    public static EntityId find(SimContext ctx, String uuid) {
        AtomicReference<EntityId> out = new AtomicReference<>(EntityId.NONE);
        ctx.forEach(PLAYER, (id, p) -> {
            if (p.uuid().equals(uuid)) out.set(id);
        });
        return out.get();
    }

    public static String name(SimContext ctx, EntityId player) {
        PlayerRecord p = ctx.get(player, PLAYER);
        return p == null ? "someone" : p.name();
    }

    /** Gift value of an item id before the villager's taste. */
    public static double giftValue(SimContext ctx, String item) {
        var registry = ctx.registry(GIFTS);
        for (Id id : registry.ids()) {
            GiftValue g = registry.get(id);
            if (g.items().contains(item)) return g.value();
        }
        return DEFAULT_GIFT;
    }

    /** Which gift-values entry an item belongs to (the villager's taste is per entry), or the item itself. */
    public static String giftCategory(SimContext ctx, String item) {
        var registry = ctx.registry(GIFTS);
        for (Id id : registry.ids()) if (registry.get(id).items().contains(item)) return id.toString();
        return item;
    }
}
