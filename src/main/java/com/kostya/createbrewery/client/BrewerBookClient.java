package com.kostya.createbrewery.client;

import com.kostya.createbrewery.network.OpenBrewerBookPayload;

/** Client side of the brewer's book: opens its screen from the server's payload. */
public final class BrewerBookClient {
    private BrewerBookClient() {
    }

    /** Opens the book. The screen comes later; for now this does nothing. */
    public static void open(OpenBrewerBookPayload payload) {
    }
}
