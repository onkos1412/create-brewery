package com.kostya.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangoverRulesTest {

    @Test
    void goodVodkaBarelyCounts() {
        assertEquals(1, HangoverRules.debtFor(40, 5, false));
    }

    @Test
    void poorVodkaCountsInFull() {
        assertEquals(8, HangoverRules.debtFor(40, 2, false));
    }

    @Test
    void middleQualitiesAreWeighted() {
        assertEquals(3, HangoverRules.debtFor(40, 4, false));
        assertEquals(6, HangoverRules.debtFor(40, 3, false));
    }

    @Test
    void muddyAndSpoiledCountInFullWhateverTheStars() {
        assertEquals(0, HangoverRules.debtFor(2, 5, true));
        assertEquals(8, HangoverRules.debtFor(40, 5, true));
    }

    @Test
    void thresholdIsEight() {
        assertFalse(HangoverRules.hungOver(7));
        assertTrue(HangoverRules.hungOver(8));
    }

    @Test
    void durationIsFifteenSecondsPerPointCappedAtFiveMinutes() {
        assertEquals(8 * 300, HangoverRules.durationTicks(8));
        assertEquals(6000, HangoverRules.durationTicks(24));
        assertEquals(6000, HangoverRules.durationTicks(1_000_000));
    }
}
