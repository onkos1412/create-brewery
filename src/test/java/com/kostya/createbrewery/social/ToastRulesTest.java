package com.kostya.createbrewery.social;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToastRulesTest {

    @Test
    void toastedEffectsLastHalfAsLongAgain() {
        assertEquals(300, ToastRules.effectTicks(200, true));
    }

    @Test
    void untoastedEffectsAreUnchanged() {
        assertEquals(200, ToastRules.effectTicks(200, false));
    }

    @Test
    void toastHalvesTheDebtRoundingDown() {
        assertEquals(3, ToastRules.hangoverDebt(7, true));
    }

    @Test
    void untoastedDebtIsUnchanged() {
        assertEquals(7, ToastRules.hangoverDebt(7, false));
    }

    @Test
    void clinkNeedsTwoRealDrinkers() {
        assertTrue(ToastRules.canClink(true, true, true, true));
        assertFalse(ToastRules.canClink(false, true, true, true));
        assertFalse(ToastRules.canClink(true, false, true, true));
        assertFalse(ToastRules.canClink(true, true, false, true));
        assertFalse(ToastRules.canClink(true, true, true, false));
    }
}
