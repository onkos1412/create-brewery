package com.kostya.createbrewery.quality;

import java.util.List;

/** Pure quality conversion shared by every production stage. */
public final class QualityRules {

    private QualityRules() {
    }

    public static int starsFromScore(int score) {
        int clamped = Math.max(0, Math.min(100, score));
        if (clamped >= 90) {
            return 5;
        }
        if (clamped >= 75) {
            return 4;
        }
        if (clamped >= 55) {
            return 3;
        }
        if (clamped >= 30) {
            return 2;
        }
        return 1;
    }

    /** The weakest required stage determines the final quality. */
    public static int finalStars(List<Integer> scores) {
        if (scores.isEmpty()) {
            return 1;
        }
        int weakest = scores.stream().mapToInt(Integer::intValue).min().orElse(0);
        return starsFromScore(weakest);
    }
}
