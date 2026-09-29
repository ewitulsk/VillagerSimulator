package com.ewitulsk.villagersimulator.neoforge.net;

import com.ewitulsk.villagersimulator.content.players.Dialogue;
import com.ewitulsk.villagersimulator.neoforge.VillagerSimulatorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: open or update the dialogue screen with a villager (entity {@code entityId}).
 *
 * @param facts addons' facts about the villager for their dialogue panels (mod-api {@code VillagerFact})
 */
public record DialoguePayload(int entityId, String name, String header, String greeting, List<Dialogue.Option> options,
                              String response, java.util.Map<String, String> facts) implements CustomPacketPayload {
    public static final Type<DialoguePayload> TYPE = new Type<>(VillagerSimulatorMod.id("dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DialoguePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId);
                buf.writeUtf(p.name);
                buf.writeUtf(p.header);
                buf.writeUtf(p.greeting);
                buf.writeVarInt(p.options.size());
                for (Dialogue.Option o : p.options) {
                    buf.writeUtf(o.id());
                    buf.writeUtf(o.label());
                    buf.writeBoolean(o.enabled());
                    buf.writeUtf(o.reason());
                }
                buf.writeUtf(p.response);
                buf.writeVarInt(p.facts.size());
                p.facts.forEach((k, v) -> {
                    buf.writeUtf(k);
                    buf.writeUtf(v);
                });
            },
            buf -> {
                int entityId = buf.readVarInt();
                String name = buf.readUtf(), header = buf.readUtf(), greeting = buf.readUtf();
                int n = buf.readVarInt();
                List<Dialogue.Option> options = new ArrayList<>(n);
                for (int i = 0; i < n; i++) options.add(new Dialogue.Option(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readUtf()));
                String response = buf.readUtf();
                int f = buf.readVarInt();
                java.util.Map<String, String> facts = new java.util.LinkedHashMap<>();
                for (int i = 0; i < f; i++) facts.put(buf.readUtf(), buf.readUtf());
                return new DialoguePayload(entityId, name, header, greeting, List.copyOf(options), response, facts);
            });

    public static DialoguePayload of(int entityId, Dialogue.View view, java.util.Map<String, String> facts) {
        return new DialoguePayload(entityId, view.name(), view.header(), view.greeting(), view.options(), view.response(),
                java.util.Map.copyOf(facts));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
