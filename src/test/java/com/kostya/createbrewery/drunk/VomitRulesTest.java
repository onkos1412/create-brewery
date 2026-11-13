package com.kostya.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VomitRulesTest {

    @Test
    void chanceIsOnePercentWithoutDebt() {
        assertEquals(0.01f, VomitRules.chance(0), 1e-6f);
    }

    @Test
    void debtRaisesTheChanceUpToFivePercent() {
        assertEquals(0.03f, VomitRules.chance(10), 1e-6f);
        assertEquals(0.05f, VomitRules.chance(20), 1e-6f);
        assertEquals(0.05f, VomitRules.chance(500), 1e-6f);
    }

    @Test
    void soberEnoughNeverVomits() {
        assertFalse(VomitRules.shouldVomit(69, 20, 0, 0.0f));
        assertTrue(VomitRules.shouldVomit(70, 20, 0, 0.0f));
    }

    @Test
    void aRunningCooldownBlocksIt() {
        assertFalse(VomitRules.shouldVomit(90, 20, 1, 0.0f));
        assertTrue(VomitRules.shouldVomit(90, 20, 0, 0.0f));
    }

    @Test
    void rollBelowTheChanceVomits() {
        assertTrue(VomitRules.shouldVomit(90, 0, 0, 0.009f));
        assertFalse(VomitRules.shouldVomit(90, 0, 0, 0.01f));
    }
}
