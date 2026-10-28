package com.kostya.createbrewery.barrel;

/** Pure rules for the first three-day whiskey aging profile. */
public final class AgingRules {
    public static final long TICKS_PER_DAY = 24_000L;
    public static final long REQUIRED_TICKS = TICKS_PER_DAY * 3;

    private AgingRules() {
    }

    public static boolean isValidWood(String woodId) {
        return "minecraft:oak_planks".equals(woodId) || "minecraft:oak_log".equals(woodId);
    }

    public static boolean isComplete(long elapsedTicks) {
        return elapsedTicks >= REQUIRED_TICKS;
    }

    public static int score(String woodId, long elapsedTicks) {
        if (!isComplete(elapsedTicks)) {
            return 0;
        }
        return isValidWood(woodId) ? 100 : 60;
    }
}
