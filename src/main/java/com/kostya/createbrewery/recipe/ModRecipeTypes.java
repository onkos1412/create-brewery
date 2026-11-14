package com.kostya.createbrewery.recipe;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry of the mod's recipe types.
 */
public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, CreateBrewery.MOD_ID);

    public static final RegistryObject<RecipeType<BrewingRecipe>> BREWING =
            RECIPE_TYPES.register("brewing", () -> RecipeType.simple(CreateBrewery.id("brewing")));
    private ModRecipeTypes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
    }
}
