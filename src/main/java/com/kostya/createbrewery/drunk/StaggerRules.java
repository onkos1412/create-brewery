package com.kostya.createbrewery.drunk;

/**
 * Pure stagger arithmetic: how likely a drunk walker is to be shoved, and how hard.
 */
public final class StaggerRules {

    /** At or below this level nobody staggers. */
    public static final int FROM = 25;

    private StaggerRules() {
    }

    /** Chance per second: {@code (level - 25) / 150}, so 0 up to 25 and one half at 100. */
    public static float chance(int level) {
        return Math.max(0, level - FROM) / 150f;
    }

    /** Push strength: {@code 0.1 + level / 400}, 0.35 at 100. */
    public static double strength(int level) {
        return 0.1 + level / 400.0;
    }

    /** Whether a {@code roll} in [0, 1) falls inside the chance. */
    public static boolean shouldStagger(int level, float roll) {
        return roll < chance(level);
    }
}
