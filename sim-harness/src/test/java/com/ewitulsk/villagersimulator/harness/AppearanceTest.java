package com.ewitulsk.villagersimulator.harness;

import com.ewitulsk.villagersimulator.api.sim.EntityId;
import com.ewitulsk.villagersimulator.api.sim.core.Embodiment;
import com.ewitulsk.villagersimulator.content.villages.Appearance;
import com.ewitulsk.villagersimulator.core.SimWorld;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 4: appearance genes. */
class AppearanceTest {

    @Test
    void genesAreDeterministicInRangeAndVaried() {
        Set<Integer> distinct = new HashSet<>();
        Set<Integer> styles = new HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            int g = Appearance.fromSeed(seed);
            assertEquals(g, Appearance.fromSeed(seed));
            assertTrue(Appearance.skin(g) < Appearance.SKIN_TONES);
            assertTrue(Appearance.hair(g) < Appearance.HAIR_COLOURS);
            assertTrue(Appearance.style(g) < Appearance.HAIR_STYLES);
            assertTrue(Appearance.outfit(g) < Appearance.OUTFITS);
            assertTrue(Appearance.eyes(g) < Appearance.EYE_COLOURS);
            distinct.add(g);
            styles.add(Appearance.style(g));
        }
        assertTrue(distinct.size() > 300, "villagers look different: " + distinct.size() + " looks in 500");
        assertEquals(Appearance.HAIR_STYLES, styles.size(), "every hair style occurs");
        int g = Appearance.pack(5, 7, 3, 7, 3);
        assertEquals(List.of(5, 7, 3, 7, 3), List.of(Appearance.skin(g), Appearance.hair(g), Appearance.style(g),
                Appearance.outfit(g), Appearance.eyes(g)));
    }

    @Test
    void theEmbodimentViewCarriesGenesAndTheySurviveASave() {
        Scenario s = Scenario.start();
        EntityId village = s.spawnHamlet("Lookton", 42, 8);
        List<Embodiment> view = s.world().snapshotViews().get(Embodiment.VIEW);
        for (Embodiment e : view) assertEquals(Appearance.genes(s.world(), e.id()), (int) e.appearance());

        SimWorld restored = Scenario.world(s.world().modules(), s.world().data(), 0);
        restored.restore(s.world().snapshot(0).data(), List.of());
        for (EntityId r : s.village(village).residents()) {
            assertEquals(Appearance.genes(s.world(), r), Appearance.genes(restored, r));
        }
    }
}
