package com.kostya.createbrewery.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to owning client: the player's drunk level and blackout ticks, the two values 1.21.1 synced as
 * attachments ({@code drunk_level}, {@code knockout_ticks}, both as var-ints).
 */
public record DrunkSyncPacket(int level, int knockoutTicks) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(level);
        buf.writeVarInt(knockoutTicks);
    }

    public static DrunkSyncPacket decode(FriendlyByteBuf buf) {
        return new DrunkSyncPacket(buf.readVarInt(), buf.readVarInt());
    }

    /** Runs on the main thread. The client class is named only inside the client-only lambda. */
    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.kostya.createbrewery.client.DrunkClientSync.apply(this));
    }
}
