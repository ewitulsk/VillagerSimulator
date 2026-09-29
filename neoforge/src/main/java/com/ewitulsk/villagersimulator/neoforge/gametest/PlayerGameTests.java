package com.ewitulsk.villagersimulator.neoforge.gametest;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.core.ForceTierCommand;
import com.ewitulsk.villagersimulator.api.sim.core.Tier;
import com.ewitulsk.villagersimulator.content.players.Dialogue;
import com.ewitulsk.villagersimulator.content.players.Players;
import com.ewitulsk.villagersimulator.content.plans.PlanEntry;
import com.ewitulsk.villagersimulator.content.plans.Plans;
import com.ewitulsk.villagersimulator.content.villages.Villages;
import com.ewitulsk.villagersimulator.neoforge.SimVillagerEntity;
import com.ewitulsk.villagersimulator.neoforge.server.SimPlayers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.require;
import static com.ewitulsk.villagersimulator.neoforge.gametest.TestSupport.sim;

/** Player tests (namespace {@code villagersimulator_players}), with a mock server player. */
@GameTestHolder(PlayerGameTests.NS)
@PrefixGameTestTemplate(false)
public final class PlayerGameTests {
    public static final String NS = "villagersimulator_players";
    private static final String BATCH = "players";

    private PlayerGameTests() {}

    /** An embodied villager of a one-villager compact village, forced to T0. */
    private static AtomicReference<EntityId> embodiedVillager(GameTestHelper h, String name) {
        AtomicReference<EntityId> villager = new AtomicReference<>();
        TestSupport.spawn(TestSupport.compactVillage(h, name, 17, 1, null), null)
                .thenCompose(village -> sim().runtime().query(ctx -> ctx.get(village, Villages.VILLAGE).residents().get(0)))
                .thenAccept(v -> {
                    sim().runtime().submit(new ForceTierCommand(List.of(v), Tier.T0));
                    villager.set(v);
                });
        return villager;
    }

    /** Right-clicking a villager with cake gives it as a gift: the cake is used up and the villager likes you more. */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void giftingAnItemChangesTheRelationship(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CAKE, 2));
        AtomicReference<EntityId> me = new AtomicReference<>();
        SimPlayers.handle(sim(), player).thenAccept(me::set);
        AtomicReference<EntityId> villager = embodiedVillager(h, "Giftham");
        AtomicReference<Float> friendship = new AtomicReference<>();

        h.startSequence()
                .thenWaitUntil(() -> require(me.get() != null && villager.get() != null, "player and villager ready"))
                .thenWaitUntil(() -> require(sim().bridge().puppet(villager.get()) != null, "villager embodied"))
                .thenExecute(() -> sim().bridge().puppet(villager.get()).interact(player, InteractionHand.MAIN_HAND))
                .thenExecute(() -> require(player.getMainHandItem().getCount() == 1, "one cake given"))
                .thenWaitUntil(() -> {
                    sim().runtime().query(ctx -> ctx.relationships().friendship(villager.get(), me.get())).thenAccept(friendship::set);
                    require(friendship.get() != null && friendship.get() > 0, "villager likes the player: " + friendship.get());
                })
                .thenExecute(() -> sim().runtime().submit(new ForceTierCommand(List.of(villager.get()), null)))
                .thenSucceed();
    }

    /**
     * Right-clicking with an empty hand opens the dialogue: the server sends options built from sim state (a
     * stranger can't be invited out yet), and the villager stops to listen.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void dialogueReflectsSimStateAndTheVillagerListens(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        AtomicReference<EntityId> villager = embodiedVillager(h, "Talkerton");
        AtomicReference<Dialogue.View> view = new AtomicReference<>();
        AtomicReference<PlanEntry> doing = new AtomicReference<>();

        h.startSequence()
                .thenWaitUntil(() -> require(villager.get() != null && sim().bridge().puppet(villager.get()) != null, "embodied"))
                .thenExecute(() -> {
                    SimVillagerEntity puppet = sim().bridge().puppet(villager.get());
                    SimPlayers.openDialogue(sim(), player, puppet).thenAccept(view::set);
                })
                .thenWaitUntil(() -> require(view.get() != null, "dialogue sent"))
                .thenExecute(() -> {
                    Dialogue.Option invite = view.get().options().stream().filter(o -> o.id().equals(Dialogue.INVITE)).findFirst().orElseThrow();
                    require(!invite.enabled(), "a stranger can't be invited out");
                    require(view.get().greeting().equals("Hello, stranger."), view.get().greeting());
                })
                .thenWaitUntil(() -> {
                    sim().runtime().query(ctx -> Plans.current(ctx, villager.get())).thenAccept(doing::set);
                    require(doing.get() != null && doing.get().activity().equals(Players.LISTEN), "villager stops to listen");
                })
                .thenExecute(() -> sim().runtime().submit(new ForceTierCommand(List.of(villager.get()), null)))
                .thenSucceed();
    }
}
