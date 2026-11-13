package com.kostya.createbrewery.network;

import com.kostya.createbrewery.client.BrewerBookClient;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The mod's network payloads. Registered on the mod event bus. */
public final class ModNetwork {
    public static final String VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        // The handler only runs on a client, so BrewerBookClient is never loaded on a dedicated server.
        registrar.playToClient(OpenBrewerBookPayload.TYPE, OpenBrewerBookPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> BrewerBookClient.open(payload)));
    }
}
