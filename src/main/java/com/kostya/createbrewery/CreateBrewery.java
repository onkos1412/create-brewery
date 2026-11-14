package com.kostya.createbrewery;

import com.kostya.createbrewery.block.ModBlocks;
import com.kostya.createbrewery.block.ModCapabilities;
import com.kostya.createbrewery.block.ModMenus;
import com.kostya.createbrewery.config.BreweryConfig;
import com.kostya.createbrewery.recipe.BrewingRecipeSerializer;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import com.kostya.createbrewery.registry.ModCreativeTabs;
import com.kostya.createbrewery.registry.ModEffects;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.mojang.logging.LogUtils;
import fuzs.forgeconfigapiport.api.config.v2.ForgeConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/** The mod's common entry point, named in fabric.mod.json. */
public class CreateBrewery implements ModInitializer {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "brewery";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Every content registry the mod owns, wired in one place so it can be audited in a single spot. Porting Lib's
     * {@code LazyRegistrar} stands in for Forge's {@code DeferredRegister}: the holders keep their Forge names and
     * {@code get()}, and each registrar writes its entries into the vanilla registry here. Content registries come
     * back here as the port brings each slice over from port/todo.
     */
    @Override
    public void onInitialize() {
        ModFluids.register();
        ModBlocks.register();
        ModItems.register();
        ModEffects.register();
        // 1.21.1's ModDataComponents.DATA_COMPONENTS is gone: the data lives in stack NBT (see StackNbt).
        ModRecipeTypes.register();
        BrewingRecipeSerializer.register();
        ModCreativeTabs.register();
        ModMenus.register();
        // The machines' Transfer API storages (Forge's capabilities) and the distillery's stress impact.
        ModCapabilities.register();
        // brewery-server.toml, as on Forge: Forge Config API Port ships with Create Fabric.
        ForgeConfigRegistry.INSTANCE.register(MOD_ID, ModConfig.Type.SERVER, BreweryConfig.SPEC);
        LOGGER.info("Create Brewery: content registries wired on 1.20.1 Fabric");
    }

    /** Builds a resource location in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
