package com.kostya.createbrewery.client;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.block.KettleScreen;
import com.kostya.createbrewery.block.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only screen registration.
 */
@EventBusSubscriber(modid = CreateBrewery.MOD_ID, value = Dist.CLIENT)
public final class ModClientSetup {

    private ModClientSetup() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.KETTLE.get(), KettleScreen::new);
    }
}
