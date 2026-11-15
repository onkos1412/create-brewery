package com.kostya.createbrewery.client;

import com.kostya.createbrewery.drunk.DrunkScreenEffect;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * The mod's client entry point, named under {@code client} in fabric.mod.json, so only a physical client loads it
 * and everything it registers. Forge found these classes through {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)}.
 */
@Environment(EnvType.CLIENT)
public class CreateBreweryClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Drunkenness: the synced level, the camera sway and the blackout's lost controls.
        DrunkClientSync.register();
        DrunkScreenEffect.register();
        DrunkKnockoutClient.register();
        // The brewer's book opens from the server's payload.
        BrewerBookClient.register();
    }
}
