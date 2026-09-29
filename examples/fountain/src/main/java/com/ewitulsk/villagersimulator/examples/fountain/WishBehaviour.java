package com.ewitulsk.villagersimulator.examples.fountain;

import com.ewitulsk.villagersimulator.api.mod.embodiment.EmbodiedBehaviour;
import com.ewitulsk.villagersimulator.api.mod.embodiment.PuppetContext;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** A puppet making a wish holds a gold nugget and, every few seconds, tosses it into the water with a splash. */
public final class WishBehaviour implements EmbodiedBehaviour {
    @Override
    public void tick(PuppetContext ctx) {
        var mob = ctx.entity();
        if (!ctx.arrived()) return;
        if (!mob.getMainHandItem().is(Items.GOLD_NUGGET)) mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLD_NUGGET));
        // The fountain's water is north of the wish point.
        Vec3 water = ctx.target().add(0, 0.2, -1.5);
        mob.getLookControl().setLookAt(water.x, water.y, water.z);
        if (ctx.ticks() % 80 == 40) {
            mob.swing(InteractionHand.MAIN_HAND);
            ctx.level().sendParticles(ParticleTypes.SPLASH, water.x, water.y + 0.6, water.z, 12, 0.3, 0.1, 0.3, 0.1);
            ctx.level().playSound(null, water.x, water.y, water.z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.4f, 1.4f);
        }
    }

    @Override
    public void stop(PuppetContext ctx) {
        if (ctx.entity().getMainHandItem().is(Items.GOLD_NUGGET)) ctx.entity().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }
}
