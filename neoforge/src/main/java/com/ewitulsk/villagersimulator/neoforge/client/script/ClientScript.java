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
