package com.kostya.createbrewery.barrel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgingRulesTest {

    @Test
    void acceptsOakMaterialsOnly() {
        assertTrue(AgingRules.isValidWood("minecraft:oak_planks"));
        assertTrue(AgingRules.isValidWood("minecraft:oak_log"));
        assertFalse(AgingRules.isValidWood("minecraft:spruce_planks"));
        assertFalse(AgingRules.isValidWood("minecraft:stone"));
    }

    @Test
    void eachDrinkPrefersItsOwnWoodAndDays() {
        long fiveDays = 5 * AgingRules.TICKS_PER_DAY;
        assertEquals(100, AgingRules.score("minecraft:dark_oak_log", "dark_oak", fiveDays, 5));
        assertEquals(60, AgingRules.score("minecraft:oak_log", "dark_oak", fiveDays, 5));
        assertEquals(0, AgingRules.score("minecraft:dark_oak_log", "dark_oak", fiveDays - 1, 5));
    }

    @Test
    void aDrinkLeftInTheBarrelLosesAStarPerPeriod() {
        long day = AgingRules.TICKS_PER_DAY;
        assertEquals(5, AgingRules.overAgedStars(5, 0, 3));
        assertEquals(5, AgingRules.overAgedStars(5, 3 * day - 1, 3));
        assertEquals(4, AgingRules.overAgedStars(5, 3 * day, 3));
        assertEquals(1, AgingRules.overAgedStars(5, 100 * day, 3));
        assertEquals(1, AgingRules.overAgedStars(1, 3 * day, 3));
    }

    @Test
    void acceptsAnyWoodButNothingElse() {
        assertTrue(AgingRules.isAcceptedWood("minecraft:oak_log"));
        assertTrue(AgingRules.isAcceptedWood("minecraft:spruce_planks"));
        assertFalse(AgingRules.isAcceptedWood("minecraft:stone"));
    }

    @Test
    void requiresExactlyThreeGameDays() {
        assertFalse(AgingRules.isComplete(0));
        assertFalse(AgingRules.isComplete(AgingRules.REQUIRED_TICKS - 1));
        assertTrue(AgingRules.isComplete(AgingRules.REQUIRED_TICKS));
    }

    @Test
    void OakAtRequiredTimeIsPerfectAndWrongWoodIsLower() {
        assertEquals(100, AgingRules.score("minecraft:oak_planks", AgingRules.REQUIRED_TICKS));
        assertTrue(AgingRules.score("minecraft:spruce_planks", AgingRules.REQUIRED_TICKS) < 100);
    }
}
