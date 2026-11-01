package com.kostya.createbrewery.barrel;

import com.kostya.createbrewery.quality.QualityRules;

/** Pure state transition for a barrel holding one whiskey distillate batch. */
public final class BarrelLogic {
    private BarrelLogic() {
    }

    public record Result(boolean ready, int amount, int stars, int alcohol) {
        static Result waiting() {
            return new Result(false, 0, 1, 0);
        }
    }

    public static Result process(int distillateStars, int alcohol, String woodId,
                                 int amount, long elapsedTicks) {
        return process(distillateStars, alcohol, woodId, "oak", amount, elapsedTicks, (int) (AgingRules.REQUIRED_TICKS / AgingRules.TICKS_PER_DAY));
    }

    public static Result process(int distillateStars, int alcohol, String woodId, String preferredWood,
                                 int amount, long elapsedTicks, int days) {
        int score = AgingRules.score(woodId, preferredWood, elapsedTicks, days);
        if (score == 0 || amount <= 0) {
            return Result.waiting();
        }
        int stars = Math.min(distillateStars, QualityRules.starsFromScore(score));
        return new Result(true, amount, stars, alcohol);
    }
}
