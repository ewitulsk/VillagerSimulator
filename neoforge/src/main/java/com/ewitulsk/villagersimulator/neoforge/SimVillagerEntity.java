package com.ewitulsk.villagersimulator.neoforge;

import com.ewitulsk.villagersimulator.api.sim.activity.EmbodiedBehaviors;
import com.ewitulsk.villagersimulator.api.sim.core.Embodiment;
import com.ewitulsk.villagersimulator.neoforge.server.SimBridge;
import com.ewitulsk.villagersimulator.neoforge.server.SimServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A villager puppet: the T0 body of a sim villager (docs/ARCHITECTURE.md §12.4). It has no AI of its own; each tick
 * it follows the sim's embodiment (destination, behaviour, name). It is never saved: if it has no valid handle it
 * removes itself, so a crash can never leave duplicates behind.
 */
public class SimVillagerEntity extends PathfinderMob implements GeoEntity {
    /** Packed appearance genes, painted into a texture on the client. */
    public static final EntityDataAccessor<Integer> APPEARANCE = SynchedEntityData.defineId(SimVillagerEntity.class, EntityDataSerializers.INT);
    /** What the villager is doing, for the animation controller ({@code BEHAVIOR_*}). */
    /** The embodied behaviour key, e.g. {@code villagersimulator:work}; the client maps it to an animation ({@link AnimationKeys}). */
    public static final EntityDataAccessor<String> BEHAVIOR = SynchedEntityData.defineId(SimVillagerEntity.class, EntityDataSerializers.STRING);
    private static final String SLEEP_KEY = EmbodiedBehaviors.SLEEP.toString();

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private static final double SPEED = 0.6;
    /** Farther than this from the plan position, the puppet snaps back (e.g. it got stuck). */
    private static final double MAX_DRIFT = 12;

    private int handle;
    private double lastTx = Double.NaN, lastTy, lastTz;
    private String lastLabel = "";
    private int repath;
    // The addon behaviour being performed (mod-api EmbodiedBehaviour), and for how long.
    private com.ewitulsk.villagersimulator.api.sim.Id activeKey;
    private com.ewitulsk.villagersimulator.api.mod.embodiment.EmbodiedBehaviour active;
    private int activeTicks;

    public SimVillagerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(APPEARANCE, 0);
        builder.define(BEHAVIOR, EmbodiedBehaviors.IDLE.toString());
    }

    public int appearance() {
        return entityData.get(APPEARANCE);
    }

    public String behavior() {
        return entityData.get(BEHAVIOR);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, this::animate));
    }

    /** Animation state driven by the embodied behaviour the sim sends (docs/ARCHITECTURE.md §16). */
    private PlayState animate(AnimationState<SimVillagerEntity> state) {
        String key = behavior();
        if (isSleeping() || SLEEP_KEY.equals(key)) return state.setAndContinue(AnimationKeys.SLEEP);
        if (state.isMoving()) return state.setAndContinue(AnimationKeys.WALK);
        return state.setAndContinue(AnimationKeys.get(key));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    /** Sets the sim entity this puppet embodies. */
    public void bind(int handle) {
        this.handle = handle;
    }

    public int handle() {
        return handle;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            SimServer sim = SimServer.get();
            SimBridge bridge = sim == null ? null : sim.bridge();
            Embodiment e = bridge == null || handle == 0 ? null : bridge.embodiment(handle);
            if (e == null || !bridge.owns(this)) {
                discard();
                return;
            }
            super.tick();
            apply(e);
            perform(sim, e);
            return;
        }
        super.tick();
    }

    /** Follows the embodiment: navigation, sleeping, conversation, name tag. */
    public void apply(Embodiment e) {
        SimServer sim = SimServer.get();
        Embodiment partner = sim == null ? null : sim.bridge().partnerOf(handle);
        SimVillagerEntity partnerPuppet = partner == null ? null : sim.bridge().puppet(partner.id());
        String label = e.name() + " · " + (partner != null ? "Chatting with " + partner.name().split(" ")[0] : e.activityLabel());
        if (!label.equals(lastLabel)) {
            lastLabel = label;
            setCustomName(Component.literal(label));
            setCustomNameVisible(true);
        }

        boolean sleep = EmbodiedBehaviors.SLEEP.equals(e.behavior());
        if (appearance() != (int) e.appearance()) entityData.set(APPEARANCE, (int) e.appearance());
        String behavior = (partner != null ? EmbodiedBehaviors.TALK : e.behavior()).toString();
        if (!behavior().equals(behavior)) entityData.set(BEHAVIOR, behavior);
        BlockPos target = BlockPos.containing(e.tx(), e.ty(), e.tz());
        if (isSleeping()) {
            if (sleep && target.equals(getSleepingPos().orElse(null))) return;
            stopSleeping();
        }

        if (distanceToSqr(e.x(), e.y(), e.z()) > MAX_DRIFT * MAX_DRIFT) {
            getNavigation().stop();
            moveTo(e.x(), e.y(), e.z(), getYRot(), getXRot());
        }

        double d2 = distanceToSqr(e.tx(), e.ty(), e.tz());
        if (sleep && d2 < 2.5 * 2.5 && level().getBlockState(target).getBlock() instanceof BedBlock) {
            getNavigation().stop();
            startSleeping(target);
            return;
        }
        boolean moved = e.tx() != lastTx || e.ty() != lastTy || e.tz() != lastTz;
        if (d2 > 0.6 * 0.6) {
            if (moved || --repath <= 0 || getNavigation().isDone()) {
                getNavigation().moveTo(e.tx(), e.ty(), e.tz(), SPEED);
                repath = 40;
            }
        } else if (!getNavigation().isDone()) {
            getNavigation().stop();
        }
        // Embodied conversation: face the partner and gesture now and then. The outcome is decided by the sim.
        if (partnerPuppet != null && distanceToSqr(partnerPuppet) < 8 * 8 && getNavigation().isDone()) {
            getLookControl().setLookAt(partnerPuppet, 30, 30);
            if ((tickCount + handle) % 60 == 0) swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        lastTx = e.tx();
        lastTy = e.ty();
        lastTz = e.tz();
    }

    /** Runs the addon's embodied behaviour for the current activity, if one is registered (mod-api). */
    private void perform(SimServer sim, Embodiment e) {
        var key = e.behavior();
        var behaviour = sim.behaviour(key);
        if (active != null && (behaviour != active || !key.equals(activeKey))) {
            try {
                active.stop(new PuppetContextImpl(this, e, activeKey, activeTicks));
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger("VillagerSim").warn("Embodied behaviour {} failed to stop", activeKey, ex);
            }
            active = null;
        }
        if (behaviour == null) return;
        if (active == null) {
            active = behaviour;
            activeKey = key;
            activeTicks = 0;
        }
        try {
            behaviour.tick(new PuppetContextImpl(this, e, key, activeTicks++));
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger("VillagerSim").warn("Embodied behaviour {} failed", key, ex);
            active = null;
        }
    }

    /** What addon behaviours see of this puppet. */
    private record PuppetContextImpl(SimVillagerEntity puppet, Embodiment e, com.ewitulsk.villagersimulator.api.sim.Id key, int ticks)
            implements com.ewitulsk.villagersimulator.api.mod.embodiment.PuppetContext {
        PuppetContextImpl(SimVillagerEntity puppet, Embodiment e) {
            this(puppet, e, e.behavior(), 0);
        }

        @Override
        public PathfinderMob entity() {
            return puppet;
        }

        @Override
        public net.minecraft.server.level.ServerLevel level() {
            return (net.minecraft.server.level.ServerLevel) puppet.level();
        }

        @Override
        public com.ewitulsk.villagersimulator.api.sim.EntityId villager() {
            return e.id();
        }

        @Override
        public com.ewitulsk.villagersimulator.api.sim.Id activity() {
            return e.activity();
        }

        @Override
        public com.ewitulsk.villagersimulator.api.sim.Id behaviour() {
            return key;
        }

        @Override
        public net.minecraft.world.phys.Vec3 target() {
            return new net.minecraft.world.phys.Vec3(e.tx(), e.ty(), e.tz());
        }

        @Override
        public boolean arrived() {
            return puppet.distanceToSqr(e.tx(), e.ty(), e.tz()) < 1.2 * 1.2;
        }
    }

    /** Right-click: talk, or give the held item as a gift. */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player,
                                                               net.minecraft.world.InteractionHand hand) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResult.PASS;
        if (!level().isClientSide) com.ewitulsk.villagersimulator.neoforge.server.SimPlayers.interact(this, player, hand);
        return net.minecraft.world.InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void remove(RemovalReason reason) {
        // Free the bed so players can still use it.
        if (isSleeping()) stopSleeping();
        super.remove(reason);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Only the bridge removes puppets.
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // Damage and death become sim events in a later phase; until then puppets only yield to /kill.
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
}
