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
