package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.social.ToastEffect;
import io.github.fabricators_of_create.porting_lib.util.LazyRegistrar;
import io.github.fabricators_of_create.porting_lib.util.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;

/**
 * Registry of the mod's mob effects.
 */
public final class ModEffects {
    public static final LazyRegistrar<MobEffect> EFFECTS = LazyRegistrar.create(Registries.MOB_EFFECT, CreateBrewery.MOD_ID);

    /** The toast marker, given by clinking drinks and spent by the next drink. */
    public static final RegistryObject<MobEffect> TOAST = EFFECTS.register("toast", ToastEffect::new);

    private ModEffects() {
    }

    public static void register() {
        EFFECTS.register();
    }
}
