package com.kostya.createbrewery.drunk;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client-side drunk visuals: the camera rolls and drifts while the player is drunk,
 * with an intensity proportional to the synced drunk level.
 *
 * <p>The sway is driven by the level's continuous game time in seconds, not by the
 * per-tick partial tick, so it moves smoothly instead of jittering in place.
 */
@EventBusSubscriber(modid = CreateBrewery.MOD_ID, value = Dist.CLIENT)
public final class DrunkScreenEffect {

    private DrunkScreenEffect() {
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float strength = currentStrength();
        if (strength <= 0f) {
            return;
        }

        float seconds = currentTimeSeconds(event.getPartialTick());
        DrunkCamera.Angles angles = DrunkCamera.angles(seconds, strength);

        event.setRoll(event.getRoll() + angles.roll());
        event.setPitch(event.getPitch() + angles.pitch());
        event.setYaw(event.getYaw() + angles.yaw());
    }

    /** Continuous client time in seconds, advanced every frame. */
    private static float currentTimeSeconds(double partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        return (float) ((gameTime + partialTick) / 20.0);
    }

    /**
     * The local player's distortion strength, {@code 0} when sober or when no world is
     * loaded.
     */
    public static float currentStrength() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return 0f;
        }
        return minecraft.player.getData(ModAttachments.DRUNK_LEVEL.get()).distortionStrength();
    }
}
