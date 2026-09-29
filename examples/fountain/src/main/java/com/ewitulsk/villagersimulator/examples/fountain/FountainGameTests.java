package com.ewitulsk.villagersimulator.examples.fountain;

import com.ewitulsk.villagersimulator.api.mod.SimAccess;
import com.ewitulsk.villagersimulator.api.mod.VillagerSimApi;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The reference addon works in game (namespace {@code villagersimulator_fountain}): a village spawned with the
 * normal command includes a fountain (its {@code layout}), and within a sim-day villagers go there and make wishes
 * (its advertisement, point block, Activity and expression function). Uses only the public APIs and commands.
 */
@GameTestHolder(FountainGameTests.NS)
@PrefixGameTestTemplate(false)
public final class FountainGameTests {
    public static final String NS = "villagersimulator_fountain";

    private FountainGameTests() {}

    @GameTest(template = "empty", batch = "fountain", timeoutTicks = 100_000)
    public static void villagersMakeWishesAtTheFountain(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput()
                .withPosition(Vec3.atCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(8, 1, 8))));
        AtomicInteger wishes = new AtomicInteger(-1);
        h.startSequence()
                .thenWaitUntil(() -> require(VillagerSimApi.server().isPresent(), "sim running"))
                .thenExecute(() -> {
                    server.getCommands().performPrefixedCommand(source, "vs village spawn 16 Wishford");
                    server.getCommands().performPrefixedCommand(source, "vs time warp 1d");
                })
                .thenWaitUntil(() -> {
                    SimAccess sim = VillagerSimApi.server().orElseThrow();
                    sim.query(ctx -> ctx.events().ofType(FountainModule.EVENT_WISH).size()).thenAccept(wishes::set);
                    require(wishes.get() > 0, "villagers made wishes at the fountain: " + wishes.get());
                })
                .thenSucceed();
    }

    private static void require(boolean ok, String what) {
        if (!ok) throw new GameTestAssertException(what);
    }
}
