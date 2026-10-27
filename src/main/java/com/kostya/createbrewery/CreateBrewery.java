package com.kostya.createbrewery;

import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CreateBrewery.MOD_ID)
public class CreateBrewery {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "brewery";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public CreateBrewery(IEventBus modEventBus, ModContainer modContainer) {
        contentRegistries().forEach(registry -> registry.register(modEventBus));
        LOGGER.info("Create Brewery: {} content registries wired", contentRegistries().size());
    }

    /**
     * Every content registry the mod owns, in one place so wiring is explicit and
     * can be audited in a single spot. Called from the mod constructor.
     */
    public static java.util.List<DeferredRegister<?>> contentRegistries() {
        return java.util.List.of(
                ModFluids.FLUID_TYPES,
                ModFluids.FLUIDS,
                ModItems.ITEMS,
                ModDataComponents.DATA_COMPONENTS);
    }
}
