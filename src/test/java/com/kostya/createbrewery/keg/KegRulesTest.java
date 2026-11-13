package com.kostya.createbrewery.keg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KegRulesTest {

    @Test
    void emptyKegTakesAnyDrink() {
        assertTrue(KegRules.accepts(0, false, 0));
    }

    @Test
    void fillingKegTakesOnlyTheSameDrink() {
        assertTrue(KegRules.accepts(3, true, 0));
        assertFalse(KegRules.accepts(3, false, 0));
    }

    @Test
    void fullKegTakesNothing() {
        assertFalse(KegRules.accepts(KegRules.CAPACITY, true, 0));
    }

    @Test
    void fullEmptiesSlotRefusesBottles() {
        assertFalse(KegRules.accepts(3, true, KegRules.EMPTIES));
    }

    @Test
    void windowShowsPortionsInFourSteps() {
        assertEquals(0, KegRules.windowLevel(0));
        assertEquals(1, KegRules.windowLevel(1));
        assertEquals(1, KegRules.windowLevel(2));
        assertEquals(2, KegRules.windowLevel(3));
        assertEquals(4, KegRules.windowLevel(8));
    }
}
