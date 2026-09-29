package com.ewitulsk.villagersimulator.neoforge.gametest;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.core.ForceTierCommand;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.villages.VillageLayouts;
import com.ewitulsk.villagersimulator.content.villages.VillageQueries;
import com.ewitulsk.villagersimulator.neoforge.ModContent;
import com.ewitulsk.villagersimulator.neoforge.SimVillagerEntity;
import com.ewitulsk.villagersimulator.neoforge.world.BlueprintPlacer;
import com.ewitulsk.villagersimulator.neoforge.world.VanillaPoints;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.puppets;
import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.require;
import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.sim;

/** Bridge tests (namespace {@code villagersimulator_bridge}): puppets, tiers, never-saved entities. */
@GameTestHolder(BridgeGameTests.NS)
@PrefixGameTestTemplate(false)
public final class BridgeGameTests {
    public static final String NS = "villagersimulator_bridge";
    private static final String BATCH = "bridge";

    private BridgeGameTests() {}

    /** Promote → demote → promote leaves exactly one entity for the villager, never two. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void promoteDemotePromoteKeepsOnePuppet(GameTestHelper h) {
        AtomicReference<EntityId> villager = new AtomicReference<>();
        TestSupport.spawn(TestSupport.compactVillage(h, "Bridgeton", 11, 1, null), null)
                .thenCompose(village -> sim().runtime().query(VillageQueries.residents(village)))
                .thenAccept(list -> villager.set(list.get(0)));

        h.startSequence()
                .thenWaitUntil(() -> require(villager.get() != null, "villager spawned"))
                .thenExecute(() -> force(villager.get(), Tier.T0))
                .thenWaitUntil(() -> require(puppets(h, villager.get()) == 1, "puppet spawned at T0"))
                .thenExecuteFor(20, () -> require(puppets(h, villager.get()) == 1, "exactly one puppet"))
                .thenExecute(() -> force(villager.get(), Tier.T2))
                .thenWaitUntil(() -> require(puppets(h, villager.get()) == 0, "puppet removed at T2"))
                .thenExecute(() -> force(villager.get(), Tier.T0))
                .thenWaitUntil(() -> require(puppets(h, villager.get()) == 1, "puppet back at T0"))
                .thenExecuteFor(20, () -> require(puppets(h, villager.get()) == 1, "still exactly one puppet"))
                .thenExecute(() -> force(villager.get(), null))
                .thenSucceed();
    }

    /** A villager entity the bridge didn't spawn removes itself, and villager entities are never saved to chunks. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void orphanPuppetRemovesItselfAndIsNeverSaved(GameTestHelper h) {
        require(!ModContent.VILLAGER.get().canSerialize(), "villager entity type must not serialize");
        SimVillagerEntity orphan = ModContent.VILLAGER.get().create(h.getLevel());
        require(orphan != null, "created");
        require(!orphan.shouldBeSaved(), "puppets are never saved");
        BlockPos p = h.absolutePos(new BlockPos(8, 1, 8));
        orphan.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        h.getLevel().addFreshEntity(orphan);
        h.succeedWhen(() -> require(orphan.isRemoved(), "orphan removed"));
    }

    /**
     * Every Phase 0 blueprint loads and places, and its building type's points match the blocks: the anchor block is
     * at the anchor point and every bed point is a bed head.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public static void blueprintsPlaceAndMatchTheirPoints(GameTestHelper h) {
        var types = sim().registry(BuildingType.REGISTRY);
        for (var id : List.of(VillageLayouts.HOUSE, VillageLayouts.BAKERY, VillageLayouts.WELL)) {
            BuildingType type = types.get(id);
            BlockPos origin = h.absolutePos(new BlockPos(1, 0, 1));
            require(BlueprintPlacer.place(h.getLevel(), type, origin), "blueprint " + type.blueprint() + " placed");
            for (List<Integer> p : type.points(BuildingType.ANCHOR)) {
                BlockPos pos = origin.offset(p.get(0), p.get(1), p.get(2));
                require(h.getLevel().getBlockState(pos).is(ModContent.BUILDING_ANCHOR.get()), id + " anchor at " + p);
            }
            for (List<Integer> p : type.points(BuildingType.BED)) {
                BlockState bed = h.getLevel().getBlockState(origin.offset(p.get(0), p.get(1), p.get(2)));
                require(bed.getBlock() instanceof BedBlock && bed.getValue(BedBlock.PART) == BedPart.HEAD, id + " bed head at " + p);
            }
        }
        h.succeed();
    }

    /** Beds and workstations found in the blueprints match the points the building types list explicitly. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void vanillaBedsAndWorkstationsAreDetected(GameTestHelper h) {
        var types = sim().registry(BuildingType.REGISTRY);
        var kinds = VanillaPoints.pointBlocks(sim().data());
        for (var id : List.of(VillageLayouts.HOUSE, VillageLayouts.BAKERY)) {
            BuildingType type = types.get(id);
            var template = h.getLevel().getStructureManager().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    type.blueprint().namespace(), type.blueprint().path())).orElseThrow();
            var detected = VanillaPoints.detect(template, kinds);
            require(detected.get("bed").equals(sortedPoints(type.points(BuildingType.BED))), id + " beds: " + detected.get("bed"));
            if (!type.points(BuildingType.WORK).isEmpty()) {
                require(detected.get("work").equals(sortedPoints(type.points(BuildingType.WORK))), id + " work: " + detected.get("work"));
            }
        }
        h.succeed();
    }

    private static List<List<Integer>> sortedPoints(List<List<Integer>> points) {
        List<List<Integer>> out = new java.util.ArrayList<>(points);
        out.sort(java.util.Comparator.<List<Integer>>comparingInt(p -> p.get(0)).thenComparingInt(p -> p.get(1)).thenComparingInt(p -> p.get(2)));
        return out;
    }

    /** The puppet's synced appearance matches the sim's genes, and its behaviour byte follows the plan. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void puppetsCarryAppearanceAndBehaviour(GameTestHelper h) {
        AtomicReference<EntityId> villager = new AtomicReference<>();
        AtomicReference<Integer> genes = new AtomicReference<>();
        TestSupport.spawn(TestSupport.compactVillage(h, "Faceton", 29, 1, null), null)
                .thenCompose(village -> sim().runtime().query(ctx -> {
                    EntityId v = ctx.get(village, com.ewitulsk.villagersimulator.content.villages.Villages.VILLAGE).residents().get(0);
                    genes.set(com.ewitulsk.villagersimulator.content.villages.Appearance.genes(ctx, v));
                    return v;
                }))
                .thenAccept(v -> {
                    sim().runtime().submit(new ForceTierCommand(List.of(v), Tier.T0));
                    villager.set(v);
                });
        h.startSequence()
                .thenWaitUntil(() -> require(villager.get() != null && sim().bridge().puppet(villager.get()) != null, "embodied"))
                .thenWaitUntil(() -> {
                    SimVillagerEntity p = sim().bridge().puppet(villager.get());
                    require(p.appearance() == genes.get(), "appearance synced: " + p.appearance() + " vs " + genes.get());
                    require(p.appearance() != 0, "genes are set");
                })
                .thenExecute(() -> sim().runtime().submit(new ForceTierCommand(List.of(villager.get()), null)))
                .thenSucceed();
    }

    /**
     * A villager embodied at T0 goes far away (T3, coarse days) and comes back: its puppet reappears where the plan
     * says it is now, and its plan picks up at the entry for now.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void crossingTiersKeepsTheVillagerConsistent(GameTestHelper h) {
        AtomicReference<EntityId> villager = new AtomicReference<>();
        AtomicReference<double[]> planPos = new AtomicReference<>();
        AtomicReference<Boolean> entryOk = new AtomicReference<>();
        TestSupport.spawn(TestSupport.compactVillage(h, "Tierstead", 31, 1, null), null)
                .thenCompose(village -> sim().runtime().query(ctx -> ctx.get(village, com.ewitulsk.villagersimulator.content.villages.Villages.VILLAGE).residents().get(0)))
                .thenAccept(villager::set);
        h.startSequence()
                .thenWaitUntil(() -> require(villager.get() != null, "spawned"))
                .thenExecute(() -> force(villager.get(), Tier.T0))
                .thenWaitUntil(() -> require(sim().bridge().puppet(villager.get()) != null, "embodied"))
                .thenExecute(() -> {
                    force(villager.get(), Tier.T3);
                    sim().runtime().advanceTarget(com.ewitulsk.villagersimulator.api.sim.SimTime.hours(6));
                })
                .thenWaitUntil(() -> require(sim().bridge().puppet(villager.get()) == null, "puppet gone at T3"))
                .thenExecute(() -> force(villager.get(), Tier.T0))
                .thenWaitUntil(() -> require(sim().bridge().puppet(villager.get()) != null, "embodied again"))
                .thenExecute(() -> sim().runtime().query(ctx -> {
                    var e = com.ewitulsk.villagersimulator.content.plans.Plans.current(ctx, villager.get());
                    entryOk.set(e != null && (e.contains(ctx.now()) || e.end() == ctx.now()));
                    return com.ewitulsk.villagersimulator.content.plans.Plans.positionNow(ctx, villager.get());
                }).thenAccept(planPos::set))
                .thenWaitUntil(() -> require(planPos.get() != null, "queried"))
                .thenExecute(() -> {
                    require(Boolean.TRUE.equals(entryOk.get()), "plan resumed at the entry for now");
                    SimVillagerEntity p = sim().bridge().puppet(villager.get());
                    double d = Math.hypot(p.getX() - planPos.get()[0], p.getZ() - planPos.get()[2]);
                    require(d < 13, "puppet near its plan position (" + d + " blocks)");
                    force(villager.get(), null);
                })
                .thenSucceed();
    }

    private static void force(EntityId villager, Tier tier) {
        sim().runtime().submit(new ForceTierCommand(List.of(villager), tier));
    }
}
