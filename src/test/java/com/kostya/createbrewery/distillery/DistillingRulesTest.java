package com.kostya.createbrewery.distillery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistillingRulesTest {

    @Test
    void idealConditionsCanDistillAndScorePerfectly() {
        assertTrue(DistillingRules.canDistill(1, 1, 32f));
        assertEquals(100, DistillingRules.score(1, 1, 200, 200, 32f, 32f));
    }

    @Test
    void missingHeatOrKineticSpeedCannotStart() {
        assertFalse(DistillingRules.canDistill(0, 1, 32f));
        assertFalse(DistillingRules.canDistill(1, 1, 0f));
    }

    @Test
    void theWorstDeviationDecidesTheScore() {
        assertEquals(94, DistillingRules.score(1, 1, 200, 200, 30f, 32f));
        assertEquals(50, DistillingRules.score(1, 1, 200, 200, 16f, 32f));
        assertEquals(0, DistillingRules.score(1, 1, 200, 200, 256f, 32f));
        assertEquals(50, DistillingRules.score(2, 1, 200, 200, 32f, 32f));
    }

    @Test
    void imperfectConditionsReduceButDoNotBreakTheScore() {
        int score = DistillingRules.score(1, 1, 260, 200, 24f, 32f);

        assertTrue(score >= 0 && score < 100);
    }
}
