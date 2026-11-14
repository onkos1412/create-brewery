package com.kostya.createbrewery.network;

import com.kostya.createbrewery.CreateBrewery;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to owning client: the player's drunk level and blackout ticks, the two values 1.21.1 synced as
 * attachments ({@code drunk_level}, {@code knockout_ticks}, both as var-ints), on channel {@link #ID}.
 *
 * <p>Common code: it only writes and reads the bytes. The client receiver is {@code client.DrunkClientSync},
 * registered from the client entrypoint, so a dedicated server never loads a client class for it.
 */
public record DrunkSyncPacket(int level, int knockoutTicks) {

    public static final ResourceLocation ID = CreateBrewery.id("drunk_sync");

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(level);
        buf.writeVarInt(knockoutTicks);
    }

    public static DrunkSyncPacket decode(FriendlyByteBuf buf) {
        return new DrunkSyncPacket(buf.readVarInt(), buf.readVarInt());
    }

    /** Sends this through the player's own connection listener, as Forge's {@code PacketDistributor.PLAYER} did. */
    public void sendTo(ServerPlayer player) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encode(buf);
        player.connection.send(ServerPlayNetworking.createS2CPacket(ID, buf));
    }
}
