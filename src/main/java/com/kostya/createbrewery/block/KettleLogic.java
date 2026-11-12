package com.kostya.createbrewery.block;

import com.kostya.createbrewery.quality.QualityRules;
import com.kostya.createbrewery.recipe.BrewingRules;

/**
 * The pure, Minecraft-free core of the kettle: how the heat beneath it and the closeness
 * of the mix to its recipe decide the quality of the wort.
 *
 * <p>The rules live here so they can be unit-tested in a plain JVM; the block entity
 * only bridges game state into these calls. Which recipe a mix brews is
 * {@link com.kostya.createbrewery.recipe.MixRules}' job.
 */
public final class KettleLogic {

    public static final int HEAT_NONE = BrewingRules.HEAT_NONE;
    public static final int HEAT_HEATED = BrewingRules.HEAT_HEATED;
    public static final int HEAT_SUPERHEATED = BrewingRules.HEAT_SUPERHEATED;
    private static final int OVERHEATED_SCORE = 70;

    /** Why a kettle with a mix and a fire is not brewing, for the GUI. */
    public static final int BLOCKED_NONE = 0;
    /** The tank holds wort the new brew cannot join: another drink, or no room left. */
    public static final int BLOCKED_BY_WORT = 1;
    public static final int BLOCKED_BY_WATER = 2;

    private KettleLogic() {
    }

    /** Quality of a brew: the recipe's exact heat is perfect, overheating costs stars but never spoils it. */
    public static int brewStars(int heatLevel, int idealHeatLevel) {
        return QualityRules.starsFromScore(heatLevel == idealHeatLevel ? 100 : OVERHEATED_SCORE);
    }

    /** The wort's stars: an inexact mix and a bad fire each cap them, and the weaker one wins. */
    public static int mixStars(int score, int heatStars) {
        return Math.min(QualityRules.starsFromScore(score), heatStars);
    }

    /**
     * Whether the brew starts over because a different recipe (or muddy brew) now wins.
     * {@code lastWinner} is {@code null} when it is unknown, as after a reload, and that
     * keeps the progress.
     */
    public static boolean brewRestarts(String lastWinner, String winner) {
        return lastWinner != null && !lastWinner.equals(winner);
    }

    /** What a brew that cannot go on waits for; the wort comes first, since water alone would not help. */
    public static int blockReason(boolean wortInTheWay, boolean enoughWater) {
        if (wortInTheWay) {
            return BLOCKED_BY_WORT;
        }
        return enoughWater ? BLOCKED_NONE : BLOCKED_BY_WATER;
    }
}
