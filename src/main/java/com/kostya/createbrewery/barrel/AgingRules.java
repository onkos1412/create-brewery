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

    /** Any log or planks can age a drink; {@link #score} rewards only oak. */
    public static boolean isAcceptedWood(String woodId) {
        return woodId.endsWith("_planks") || woodId.endsWith("_log");
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

    /** Whether {@code woodId} is the planks or log of {@code wood}, such as {@code dark_oak}. */
    public static boolean isPreferredWood(String woodId, String wood) {
        return woodId.endsWith(":" + wood + "_planks") || woodId.endsWith(":" + wood + "_log");
    }

    /** The barrel's score once a drink's own days have passed: the right wood is perfect. */
    public static int score(String woodId, String preferredWood, long elapsedTicks, int days) {
        if (elapsedTicks < days * TICKS_PER_DAY) {
            return 0;
        }
        return isPreferredWood(woodId, preferredWood) ? 100 : 60;
    }
}
