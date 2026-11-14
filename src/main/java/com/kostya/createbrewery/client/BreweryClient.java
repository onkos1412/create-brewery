package com.kostya.createbrewery.client;

import com.kostya.createbrewery.CreateBrewery;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Client-only half of the mod constructor, called from {@link CreateBrewery} on the physical client only.
 *
 * <p>Gives the Mods list a "Config" button. 1.21.1 opened NeoForge's generated {@code ConfigurationScreen};
 * Forge 47 has no generated config screen, so this opens Catnip's (the one Create uses), which finds the
 * mod's server config spec by mod id. It also adds the Ponder plugin, as Create does from its client constructor.
 */
public final class BreweryClient {
    private BreweryClient() {
    }

    public static void init(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new BaseConfigScreen(parent, CreateBrewery.MOD_ID)));
        PonderIndex.addPlugin(new BreweryPonder());
    }
}
