package com.ewitulsk.villagersimulator.compat.kubejs;

import com.ewitulsk.villagersimulator.api.mod.SimAccess;
import com.ewitulsk.villagersimulator.api.mod.VillagerSimApi;
import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.core.VillageSummary;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException;
import com.ewitulsk.villagersimulator.api.sim.logic.EffectEnv;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The {@code VillagerSim} binding for server scripts (docs/ARCHITECTURE.md §10.3). Commands go to the sim and are
 * applied at its next boundary; reads come from the latest views; queries call back on the server thread. The sim
 * never waits for a script. Entity ids are the plain numbers events carry.
 *
 * <pre>{@code
 * VillagerSim.recordEvent(e.actor, 'mypack:blessed', 'Blessed by the priest')
 * VillagerSim.effect(e.actor, { type: 'add_modifier', stat: 'fun_decay', mult: -0.3, duration: '4h' })
 * VillagerSim.eval("need('hunger')", e.actor, value => console.info(value))
 * }</pre>
 */
public final class VillagerSimBinding {
    private static SimAccess sim() {
        return VillagerSimApi.server().orElseThrow(() -> new IllegalStateException("The Villager Simulator sim isn't running"));
    }

    /** True while the sim is running. */
    public boolean isRunning() {
        return VillagerSimApi.server().isPresent();
    }

    /** Sim time of the latest views, in sim ticks. */
    public long time() {
        return sim().time();
    }

    /** Every village: {@code id, name, x, z, population, mode} ({@code mode}: 0 detailed, 1 abstract, 2 coarse). */
    public List<Map<String, Object>> villages() {
        List<VillageSummary> list = sim().views().get(VillageSummary.VIEW);
        List<Map<String, Object>> out = new ArrayList<>();
        if (list != null) for (VillageSummary v : list) out.add(toMap(v));
        return out;
    }

    /** The village with this name, or {@code null}. */
    public Map<String, Object> village(String name) {
        List<VillageSummary> list = sim().views().get(VillageSummary.VIEW);
        if (list != null) for (VillageSummary v : list) if (v.name().equals(name)) return toMap(v);
        return null;
    }

    private static Map<String, Object> toMap(VillageSummary v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.village().raw());
        m.put("name", v.name());
        m.put("x", v.x());
        m.put("z", v.z());
        m.put("population", v.population());
        m.put("mode", v.mode());
        return m;
    }

    /** Tells every player on the server. */
    public void announce(String message) {
        sim().server().getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(message), false);
    }

    /** Logs an event record about {@code actor}; it comes back through {@code VillagerSimEvents.recorded}. */
    public void recordEvent(int actor, String type, String detail) {
        Id id = Id.parse(type);
        sim().submit(ctx -> {
            EntityId e = new EntityId(actor);
            if (ctx.alive(e)) ctx.events().record(id, e, detail);
        });
    }

    /** Changes how {@code a} feels about {@code b} (and {@code b} about {@code a}) by {@code amount}. */
    public void changeFriendship(int a, int b, double amount) {
        sim().submit(ctx -> {
            EntityId ea = new EntityId(a), eb = new EntityId(b);
            if (ctx.alive(ea) && ctx.alive(eb)) ctx.relationships().changeFriendship(ea, eb, (float) amount);
        });
    }

    /** Applies a sim effect, e.g. {@code {type: 'add_modifier', stat: 'fun_decay', mult: -0.3, duration: '4h'}}. */
    public void effect(int target, Object effect) {
        JsonElement json = toJson(effect);
        sim().submit(ctx -> {
            EntityId e = new EntityId(target);
            if (!ctx.alive(e)) return;
            try {
                ctx.logic().effect(json).apply(EffectEnv.of(ctx, e, EntityId.NONE, null, 1.0));
            } catch (ExpressionException | IllegalArgumentException ex) {
                VillagerSimKubeJSPlugin.LOG.warn("Script effect {} failed: {}", json, ex.getMessage());
            }
        });
    }

    /** Evaluates a VS expression with {@code actor} (0 for none) and calls back on the server thread with the value. */
    public void eval(String expression, int actor, Consumer<Object> callback) {
        SimAccess sim = sim();
        sim.query(ctx -> {
            EntityId who = new EntityId(actor);
            try {
                return ctx.logic().expression(expression, null).value(ExprEnv.of(ctx, ctx.alive(who) ? who : EntityId.NONE, EntityId.NONE, null));
            } catch (ExpressionException e) {
                return "error: " + e.getMessage();
            }
        }).thenAccept(value -> sim.server().execute(() -> callback.accept(value)));
    }

    /** Runs a registered scenario headless and calls back on the server thread with its result ({@code passed()}, {@code lines()}). */
    public void runScenario(String name, Consumer<Object> callback) {
        SimAccess sim = sim();
        sim.runScenario(name).whenComplete((result, error) -> sim.server().execute(
                () -> callback.accept(error != null ? "error: " + error.getMessage() : result)));
    }

    /** Converts a script value (objects, arrays, numbers, strings, booleans) to JSON. */
    static JsonElement toJson(Object o) {
        if (o == null) return JsonNull.INSTANCE;
        if (o instanceof JsonElement j) return j;
        if (o instanceof Map<?, ?> map) {
            JsonObject obj = new JsonObject();
            map.forEach((k, v) -> obj.add(String.valueOf(k), toJson(v)));
            return obj;
        }
        if (o instanceof Iterable<?> list) {
            JsonArray arr = new JsonArray();
            list.forEach(v -> arr.add(toJson(v)));
            return arr;
        }
        if (o instanceof Boolean b) return new JsonPrimitive(b);
        if (o instanceof Number n) return new JsonPrimitive(n);
        if (o instanceof CharSequence s) return new JsonPrimitive(s.toString());
        return new JsonPrimitive(o.toString());
    }
}
