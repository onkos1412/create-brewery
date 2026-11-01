package com.kostya.createbrewery.client;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.drunk.DrunkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/** Takes the controls away from a player who has passed out. */
@EventBusSubscriber(modid = CreateBrewery.MOD_ID, value = Dist.CLIENT)
public final class DrunkKnockoutClient {

    private DrunkKnockoutClient() {
    }

    private static boolean knockedOut() {
        var player = Minecraft.getInstance().player;
        return player != null && DrunkManager.isKnockedOut(player);
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (knockedOut()) {
            Input input = event.getInput();
            input.forwardImpulse = 0f;
            input.leftImpulse = 0f;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (knockedOut()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** No menus either: they would keep working behind the blackout. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (knockedOut() && minecraft.screen != null && !minecraft.screen.isPauseScreen()) {
            minecraft.setScreen(null);
        }
    }
}
