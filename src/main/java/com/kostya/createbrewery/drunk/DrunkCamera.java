package com.kostya.createbrewery.drunk;

/**
 * The pure, Minecraft-free camera sway maths used by the drunk effect.
 *
 * <p>Angles are sinusoidal functions of continuous time in seconds, so the camera
 * drifts smoothly instead of restarting every tick. Kept free of game types so the
 * motion can be unit-tested.
 */
public final class DrunkCamera {

    /** Maximum camera roll, in degrees, at full drunk strength. */
    public static final float MAX_ROLL_DEGREES = 7.0f;
    /** Maximum vertical camera drift, in degrees, at full drunk strength. */
    public static final float MAX_PITCH_DEGREES = 3.5f;
    /** Maximum horizontal camera drift, in degrees, at full drunk strength. */
    public static final float MAX_YAW_DEGREES = 4.5f;

    /** Angular speed of the roll sway, in radians per second. */
    public static final float ROLL_ANGULAR_SPEED = 1.1f;
    private static final float PITCH_ANGULAR_SPEED = 1.7f;
    private static final float YAW_ANGULAR_SPEED = 0.8f;

    private DrunkCamera() {
    }

    /** One frame's camera offsets, in degrees. */
    public record Angles(float roll, float pitch, float yaw) {
    }

    /**
     * Camera offsets for a continuous {@code seconds} timeline and a drunk
     * {@code strength} clamped into {@code [0, 1]}. A sober player gets no offset.
     */
    public static Angles angles(float seconds, float strength) {
        float s = Math.max(0f, Math.min(1f, strength));
        if (s <= 0f) {
            return new Angles(0f, 0f, 0f);
        }

        double t = seconds;
        float roll = (float) (Math.sin(t * ROLL_ANGULAR_SPEED) * MAX_ROLL_DEGREES * s);
        float pitch = (float) (Math.sin(t * PITCH_ANGULAR_SPEED + 1.7) * MAX_PITCH_DEGREES * s);
        float yaw = (float) (Math.sin(t * YAW_ANGULAR_SPEED + 0.6) * MAX_YAW_DEGREES * s);
        return new Angles(roll, pitch, yaw);
    }
}
