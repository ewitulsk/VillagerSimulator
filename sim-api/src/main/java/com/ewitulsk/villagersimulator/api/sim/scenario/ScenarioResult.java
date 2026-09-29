package com.ewitulsk.villagersimulator.api.sim.scenario;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * How a scenario went.
 *
 * @param error  the exception message if the body threw, else {@code null}
 * @param simTicks sim ticks the scenario ran
 * @param wallMillis wall-clock time it took
 */
@ApiStatus.Experimental
public record ScenarioResult(String name, List<SimScenario.Check> checks, String error, long simTicks, long wallMillis) {
    public boolean passed() {
        return error == null && !checks.isEmpty() && checks.stream().allMatch(SimScenario.Check::passed);
    }

    /** One line per check, plus a summary, for chat and logs. */
    public List<String> lines() {
        List<String> out = new java.util.ArrayList<>();
        out.add(String.format(java.util.Locale.ROOT, "Scenario %s %s: %d/%d checks in %d sim ticks (%d ms)", name,
                passed() ? "PASSED" : "FAILED", checks.stream().filter(SimScenario.Check::passed).count(), checks.size(),
                simTicks, wallMillis));
        for (SimScenario.Check c : checks) out.add((c.passed() ? "  ok   " : "  FAIL ") + c.description());
        if (error != null) out.add("  error: " + error);
        if (checks.isEmpty() && error == null) out.add("  (no checks recorded)");
        return out;
    }
}
