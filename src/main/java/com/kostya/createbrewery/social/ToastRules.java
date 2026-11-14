package com.kostya.createbrewery.social;

/**
 * Pure toast arithmetic: who may clink, and what drinking with the toast marker on is worth.
 */
public final class ToastRules {

    /** A toasted drink's own effects last this many times longer. */
    public static final float DURATION_FACTOR = 1.5f;

    private ToastRules() {
    }

    /** The duration of one of the drink's effects, half as long again after a toast. */
    public static int effectTicks(int ticks, boolean toasted) {
        return toasted ? Math.round(ticks * DURATION_FACTOR) : ticks;
    }

    /** The hangover debt the drink adds, halved (rounded down) after a toast. */
    public static int hangoverDebt(int debt, boolean toasted) {
        return toasted ? debt / 2 : debt;
    }

    /** Two players clink only when both hold a drink and neither is a fake player (a deployer and the like). */
    public static boolean canClink(boolean aHoldsDrink, boolean bHoldsDrink, boolean aReal, boolean bReal) {
        return aHoldsDrink && bHoldsDrink && aReal && bReal;
    }
}
