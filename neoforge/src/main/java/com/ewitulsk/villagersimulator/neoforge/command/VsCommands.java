package com.ewitulsk.villagersimulator.neoforge.command;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimTime;
import com.ewitulsk.villagersimulator.api.sim.core.ForceTierCommand;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.content.buildings.BuildingType;
import com.ewitulsk.villagersimulator.content.villages.Names;
import com.ewitulsk.villagersimulator.content.villages.SpawnVillageCommand;
import com.ewitulsk.villagersimulator.content.villages.VillageLayouts;
import com.ewitulsk.villagersimulator.content.villages.VillageQueries;
import com.ewitulsk.villagersimulator.neoforge.SimVillagerEntity;
import com.ewitulsk.villagersimulator.neoforge.server.SimServer;
import com.ewitulsk.villagersimulator.neoforge.world.BlueprintPlacer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** {@code /vs}: debug and dev commands (docs/ARCHITECTURE.md §18). */
public final class VsCommands {
    private static final SimpleCommandExceptionType NOT_RUNNING =
            new SimpleCommandExceptionType(Component.literal("The sim isn't running"));
    private static final SimpleCommandExceptionType OVERWORLD_ONLY =
            new SimpleCommandExceptionType(Component.literal("Phase 0 villages live in the overworld"));
    private static final SimpleCommandExceptionType NO_VILLAGER =
            new SimpleCommandExceptionType(Component.literal("No sim villager within 32 blocks"));

    private VsCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vs").requires(s -> s.hasPermission(2))
                .then(Commands.literal("village")
                        .then(Commands.literal("spawn")
                                .executes(c -> spawnVillage(c, 8, null))
                                .then(Commands.argument("villagers", IntegerArgumentType.integer(1, 64))
                                        .executes(c -> spawnVillage(c, IntegerArgumentType.getInteger(c, "villagers"), null))
                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                .executes(c -> spawnVillage(c, IntegerArgumentType.getInteger(c, "villagers"),
                                                        StringArgumentType.getString(c, "name"))))))
                        .then(Commands.literal("list").executes(c -> reply(c, sim().runtime().query(VillageQueries.villages())))))
                .then(Commands.literal("inspect")
                        .executes(c -> inspect(c, nearestVillager(c.getSource())))
                        .then(Commands.argument("villager", EntityArgument.entity())
                                .executes(c -> inspect(c, EntityArgument.getEntity(c, "villager")))))
                .then(Commands.literal("time")
                        .then(Commands.literal("warp").then(Commands.argument("duration", StringArgumentType.word())
                                .executes(VsCommands::warp)))
                        .then(Commands.literal("status").executes(VsCommands::status)))
                .then(Commands.literal("tier").then(Commands.literal("force").then(Commands.literal("all")
                        .then(tierLiteral("t0", Tier.T0)).then(tierLiteral("t1", Tier.T1))
                        .then(tierLiteral("t2", Tier.T2)).then(tierLiteral("t3", Tier.T3))
                        .then(Commands.literal("auto").executes(c -> forceTier(c, null))))))
                .then(Commands.literal("expr").then(Commands.literal("eval").then(
                        Commands.argument("expression", StringArgumentType.greedyString()).executes(VsCommands::evalExpression))))
                .then(Commands.literal("problems").executes(VsCommands::problems))
                .then(Commands.literal("reputation").executes(VsCommands::reputation))
                .then(Commands.literal("debug").then(Commands.literal("overlay").executes(c -> {
                    boolean on = sim().bridge().toggleOverlay(c.getSource().getPlayerOrException());
                    c.getSource().sendSuccess(() -> Component.literal("Debug overlay " + (on ? "on: chunk tiers (T0 green, T1 yellow, T2 orange, T3 red), districts, routes" : "off")), false);
                    return 1;
                })))
                .then(Commands.literal("profile").executes(VsCommands::profile)
                        .then(Commands.literal("reset").executes(c -> {
                            sim().runtime().submitWorld(com.ewitulsk.villagersimulator.core.SimWorld::resetProfile);
                            c.getSource().sendSuccess(() -> Component.literal("Sim profile reset"), false);
                            return 1;
                        })))
                .then(Commands.literal("stress")
                        .then(Commands.literal("clear").executes(VsCommands::clearStress))
                        .then(Commands.argument("villagers", IntegerArgumentType.integer(1, 2_000_000))
                                .executes(c -> stress(c, IntegerArgumentType.getInteger(c, "villagers")))))
                .then(Commands.literal("scenario")
                        .then(Commands.literal("list").executes(VsCommands::listScenarios))
                        .then(Commands.literal("run").then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, builder) -> {
                                    SimServer s = SimServer.get();
                                    if (s != null) s.scenarios().all().forEach(d -> builder.suggest(d.name()));
                                    return builder.buildFuture();
                                })
                                .executes(c -> runScenario(c, StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("save").executes(VsCommands::save)));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> tierLiteral(String name, Tier tier) {
        return Commands.literal(name).executes(c -> forceTier(c, tier));
    }

    private static SimServer sim() throws CommandSyntaxException {
        SimServer s = SimServer.get();
        if (s == null) throw NOT_RUNNING.create();
        return s;
    }

    private static int spawnVillage(CommandContext<CommandSourceStack> c, int villagers, String name) throws CommandSyntaxException {
        SimServer sim = sim();
        CommandSourceStack source = c.getSource();
        ServerLevel level = source.getLevel();
        if (level.dimension() != Level.OVERWORLD) throw OVERWORLD_ONLY.create();
        BlockPos centre = BlockPos.containing(source.getPosition());
        long seed = level.random.nextLong();
        String villageName = name != null ? name : Names.pick(seed).split(" ")[1] + "ton";

        var types = sim.registry(BuildingType.REGISTRY);
        List<SpawnVillageCommand.Placement> placements = new ArrayList<>();
        // Up to 16 villagers get a hamlet; more get a two-district town.
        var layout = villagers > 16 ? VillageLayouts.town(types, centre.getX(), centre.getY(), centre.getZ(), villagers)
                : new VillageLayouts.Layout(VillageLayouts.hamlet(types, centre.getX(), centre.getY(), centre.getZ(), villagers), List.of());
        for (SpawnVillageCommand.Placement p : layout.placements()) {
            BuildingType type = types.get(p.type());
            int y = BlueprintPlacer.groundY(level, p.x(), p.z(), type);
            if (!BlueprintPlacer.place(level, type, new BlockPos(p.x(), y, p.z()))) {
                source.sendFailure(Component.literal("Missing blueprint " + type.blueprint()));
                return 0;
            }
            placements.add(new SpawnVillageCommand.Placement(p.type(), p.x(), y, p.z()));
        }
        int groundY = BlueprintPlacer.groundY(level, centre.getX(), centre.getZ(), types.get(VillageLayouts.WELL)) + 1;
        sim.runtime().submit(new SpawnVillageCommand(villageName, seed, centre.getX(), groundY, centre.getZ(), placements,
                layout.districts(), villagers, id -> sim.server().execute(() -> source.sendSuccess(() -> Component.literal(
                        "Founded " + villageName + " with " + villagers + " villagers (" + id + ")"), true))));
        return 1;
    }

    private static Entity nearestVillager(CommandSourceStack source) throws CommandSyntaxException {
        return source.getLevel().getEntitiesOfClass(SimVillagerEntity.class, AABB.ofSize(source.getPosition(), 64, 64, 64)).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(source.getPosition())))
                .orElseThrow(NO_VILLAGER::create);
    }

    private static int inspect(CommandContext<CommandSourceStack> c, Entity entity) throws CommandSyntaxException {
        if (!(entity instanceof SimVillagerEntity v)) throw NO_VILLAGER.create();
        return reply(c, sim().runtime().query(VillageQueries.inspect(new EntityId(v.handle()))));
    }

    private static int warp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        long ticks;
        try {
            ticks = SimTime.parseDuration(StringArgumentType.getString(c, "duration"));
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal(e.getMessage() + " (use e.g. 1d, 6h, 30m, 200t)"));
            return 0;
        }
        SimServer sim = sim();
        sim.runtime().advanceTarget(ticks);
        c.getSource().sendSuccess(() -> Component.literal("Warping the sim " + ticks + " ticks, to "
                + SimTime.describe(sim.runtime().targetTime())), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        SimServer sim = sim();
        var runtime = sim.runtime();
        List<String> lines = List.of(
                "Sim time: " + SimTime.describe(runtime.views().time()) + " (tick " + runtime.views().time() + ")",
                "Target: tick " + runtime.targetTime() + (runtime.catchingUp() ? " (catching up)" : ""),
                "Puppets: " + sim.bridge().puppetCount(),
                runtime.lastError() == null ? "No sim errors" : "Last sim error: " + runtime.lastError());
        return reply(c, CompletableFuture.completedFuture(lines));
    }

    private static int forceTier(CommandContext<CommandSourceStack> c, Tier tier) throws CommandSyntaxException {
        sim().runtime().submit(new ForceTierCommand(List.of(), tier));
        c.getSource().sendSuccess(() -> Component.literal(tier == null ? "Tiers follow players again" : "All villagers forced to " + tier), true);
        return 1;
    }

    /** Evaluates an expression with the nearest villager (if any) as the actor. */
    private static int evalExpression(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String source = StringArgumentType.getString(c, "expression");
        EntityId actor = EntityId.NONE;
        try {
            if (nearestVillager(c.getSource()) instanceof SimVillagerEntity v) actor = new EntityId(v.handle());
        } catch (CommandSyntaxException none) {
            // no villager nearby: evaluate without an actor
        }
        EntityId who = actor;
        return reply(c, sim().runtime().query(ctx -> {
            try {
                var e = ctx.logic().expression(source, null);
                double[] pos = who.isNone() ? null : com.ewitulsk.villagersimulator.content.plans.Plans.positionNow(ctx, who);
                Object value = e.value(com.ewitulsk.villagersimulator.api.sim.expr.ExprEnv.of(ctx, who, EntityId.NONE, pos));
                return List.of("= " + value + " (" + e.type().displayName() + (who.isNone() ? "" : ", actor " + who) + ")");
            } catch (com.ewitulsk.villagersimulator.api.sim.expr.ExpressionException ex) {
                return List.of("Error" + (ex.position() >= 0 ? " at column " + (ex.position() + 1) : "") + ": " + ex.getMessage());
            }
        }));
    }

    /** Your reputation in each village: how residents feel about you on average, and how many have heard of you. */
    private static int reputation(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        SimServer sim = sim();
        net.minecraft.server.level.ServerPlayer player = c.getSource().getPlayerOrException();
        com.ewitulsk.villagersimulator.neoforge.server.SimPlayers.handle(sim, player).thenAccept(me ->
                reply(c, sim.runtime().query(com.ewitulsk.villagersimulator.content.players.PlayerCommands.reputation(me))));
        return 1;
    }

    /** Server tick time, villagers per tier, and where the sim spent its time since the last reset. */
    private static int profile(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        SimServer sim = sim();
        double mspt = sim.server().getAverageTickTimeNanos() / 1e6;
        long lag = sim.runtime().targetTime() - sim.runtime().views().time();
        return reply(c, sim.runtime().queryWorld(w -> {
            List<String> out = new ArrayList<>();
            out.add(String.format(java.util.Locale.ROOT, "Server %.1f ms/tick (%.1f TPS); sim %d ticks behind", mspt,
                    Math.min(20, 1000 / Math.max(mspt, 1e-3)), lag));
            int[] tiers = new int[Tier.values().length];
            for (EntityId e : w.with(com.ewitulsk.villagersimulator.api.sim.core.CoreComponents.TIER)) {
                tiers[com.ewitulsk.villagersimulator.api.sim.core.CoreComponents.tier(w, e).ordinal()]++;
            }
            out.add(String.format(java.util.Locale.ROOT, "Villagers T0 %,d  T1 %,d  T2 %,d  T3 %,d; %,d entities, %,d shards, %,d tasks, %,d events in memory",
                    tiers[0], tiers[1], tiers[2], tiers[3], w.entityCount(), w.shardCount(), w.pendingTasks(), w.retainedEvents()));
            out.addAll(w.profile().lines(12));
            return out;
        }));
    }

    /**
     * Spawns {@code villagers} sim-only villagers (no blocks) in towns of up to 200, far enough away that they go to
     * T3, to check that the server holds 20 TPS (docs/ROADMAP.md Phase 6).
     */
    private static int stress(CommandContext<CommandSourceStack> c, int villagers) throws CommandSyntaxException {
        SimServer sim = sim();
        CommandSourceStack source = c.getSource();
        var types = sim.registry(BuildingType.REGISTRY);
        BlockPos origin = BlockPos.containing(source.getPosition());
        int size = 200;
        int villages = (villagers + size - 1) / size;
        int side = (int) Math.ceil(Math.sqrt(villages));
        int baseX = origin.getX() + Math.max(4_000, 2 * com.ewitulsk.villagersimulator.neoforge.SimConfig.T3_RADIUS.get() + 1_000);
        int baseZ = origin.getZ() - (side * 600) / 2;
        long seed = source.getLevel().random.nextLong();
        for (int i = 0; i < villages; i++) {
            int n = Math.min(size, villagers - i * size);
            int x = baseX + (i % side) * 600, z = baseZ + (i / side) * 600;
            var layout = n > 16 ? VillageLayouts.town(types, x, 64, z, n)
                    : new VillageLayouts.Layout(VillageLayouts.hamlet(types, x, 64, z, n), List.of());
            boolean last = i == villages - 1;
            sim.runtime().submit(new SpawnVillageCommand("Stress " + (i + 1), seed + i, x, 64, z, layout.placements(),
                    layout.districts(), n, !last ? null : id -> sim.server().execute(() -> source.sendSuccess(() -> Component.literal(
                            String.format(java.util.Locale.ROOT, "Spawned %,d stress villagers in %,d villages from x=%d. They exist only in the sim and "
                                    + "go to T3 while players are far away; watch /vs profile.", villagers, villages, baseX)), true))));
        }
        return 1;
    }

    private static int listScenarios(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<String> lines = new ArrayList<>();
        for (var d : sim().scenarios().all()) lines.add(d.name() + (d.description().isEmpty() ? "" : ": " + d.description()));
        return reply(c, CompletableFuture.completedFuture(lines.isEmpty() ? List.of("No scenarios") : lines));
    }

    /** Runs a scenario headless in a world of its own (the live sim is untouched) and reports its checks. */
    private static int runScenario(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        SimServer sim = sim();
        if (sim.scenarios().find(name).isEmpty()) {
            c.getSource().sendFailure(Component.literal("No scenario " + name + " (see /vs scenario list)"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Running scenario " + name + "..."), false);
        return reply(c, sim.runScenario(name).thenApply(r -> r.lines()));
    }

    /** Removes every {@code /vs stress} village from the sim. */
    private static int clearStress(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        SimServer sim = sim();
        CommandSourceStack source = c.getSource();
        sim.runtime().submit(ctx -> {
            List<EntityId> villages = new ArrayList<>();
            ctx.forEach(com.ewitulsk.villagersimulator.content.villages.Villages.VILLAGE, (id, v) -> {
                if (v.name().startsWith("Stress ")) villages.add(id);
            });
            int villagers = 0;
            for (EntityId id : villages) {
                var v = ctx.get(id, com.ewitulsk.villagersimulator.content.villages.Villages.VILLAGE);
                villagers += v.residents().size();
                v.residents().forEach(ctx::destroy);
                v.buildings().forEach(ctx::destroy);
                v.districts().forEach(ctx::destroy);
                ctx.destroy(id);
            }
            int n = villagers, k = villages.size();
            sim.server().execute(() -> source.sendSuccess(() -> Component.literal(
                    String.format(java.util.Locale.ROOT, "Removed %,d stress villagers in %,d villages", n, k)), true));
        });
        return 1;
    }

    private static int problems(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return reply(c, sim().runtime().queryWorld(w -> w.problems().isEmpty() ? List.of("No data problems") : w.problems()));
    }

    private static int save(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        SimServer sim = sim();
        CommandSourceStack source = c.getSource();
        sim.save().thenRun(() -> sim.server().execute(() -> source.sendSuccess(() -> Component.literal("Sim saved"), true)));
        return 1;
    }

    /** Sends the lines when the future completes, back on the server thread. Never blocks. */
    private static int reply(CommandContext<CommandSourceStack> c, CompletableFuture<List<String>> lines) {
        CommandSourceStack source = c.getSource();
        var server = source.getServer();
        lines.whenComplete((list, error) -> server.execute(() -> {
            if (error != null) {
                source.sendFailure(Component.literal("Sim query failed: " + error.getMessage()));
            } else if (list.isEmpty()) {
                source.sendSuccess(() -> Component.literal("(nothing)"), false);
            } else {
                for (String line : list) source.sendSuccess(() -> Component.literal(line), false);
            }
        }));
        return 1;
    }
}
