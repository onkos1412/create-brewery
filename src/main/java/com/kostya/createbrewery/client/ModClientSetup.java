package com.kostya.createbrewery.client;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.block.BarrelScreen;
import com.kostya.createbrewery.block.DistilleryScreen;
import com.kostya.createbrewery.block.KettleBlockEntity;
import com.kostya.createbrewery.block.KettleScreen;
import com.kostya.createbrewery.block.ModBlocks;
import com.kostya.createbrewery.block.ModMenus;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import com.kostya.createbrewery.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterRecipeBookCategoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;

/**
 * Client-only screen registration. Mod-bus events, physical client only.
 */
@Mod.EventBusSubscriber(modid = CreateBrewery.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModClientSetup {
    /** Values of the {@code brewery:drink} property, matching the overrides in drink_bottle.json. */
    private static final Map<String, Float> BOTTLE_ART = Map.ofEntries(
            Map.entry("whiskey", 1f), Map.entry("aged_whiskey", 1f),
            Map.entry("cider", 2f),
            Map.entry("potato_mash", 3f), Map.entry("cane_mash", 3f),
            Map.entry("calvados", 4f), Map.entry("aged_calvados", 4f),
            Map.entry("vodka", 5f),
            Map.entry("rum", 6f), Map.entry("aged_rum", 6f),
            Map.entry("young_wine", 7f), Map.entry("wine", 7f),
            Map.entry("young_mead", 8f), Map.entry("mead", 8f),
            Map.entry(DrinkData.SPOILED_STAGE, 9f), Map.entry("muddy", 9f),
            Map.entry("kvass", 10f),
            Map.entry("young_stout", 11f), Map.entry("stout", 11f),
            Map.entry("pulque", 3f), Map.entry("wormwood_mash", 3f), Map.entry("glow_mash", 3f),
            Map.entry("tequila", 12f), Map.entry("aged_tequila", 12f),
            Map.entry("absinthe", 13f),
            Map.entry("glow_spirit", 14f), Map.entry("firefly_liqueur", 14f));

    private ModClientSetup() {
    }

    /** The kettle's surface is drawn in grey and tinted with whatever is inside right now. */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> level != null && pos != null
                && level.getBlockEntity(pos) instanceof KettleBlockEntity kettle
                ? kettle.getLiquidColor() : KettleBlockEntity.EMPTY_COLOR, ModBlocks.KETTLE.get());
    }

    /** The item has nothing inside, so its surface shows the kettle's bare bottom. */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> 0xFF000000 | KettleBlockEntity.EMPTY_COLOR, ModBlocks.KETTLE_ITEM.get());
        // The mug's liquid layer is grey art tinted with the colour of the drink inside.
        event.register((stack, tintIndex) -> {
            DrinkData data = DrinkData.get(stack);
            var level = Minecraft.getInstance().level;
            if (tintIndex != 1 || data == null || level == null) {
                return 0xFFFFFFFF;
            }
            return 0xFF000000 | BrewingRecipe.profileWithStage(level, data.stage()).color();
        }, ModItems.DRINK_MUG.get());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.DISTILLERY_BLOCK_ENTITY.get(), DistilleryRenderer::new);
    }

    /**
     * Lets the bottle's item model pick its art from the drink inside. Also the menu screens: 1.20.1 has no
     * {@code RegisterMenuScreensEvent}, so they go through {@link MenuScreens#register} on the main thread.
     * The Ponder plugin is added earlier, from {@link BreweryClient#init}.
     */
    @SubscribeEvent
    public static void registerItemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(DistilleryRenderer::registerVisual);
        event.enqueueWork(() -> ItemProperties.register(ModItems.DRINK_BOTTLE.get(), CreateBrewery.id("drink"),
                (stack, level, entity, seed) -> {
                    DrinkData data = DrinkData.get(stack);
                    return data == null ? 0f : BOTTLE_ART.getOrDefault(data.stage(), 0f);
                }));
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.KETTLE.get(), KettleScreen::new);
            MenuScreens.register(ModMenus.DISTILLERY.get(), DistilleryScreen::new);
            MenuScreens.register(ModMenus.BARREL.get(), BarrelScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerRecipeCategories(RegisterRecipeBookCategoriesEvent event) {
        event.registerRecipeCategoryFinder(ModRecipeTypes.BREWING.get(), recipe -> RecipeBookCategories.UNKNOWN);
    }
}
