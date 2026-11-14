package com.kostya.createbrewery.recipe;

import com.kostya.createbrewery.CreateBrewery;
import io.github.fabricators_of_create.porting_lib.util.LazyRegistrar;
import io.github.fabricators_of_create.porting_lib.util.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Registry of the mod's recipe types.
 */
public final class ModRecipeTypes {
    public static final LazyRegistrar<RecipeType<?>> RECIPE_TYPES =
            LazyRegistrar.create(Registries.RECIPE_TYPE, CreateBrewery.MOD_ID);

    public static final RegistryObject<RecipeType<BrewingRecipe>> BREWING =
            RECIPE_TYPES.register("brewing", () -> simple(CreateBrewery.id("brewing")));
    private ModRecipeTypes() {
    }

    /** Forge's {@code RecipeType.simple}: vanilla 1.20.1 only has the self-registering {@code RecipeType.register}. */
    private static <T extends Recipe<?>> RecipeType<T> simple(ResourceLocation id) {
        String name = id.toString();
        return new RecipeType<>() {
            @Override
            public String toString() {
                return name;
            }
        };
    }

    public static void register() {
        RECIPE_TYPES.register();
    }
}
