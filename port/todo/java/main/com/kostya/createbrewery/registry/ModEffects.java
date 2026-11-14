package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.social.ToastEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry of the mod's mob effects.
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, CreateBrewery.MOD_ID);

    /** The toast marker, given by clinking drinks and spent by the next drink. */
    public static final RegistryObject<MobEffect> TOAST = EFFECTS.register("toast", ToastEffect::new);

    private ModEffects() {
    }
}
