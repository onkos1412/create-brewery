package com.kostya.createbrewery.client;

import com.kostya.createbrewery.drunk.DrunkData;
import com.kostya.createbrewery.network.DrunkSyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Client only: stores a synced drunk level and blackout on the local player. */
public final class DrunkClientSync {

    private DrunkClientSync() {
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
