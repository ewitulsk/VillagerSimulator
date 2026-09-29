package com.ewitulsk.villagersimulator.core.logic;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprType;
import com.ewitulsk.villagersimulator.api.sim.expr.Expression;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.api.sim.logic.Condition;
import com.ewitulsk.villagersimulator.api.sim.logic.Effect;
import com.ewitulsk.villagersimulator.api.sim.logic.Logic;
import com.ewitulsk.villagersimulator.api.sim.logic.LogicFactories;
import com.ewitulsk.villagersimulator.api.sim.stat.Modifier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compiles expressions, conditions and effects, with caches cleared on data reload. */
public final class LogicImpl implements Logic {
    public static final String NAMESPACE = "villagersimulator";

    private final Map<String, ExpressionFunction> functions = new LinkedHashMap<>();
    private final Map<Id, LogicFactories.ConditionFactory> conditionTypes = new LinkedHashMap<>();
    private final Map<Id, LogicFactories.EffectFactory> effectTypes = new LinkedHashMap<>();
    // Shards compile in parallel. Plain get/put (not computeIfAbsent): compiling a condition compiles expressions,
    // and ConcurrentHashMap doesn't allow nested updates. Compiling the same thing twice is harmless.
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();
    private final Map<String, Condition> conditions = new ConcurrentHashMap<>();
    private final Map<String, Effect> effects = new ConcurrentHashMap<>();
    private final ExpressionCompiler compiler = new ExpressionCompiler(functions);
    // Identity caches in front of the string-keyed ones: definitions hand in the same String/JsonElement objects
    // every time, so the hot path skips building the key (docs/ROADMAP.md Phase 6).
    private final Map<ExprType, com.ewitulsk.villagersimulator.api.sim.util.IdentityCache<String, Expression>> expressionsById =
            new java.util.EnumMap<>(ExprType.class);
    private final com.ewitulsk.villagersimulator.api.sim.util.IdentityCache<JsonElement, Condition> conditionsById =
            new com.ewitulsk.villagersimulator.api.sim.util.IdentityCache<>(4096);
    private final com.ewitulsk.villagersimulator.api.sim.util.IdentityCache<JsonElement, Effect> effectsById =
            new com.ewitulsk.villagersimulator.api.sim.util.IdentityCache<>(4096);

    public LogicImpl() {
        for (ExprType t : ExprType.values()) expressionsById.put(t, new com.ewitulsk.villagersimulator.api.sim.util.IdentityCache<>(4096));
        registerBuiltins();
    }

    public void function(ExpressionFunction fn) {
        if (functions.putIfAbsent(fn.name(), fn) != null) throw new IllegalArgumentException("Duplicate function " + fn.name());
    }

    public void condition(Id type, LogicFactories.ConditionFactory factory) {
        if (conditionTypes.putIfAbsent(type, factory) != null) throw new IllegalArgumentException("Duplicate condition type " + type);
    }

    public void effect(Id type, LogicFactories.EffectFactory factory) {
        if (effectTypes.putIfAbsent(type, factory) != null) throw new IllegalArgumentException("Duplicate effect type " + type);
    }

    public void clearCaches() {
        expressionsById.values().forEach(com.ewitulsk.villagersimulator.api.sim.util.IdentityCache::clear);
        conditionsById.clear();
        effectsById.clear();
        expressions.clear();
        conditions.clear();
        effects.clear();
    }

    @Override
    public Collection<ExpressionFunction> functions() {
        return Collections.unmodifiableCollection(functions.values());
    }

    @Override
    public Expression expression(String source, ExprType expected) {
        return expressionsById.get(expected).get(source, s -> compileExpression(s, expected));
    }

    private Expression compileExpression(String source, ExprType expected) {
        String key = expected + "|" + source;
        Expression e = expressions.get(key);
        if (e == null) {
            e = compiler.compile(source, expected);
            Expression raced = expressions.putIfAbsent(key, e);
            if (raced != null) e = raced;
        }
        return e;
    }

    @Override
    public Condition condition(JsonElement json) {
        return conditionsById.get(json, this::compileCondition);
    }

    private Condition compileCondition(JsonElement json) {
        String key = json.toString();
        Condition c = conditions.get(key);
        if (c == null) {
            c = buildCondition(json);
            Condition raced = conditions.putIfAbsent(key, c);
            if (raced != null) c = raced;
        }
        return c;
    }

    @Override
    public Effect effect(JsonElement json) {
        return effectsById.get(json, this::compileEffect);
    }

    private Effect compileEffect(JsonElement json) {
        String key = json.toString();
        Effect e = effects.get(key);
        if (e == null) {
            e = buildEffect(json);
            Effect raced = effects.putIfAbsent(key, e);
            if (raced != null) e = raced;
        }
        return e;
    }

    private Condition buildCondition(JsonElement json) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            Expression e = expression(json.getAsString(), ExprType.BOOL);
            return e::bool;
        }
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
            boolean v = json.getAsBoolean();
            return env -> v;
        }
        JsonObject obj = object(json, "condition");
        Id type = type(obj);
        LogicFactories.ConditionFactory f = conditionTypes.get(type);
        if (f == null) throw new ExpressionException("Unknown condition type " + type);
        return f.create(obj, this);
    }

    private Effect buildEffect(JsonElement json) {
        if (json.isJsonArray()) {
            List<Effect> list = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray()) list.add(effect(e));
            return env -> list.forEach(e -> e.apply(env));
        }
        JsonObject obj = object(json, "effect");
        Id type = type(obj);
        LogicFactories.EffectFactory f = effectTypes.get(type);
        if (f == null) throw new ExpressionException("Unknown effect type " + type);
        return f.create(obj, this);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Parses a type id; a bare name means the {@code villagersimulator} namespace. */
    public static Id type(JsonObject obj) {
        JsonElement t = obj.get("type");
        if (t == null || !t.isJsonPrimitive()) throw new ExpressionException("Missing \"type\" in " + obj);
        String s = t.getAsString();
        return s.indexOf(':') < 0 ? Id.of(NAMESPACE, s) : Id.parse(s);
    }

    public static JsonObject object(JsonElement json, String what) {
        if (!json.isJsonObject()) throw new ExpressionException("Expected a " + what + " object but got " + json);
        return json.getAsJsonObject();
    }

    public static String string(JsonObject obj, String field) {
        JsonElement e = obj.get(field);
        if (e == null || !e.isJsonPrimitive()) throw new ExpressionException("Missing \"" + field + "\" in " + obj);
        return e.getAsString();
    }

    public static double number(JsonObject obj, String field, double fallback) {
        JsonElement e = obj.get(field);
        if (e == null) return fallback;
        if (!e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber()) throw new ExpressionException("\"" + field + "\" must be a number in " + obj);
        return e.getAsDouble();
    }

    public static long duration(JsonObject obj, String field, long fallback) {
        JsonElement e = obj.get(field);
        if (e == null) return fallback;
        if (e.isJsonPrimitive() && ((JsonPrimitive) e).isNumber()) return e.getAsLong();
        try {
            return SimTime.parseDuration(e.getAsString());
        } catch (IllegalArgumentException ex) {
            throw new ExpressionException(ex.getMessage() + " in " + obj);
        }
    }

    private static JsonArray array(JsonObject obj, String field) {
        JsonElement e = obj.get(field);
        if (e == null || !e.isJsonArray()) throw new ExpressionException("\"" + field + "\" must be an array in " + obj);
        return e.getAsJsonArray();
    }

    private static Id core(String path) {
        return Id.of(NAMESPACE, path);
    }

    // ------------------------------------------------------------------------------------------------ built-ins

    private void registerBuiltins() {
        List<ExprType> n1 = List.of(ExprType.NUMBER);
        List<ExprType> n2 = List.of(ExprType.NUMBER, ExprType.NUMBER);
        function(ExpressionFunction.number("min", n2, (env, a) -> Math.min(a.number(0, env), a.number(1, env))).describe("The smaller of two numbers."));
        function(ExpressionFunction.number("max", n2, (env, a) -> Math.max(a.number(0, env), a.number(1, env))).describe("The larger of two numbers."));
        function(ExpressionFunction.number("clamp", List.of(ExprType.NUMBER, ExprType.NUMBER, ExprType.NUMBER),
                (env, a) -> Math.max(a.number(1, env), Math.min(a.number(2, env), a.number(0, env)))).describe("`clamp(x, lo, hi)`: x limited to the range [lo, hi]."));
        function(ExpressionFunction.number("abs", n1, (env, a) -> Math.abs(a.number(0, env))).describe("Absolute value."));
        function(ExpressionFunction.number("floor", n1, (env, a) -> Math.floor(a.number(0, env))).describe("Rounds down to a whole number."));
        /* Clock hour with fraction, 0-24 (06:30 is 6.5). */
        function(ExpressionFunction.number("hour", List.of(), (env, a) -> {
            long t = Math.floorMod(env.sim().now(), SimTime.TICKS_PER_DAY);
            return (t / (double) SimTime.TICKS_PER_HOUR + SimTime.DAY_START_HOUR) % 24;
        }).describe("Hour of the sim day as a fraction, 0 to 24 (tick 0 is 06:00)."));
        function(ExpressionFunction.number("day", List.of(), (env, a) -> SimTime.day(env.sim().now())).describe("Days since the sim started."));
        /* Deterministic: the same actor, venue and time always give the same number in [0, 1). */
        function(ExpressionFunction.number("random", List.of(),
                (env, a) -> SimRandom.unit(env.actor().raw(), env.venue().raw(), env.sim().now())).describe("A deterministic pseudo-random number in [0, 1) for this actor, venue and time."));
        function(ExpressionFunction.number("stat", List.of(ExprType.STRING), (env, a) -> {
            if (env.actor().isNone()) return 0;
            String name = a.string(0, env);
            return env.sim().stats().value(env.actor(), name.indexOf(':') < 0 ? core(name) : Id.parse(name));
        }).describe("`stat('id')`: the actor's value of a stat, after modifiers."));
        function(ExpressionFunction.bool("has_actor", List.of(), (env, a) -> !env.actor().isNone()).describe("True when the expression has an actor (a villager or player)."));
        function(ExpressionFunction.bool("has_venue", List.of(), (env, a) -> !env.venue().isNone()).describe("True when the expression has a venue (a building)."));

        condition(core("expr"), (obj, logic) -> {
            Expression e = logic.expression(string(obj, "value"), ExprType.BOOL);
            return e::bool;
        });
        condition(core("all_of"), (obj, logic) -> {
            List<Condition> list = new ArrayList<>();
            for (JsonElement e : array(obj, "conditions")) list.add(logic.condition(e));
            return env -> {
                for (Condition c : list) if (!c.test(env)) return false;
                return true;
            };
        });
        condition(core("any_of"), (obj, logic) -> {
            List<Condition> list = new ArrayList<>();
            for (JsonElement e : array(obj, "conditions")) list.add(logic.condition(e));
            return env -> {
                for (Condition c : list) if (c.test(env)) return true;
                return false;
            };
        });
        condition(core("not"), (obj, logic) -> {
            JsonElement inner = obj.get("condition");
            if (inner == null) throw new ExpressionException("\"not\" needs a \"condition\" in " + obj);
            Condition c = logic.condition(inner);
            return env -> !c.test(env);
        });
        /* Clock hours, wrapping past midnight: {"from": 22, "to": 6}. */
        condition(core("time_between"), (obj, logic) -> {
            double from = number(obj, "from", 0), to = number(obj, "to", 24);
            Expression hour = logic.expression("hour()", ExprType.NUMBER);
            return env -> {
                double h = hour.number(env);
                return from <= to ? h >= from && h < to : h >= from || h < to;
            };
        });

        effect(core("all"), (obj, logic) -> logic.effect(array(obj, "effects")));
        /* {"type": "add_modifier", "stat": "...", "add": 1, "mult": -0.5, "duration": "6h", "source": "..."} */
        effect(core("add_modifier"), (obj, logic) -> {
            String statName = string(obj, "stat");
            Id stat = statName.indexOf(':') < 0 ? core(statName) : Id.parse(statName);
            Id source = obj.has("source") ? Id.parse(string(obj, "source")) : core("effect");
            double add = number(obj, "add", 0), mult = number(obj, "mult", 0);
            long duration = duration(obj, "duration", -1);
            return env -> {
                EntityId target = env.actor();
                if (target.isNone() || !env.sim().alive(target)) return;
                long expires = duration < 0 ? Long.MAX_VALUE : env.sim().now() + duration;
                env.sim().stats().add(target, new Modifier(stat, source, add, mult, expires));
            };
        });
    }

    /** For tests and {@code /vs expr eval}: evaluate with no actor or venue. */
    public static ExprEnv emptyEnv(com.ewitulsk.villagersimulator.api.sim.SimContext ctx) {
        return ExprEnv.of(ctx, EntityId.NONE, EntityId.NONE, null);
    }
}
