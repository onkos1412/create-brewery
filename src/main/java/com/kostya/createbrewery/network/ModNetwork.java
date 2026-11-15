package com.kostya.createbrewery.network;

import com.kostya.createbrewery.CreateBrewery;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * The mod's server-to-client channels. Forge used one {@code SimpleChannel} with a message id per packet; Fabric
 * names a channel per packet, so the ids live here and every packet goes out through {@link #send}.
 *
 * <p>Common code: only bytes are written here. The client receivers ({@code client.DrunkClientSync},
 * {@code client.BrewerBookClient}) are registered from the client entrypoint, so a dedicated server never loads a
 * client class.
 */
public final class ModNetwork {
    /** The player's drunk level and blackout: {@link DrunkSyncPacket}. */
    public static final ResourceLocation DRUNK_SYNC = CreateBrewery.id("drunk_sync");
    /** Opens the brewer's book: {@link OpenBrewerBookPayload}. */
    public static final ResourceLocation OPEN_BOOK = CreateBrewery.id("open_book");

    private ModNetwork() {
    }

    /**
     * Sends a packet on {@code channel} through the player's own connection listener, as Forge's
     * {@code PacketDistributor.PLAYER} did. Sent unconditionally, as Forge's channel was: the client's channel
     * registration may arrive after the player joined.
     */
    public static void send(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writer.accept(buf);
        player.connection.send(ServerPlayNetworking.createS2CPacket(channel, buf));
    }
}
