package com.kostya.createbrewery.recipe;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

/**
 * Registry of the mod's recipe types.
 */
public final class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, CreateBrewery.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<BrewingRecipe>> BREWING =
            RECIPE_TYPES.register("brewing", () -> RecipeType.simple(CreateBrewery.id("brewing")));
    private ModRecipeTypes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
    }
}
