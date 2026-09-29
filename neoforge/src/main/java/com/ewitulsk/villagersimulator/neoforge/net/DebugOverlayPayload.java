package com.ewitulsk.villagersimulator.neoforge.net;

import com.ewitulsk.villagersimulator.neoforge.VillagerSimulatorMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: what the debug overlay draws (docs/ARCHITECTURE.md §18): chunk tiers around the player, district
 * borders, and embodied villagers' routes.
 *
 * @param chunks flattened {@code chunkX, chunkZ, tier}
 * @param routes each a flattened {@code x, y, z, ...} polyline
 */
public record DebugOverlayPayload(int[] chunks, List<District> districts, List<double[]> routes) implements CustomPacketPayload {
    public record District(String name, int x0, int z0, int x1, int z1) {}

    public static final Type<DebugOverlayPayload> TYPE = new Type<>(VillagerSimulatorMod.id("debug_overlay"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DebugOverlayPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarIntArray(p.chunks);
                buf.writeVarInt(p.districts.size());
                for (District d : p.districts) {
                    buf.writeUtf(d.name());
                    buf.writeVarInt(d.x0());
                    buf.writeVarInt(d.z0());
                    buf.writeVarInt(d.x1());
                    buf.writeVarInt(d.z1());
                }
                buf.writeVarInt(p.routes.size());
                for (double[] r : p.routes) {
                    buf.writeVarInt(r.length);
                    for (double v : r) buf.writeFloat((float) v);
                }
            },
            buf -> {
                int[] chunks = buf.readVarIntArray();
                int n = buf.readVarInt();
                List<District> districts = new ArrayList<>(n);
                for (int i = 0; i < n; i++) districts.add(new District(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                int m = buf.readVarInt();
                List<double[]> routes = new ArrayList<>(m);
                for (int i = 0; i < m; i++) {
                    double[] r = new double[buf.readVarInt()];
                    for (int k = 0; k < r.length; k++) r[k] = buf.readFloat();
                    routes.add(r);
                }
                return new DebugOverlayPayload(chunks, districts, routes);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
