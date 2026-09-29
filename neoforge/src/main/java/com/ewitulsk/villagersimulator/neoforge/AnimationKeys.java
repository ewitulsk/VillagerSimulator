package com.ewitulsk.villagersimulator.neoforge;

import com.ewitulsk.villagersimulator.api.mod.animation.RegisterAnimationKeysEvent;
import com.ewitulsk.villagersimulator.api.sim.Id;
import com.ewitulsk.villagersimulator.api.sim.activity.EmbodiedBehaviors;
import net.neoforged.fml.ModLoader;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.HashMap;
import java.util.Map;

/**
 * Embodied behaviour key → villager animation, on the client (docs/ARCHITECTURE.md §16). The base keys are built in;
 * addons add theirs with {@link RegisterAnimationKeysEvent}. Unknown keys play idle.
 */
public final class AnimationKeys {
    public static final RawAnimation IDLE = loop(RegisterAnimationKeysEvent.IDLE);
    public static final RawAnimation WALK = loop(RegisterAnimationKeysEvent.WALK);
    public static final RawAnimation SLEEP = loop(RegisterAnimationKeysEvent.SLEEP);
    private static volatile Map<String, RawAnimation> byKey = base();

    private AnimationKeys() {}

    private static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop(name);
    }

    private static Map<String, RawAnimation> base() {
        Map<String, RawAnimation> m = new HashMap<>();
        m.put(EmbodiedBehaviors.IDLE.toString(), IDLE);
        m.put(EmbodiedBehaviors.WALK.toString(), WALK);
        m.put(EmbodiedBehaviors.SLEEP.toString(), SLEEP);
        m.put(EmbodiedBehaviors.WORK.toString(), loop(RegisterAnimationKeysEvent.WORK));
        m.put(EmbodiedBehaviors.EAT.toString(), loop(RegisterAnimationKeysEvent.EAT));
        m.put(EmbodiedBehaviors.TALK.toString(), loop(RegisterAnimationKeysEvent.TALK));
        return m;
    }

    /** Collects addons' keys; called once during client setup. */
    public static void load() {
        RegisterAnimationKeysEvent event = new RegisterAnimationKeysEvent();
        ModLoader.postEvent(event);
        Map<String, RawAnimation> m = base();
        for (Map.Entry<Id, String> e : event.animations().entrySet()) m.put(e.getKey().toString(), loop(e.getValue()));
        byKey = m;
    }

    public static RawAnimation get(String key) {
        return byKey.getOrDefault(key, IDLE);
    }
}
