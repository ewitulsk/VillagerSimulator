package com.ewitulsk.villagersimulator.neoforge.client.script;

import com.ewitulsk.villagersimulator.neoforge.client.VillagerTextures;
import com.ewitulsk.villagersimulator.neoforge.server.SimServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Scripted client runs for visual checks (see ../../ModTesting.md, "Scripted server + hidden client"). Off unless the
 * JVM has {@code -Dvillagersimulator.clientScript=<name>}: the client then makes a fresh superflat creative world,
 * plays the steps, takes screenshots ({@code screenshots/vs_NN_name.png}) and quits. With
 * {@code -Dvillagersimulator.hiddenClient=true} the window is moved off-screen. Results go to
 * {@code villagersimulator-script/script.log}; checks log {@code VS_SCRIPT_CHECK PASS|FAIL}.
 */
public final class ClientScript {
    public static final String NAME = System.getProperty("villagersimulator.clientScript", "");
    private static final boolean HIDDEN = Boolean.getBoolean("villagersimulator.hiddenClient");
    private static final Logger LOG = LoggerFactory.getLogger("VillagerSim/Script");
    private static final String WORLD = "vs-script";
    private static final int TIMEOUT_TICKS = 20 * 60 * 5;

    private interface Step {
        boolean run();
    }

    private record Entry(String label, int waitAfter, Step step) {}

    private static ClientScript instance;

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Entry> steps = new ArrayList<>();
    private final Path logFile;
    private int phase, index, wait, ticks, settle, shots, stepTicks, passed, failed;
    private Vec3 origin;

    public static void init() {
        if (NAME.isEmpty()) return;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> {
            if (instance == null) instance = new ClientScript();
            try {
                instance.tick();
            } catch (Throwable t) {
                instance.check("script ran", false, t.toString());
                instance.finish();
            }
        });
    }

    private ClientScript() {
        logFile = mc.gameDirectory.toPath().resolve("villagersimulator-script/script.log");
        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        switch (NAME) {
            case "look" -> buildLook();
            case "town" -> buildTown();
            case "fountain" -> buildFountain();
            default -> throw new IllegalArgumentException("Unknown client script " + NAME);
        }
    }

    /** Phase 4 visual check: a hamlet of textured, animated villagers going about their day. */
    private void buildLook() {
        then("spawn a hamlet", 40, () -> {
            origin = mc.player.position();
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            cmd("vs village spawn 8 Lookton");
        });
        then("warp to mid-morning", 10, () -> cmd("vs time warp 3h"));
        then("overview camera", 200, () -> tp(origin.add(-10, 7, 14), origin.add(4, 0, -3)));
        until("villagers embodied", 400, () -> puppets() > 0);
        shot("hamlet_overview", "houses, well, bakery, tavern and market stall with villagers in different outfits");
        then("well camera", 80, () -> tp(origin.add(5, 2.5, 6), origin.add(0, 1, 0)));
        shot("well_closeup", "villagers with distinct faces, hair and outfits");
        then("check textures", 1, () -> check("villager textures painted", VillagerTextures.painted() > 0,
                VillagerTextures.painted() + " textures"));
        then("warp to evening", 200, () -> {
            cmd("vs time warp 9h");
            tp(origin.add(12, 6, 10), origin.add(6, 0, -6));
        });
        shot("evening", "villagers relaxing, drinking at the tavern or walking home");
    }

    /** Phase 5 visual check: a two-district town of 60 from above, with the debug overlay on. */
    private void buildTown() {
        then("spawn a town", 60, () -> {
            origin = mc.player.position();
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            cmd("vs village spawn 60 Greatford");
        });
        then("warp to mid-morning", 10, () -> cmd("vs time warp 3h"));
        then("overlay on", 10, () -> cmd("vs debug overlay"));
        then("aerial camera", 200, () -> tp(origin.add(-5, 45, 40), origin.add(-5, 0, 0)));
        until("villagers embodied", 400, () -> puppets() > 0);
        shot("town_overlay", "two districts (cyan boxes), chunk tiers, houses west, market east, routes in blue");
        then("street camera", 100, () -> tp(origin.add(20, 4, 12), origin.add(30, 0, -6)));
        shot("market_street", "villagers walking between bakeries, taverns and stalls in the Market Quarter");
    }

    /**
     * Phase 7 visual check: the reference addon's fountain in a hamlet, a villager making a wish at it (gold nugget,
     * splashes), and the addon's dialogue panel.
     */
    private void buildFountain() {
        then("spawn a hamlet", 60, () -> {
            origin = mc.player.position();
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            cmd("vs village spawn 16 Wishford");
        });
        then("warp to morning", 10, () -> cmd("vs time warp 2h"));
        then("fountain camera", 100, () -> tp(origin.add(20, 4, 12), origin.add(27, 1, 1)));
        until("villagers embodied", 400, () -> puppets() > 0);
        // Keep warping the sim an hour at a time until someone is making a wish up close.
        until("someone makes a wish at the fountain", 2400, () -> {
            if (stepTicks % 200 == 199) cmd("vs time warp 1h");
            return onServer(() -> wisher() != null);
        });
        then("look at the wisher", 60, () -> {
            var w = onServer(ClientScript::wisherPosition);
            if (w != null) tp(w.add(2.5, 3.5, 4.5), w.add(0, 0.8, 0));
        });
        shot("fountain_wish", "a villager at the fountain holding a gold nugget, name tag 'Making a wish', splashes");
        until("a wish was made", 2400, () -> {
            if (stepTicks % 200 == 199) cmd("vs time warp 1h");
            return onServer(() -> wishMaker() != null);
        });
        then("talk to someone who made a wish", 40, () -> onServer(() -> {
            var sim = SimServer.get();
            var who = wishMaker();
            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            if (who != null) {
                player.teleportTo(who.getX() + 1.5, who.getY(), who.getZ() + 1.5);
                com.ewitulsk.villagersimulator.neoforge.server.SimPlayers.openDialogue(sim, player, who);
            }
            return who != null;
        }));
        until("dialogue shows the fountain panel", 200, () -> mc.screen instanceof com.ewitulsk.villagersimulator.neoforge.client.DialogueScreen);
        shot("dialogue_panel", "the dialogue screen with a line 'Wishes made at the fountain: N'");
        then("close", 1, () -> mc.setScreen(null));
    }

    private <T> T onServer(java.util.function.Supplier<T> task) {
        var server = mc.getSingleplayerServer();
        return server == null ? null : server.submit(task::get).join();
    }

    /** A puppet at the fountain holding the wishing nugget (server thread). */
    private static com.ewitulsk.villagersimulator.neoforge.SimVillagerEntity wisher() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        for (var e : server.overworld().getEntities(com.ewitulsk.villagersimulator.neoforge.ModContent.VILLAGER.get(), x -> true)) {
            if (e.getMainHandItem().is(net.minecraft.world.item.Items.GOLD_NUGGET)) return e;
        }
        return null;
    }

    private static Vec3 wisherPosition() {
        var w = wisher();
        return w == null ? null : w.position();
    }

    /** A puppet whose villager has made at least one wish (server thread). */
    private static com.ewitulsk.villagersimulator.neoforge.SimVillagerEntity wishMaker() {
        var sim = SimServer.get();
        var server = Minecraft.getInstance().getSingleplayerServer();
        java.util.Set<Integer> makers = sim.runtime().query(ctx -> {
            java.util.Set<Integer> out = new java.util.HashSet<>();
            for (var r : ctx.events().ofType(com.ewitulsk.villagersimulator.api.sim.Id.of("villagersimulator_fountain", "wish"))) out.add(r.actor().raw());
            return out;
        }).join();
        for (var e : server.overworld().getEntities(com.ewitulsk.villagersimulator.neoforge.ModContent.VILLAGER.get(), x -> true)) {
            if (makers.contains(e.handle())) return e;
        }
        return null;
    }

    // ------------------------------------------------------------------ building

    private void then(String label, int waitAfter, Runnable step) {
        steps.add(new Entry(label, waitAfter, () -> {
            step.run();
            return true;
        }));
    }

    private void until(String label, int maxTicks, BooleanSupplier done) {
        steps.add(new Entry(label, 0, () -> {
            if (done.getAsBoolean()) {
                check(label, true, "after " + stepTicks + " ticks");
                return true;
            }
            if (stepTicks >= maxTicks) {
                check(label, false, "timed out after " + maxTicks + " ticks");
                return true;
            }
            return false;
        }));
    }

    private void shot(String name, String expect) {
        steps.add(new Entry("shot " + name, 1, () -> {
            String file = String.format(Locale.ROOT, "vs_%02d_%s.png", ++shots, name);
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), c -> {});
            log("SHOT " + file + " | expect: " + expect + " | puppets=" + puppets());
            return true;
        }));
    }

    // ------------------------------------------------------------------ actions

    private void cmd(String command) {
        log("CMD /" + command);
        mc.player.connection.sendCommand(command);
    }

    private void tp(Vec3 at, Vec3 lookAt) {
        cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f", at.x, at.y, at.z, lookAt.x, lookAt.y, lookAt.z));
    }

    private int puppets() {
        var server = mc.getSingleplayerServer();
        if (server == null) return 0;
        return server.submit(() -> SimServer.get() == null ? 0 : SimServer.get().bridge().puppetCount()).join();
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) passed++;
        else failed++;
        log("VS_SCRIPT_CHECK " + (ok ? "PASS " : "FAIL ") + name + " | " + detail);
    }

    private void log(String line) {
        String text = String.format(Locale.ROOT, "[t%05d] %s", ticks, line);
        LOG.info("VS_SCRIPT {}", text);
        try {
            Files.writeString(logFile, text + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // logging is best effort
        }
    }

    // ------------------------------------------------------------------ driver

    private void tick() {
        ticks++;
        if (phase == 4) return;
        if (ticks > TIMEOUT_TICKS) {
            check("script finished in time", false, "timeout");
            finish();
            return;
        }
        switch (phase) {
            case 0 -> {
                if (HIDDEN) GLFW.glfwSetWindowPos(mc.getWindow().getWindow(), -4000, -4000);
                // A fresh game directory shows the accessibility onboarding screen first; any menu will do.
                if (mc.screen != null && ticks > 40 && mc.level == null) {
                    log("starting from " + mc.screen.getClass().getSimpleName());
                    createWorld();
                    phase = 1;
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && mc.screen == null) {
                    phase = 2;
                    log("world loaded");
                }
            }
            case 2 -> {
                if (++settle >= 60) {
                    phase = 3;
                    log("running " + steps.size() + " steps");
                }
            }
            case 3 -> runSteps();
            default -> {
            }
        }
    }

    private void runSteps() {
        if (wait > 0) {
            wait--;
            return;
        }
        if (index >= steps.size()) {
            finish();
            return;
        }
        Entry e = steps.get(index);
        if (stepTicks == 0 && !e.label.startsWith("shot ")) log("STEP " + e.label);
        boolean done = e.step.run();
        stepTicks++;
        if (done) {
            index++;
            wait = e.waitAfter;
            stepTicks = 0;
        }
    }

    private void createWorld() {
        log("creating world " + WORLD);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20260929L, false, false),
                ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }

    private void finish() {
        if (phase == 4) return;
        phase = 4;
        log(String.format(Locale.ROOT, "VS_SCRIPT_DONE %s passed=%d failed=%d", NAME, passed, failed));
        mc.execute(() -> {
            if (mc.level != null) mc.level.disconnect();
            mc.disconnect();
            mc.stop();
        });
    }
}
