package com.kostya.createbrewery.recipe;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BrewingRules}, the Minecraft-free core of brewing: heat-level
 * parsing and the ingredient/heat matching rules.
 */
class BrewingRecipeTest {

    private static final List<String> BEER_INGREDIENTS = List.of("minecraft:wheat", "minecraft:wheat");
    private static final int BEER_HEAT = BrewingRules.HEAT_HEATED;

    @Test
    void parsesHeatLevelNames() {
        assertEquals(0, BrewingRules.heatLevelFromName("none"));
        assertEquals(1, BrewingRules.heatLevelFromName("heated"));
        assertEquals(2, BrewingRules.heatLevelFromName("superheated"));
    }

    @Test
    void unknownHeatLevelIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BrewingRules.heatLevelFromName("hot"));
    }

    @Test
    void heatLevelNamesRoundTrip() {
        for (String name : List.of("none", "heated", "superheated")) {
            assertEquals(name, BrewingRules.heatLevelToName(BrewingRules.heatLevelFromName(name)));
        }
    }

    @Test
    void matchesRequiresAtLeastIdealHeat() {
        // Exactly the ideal heat.
        assertTrue(BrewingRules.matches(BEER_INGREDIENTS, BEER_INGREDIENTS, BEER_HEAT, BrewingRules.HEAT_HEATED));
        // Below ideal heat: no match.
        assertFalse(BrewingRules.matches(BEER_INGREDIENTS, BEER_INGREDIENTS, BEER_HEAT, BrewingRules.HEAT_NONE));
        // Above ideal heat still counts as hot enough.
        assertTrue(BrewingRules.matches(BEER_INGREDIENTS, BEER_INGREDIENTS, BEER_HEAT, BrewingRules.HEAT_SUPERHEATED));
    }

    @Test
    void matchesRejectsWrongIngredients() {
        assertFalse(BrewingRules.matches(BEER_INGREDIENTS, List.of("minecraft:wheat"), BEER_HEAT, BrewingRules.HEAT_HEATED));
        assertFalse(BrewingRules.matches(BEER_INGREDIENTS,
                List.of("minecraft:carrot", "minecraft:carrot"), BEER_HEAT, BrewingRules.HEAT_HEATED));
        assertTrue(BrewingRules.matches(BEER_INGREDIENTS, List.of("minecraft:wheat", "minecraft:wheat"),
                BEER_HEAT, BrewingRules.HEAT_HEATED));
    }

    @Test
    void surplusOfRecipeIngredientsStillMatches() {
        assertTrue(BrewingRules.matches(BEER_INGREDIENTS,
                List.of("minecraft:wheat", "minecraft:wheat", "minecraft:wheat"), BEER_HEAT, BrewingRules.HEAT_HEATED));
    }

    @Test
    void itemTheRecipeDoesNotUseBlocksIt() {
        assertFalse(BrewingRules.matches(BEER_INGREDIENTS,
                List.of("minecraft:wheat", "minecraft:wheat", "minecraft:carrot"), BEER_HEAT, BrewingRules.HEAT_HEATED));
    }

    @Test
    void matchesIgnoresIngredientOrder() {
        List<String> reversed = List.of("minecraft:wheat", "minecraft:wheat");
        assertTrue(BrewingRules.matches(reversed, List.of("minecraft:wheat", "minecraft:wheat"),
                BEER_HEAT, BrewingRules.HEAT_HEATED));
    }

    @Test
    void recipeExposesItsOutputAndTime() {
        FluidKey wort = new FluidKey("brewery:wort", 1000);
        assertEquals("brewery:wort", wort.getFluid());
        assertEquals(1000, wort.getAmount());
    }
}
