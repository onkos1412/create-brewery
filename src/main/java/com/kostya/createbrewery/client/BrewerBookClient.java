package com.kostya.createbrewery.client;

import com.kostya.createbrewery.network.OpenBrewerBookPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Client side of the brewer's book: opens its screen from the server's payload. Client only: registered from the
 * client entrypoint, as Forge reached it through {@code DistExecutor}.
 */
@Environment(EnvType.CLIENT)
public final class BrewerBookClient {
    private BrewerBookClient() {
    }

    /** Registers the {@link OpenBrewerBookPayload#ID} receiver; called from the client entrypoint. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(OpenBrewerBookPayload.ID, (client, handler, buf, sender) -> {
            OpenBrewerBookPayload payload = OpenBrewerBookPayload.decode(buf);
            // Received on the network thread; screens open on the main thread, as Forge's consumerMainThread did.
            client.execute(() -> open(payload));
        });
    }

    private static void open(OpenBrewerBookPayload payload) {
        Minecraft.getInstance().setScreen(new BrewerBookScreen(payload));
    }
}
