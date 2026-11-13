package com.kostya.createbrewery.keg;

/** What a keg holds and takes, and how full its little side window looks. */
public final class KegRules {
    /** Portions of one drink a keg holds. */
    public static final int CAPACITY = 8;
    /** Empty glass bottles a keg keeps from automated filling before it refuses more. */
    public static final int EMPTIES = 8;
    /** Steps of liquid the side window shows. */
    public static final int WINDOW_STEPS = 4;

    private KegRules() {
    }

    /**
     * Whether a keg takes one more bottle: it has room, holds nothing or the same drink, and has somewhere to put
     * the bottle that came in. Filling by hand hands the bottle back, so it passes {@code empties = 0}.
     */
    public static boolean accepts(int portions, boolean sameDrink, int empties) {
        return portions < CAPACITY && (portions == 0 || sameDrink) && empties < EMPTIES;
    }

    /** The window's level: nothing when empty, otherwise one of four steps rounded up. */
    public static int windowLevel(int portions) {
        if (portions <= 0) {
            return 0;
        }
        return Math.min(WINDOW_STEPS, (portions * WINDOW_STEPS + CAPACITY - 1) / CAPACITY);
    }
}
