package com.kostya.createbrewery.drunk;

/**
 * Pure hangover arithmetic: how much a drink adds to the debt and how long the hangover lasts.
 */
public final class HangoverRules {

    /** A debt below this leaves no hangover. */
    public static final int THRESHOLD = 8;

    /** Each point of debt costs fifteen seconds. */
    public static final int TICKS_PER_DEBT = 15 * 20;

    /** A hangover never lasts longer than five minutes. */
    public static final int MAX_TICKS = 300 * 20;

    private HangoverRules() {
    }

    /** What one drink adds: {@code alcohol / 5} weighted by quality, so good drinks barely count. */
    public static int debtFor(int alcohol, int stars, boolean spoiledOrMuddy) {
        return (int) Math.round(alcohol / 5.0 * weight(stars, spoiledOrMuddy));
    }

    private static double weight(int stars, boolean spoiledOrMuddy) {
        if (spoiledOrMuddy) {
            return 1.0;
        }
        return switch (stars) {
            case 5 -> 0.1;
            case 4 -> 0.4;
            case 3 -> 0.7;
            default -> 1.0;
        };
    }

    public static boolean hungOver(int debt) {
        return debt >= THRESHOLD;
    }

    public static int durationTicks(int debt) {
        return (int) Math.min((long) Math.max(debt, 0) * TICKS_PER_DEBT, MAX_TICKS);
    }
}
