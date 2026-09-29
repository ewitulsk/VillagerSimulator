package com.ewitulsk.villagersimulator.neoforge.client;

import com.ewitulsk.villagersimulator.neoforge.net.DebugOverlayPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the debug overlay (docs/ARCHITECTURE.md §18): chunk outlines coloured by tier (T0 green, T1 yellow, T2
 * orange, T3 red), district borders (cyan) and villagers' routes (light blue).
 */
public final class DebugOverlayRenderer {
    private static final float[][] TIER_COLOURS = {{0.2f, 1f, 0.2f}, {1f, 1f, 0.2f}, {1f, 0.55f, 0.1f}, {1f, 0.2f, 0.2f}};
    private static volatile DebugOverlayPayload latest;

    private DebugOverlayRenderer() {}

    public static void update(DebugOverlayPayload payload) {
        latest = payload.chunks().length == 0 && payload.districts().isEmpty() && payload.routes().isEmpty() ? null : payload;
    }

    public static void render(RenderLevelStageEvent event) {
        DebugOverlayPayload p = latest;
        Minecraft mc = Minecraft.getInstance();
        if (p == null || mc.player == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        double y = Math.floor(mc.player.getY()) + 0.05;

        for (int i = 0; i + 2 < p.chunks().length; i += 3) {
            int cx = p.chunks()[i], cz = p.chunks()[i + 1], tier = Math.max(0, Math.min(3, p.chunks()[i + 2]));
            float[] c = TIER_COLOURS[tier];
            double x0 = cx * 16 + 0.3, z0 = cz * 16 + 0.3;
            LevelRenderer.renderLineBox(pose, lines, x0, y, z0, x0 + 15.4, y + 0.02, z0 + 15.4, c[0], c[1], c[2], 0.8f);
        }
        for (DebugOverlayPayload.District d : p.districts()) {
            LevelRenderer.renderLineBox(pose, lines, d.x0(), y - 1, d.z0(), d.x1(), y + 6, d.z1(), 0.2f, 0.9f, 1f, 1f);
        }
        PoseStack.Pose last = pose.last();
        for (double[] r : p.routes()) {
            for (int i = 0; i + 5 < r.length; i += 3) {
                float x1 = (float) r[i], y1 = (float) r[i + 1] + 0.2f, z1 = (float) r[i + 2];
                float x2 = (float) r[i + 3], y2 = (float) r[i + 4] + 0.2f, z2 = (float) r[i + 5];
                float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-4f) continue;
                nx /= len;
                ny /= len;
                nz /= len;
                lines.addVertex(last, x1, y1, z1).setColor(0.5f, 0.8f, 1f, 1f).setNormal(last, nx, ny, nz);
                lines.addVertex(last, x2, y2, z2).setColor(0.5f, 0.8f, 1f, 1f).setNormal(last, nx, ny, nz);
            }
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}
