package com.ewitulsk.villagersimulator.neoforge.client;

import com.ewitulsk.villagersimulator.content.villages.Appearance;
import com.ewitulsk.villagersimulator.neoforge.VillagerSimulatorMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Paints a villager texture from appearance genes, in layers (skin, hair, face, outfit), and caches one texture per
 * gene combination (docs/ARCHITECTURE.md §16). The UV layout matches {@code tools/models/villager.py}.
 */
public final class VillagerTextures {
    private static final int SIZE = 64;

    private static final int[] SKIN = {0xFFE0C4, 0xF1C2A0, 0xE0AC7D, 0xC6865A, 0x8D5536, 0x5E3A28};
    private static final int[] HAIR = {0x231E1C, 0x462D1E, 0x6E4628, 0x8C3C1E, 0xDCBE78, 0xBE5A28, 0x969696, 0xE6E6E1};
    private static final int[] OUTFIT = {0x785537, 0x466E3C, 0x3C5082, 0x8C322D, 0x6E6E73, 0x644678, 0xAA8C3C, 0x3C7878};
    private static final int[] EYES = {0x50321E, 0x3C6EB4, 0x468246, 0x787882};
    private static final int TROUSERS = 0x4A3A2C, SHOES = 0x2A2018, BELT = 0x3A2A1A, WHITE = 0xF4F2EE, MOUTH = 0x7A3A30;

    private static final Map<Integer, ResourceLocation> CACHE = new HashMap<>();

    private VillagerTextures() {}

    /** The texture for {@code genes}, painted on first use. */
    public static ResourceLocation texture(int genes) {
        return CACHE.computeIfAbsent(genes, g -> {
            ResourceLocation id = VillagerSimulatorMod.id("dynamic/villager_" + Integer.toHexString(g));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(paint(g)));
            return id;
        });
    }

    /** How many textures have been painted (for the client script's checks). */
    public static int painted() {
        return CACHE.size();
    }

    /** A box's faces in Bedrock box UV at (u, v) for a cube of size (w, h, d). */
    private record Box(int u, int v, int w, int h, int d) {
        void top(Painter p, int c) {
            p.rect(u + d, v, w, d, c);
        }

        void bottom(Painter p, int c) {
            p.rect(u + d + w, v, w, d, c);
        }

        /** All four sides, rows {@code fromRow..toRow} (0 = top of the side). */
        void sides(Painter p, int fromRow, int toRow, int c) {
            int rows = Math.min(h, toRow) - fromRow;
            if (rows <= 0) return;
            p.rect(u, v + d + fromRow, 2 * d + 2 * w, rows, c);
        }

        void all(Painter p, int c) {
            top(p, c);
            bottom(p, c);
            sides(p, 0, h, c);
        }

        /** The front (north) face's top-left pixel. */
        int frontX() {
            return u + d;
        }

        int frontY() {
            return v + d;
        }

        int backX() {
            return u + 2 * d + w;
        }
    }

    private static final Box HEAD = new Box(0, 0, 8, 10, 8);
    private static final Box HAIR_BOX = new Box(32, 0, 8, 4, 8);
    private static final Box HAIR_LONG = new Box(32, 50, 8, 8, 1);
    private static final Box NOSE = new Box(0, 18, 2, 4, 2);
    private static final Box BODY = new Box(16, 20, 8, 11, 6);
    private static final Box ROBE = new Box(16, 37, 8, 6, 6);
    private static final Box RIGHT_ARM = new Box(44, 20, 4, 11, 4);
    private static final Box LEFT_ARM = new Box(44, 35, 4, 11, 4);
    private static final Box RIGHT_LEG = new Box(0, 24, 4, 11, 4);
    private static final Box LEFT_LEG = new Box(0, 39, 4, 11, 4);

    /** Writes pixels with a little per-pixel brightness noise so surfaces don't look flat. */
    private static final class Painter {
        final NativeImage img = new NativeImage(SIZE, SIZE, true);
        final int seed;

        Painter(int seed) {
            this.seed = seed;
        }

        void px(int x, int y, int rgb) {
            if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
            int h = (x * 73856093) ^ (y * 19349663) ^ seed;
            double jitter = 1 + (((h >>> 8) & 0xFF) / 255.0 - 0.5) * 0.10;
            int r = clamp((int) (((rgb >> 16) & 0xFF) * jitter));
            int g = clamp((int) (((rgb >> 8) & 0xFF) * jitter));
            int b = clamp((int) ((rgb & 0xFF) * jitter));
            img.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r); // NativeImage is ABGR
        }

        void rect(int x, int y, int w, int h, int rgb) {
            for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) px(x + i, y + j, rgb);
        }

        private static int clamp(int v) {
            return Math.max(0, Math.min(255, v));
        }
    }

    private static int shade(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f), g = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    static NativeImage paint(int genes) {
        int skin = SKIN[Appearance.skin(genes) % SKIN.length];
        int hair = HAIR[Appearance.hair(genes) % HAIR.length];
        int outfit = OUTFIT[Appearance.outfit(genes) % OUTFIT.length];
        int eyes = EYES[Appearance.eyes(genes) % EYES.length];
        int style = Appearance.style(genes);
        int hairColour = style == Appearance.HOOD ? shade(outfit, 0.85) : hair;
        Painter p = new Painter(genes);

        // Skin layer.
        HEAD.all(p, skin);
        NOSE.all(p, shade(skin, 0.9));
        // Hair layer: on the head itself (so bald heads still read), and the hair cubes.
        if (style != Appearance.BALD) {
            HEAD.top(p, hairColour);
            HEAD.sides(p, 0, 2, hairColour);
            p.rect(HEAD.backX(), HEAD.frontY(), 8, style == Appearance.SHORT ? 5 : 9, hairColour);
        }
        HAIR_BOX.all(p, hairColour);
        HAIR_LONG.all(p, hairColour);
        // Face layer, on the front of the head (8 wide, 10 tall).
        int fx = HEAD.frontX(), fy = HEAD.frontY();
        p.rect(fx + 1, fy + 3, 2, 1, shade(hair, 0.8));
        p.rect(fx + 5, fy + 3, 2, 1, shade(hair, 0.8));
        p.px(fx + 1, fy + 4, WHITE);
        p.px(fx + 2, fy + 4, eyes);
        p.px(fx + 5, fy + 4, eyes);
        p.px(fx + 6, fy + 4, WHITE);
        p.rect(fx + 3, fy + 7, 2, 1, MOUTH);
        // Outfit layer: shirt with a collar, belt and trim; robe; sleeves with bare hands.
        BODY.all(p, outfit);
        p.rect(BODY.frontX() + 3, BODY.frontY(), 2, 2, skin);
        p.rect(BODY.frontX(), BODY.frontY() + 9, 8, 1, BELT);
        p.rect(BODY.frontX() + 3, BODY.frontY() + 2, 2, 7, shade(outfit, 0.8));
        ROBE.all(p, shade(outfit, 0.8));
        ROBE.sides(p, 5, 6, shade(outfit, 0.6));
        for (Box arm : new Box[]{RIGHT_ARM, LEFT_ARM}) {
            arm.all(p, outfit);
            arm.sides(p, 8, 11, skin);
            arm.bottom(p, skin);
        }
        for (Box leg : new Box[]{RIGHT_LEG, LEFT_LEG}) {
            leg.all(p, TROUSERS);
            leg.sides(p, 9, 11, SHOES);
            leg.bottom(p, SHOES);
        }
        return p.img;
    }
}
