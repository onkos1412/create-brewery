package com.kostya.createbrewery.drunk;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Gameplay events for the drunk system: sobering over time and resetting on death.
 */
@EventBusSubscriber(modid = CreateBrewery.MOD_ID)
public final class DrunkEvents {

    /** How often (in ticks) a sober step is applied: once per second. */
    public static final int SOBER_INTERVAL_TICKS = 20;

    private DrunkEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (player.tickCount % SOBER_INTERVAL_TICKS == 0) {
            DrunkManager.soberTick(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            DrunkManager.reset(player);
        }
    }
}
