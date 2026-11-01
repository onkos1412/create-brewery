package com.kostya.createbrewery.client;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.block.BarrelScreen;
import com.kostya.createbrewery.block.DistilleryScreen;
import com.kostya.createbrewery.block.KettleScreen;
import com.kostya.createbrewery.block.ModMenus;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModItems;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;

import java.util.Map;

/**
 * Client-only screen registration.
 */
@EventBusSubscriber(modid = CreateBrewery.MOD_ID, value = Dist.CLIENT)
public final class ModClientSetup {

    /** Values of the {@code brewery:drink} property, matching the overrides in drink_bottle.json. */
    private static final Map<String, Float> BOTTLE_ART = Map.of(
            "whiskey", 1f, "aged_whiskey", 1f,
            "cider", 2f,
            "potato_mash", 3f, "cane_mash", 3f,
            "calvados", 4f, "aged_calvados", 4f,
            "vodka", 5f,
            "rum", 6f, "aged_rum", 6f);

    private ModClientSetup() {
    }

    /** Lets the bottle's item model pick its art from the drink inside. */
    @SubscribeEvent
    public static void registerItemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.DRINK_BOTTLE.get(), CreateBrewery.id("drink"),
                (stack, level, entity, seed) -> {
                    DrinkData data = stack.get(ModDataComponents.DRINK_DATA.get());
                    return data == null ? 0f : BOTTLE_ART.getOrDefault(data.stage(), 0f);
                }));
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
