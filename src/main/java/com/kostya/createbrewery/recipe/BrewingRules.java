package com.kostya.createbrewery.recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure, Minecraft-free rules of brewing: how a heat name maps to a level and
 * whether a set of items plus a heat level satisfies a recipe. Kept out of
 * {@link BrewingRecipe} (which is bound to the game's {@code Recipe} interface) so the
 * rules can be unit-tested in a plain JVM.
 */
public final class BrewingRules {

    public static final int HEAT_NONE = 0;
    public static final int HEAT_HEATED = 1;
    public static final int HEAT_SUPERHEATED = 2;

    private BrewingRules() {
    }

    /**
     * Maps a datapack heat-level name onto its numeric level.
     *
     * @throws IllegalArgumentException for an unknown name
     */
    public static int heatLevelFromName(String name) {
        return switch (name) {
            case "none" -> HEAT_NONE;
            case "heated" -> HEAT_HEATED;
            case "superheated" -> HEAT_SUPERHEATED;
            default -> throw new IllegalArgumentException("unknown heat level: " + name);
        };
    }

    /** Maps a numeric heat level back onto its datapack name. */
    public static String heatLevelToName(int level) {
        return switch (level) {
            case HEAT_NONE -> "none";
            case HEAT_SUPERHEATED -> "superheated";
            default -> "heated";
        };
    }

    /**
     * Whether the given ingredients and available heat satisfy a recipe with the given
     * required inputs and ideal heat.
     *
     * <p>The heat must be at least {@code idealHeatLevel}: a hotter source still brews,
     * matching Create's basin behaviour where superheated satisfies a heated recipe.
     * The kettle must hold at least one batch of the ingredients; a surplus waits for the
     * next batch, so funnels can keep it stocked. Any item the recipe does not use blocks
     * it, so a simpler recipe never fires on the contents of a richer one.
     */
    public static boolean matches(List<String> requiredInputs, List<String> givenInputs,
                                  int idealHeatLevel, int availableHeat) {
        if (availableHeat < idealHeatLevel) {
            return false;
        }
        if (!requiredInputs.containsAll(givenInputs)) {
            return false;
        }
        List<String> given = new ArrayList<>(givenInputs);
        for (String id : requiredInputs) {
            if (!given.remove(id)) {
                return false;
            }
        }
        return true;
    }
}
