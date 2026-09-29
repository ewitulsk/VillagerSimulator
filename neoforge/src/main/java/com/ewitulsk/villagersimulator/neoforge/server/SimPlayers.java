package com.ewitulsk.villagersimulator.neoforge.server;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.content.players.Dialogue;
import com.ewitulsk.villagersimulator.content.players.PlayerCommands;
import com.ewitulsk.villagersimulator.content.players.PlayersModule;
import com.ewitulsk.villagersimulator.neoforge.ModContent;
import com.ewitulsk.villagersimulator.neoforge.SimVillagerEntity;
import com.ewitulsk.villagersimulator.neoforge.net.DialogueChoicePayload;
import com.ewitulsk.villagersimulator.neoforge.net.DialoguePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.concurrent.CompletableFuture;

/**
 * Players on the Minecraft side (docs/ARCHITECTURE.md §14): their sim record handle (cached in a player attachment),
 * talking and gifting, and where they are.
 */
public final class SimPlayers {
    /** How close a player must be to keep talking to a villager. */
    private static final double TALK_RANGE = 8;

    private SimPlayers() {}

    /** The player's sim entity, creating the record if needed. Completes on the server thread. */
    public static CompletableFuture<EntityId> handle(SimServer sim, ServerPlayer player) {
        int cached = player.getData(ModContent.PLAYER_HANDLE);
        if (cached != 0) return CompletableFuture.completedFuture(new EntityId(cached));
        CompletableFuture<EntityId> out = new CompletableFuture<>();
        sim.runtime().submit(new PlayerCommands.Join(player.getStringUUID(), player.getGameProfile().getName(),
                id -> sim.server().execute(() -> {
                    player.setData(ModContent.PLAYER_HANDLE, id.raw());
                    out.complete(id);
                })));
        return out;
    }

    public static void onLogin(ServerPlayer player) {
        SimServer sim = SimServer.get();
        if (sim != null) handle(sim, player);
    }

    /** Right-click on a villager: an item in hand is a gift, an empty hand opens the dialogue. */
    public static void interact(SimVillagerEntity villager, Player p, InteractionHand hand) {
        SimServer sim = SimServer.get();
        if (sim == null || !(p instanceof ServerPlayer player) || villager.handle() == 0) return;
        ItemStack stack = player.getItemInHand(hand);
        EntityId v = new EntityId(villager.handle());
        if (!stack.isEmpty()) {
            String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!player.getAbilities().instabuild) stack.shrink(1);
            handle(sim, player).thenAccept(me -> sim.runtime().submit(new PlayersModule.Gift(v, me, item,
                    reply -> sim.server().execute(() -> say(player, villager, reply)))));
            return;
        }
        openDialogue(sim, player, villager);
    }

    /** Opens the dialogue: the villager stops to listen, and the screen is sent. Completes with the view sent. */
    public static CompletableFuture<Dialogue.View> openDialogue(SimServer sim, ServerPlayer player, SimVillagerEntity villager) {
        EntityId v = new EntityId(villager.handle());
        CompletableFuture<Dialogue.View> sent = new CompletableFuture<>();
        handle(sim, player).thenAccept(me -> {
            sim.runtime().submit(new Dialogue.Open(v, me));
            sim.runtime().query(ctx -> new ViewAndFacts(Dialogue.view(v, me, "").run(ctx), sim.facts(ctx, v)))
                    .thenAccept(vf -> sim.server().execute(() -> {
                        sent.complete(vf.view());
                        send(player, DialoguePayload.of(villager.getId(), vf.view(), vf.facts()));
                    }));
        });
        return sent;
    }

    /** The player chose an option: apply it, then send the updated dialogue with the villager's reply. */
    public static void onChoice(DialogueChoicePayload payload, IPayloadContext context) {
        SimServer sim = SimServer.get();
        if (sim == null || !(context.player() instanceof ServerPlayer player)) return;
        if (!(player.level().getEntity(payload.entityId()) instanceof SimVillagerEntity villager)) return;
        if (villager.distanceTo(player) > TALK_RANGE) return;
        EntityId v = new EntityId(villager.handle());
        handle(sim, player).thenAccept(me -> sim.runtime().submit(new Dialogue.Choose(v, me, payload.option(),
                reply -> sim.runtime().query(ctx -> new ViewAndFacts(Dialogue.view(v, me, reply).run(ctx), sim.facts(ctx, v)))
                        .thenAccept(vf -> sim.server().execute(
                                () -> send(player, DialoguePayload.of(villager.getId(), vf.view(), vf.facts())))))));
    }

    private record ViewAndFacts(Dialogue.View view, java.util.Map<String, String> facts) {}

    /** Sends a payload; players without the channel (e.g. fake connections) are skipped. */
    private static void send(ServerPlayer player, DialoguePayload payload) {
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("VillagerSim").debug("Couldn't send dialogue to {}: {}", player.getName().getString(), e.toString());
        }
    }

    private static void say(ServerPlayer player, SimVillagerEntity villager, String line) {
        String name = villager.getCustomName() == null ? "Villager" : villager.getCustomName().getString().split(" · ")[0];
        player.displayClientMessage(Component.literal("<" + name + "> " + line), false);
    }

    /** Reports every player's position to the sim (about once a second), so visits to buildings are tracked. */
    static void reportPositions(SimServer sim) {
        for (ServerPlayer player : sim.server().overworld().players()) {
            int handle = player.getData(ModContent.PLAYER_HANDLE);
            if (handle == 0) continue;
            sim.runtime().submit(new PlayerCommands.Position(new EntityId(handle), player.getX(), player.getY(), player.getZ()));
        }
    }
}
