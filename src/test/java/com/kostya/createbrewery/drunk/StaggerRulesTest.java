package com.kostya.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaggerRulesTest {

    @Test
    void noChanceUpToTwentyFive() {
        assertEquals(0f, StaggerRules.chance(0));
        assertEquals(0f, StaggerRules.chance(25));
    }

    @Test
    void chanceGrowsToHalfAtTheTop() {
        assertEquals(0.5f, StaggerRules.chance(100), 1e-6f);
        assertEquals(0.25f, StaggerRules.chance(62), 0.01f);
    }

    @Test
    void strengthIsOneTenthPlusAQuarterHundredth() {
        assertEquals(0.35, StaggerRules.strength(100), 1e-9);
        assertEquals(0.1, StaggerRules.strength(0), 1e-9);
    }

    @Test
    void rollBelowTheChancePushes() {
        assertTrue(StaggerRules.shouldStagger(100, 0.0f));
        assertTrue(StaggerRules.shouldStagger(100, 0.49f));
        assertFalse(StaggerRules.shouldStagger(100, 0.5f));
        assertFalse(StaggerRules.shouldStagger(25, 0.0f));
    }
}
