package com.kostya.createbrewery;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/** The mod's common entry point, named in fabric.mod.json. */
public class CreateBrewery implements ModInitializer {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "brewery";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // Content registries come back here as the port brings each slice over from port/todo.
    @Override
    public void onInitialize() {
        LOGGER.info("Create Brewery: initialized on 1.20.1 Fabric");
    }

    /** Builds a resource location in this mod's namespace. */
    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
