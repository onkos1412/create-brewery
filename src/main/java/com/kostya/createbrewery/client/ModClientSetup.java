package com.kostya.createbrewery.client;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.block.BarrelScreen;
import com.kostya.createbrewery.block.DistilleryScreen;
import com.kostya.createbrewery.block.KettleScreen;
import com.kostya.createbrewery.block.ModMenus;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import net.minecraft.client.RecipeBookCategories;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;

/**
 * Client-only screen registration.
 */
@EventBusSubscriber(modid = CreateBrewery.MOD_ID, value = Dist.CLIENT)
public final class ModClientSetup {

    private ModClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.KETTLE.get(), KettleScreen::new);
        event.register(ModMenus.DISTILLERY.get(), DistilleryScreen::new);
        event.register(ModMenus.BARREL.get(), BarrelScreen::new);
    }

    @SubscribeEvent
    public static void registerRecipeCategories(RegisterRecipeBookCategoriesEvent event) {
        event.registerRecipeCategoryFinder(ModRecipeTypes.BREWING.get(), recipe -> RecipeBookCategories.UNKNOWN);
    }
}
