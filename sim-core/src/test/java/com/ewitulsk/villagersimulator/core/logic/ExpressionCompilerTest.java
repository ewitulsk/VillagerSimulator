package com.ewitulsk.villagersimulator.core.logic;

import com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv;
import com.ewitulsk.villagersimulator.api.sim.expr.ExprType;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException;
import com.ewitulsk.villagersimulator.api.sim.expr.ExpressionFunction;
import com.ewitulsk.villagersimulator.core.SimWorld;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionCompilerTest {
    private final SimWorld world = SimWorld.builder().build();
    private final ExprEnv env = LogicImpl.emptyEnv(world);

    private double num(String src) {
        return world.logic().expression(src, ExprType.NUMBER).number(env);
    }

    private boolean bool(String src) {
        return world.logic().expression(src, ExprType.BOOL).bool(env);
    }

    private ExpressionException error(String src, ExprType type) {
        return assertThrows(ExpressionException.class, () -> world.logic().expression(src, type));
    }

    @Test
    void arithmeticFollowsPrecedence() {
        assertEquals(7, num("1 + 2 * 3"));
        assertEquals(9, num("(1 + 2) * 3"));
        assertEquals(-4, num("-2 * 2"));
        assertEquals(1, num("7 % 3"));
        assertEquals(2.5, num("5 / 2"));
        assertEquals(0, num("5 / 0"), "division by zero is 0, not infinity");
    }

    @Test
    void booleansComparisonsAndTernary() {
        assertTrue(bool("1 < 2 && 2 <= 2"));
        assertTrue(bool("!(3 > 4) || false"));
        assertTrue(bool("'a' == 'a' && 'a' != 'b'"));
        assertEquals(10, num("3 > 2 ? 10 : 20"));
        assertEquals(3, num("false ? 1 : true ? 3 : 4"), "ternary is right-associative");
    }

    @Test
    void builtInFunctions() {
        assertEquals(2, num("min(2, 5)"));
        assertEquals(5, num("max(2, 5)"));
        assertEquals(10, num("clamp(15, 0, 10)"));
        assertEquals(3, num("abs(-3)"));
        assertEquals(6, num("hour()"), "tick 0 is 06:00");
        double r = num("random()");
        assertTrue(r >= 0 && r < 1);
        assertEquals(r, num("random()"), "deterministic");
    }

    @Test
    void errorsAreReportedWithPositions() {
        ExpressionException e = error("1 + true", ExprType.NUMBER);
        assertEquals(4, e.position(), "points at the offending operand");
        assertTrue(e.getMessage().contains("'+' needs a number"), e.getMessage());

        assertTrue(error("nope(1)", ExprType.NUMBER).getMessage().contains("Unknown function 'nope'"));
        assertTrue(error("min(1)", ExprType.NUMBER).getMessage().contains("takes 2 argument(s)"));
        assertTrue(error("'open", ExprType.STRING).getMessage().contains("Unclosed string"));
        assertTrue(error("1 +", ExprType.NUMBER).getMessage().contains("end of expression"));
        assertTrue(error("1 2", ExprType.NUMBER).getMessage().contains("Expected end of expression"));
        assertTrue(error("1 < 2", ExprType.NUMBER).getMessage().contains("Expected a number expression but got bool"));
        assertTrue(error("true ? 1 : 'x'", ExprType.NUMBER).getMessage().contains("same type"));
        assertTrue(error("min", ExprType.NUMBER).getMessage().contains("is a function"));
    }

    @Test
    void registeredFunctionsReceiveArgumentsAndConstants() {
        LogicImpl logic = new LogicImpl();
        logic.function(ExpressionFunction.number("twice", List.of(ExprType.NUMBER), (e, a) -> a.number(0, e) * 2));
        logic.function(ExpressionFunction.bool("is_named", List.of(ExprType.STRING), (e, a) -> "bob".equals(a.constant(0))));
        assertEquals(8, logic.expression("twice(twice(2))", ExprType.NUMBER).number(env));
        assertTrue(logic.expression("is_named('bob')", ExprType.BOOL).bool(env));
        assertFalse(logic.expression("is_named('amy')", ExprType.BOOL).bool(env));
    }

    @Test
    void compiledExpressionsAreCached() {
        assertTrue(world.logic().expression("1 + 1", ExprType.NUMBER) == world.logic().expression("1 + 1", ExprType.NUMBER));
    }
}
