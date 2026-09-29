package com.ewitulsk.villagersimulator.content.villages;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.SimContext;
import com.ewitulsk.villagersimulator.api.sim.SimRandom;
import com.ewitulsk.villagersimulator.api.sim.component.DenseComponent;
import com.ewitulsk.villagersimulator.api.sim.component.IntField;
import com.ewitulsk.villagersimulator.content.VS;

/**
 * Appearance genes (docs/DESIGN.md §20). Kept in the sim so they can be inherited (Phase 10) and so the same villager
 * looks the same everywhere. The bridge sends the packed genes to clients, which paint a texture from them.
 *
 * <p>Packed layout (bits): skin 0-3, hair colour 4-7, hair style 8-11, outfit 12-15, eyes 16-19.
 */
public final class Appearance {
    public static final DenseComponent COMPONENT = new DenseComponent(VS.id("appearance"), 1);
    public static final IntField GENES = COMPONENT.intField("genes");

    public static final int SKIN_TONES = 6, HAIR_COLOURS = 8, HAIR_STYLES = 4, OUTFITS = 8, EYE_COLOURS = 4;
    /** Hair styles. */
    public static final int BALD = 0, SHORT = 1, LONG = 2, HOOD = 3;

    private Appearance() {}

    public static int pack(int skin, int hair, int style, int outfit, int eyes) {
        return (skin & 0xF) | (hair & 0xF) << 4 | (style & 0xF) << 8 | (outfit & 0xF) << 12 | (eyes & 0xF) << 16;
    }

    public static int skin(int genes) {
        return genes & 0xF;
    }

    public static int hair(int genes) {
        return (genes >> 4) & 0xF;
    }

    public static int style(int genes) {
        return (genes >> 8) & 0xF;
    }

    public static int outfit(int genes) {
        return (genes >> 12) & 0xF;
    }

    public static int eyes(int genes) {
        return (genes >> 16) & 0xF;
    }

    /** Random genes from a seed: the look of a villager with no parents. */
    public static int fromSeed(long seed) {
        long s = SimRandom.salt("appearance");
        // Bald is rarer than the other styles.
        int styleRoll = SimRandom.below(10, seed, s, 3);
        int style = styleRoll == 0 ? BALD : styleRoll < 5 ? SHORT : styleRoll < 8 ? LONG : HOOD;
        return pack(SimRandom.below(SKIN_TONES, seed, s, 1), SimRandom.below(HAIR_COLOURS, seed, s, 2), style,
                SimRandom.below(OUTFITS, seed, s, 4), SimRandom.below(EYE_COLOURS, seed, s, 5));
    }

    public static void init(SimContext ctx, EntityId v, long seed) {
        ctx.add(v, COMPONENT);
        ctx.set(v, GENES, fromSeed(seed));
    }

    /** A villager's genes (derived from the seed for villagers saved before appearance existed). */
    public static int genes(SimContext ctx, EntityId v) {
        if (ctx.has(v, COMPONENT)) return ctx.get(v, GENES);
        Villager villager = ctx.get(v, Villages.VILLAGER);
        return villager == null ? 0 : fromSeed(villager.seed());
    }
}
