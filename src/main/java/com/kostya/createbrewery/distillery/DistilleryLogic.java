package com.kostya.createbrewery.distillery;

import com.kostya.createbrewery.quality.QualityRules;

/** Pure state transition for the first whiskey distillation profile. */
public final class DistilleryLogic {

    public static final int WORT_REQUIRED = 1000;
    public static final int DISTILLATE_AMOUNT = 1000;
    public static final int IDEAL_HEAT = 1;
    public static final int IDEAL_TICKS = 200;
    public static final float IDEAL_RPM = 32f;
    public static final int ALCOHOL = 40;

    private DistilleryLogic() {
    }

    public record Result(boolean distilled, int amount, int stars, int alcohol) {
        static Result nothing() {
            return new Result(false, 0, 1, 0);
        }
    }

    public static Result process(int wortAmount, int heat, float rpm, int elapsedTicks, int inputStars) {
        return process(wortAmount, heat, rpm, elapsedTicks, inputStars, IDEAL_TICKS, IDEAL_RPM);
    }

    public static Result process(int wortAmount, int heat, float rpm, int elapsedTicks, int inputStars,
                                 int idealTicks, float idealRpm) {
        if (wortAmount < WORT_REQUIRED
                || !DistillingRules.canDistill(heat, IDEAL_HEAT, rpm)
                || elapsedTicks < idealTicks) {
            return Result.nothing();
        }

        int score = DistillingRules.score(heat, IDEAL_HEAT, elapsedTicks, idealTicks, rpm, idealRpm);
        int stars = Math.min(inputStars, QualityRules.starsFromScore(score));
        return new Result(true, DISTILLATE_AMOUNT, stars, ALCOHOL);
    }
}
