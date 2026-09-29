package com.ewitulsk.villagersimulator.api.mod.animation;

import com.ewitulsk.villagersimulator.api.sim.Id;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client: maps embodied behaviour keys to villager animations (docs/ARCHITECTURE.md §16). Fired on the mod bus
 * during client setup. The villager model's animations are {@link #IDLE}, {@link #WALK}, {@link #WORK},
 * {@link #EAT}, {@link #TALK} and {@link #SLEEP}; keys without a mapping play {@link #IDLE}.
 *
 * <pre>{@code
 * e.register(Id.of("villagersimulator_fountain", "make_wish"), RegisterAnimationKeysEvent.WORK);
 * }</pre>
 */
public final class RegisterAnimationKeysEvent extends Event implements IModBusEvent {
    public static final String IDLE = "animation.villager.idle";
    public static final String WALK = "animation.villager.walk";
    public static final String WORK = "animation.villager.work";
    public static final String EAT = "animation.villager.eat";
    public static final String TALK = "animation.villager.talk";
    public static final String SLEEP = "animation.villager.sleep";

    private final Map<Id, String> animations = new LinkedHashMap<>();

    /** Plays the looping animation {@code animation} for puppets whose behaviour key is {@code key}. */
    public void register(Id key, String animation) {
        animations.put(key, animation);
    }

    public Map<Id, String> animations() {
        return Map.copyOf(animations);
    }
}
