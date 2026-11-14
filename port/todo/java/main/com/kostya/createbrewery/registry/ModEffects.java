package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.social.ToastEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry of the mod's mob effects.
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, CreateBrewery.MOD_ID);

    /** The toast marker, given by clinking drinks and spent by the next drink. */
    public static final DeferredHolder<MobEffect, MobEffect> TOAST = EFFECTS.register("toast", ToastEffect::new);

    private ModEffects() {
    }
}
