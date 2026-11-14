package com.kostya.createbrewery.network;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's network channel. 1.21.1 registered payloads in {@code RegisterPayloadHandlersEvent} and synced
 * the drunk attachments itself; 1.20.1 Forge uses one {@link SimpleChannel}, set up from the mod constructor.
 */
public final class ModNetwork {
    public static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CreateBrewery.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private static int nextId;

    private ModNetwork() {
    }

    public static void register() {
        // Handlers that touch the client run only on a client (see each packet), so client classes are never
        // loaded on a dedicated server.
        CHANNEL.messageBuilder(DrunkSyncPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DrunkSyncPacket::encode)
                .decoder(DrunkSyncPacket::decode)
                .consumerMainThread(DrunkSyncPacket::handle)
                .add();
        // PORT: Task 5 - the brewer's book payload (1.21.1: registrar.playToClient(OpenBrewerBookPayload.TYPE,
        // OpenBrewerBookPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> BrewerBookClient.open(payload)))).
    }

    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
