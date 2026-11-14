package com.kostya.createbrewery.drunk;

import io.github.fabricators_of_create.porting_lib.event.client.CameraSetupCallback;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/**
 * Client-side drunk visuals: the camera rolls and drifts while the player is drunk,
 * with an intensity proportional to the synced drunk level.
 *
 * <p>The sway is driven by the level's continuous game time in seconds, not by the
 * per-tick partial tick, so it moves smoothly instead of jittering in place.
 *
 * <p>Client only: registered from the client entrypoint, and nothing in common code names this class. Forge's
 * {@code ViewportEvent.ComputeCameraAngles} is Porting Lib's {@link CameraSetupCallback}.
 */
@Environment(EnvType.CLIENT)
public final class DrunkScreenEffect {

    private DrunkScreenEffect() {
    }

    public static void register() {
        CameraSetupCallback.EVENT.register(DrunkScreenEffect::onComputeCameraAngles);
    }

    private static boolean onComputeCameraAngles(CameraSetupCallback.CameraInfo info) {
        float strength = currentStrength();
        if (strength <= 0f) {
            return false;
        }

        float seconds = currentTimeSeconds(info.partialTicks);
        DrunkCamera.Angles angles = DrunkCamera.angles(seconds, strength);

        info.roll += angles.roll();
        info.pitch += angles.pitch();
        info.yaw += angles.yaw();
        // Other listeners still get their turn.
        return false;
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
        return DrunkData.of(minecraft.player).level().distortionStrength();
    }
}
