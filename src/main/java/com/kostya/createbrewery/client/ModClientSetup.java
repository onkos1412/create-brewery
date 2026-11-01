package com.kostya.createbrewery.client;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.block.BarrelScreen;
import com.kostya.createbrewery.block.DistilleryScreen;
import com.kostya.createbrewery.block.KettleBlockEntity;
import com.kostya.createbrewery.block.KettleScreen;
import com.kostya.createbrewery.block.ModBlocks;
import com.kostya.createbrewery.block.ModMenus;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModItems;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;

import java.util.Map;

/**
 * Client-only screen registration.
 */
@EventBusSubscriber(modid = CreateBrewery.MOD_ID, value = Dist.CLIENT)
public final class ModClientSetup {
    private static final int STAR_EARNED = 0xFFFFC83D;
    private static final int STAR_MISSED = 0xFF3A3226;

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
            Map.entry(DrinkData.SPOILED_STAGE, 9f));

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
    }

    /** A row of pips across the top of a bottle's icon, so quality reads without hovering. */
    @SubscribeEvent
    public static void registerItemDecorators(RegisterItemDecorationsEvent event) {
        IItemDecorator pips = (graphics, font, stack, x, y) -> {
            DrinkData data = stack.get(ModDataComponents.DRINK_DATA.get());
            if (data == null || data.isSpoiled()) {
                return false;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            for (int star = 0; star < DrinkData.MAX_STARS; star++) {
                int left = x + 1 + star * 3;
                graphics.fill(left, y, left + 2, y + 2, star < data.stars() ? STAR_EARNED : STAR_MISSED);
            }
            graphics.pose().popPose();
            return false;
        };
        event.register(ModItems.DRINK_BOTTLE.get(), pips);
        event.register(ModItems.DISTILLATE.get(), pips);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlocks.DISTILLERY_BLOCK_ENTITY.get(), DistilleryRenderer::new);
    }

    /** Lets the bottle's item model pick its art from the drink inside. */
    @SubscribeEvent
    public static void registerItemProperties(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new BreweryPonder());
        event.enqueueWork(DistilleryRenderer::registerVisual);
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
