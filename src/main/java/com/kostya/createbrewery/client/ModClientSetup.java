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
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import net.createmod.ponder.foundation.PonderIndex;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRenderHandler;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Client-only registration: colours, renderers, item properties, screens, fluid rendering and Ponder. Forge reached it
 * through {@code @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = MOD)}; here only {@link CreateBreweryClient}, the
 * {@code client} entrypoint, calls it, so a dedicated server never loads it.
 */
@Environment(EnvType.CLIENT)
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

    /**
     * Everything Forge registered from its client mod-bus events, in the same order. Called from the client
     * entrypoint, after every {@code main} entrypoint, so the registries are filled.
     */
    static void register() {
        registerBlockColors();
        registerItemColors();
        registerRenderers();
        registerItemProperties();
        registerFluidRendering();
        // Forge added the plugin from the client half of the mod constructor; Create Fabric does it from its
        // client entrypoint too.
        PonderIndex.addPlugin(new BreweryPonder());
        // Forge's RegisterRecipeBookCategoriesEvent finder (brewing -> RecipeBookCategories.UNKNOWN) is the
        // client mixin ClientRecipeBookMixin: there is no Fabric API event for it.
    }

    /** The kettle's surface is drawn in grey and tinted with whatever is inside right now. */
    private static void registerBlockColors() {
        ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> level != null && pos != null
                && level.getBlockEntity(pos) instanceof KettleBlockEntity kettle
                ? kettle.getLiquidColor() : KettleBlockEntity.EMPTY_COLOR, ModBlocks.KETTLE.get());
    }

    /** The item has nothing inside, so its surface shows the kettle's bare bottom. */
    private static void registerItemColors() {
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> 0xFF000000 | KettleBlockEntity.EMPTY_COLOR,
                ModBlocks.KETTLE_ITEM.get());
        // The mug's liquid layer is grey art tinted with the colour of the drink inside.
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            DrinkData data = DrinkData.get(stack);
            var level = Minecraft.getInstance().level;
            if (tintIndex != 1 || data == null || level == null) {
                return 0xFFFFFFFF;
            }
            return 0xFF000000 | BrewingRecipe.profileWithStage(level, data.stage()).color();
        }, ModItems.DRINK_MUG.get());
    }

    private static void registerRenderers() {
        BlockEntityRenderers.register(ModBlocks.DISTILLERY_BLOCK_ENTITY.get(), DistilleryRenderer::new);
        DistilleryRenderer.registerVisual();
    }

    /**
     * Lets the bottle's item model pick its art from the drink inside. Also the menu screens: 1.20.1 has no
     * {@code RegisterMenuScreensEvent}, so they go through {@link MenuScreens#register}.
     */
    private static void registerItemProperties() {
        ItemProperties.register(ModItems.DRINK_BOTTLE.get(), CreateBrewery.id("drink"),
                (stack, level, entity, seed) -> {
                    DrinkData data = DrinkData.get(stack);
                    return data == null ? 0f : BOTTLE_ART.getOrDefault(data.stage(), 0f);
                });
        MenuScreens.register(ModMenus.KETTLE.get(), KettleScreen::new);
        MenuScreens.register(ModMenus.DISTILLERY.get(), DistilleryScreen::new);
        MenuScreens.register(ModMenus.BARREL.get(), BarrelScreen::new);
    }

    /**
     * Forge's wort {@code IClientFluidTypeExtensions}: the still and flowing textures and the plain beer tint come
     * from the fluid render handler, and each wort's own drink colour in Create's pipes, tanks and spouts from the
     * Transfer API's variant render handler, which Create Fabric asks with the variant (and so its NBT). Forge's
     * fog colour has no Fabric hook and no use: wort has no block form, so the camera is never inside it.
     */
    private static void registerFluidRendering() {
        int beer = 0xFF000000 | DrinkProfile.BEER.color();
        FluidRenderHandlerRegistry.INSTANCE.register(ModFluids.WORT.get(), ModFluids.FLOWING_WORT.get(),
                new SimpleFluidRenderHandler(CreateBrewery.id("block/wort_still"), CreateBrewery.id("block/wort_flow"),
                        beer));
        FluidVariantRenderHandler drinkTint = new FluidVariantRenderHandler() {
            @Override
            public int getColor(FluidVariant variant, @Nullable BlockAndTintGetter view, @Nullable BlockPos pos) {
                DrinkData data = DrinkData.get(variant);
                ClientLevel level = Minecraft.getInstance().level;
                if (data == null || level == null) {
                    return beer;
                }
                return 0xFF000000 | BrewingRecipe.profileOf(level, data.stage()).color();
            }
        };
        FluidVariantRendering.register(ModFluids.WORT.get(), drinkTint);
        FluidVariantRendering.register(ModFluids.FLOWING_WORT.get(), drinkTint);
    }
}
