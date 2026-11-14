package com.kostya.createbrewery;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(CreateBrewery.MOD_ID)
public class CreateBrewery {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "brewery";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // Content registries come back here as the port brings each slice over from port/todo.
    public CreateBrewery() {
        LOGGER.info("Create Brewery: constructed on 1.20.1 Forge");
    }

    /** Builds a resource location in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
