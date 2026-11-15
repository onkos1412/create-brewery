package com.kostya.createbrewery.client;

import com.kostya.createbrewery.CreateBrewery;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Gives the Mods list a "Config" button, as Forge's {@code ConfigScreenHandler} extension point did. It opens Catnip's
 * config screen (the one Create uses), which finds the mod's server config spec by mod id.
 *
 * <p>Named only under the {@code modmenu} entrypoint in fabric.mod.json, which only Mod Menu reads, so without Mod
 * Menu nothing loads this class or its Mod Menu interface. The Ponder plugin that Forge also added here is added by
 * {@link ModClientSetup}.
 */
@Environment(EnvType.CLIENT)
public final class BreweryModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new BaseConfigScreen(parent, CreateBrewery.MOD_ID);
    }
}
