package com.ewitulsk.villagersimulator.content;

import com.ewitulsk.villagersimulator.api.sim.module.SimModule;
import com.ewitulsk.villagersimulator.content.buildings.BuildingsModule;
import com.ewitulsk.villagersimulator.content.needs.NeedsModule;
import com.ewitulsk.villagersimulator.content.plans.PlansModule;
import com.ewitulsk.villagersimulator.content.players.PlayersModule;
import com.ewitulsk.villagersimulator.content.social.SocialModule;
import com.ewitulsk.villagersimulator.content.villages.VillagesModule;

import java.util.List;

/** The base game's modules. The mod registers these through RegisterSimModulesEvent, like any addon would. */
public final class ContentModules {
    private ContentModules() {}

    public static List<SimModule> all() {
        return List.of(new NeedsModule(), new BuildingsModule(), new VillagesModule(), new PlansModule(), new SocialModule(), new PlayersModule());
    }
}
