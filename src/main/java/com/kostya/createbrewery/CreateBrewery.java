package com.kostya.createbrewery;

import com.kostya.createbrewery.block.ModBlocks;
import com.kostya.createbrewery.block.ModMenus;
import com.kostya.createbrewery.config.BreweryConfig;
import com.kostya.createbrewery.network.ModNetwork;
import com.kostya.createbrewery.recipe.BrewingRecipeSerializer;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import com.kostya.createbrewery.registry.ModCreativeTabs;
import com.kostya.createbrewery.registry.ModEffects;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.List;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(CreateBrewery.MOD_ID)
public class CreateBrewery {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "brewery";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // Content registries come back here as the port brings each slice over from port/todo.
    // Forge 47.4 hands the loading context to the constructor, as NeoForge does with the bus and container.
    public CreateBrewery(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        contentRegistries().forEach(registry -> registry.register(modEventBus));
        // The channel carries the drunk sync now; Task 5 adds the brewer's book payload to it.
        ModNetwork.register();
        context.registerConfig(ModConfig.Type.SERVER, BreweryConfig.SPEC);
        LOGGER.info("Create Brewery: {} content registries wired", contentRegistries().size());
    }

    /** Builds a resource location in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Every content registry the mod owns, in one place so wiring is explicit and
     * can be audited in a single spot. Called from the mod constructor.
     */
    public static List<DeferredRegister<?>> contentRegistries() {
        return List.of(
                ModFluids.FLUID_TYPES,
                ModFluids.FLUIDS,
                ModItems.ITEMS,
                ModEffects.EFFECTS,
                // 1.21.1's ModDataComponents.DATA_COMPONENTS is gone: the data lives in stack NBT (see StackNbt).
                ModRecipeTypes.RECIPE_TYPES,
                BrewingRecipeSerializer.SERIALIZERS,
                ModBlocks.BLOCKS,
                ModBlocks.BLOCK_ITEMS,
                ModBlocks.BLOCK_ENTITIES,
                ModMenus.MENUS,
                ModCreativeTabs.TABS
                // 1.21.1's ModAttachments.ATTACHMENTS is gone: the drunk player data is the DrunkData capability,
                // registered by @AutoRegisterCapability and attached in DrunkEvents.
        );
    }
}
