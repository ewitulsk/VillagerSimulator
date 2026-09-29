package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioDefinition;
import com.ewitulsk.villagersimulator.api.sim.scenario.ScenarioResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 7: the built-in scenarios pass, failures are reported, and the expression reference is up to date. */
class ScenariosAndReferenceTest {
    @Test
    void builtinScenariosPass() {
        for (ScenarioDefinition d : BuiltinScenarios.all()) {
            ScenarioResult r = ScenarioRunner.run(d);
            assertTrue(r.passed(), String.join("\n", r.lines()));
        }
    }

    @Test
    void failingChecksAndErrorsAreReported() {
        ScenarioResult failed = ScenarioRunner.run(new ScenarioDefinition("bad", "", s -> s.expect("impossible", false)));
        assertFalse(failed.passed());
        ScenarioResult threw = ScenarioRunner.run(new ScenarioDefinition("boom", "", s -> s.warp("not a duration")));
        assertFalse(threw.passed());
        assertTrue(threw.error() != null && threw.error().contains("duration"), threw.error());
    }

    @Test
    void expressionReferenceIsUpToDate() throws IOException {
        Path doc = Path.of("..", "docs", "EXPRESSIONS.md");
        String expected = ExpressionReference.markdown();
        assertEquals(expected, Files.exists(doc) ? Files.readString(doc).replace("\r\n", "\n") : "",
                "docs/EXPRESSIONS.md is stale: run ./gradlew :sim-harness:expressionReference");
        assertFalse(expected.contains("|  |"), "every function has a description");
    }
}
