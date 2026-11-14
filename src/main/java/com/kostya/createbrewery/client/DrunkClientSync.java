package com.kostya.createbrewery.client;

import com.kostya.createbrewery.drunk.DrunkData;
import com.kostya.createbrewery.network.DrunkSyncPacket;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Client only: stores a synced drunk level and blackout on the local player. */
@Environment(EnvType.CLIENT)
public final class DrunkClientSync {

    private DrunkClientSync() {
    }

    /** Registers the {@link DrunkSyncPacket#ID} receiver; called from the client entrypoint. */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DrunkSyncPacket.ID, (client, handler, buf, sender) -> {
            DrunkSyncPacket packet = DrunkSyncPacket.decode(buf);
            // Received on the network thread; the player is only touched on the main thread, as Forge's
            // consumerMainThread did.
            client.execute(() -> apply(packet));
        });
    }

    public static void apply(DrunkSyncPacket packet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            DrunkData data = DrunkData.of(player);
            data.level().set(packet.level());
            data.setKnockoutTicks(packet.knockoutTicks());
        }
    }
}
