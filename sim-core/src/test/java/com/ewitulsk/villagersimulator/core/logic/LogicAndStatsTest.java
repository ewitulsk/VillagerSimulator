package com.ewitulsk.villagersimulator.core.logic;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException;
import com.ewitulsk.villagersimulator.api.sim.logic.EffectEnv;
import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.api.sim.module.SimRegistrar;
import com.ewitulsk.villagersimulator.api.sim.stat.Modifier;
import com.ewitulsk.villagersimulator.api.sim.stat.StatType;
import com.ewitulsk.villagersimulator.core.SimWorld;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicAndStatsTest {
    private static final Id SPEED = Id.of("villagersimulator", "speed");

    private static final SimModule STATS = new SimModule() {
        @Override
        public Id id() {
            return Id.of("test", "stats");
        }

        @Override
        public void register(SimRegistrar r) {
            r.stat(new StatType(SPEED, 10));
        }
    };

    private final SimWorld world = SimWorld.builder().module(STATS).build();

    private boolean test(String json) {
        return world.logic().condition(JsonParser.parseString(json)).test(LogicImpl.emptyEnv(world));
    }

    @Test
    void conditionTypes() {
        assertTrue(test("\"1 < 2\""), "a string is an expr condition");
        assertTrue(test("{\"type\": \"expr\", \"value\": \"true\"}"));
        assertFalse(test("{\"type\": \"not\", \"condition\": true}"));
        assertTrue(test("{\"type\": \"all_of\", \"conditions\": [\"true\", {\"type\": \"any_of\", \"conditions\": [false, true]}]}"));
        assertTrue(test("{\"type\": \"time_between\", \"from\": 5, \"to\": 7}"), "06:00 is between 5 and 7");
        assertTrue(test("{\"type\": \"time_between\", \"from\": 22, \"to\": 7}"), "wraps past midnight");
        assertFalse(test("{\"type\": \"time_between\", \"from\": 8, \"to\": 20}"));
        assertThrows(ExpressionException.class, () -> test("{\"type\": \"no_such_type\"}"));
    }

    @Test
    void modifiersStackAndExpire() {
        EntityId e = world.create();
        assertEquals(10, world.stats().value(e, SPEED));
        world.stats().add(e, new Modifier(SPEED, Id.of("test", "boots"), 5, 0, Long.MAX_VALUE));
        world.stats().add(e, new Modifier(SPEED, Id.of("test", "tired"), 0, -0.5, SimTime.hours(1)));
        assertEquals((10 + 5) * 0.5, world.stats().value(e, SPEED));

        world.stats().add(e, new Modifier(SPEED, Id.of("test", "boots"), 2, 0, Long.MAX_VALUE));
        assertEquals((10 + 2) * 0.5, world.stats().value(e, SPEED), "same source replaces");

        world.advanceTo(SimTime.hours(1));
        assertEquals(12, world.stats().value(e, SPEED), "expired modifier no longer applies");
        assertEquals(1, world.stats().modifiers(e).size());
    }

    @Test
    void addModifierEffect() {
        EntityId e = world.create();
        world.logic().effect(JsonParser.parseString(
                        "[{\"type\": \"add_modifier\", \"stat\": \"speed\", \"add\": 3, \"duration\": \"2h\"}]"))
                .apply(EffectEnv.of(world, e, EntityId.NONE, null, 1));
        assertEquals(13, world.stats().value(e, SPEED));
        world.advanceTo(SimTime.hours(2));
        assertEquals(10, world.stats().value(e, SPEED));
    }

    @Test
    void statFunctionReadsTheActor() {
        EntityId e = world.create();
        world.stats().add(e, new Modifier(SPEED, Id.of("test", "x"), 1, 0, Long.MAX_VALUE));
        ExprEnv env = ExprEnv.of(world, e, EntityId.NONE, null);
        assertEquals(11, world.logic().expression("stat('speed')", com.ewitulsk.villagersimulator.api.sim.expr.ExprType.NUMBER).number(env));
    }
}
