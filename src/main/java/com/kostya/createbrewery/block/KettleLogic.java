package com.kostya.createbrewery.block;

import com.kostya.createbrewery.quality.QualityRules;
import com.kostya.createbrewery.recipe.BrewingRules;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure, Minecraft-free core of the kettle: given a recipe, the heat beneath the
 * kettle, the ingredients inside and the water available, decides when wort is
 * produced and how progress accumulates.
 *
 * <p>The rules live here so they can be unit-tested in a plain JVM; the block entity
 * only bridges game state into these calls.
 */
public final class KettleLogic {

    public static final int HEAT_NONE = BrewingRules.HEAT_NONE;
    public static final int HEAT_HEATED = BrewingRules.HEAT_HEATED;
    public static final int HEAT_SUPERHEATED = BrewingRules.HEAT_SUPERHEATED;
    private static final int OVERHEATED_SCORE = 70;

    private KettleLogic() {
    }

    /**
     * The parts of a brewing recipe this logic needs.
     *
     * @param inputs         required item ids, one per item
     * @param wortFluid      id of the fluid produced
     * @param wortAmount     amount of wort produced, in milli-buckets
     * @param brewTimeTicks  ideal brewing duration
     * @param idealHeatLevel minimum heat level required
     */
    public record RecipeView(List<String> inputs, String wortFluid, int wortAmount,
                             int brewTimeTicks, int idealHeatLevel) {
    }

    /**
     * Outcome of a brewing attempt.
     *
     * @param brewed     whether wort was produced
     * @param wortFluid  id of the produced fluid, or {@code null} when nothing brewed
     * @param wortAmount amount of produced wort, or {@code 0}
     * @param consumed   the item ids that were eaten by the brew
     */
    public record Result(boolean brewed, String wortFluid, int wortAmount, List<String> consumed) {
        static Result nothing() {
            return new Result(false, null, 0, List.of());
        }
    }

    /** Quality of a brew: the recipe's exact heat is perfect, overheating costs stars but never spoils it. */
    public static int brewStars(int heatLevel, int idealHeatLevel) {
        return QualityRules.starsFromScore(heatLevel == idealHeatLevel ? 100 : OVERHEATED_SCORE);
    }

    /** Whether this recipe can run at all with the given heat and water (ignoring progress). */
    public static boolean brewsAtAll(RecipeView recipe, int heatLevel, List<String> itemIds, int waterAmount) {
        return matchesRecipe(recipe, heatLevel, itemIds, waterAmount);
    }

    /**
     * Attempts to finish a brew.
     *
     * @param progressTicks how long the kettle has been brewing with its current contents
     * @return the result; a successful result lists the consumed ingredients
     */
    public static Result tryBrew(RecipeView recipe, int heatLevel, List<String> itemIds,
                                 int waterAmount, int progressTicks) {
        if (!matchesRecipe(recipe, heatLevel, itemIds, waterAmount)) {
            return Result.nothing();
        }
        if (progressTicks < recipe.brewTimeTicks()) {
            return Result.nothing();
        }
        return new Result(true, recipe.wortFluid(), recipe.wortAmount(), List.copyOf(recipe.inputs()));
    }

    /**
     * Advances (or resets) the brewing progress by one tick.
     *
     * <p>Progress accumulates only while the kettle holds the right ingredients, enough
     * water and enough heat. Losing any of those resets progress to zero, so an
     * interrupted brew has to start over rather than resuming half-done.
     */
    public static int advanceProgress(RecipeView recipe, int heatLevel, List<String> itemIds,
                                      int waterAmount, int progressTicks) {
        if (!matchesRecipe(recipe, heatLevel, itemIds, waterAmount)) {
            return 0;
        }
        return progressTicks + 1;
    }

    private static boolean matchesRecipe(RecipeView recipe, int heatLevel, List<String> itemIds, int waterAmount) {
        if (waterAmount < recipe.wortAmount()) {
            return false;
        }
        List<String> given = new ArrayList<>(itemIds);
        return BrewingRules.matches(recipe.inputs(), given, recipe.idealHeatLevel(), heatLevel);
    }
}
