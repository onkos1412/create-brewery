package com.kostya.createbrewery.drunk;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client-side drunk visuals: the camera rolls and drifts while the player is drunk,
 * with an intensity proportional to the synced drunk level.
 */
@EventBusSubscriber(modid = CreateBrewery.MOD_ID, value = Dist.CLIENT)
public final class DrunkScreenEffect {

    /** Maximum camera roll, in degrees, at full drunk level. */
    public static final float MAX_ROLL_DEGREES = 6.0f;
    /** Maximum vertical camera drift, in degrees, at full drunk level. */
    public static final float MAX_PITCH_DRIFT_DEGREES = 3.0f;

    private DrunkScreenEffect() {
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float strength = currentStrength();
        if (strength <= 0f) {
            return;
        }

        float time = (float) event.getPartialTick();
        float roll = Mth.sin(time * 0.5f) * MAX_ROLL_DEGREES * strength;
        float pitchDrift = Mth.sin(time * 0.9f + 1.7f) * MAX_PITCH_DRIFT_DEGREES * strength;

        event.setRoll(event.getRoll() + roll);
        event.setPitch(event.getPitch() + pitchDrift);
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
