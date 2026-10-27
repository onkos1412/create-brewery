package com.kostya.createbrewery.drunk;

/**
 * The pure, Minecraft-free drunk counter.
 *
 * <p>Levels run from {@code 0} (sober) to {@link #MAX}. Drinking raises the level;
 * {@link #tick()} lowers it while the player sobers up. Kept free of game types so the
 * rules can be unit-tested.
 */
public final class DrunkLevel {

    /** The highest drunk level a player can reach. */
    public static final int MAX = 100;
    /** How much the level drops per sober tick. */
    public static final int SOBER_RATE = 1;

    private int level;

    public DrunkLevel() {
        this(0);
    }

    /** Creates a level already at {@code initial}, clamped into range. */
    public DrunkLevel(int initial) {
        this.level = clamp(initial);
    }

    public int get() {
        return level;
    }

    public void set(int value) {
        level = clamp(value);
    }

    /** Raises the level. Negative amounts are ignored: drink never sobers you up. */
    public void add(int amount) {
        if (amount > 0) {
            level = clamp(level + amount);
        }
    }

    /** Sobering: lowers the level by {@link #SOBER_RATE}, never below zero. */
    public void tick() {
        level = Math.max(0, level - SOBER_RATE);
    }

    public boolean isDrunk() {
        return level > 0;
    }

    /** How strongly to distort the screen, from {@code 0} (sober) to {@code 1} (max). */
    public float distortionStrength() {
        return (float) level / MAX;
    }

    /** Back to sober, e.g. on death or respawn. */
    public void reset() {
        level = 0;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX, value));
    }
}
