package com.kostya.createbrewery.client;

import com.kostya.createbrewery.drunk.DrunkManager;
import io.github.fabricators_of_create.porting_lib.client_events.event.client.MovementInputUpdateCallback;
import io.github.fabricators_of_create.porting_lib.event.client.InteractEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.world.InteractionResult;

/**
 * Takes the controls away from a player who has passed out. Forge's {@code MovementInputUpdateEvent} and
 * {@code InteractionKeyMappingTriggered} are Porting Lib's {@link MovementInputUpdateCallback} and
 * {@link InteractEvents} (use, attack incl. held mining, pick block); the client tick is Fabric's.
 */
@Environment(EnvType.CLIENT)
public final class DrunkKnockoutClient {

    private DrunkKnockoutClient() {
    }

    public static void register() {
        MovementInputUpdateCallback.EVENT.register((player, input) -> onMovementInput(input));
        // FAIL cancels without swinging the hand (Forge: setSwingHand(false) and cancel).
        InteractEvents.USE.register((minecraft, hit, hand) -> knockedOut() ? InteractionResult.FAIL : InteractionResult.PASS);
        InteractEvents.ATTACK.register((minecraft, hit) -> knockedOut() ? InteractionResult.FAIL : InteractionResult.PASS);
        InteractEvents.PICK.register((minecraft, hit) -> knockedOut());
        ClientTickEvents.END_CLIENT_TICK.register(DrunkKnockoutClient::onClientTick);
    }

    private static boolean knockedOut() {
        var player = Minecraft.getInstance().player;
        return player != null && DrunkManager.isKnockedOut(player);
    }

    private static void onMovementInput(Input input) {
        if (knockedOut()) {
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

    /** No menus either: they would keep working behind the blackout. */
    private static void onClientTick(Minecraft minecraft) {
        if (knockedOut() && minecraft.screen != null && !minecraft.screen.isPauseScreen()) {
            minecraft.setScreen(null);
        }
    }
}
