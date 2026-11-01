package com.kostya.createbrewery.compat.jei;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.block.ModBlocks;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Optional JEI support: JEI finds this class itself, and nothing else in the mod touches it. */
@JeiPlugin
public final class BreweryJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return CreateBrewery.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new BrewingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            registration.addRecipes(BrewingCategory.TYPE,
                    level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.BREWING.get()).stream()
                            .map(RecipeHolder::value)
                            .toList());
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.KETTLE_ITEM.get()), BrewingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.DISTILLERY_ITEM.get()), BrewingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.BARREL_ITEM.get()), BrewingCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.SPOUT_ITEM.get()), BrewingCategory.TYPE);
    }
}
