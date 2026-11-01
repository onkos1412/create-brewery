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
     * Exact values score 100, and the single worst deviation decides the rest: precision is
     * the point of the machine, so a badly wrong setting must not hide behind two right ones.
     */
    public static int score(int heat, int idealHeat, int elapsedTicks, int idealTicks,
                            float rpm, float idealRpm) {
        if (!canDistill(heat, idealHeat, rpm) || idealTicks <= 0 || idealRpm <= 0f) {
            return 0;
        }
        float heatPenalty = heat == idealHeat ? 0f : Math.min(1f, (heat - idealHeat) / 2f);
        float timePenalty = Math.min(1f, Math.abs(elapsedTicks - idealTicks) / (float) idealTicks);
        float rpmPenalty = Math.min(1f, Math.abs(rpm - idealRpm) / idealRpm);
        float penalty = Math.max(heatPenalty, Math.max(timePenalty, rpmPenalty));
        return Math.max(0, Math.min(100, Math.round(100f * (1f - penalty))));
    }
}
