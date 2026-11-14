package com.kostya.createbrewery.social;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.advancement.BreweryAdvancements;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Treating others: credits a brewer when someone else drinks their drink, and pays out what they missed offline. */
@Mod.EventBusSubscriber(modid = CreateBrewery.MOD_ID)
public final class HospitalityEvents {

    private HospitalityEvents() {
    }

    /** {@code drinker} drank a drink brewed by {@code owner}: credit them and award "On the house!" / "Life of the party". */
    public static void treat(MinecraftServer server, ServerPlayer drinker, UUID owner) {
        if (drinker instanceof FakePlayer || owner.equals(drinker.getUUID())) {
            return;
        }
        HospitalityData data = HospitalityData.get(server);
        int count = data.credit(owner, drinker.getUUID());
        ServerPlayer brewer = online(server, owner);
        grant(data, brewer, owner, "treat");
        if (count >= HospitalityData.PARTY_SIZE) {
            grant(data, brewer, owner, "life_of_the_party");
        }
    }

    private static void grant(HospitalityData data, ServerPlayer brewer, UUID owner, String advancement) {
        if (brewer != null) {
            BreweryAdvancements.award(brewer, advancement, "treated");
        } else {
            data.addPending(owner, advancement);
        }
    }

    private static ServerPlayer online(MinecraftServer server, UUID uuid) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getUUID().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            onLogin(player);
        }
    }

    /** Grants the advancements the player earned while away. */
    public static void onLogin(ServerPlayer player) {
        for (String advancement : HospitalityData.get(player.server).takePending(player.getUUID())) {
            BreweryAdvancements.award(player, advancement, "treated");
        }
    }
}
