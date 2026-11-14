package com.kostya.createbrewery.client;

import com.kostya.createbrewery.network.OpenBrewerBookPayload;

/**
 * Client side of the brewer's book: opens its screen from the server's payload. Client only: reached from
 * {@link OpenBrewerBookPayload#handle} through {@code DistExecutor}.
 */
public final class BrewerBookClient {
    private BrewerBookClient() {
    }

    public static void open(OpenBrewerBookPayload payload) {
        // PORT: Task 6 - the screen comes back with client/**:
        // Minecraft.getInstance().setScreen(new BrewerBookScreen(payload));
    }
}
