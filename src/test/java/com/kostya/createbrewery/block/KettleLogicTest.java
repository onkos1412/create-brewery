package com.kostya.createbrewery.block;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link KettleLogic}, the Minecraft-free core of the kettle: whether a
 * recipe can brew and whether it has brewed long enough.
 */
class KettleLogicTest {

    private static final KettleLogic.RecipeView BEER =
            new KettleLogic.RecipeView(List.of("minecraft:wheat", "minecraft:wheat"),
                    "brewery:wort", 1000, 200, 1);

    @Test
    void twoWheatFromOneStackMatchesTheRecipe() {
        assertTrue(KettleLogic.brewsAtAll(BEER, KettleLogic.HEAT_HEATED,
                List.of("minecraft:wheat", "minecraft:wheat"), 1000));
    }

    @Test
    void noHeatDoesNotBrew() {
        // 400 ticks of progress is well past 200, but with no heat nothing happens.
        assertFalse(KettleLogic.tryBrew(BEER, KettleLogic.HEAT_NONE, List.of("minecraft:wheat", "minecraft:wheat"),
                1000, 400).brewed());
        assertFalse(KettleLogic.brewsAtAll(BEER, KettleLogic.HEAT_NONE,
                List.of("minecraft:wheat", "minecraft:wheat"), 1000));
    }

    @Test
    void wrongIngredientDoesNotBrew() {
        KettleLogic.Result result = KettleLogic.tryBrew(BEER, KettleLogic.HEAT_HEATED,
                List.of("minecraft:carrot", "minecraft:carrot"), 1000, 400);

        assertFalse(result.brewed());
        assertTrue(result.consumed().isEmpty(), "wrong ingredients must not be eaten");
    }

    @Test
    void notEnoughWaterDoesNotBrew() {
        KettleLogic.Result result = KettleLogic.tryBrew(BEER, KettleLogic.HEAT_HEATED,
                List.of("minecraft:wheat", "minecraft:wheat"), 500, 400);

        assertFalse(result.brewed());
        assertTrue(result.consumed().isEmpty());
    }

    @Test
    void idealHeatBrewsWort() {
        KettleLogic.Result result = KettleLogic.tryBrew(BEER, KettleLogic.HEAT_HEATED,
                List.of("minecraft:wheat", "minecraft:wheat"), 1000, 200);

        assertTrue(result.brewed());
        assertEquals("brewery:wort", result.wortFluid());
        assertEquals(1000, result.wortAmount());
        assertEquals(List.of("minecraft:wheat", "minecraft:wheat"), result.consumed());
    }

    @Test
    void brewConsumesOneBatchAndLeavesTheSurplus() {
        KettleLogic.Result result = KettleLogic.tryBrew(BEER, KettleLogic.HEAT_HEATED,
                List.of("minecraft:wheat", "minecraft:wheat", "minecraft:wheat", "minecraft:wheat"), 1000, 200);

        assertTrue(result.brewed());
        assertEquals(List.of("minecraft:wheat", "minecraft:wheat"), result.consumed());
    }

    @Test
    void tooEarlyDoesNotBrew() {
        KettleLogic.Result result = KettleLogic.tryBrew(BEER, KettleLogic.HEAT_HEATED,
                List.of("minecraft:wheat", "minecraft:wheat"), 1000, 199);

        assertFalse(result.brewed());
        assertTrue(result.consumed().isEmpty());
    }

    @Test
    void progressOnlyAdvancesWithEnoughHeat() {
        assertEquals(1, KettleLogic.advanceProgress(BEER, KettleLogic.HEAT_HEATED,
                List.of("minecraft:wheat", "minecraft:wheat"), 1000, 0));
        assertEquals(0, KettleLogic.advanceProgress(BEER, KettleLogic.HEAT_NONE,
                List.of("minecraft:wheat", "minecraft:wheat"), 1000, 0));
        assertEquals(0, KettleLogic.advanceProgress(BEER, KettleLogic.HEAT_NONE,
                List.of("minecraft:wheat", "minecraft:wheat"), 1000, 57), "progress resets when heat is lost");
    }

    @Test
    void superheatedStillBrews() {
        // Overheating does not spoil the brew in the skeleton; spoiling comes later.
        assertTrue(KettleLogic.tryBrew(BEER, KettleLogic.HEAT_SUPERHEATED,
                List.of("minecraft:wheat", "minecraft:wheat"), 1000, 200).brewed());
    }
}
