package com.kostya.createbrewery.drunk;

/**
 * Pure vomiting arithmetic: when a heavy drinker throws up and what it costs.
 */
public final class VomitRules {

    /** Below this level nobody throws up. */
    public static final int FROM_LEVEL = 70;
    /** Thirty seconds between two bouts. */
    public static final int COOLDOWN_TICKS = 600;
    /** Throwing up lowers the drunk level by this much. */
    public static final int SOBERS_BY = 15;
    /** ... and the food level by this much (two drumsticks). */
    public static final int FOOD_LOST = 4;

    private VomitRules() {
    }

    /** Chance per second: {@code 0.01 + debt * 0.002}, at most 0.05 (reached at a debt of 20). */
    public static float chance(int debt) {
        return Math.min(0.01f + debt * 0.002f, 0.05f);
    }

    /** Whether a {@code roll} in [0, 1) makes a player at {@code level} throw up, with no cooldown running. */
    public static boolean shouldVomit(int level, int debt, int cooldown, float roll) {
        return level >= FROM_LEVEL && cooldown <= 0 && roll < chance(debt);
    }
}
