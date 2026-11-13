package com.kostya.createbrewery.client;

import com.kostya.createbrewery.CreateBrewery;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only mod entry point: gives the Mods list a "Config" button that opens NeoForge's generated
 * configuration screen for the server config. Only loaded on the physical client.
 */
@Mod(value = CreateBrewery.MOD_ID, dist = Dist.CLIENT)
public final class BreweryClient {
    public BreweryClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
