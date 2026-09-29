package com.ewitulsk.villagersimulator.content.buildings;

import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;
import java.util.Optional;

/**
 * What a building offers visitors (docs/DESIGN.md §9.1, Sims-style smart objects). Villagers score the advertisements
 * they can reach against their needs and pick one; adding a building type means writing its advertisements, with no
 * villager AI changes.
 *
 * <pre>{@code
 * { "id": "drink", "activity": "villagersimulator:drink", "point": "service", "duration": "1h",
 *   "needs": { "social": 35, "fun": 30 },
 *   "condition": "hour() >= 11",
 *   "score": "need('social') < 30 ? 10 : 0",
 *   "effects": [ { "type": "add_modifier", "stat": "fun_decay", "mult": -0.3, "duration": "4h" } ],
 *   "consumes": { "bread": 1 } }
 * }</pre>
 *
 * @param point     point kind the visitor goes to; {@code bed} at the visitor's own home means their bed
 * @param needs     need gains for the full duration, used both for scoring and as the effect
 * @param score     optional expression added to the utility score
 * @param condition optional condition; the advertisement is only offered when it holds
 * @param effects   optional extra effects applied at the end, scaled by the fraction completed
 * @param consumes  goods taken from the building's stock; offered only while in stock
 */
public record Advertisement(String id, Id activity, String point, String duration, Map<String, Double> needs,
                            Optional<String> score, Optional<JsonElement> condition, Optional<JsonElement> effects,
                            Map<String, Integer> consumes) {

    public static final Codec<JsonElement> JSON = Codec.PASSTHROUGH.xmap(
            d -> d.convert(JsonOps.INSTANCE).getValue(), j -> new Dynamic<>(JsonOps.INSTANCE, j));

    public static final Codec<Advertisement> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Advertisement::id),
            Id.CODEC.fieldOf("activity").forGetter(Advertisement::activity),
            Codec.STRING.optionalFieldOf("point", BuildingType.SERVICE).forGetter(Advertisement::point),
            Codec.STRING.optionalFieldOf("duration", "1h").forGetter(Advertisement::duration),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("needs", Map.of()).forGetter(Advertisement::needs),
            Codec.STRING.optionalFieldOf("score").forGetter(Advertisement::score),
            JSON.optionalFieldOf("condition").forGetter(Advertisement::condition),
            JSON.optionalFieldOf("effects").forGetter(Advertisement::effects),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("consumes", Map.of()).forGetter(Advertisement::consumes)
    ).apply(i, Advertisement::new));

    public long durationTicks() {
        return SimTime.parseDuration(duration);
    }
}
