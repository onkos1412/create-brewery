package com.kostya.createbrewery.distillery;

/** Pure, Minecraft-free rules for the first whiskey distillation profile. */
public final class DistillingRules {

    public static final int MIN_HEAT = 1;
    public static final float MIN_RPM = 1f;

    private DistillingRules() {
    }

    public static boolean canDistill(int heat, int idealHeat, float rpm) {
        return heat >= idealHeat && rpm >= MIN_RPM;
    }

    /**
     * Scores heat, elapsed time and speed against the recipe's ideal values.
     * Exact values score 100; each normalized deviation costs up to 50 points.
     */
    public static int score(int heat, int idealHeat, int elapsedTicks, int idealTicks,
                            float rpm, float idealRpm) {
        if (!canDistill(heat, idealHeat, rpm) || idealTicks <= 0 || idealRpm <= 0f) {
            return 0;
        }
        float heatPenalty = heat == idealHeat ? 0f : Math.min(1f, (heat - idealHeat) / 2f);
        float timePenalty = Math.min(1f, Math.abs(elapsedTicks - idealTicks) / (float) idealTicks);
        float rpmPenalty = Math.min(1f, Math.abs(rpm - idealRpm) / idealRpm);
        float penalty = (heatPenalty + timePenalty + rpmPenalty) / 3f;
        return Math.max(0, Math.min(100, Math.round(100f * (1f - penalty))));
    }
}
