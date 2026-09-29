package com.ewitulsk.villagersimulator.neoforge.gametest;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.core.ForceTierCommand;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.buildings.Buildings;
import com.ewitulsk.villagersimulator.content.plans.BasicActivities;
import com.ewitulsk.villagersimulator.content.plans.PlanEntry;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.content.social.Social;
import com.ewitulsk.villagersimulator.content.villages.VillageQueries;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import com.ewitulsk.villagersimulator.neoforge.SimVillagerEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.require;
import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.sim;

/** Social tests (namespace {@code villagersimulator_social}). */
@GameTestHolder(SocialGameTests.NS)
@PrefixGameTestTemplate(false)
public final class SocialGameTests {
    public static final String NS = "villagersimulator_social";
    private static final String BATCH = "social";

    private SocialGameTests() {}

    /**
     * Two embodied villagers socialising at the same well are paired as a conversation, and their puppets turn to
     * face each other. Set up directly: both are sent to the well now (no waiting for their schedule).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void embodiedVillagersFaceTheirConversationPartner(GameTestHelper h) {
        AtomicReference<List<EntityId>> pair = new AtomicReference<>();
        TestSupport.spawn(TestSupport.compactVillage(h, "Chatham", 21, 2, null), null)
                .thenCompose(village -> sim().runtime().query(ctx -> {
                    List<EntityId> residents = ctx.get(village, Villages.VILLAGE).residents();
                    EntityId well = ctx.get(village, Villages.VILLAGE).buildings().get(0);
                    for (int i = 0; i < 2; i++) {
                        double[] p = Buildings.point(ctx, well, BuildingType.WANDER, i);
                        Plans.interrupt(ctx, residents.get(i), PlanEntry.stay(ctx.now(), ctx.now() + SimTime.hours(2),
                                BasicActivities.SOCIALIZE, well, p, Optional.of("gather")));
                    }
                    return residents;
                }))
                .thenAccept(r -> {
                    sim().runtime().submit(new ForceTierCommand(r, Tier.T0));
                    pair.set(r);
                });

        h.startSequence()
                .thenWaitUntil(() -> require(pair.get() != null, "villagers sent to the well"))
                .thenWaitUntil(() -> {
                    var talks = sim().runtime().views().get(Social.CONVERSATIONS);
                    require(talks != null && pair.get().get(1).equals(talks.get(pair.get().get(0))), "paired as a conversation");
                })
                .thenWaitUntil(() -> {
                    SimVillagerEntity a = sim().bridge().puppet(pair.get().get(0));
                    SimVillagerEntity b = sim().bridge().puppet(pair.get().get(1));
                    require(a != null && b != null, "both embodied");
                    require(facing(a, b) && facing(b, a), "facing each other");
                    require(a.getCustomName() != null && a.getCustomName().getString().contains("Chatting with"), "name tag says so");
                })
                .thenExecute(() -> sim().runtime().submit(new ForceTierCommand(pair.get(), null)))
                .thenSucceed();
    }

    /** {@code /vs inspect} includes the social lines. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void inspectShowsPersonalityAndRelationships(GameTestHelper h) {
        AtomicReference<List<String>> lines = new AtomicReference<>();
        TestSupport.spawn(TestSupport.compactVillage(h, "Inspectown", 3, 2, null), null)
                .thenCompose(village -> sim().runtime().query(ctx -> ctx.get(village, Villages.VILLAGE).residents().get(0)))
                .thenCompose(v -> sim().runtime().query(VillageQueries.inspect(v)))
                .thenAccept(lines::set);
        h.succeedWhen(() -> {
            require(lines.get() != null, "inspected");
            require(lines.get().stream().anyMatch(l -> l.startsWith("Personality:")), lines.get().toString());
        });
    }

    private static boolean facing(SimVillagerEntity from, SimVillagerEntity to) {
        double angle = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX())) - 90;
        return Math.abs(Mth.wrapDegrees(angle - from.getYHeadRot())) < 45;
    }
}
