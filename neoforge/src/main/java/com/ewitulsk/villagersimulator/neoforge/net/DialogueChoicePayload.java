package com.ewitulsk.villagersimulator.neoforge.net;

import com.ewitulsk.villagersimulator.neoforge.VillagerSimulatorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client → server: the player picked dialogue option {@code option} with villager entity {@code entityId}. */
public record DialogueChoicePayload(int entityId, String option) implements CustomPacketPayload {
    public static final Type<DialogueChoicePayload> TYPE = new Type<>(VillagerSimulatorMod.id("dialogue_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DialogueChoicePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DialogueChoicePayload::entityId,
            ByteBufCodecs.STRING_UTF8, DialogueChoicePayload::option,
            DialogueChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
