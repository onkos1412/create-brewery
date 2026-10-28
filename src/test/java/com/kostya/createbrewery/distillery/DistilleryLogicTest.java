package com.kostya.createbrewery.distillery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistilleryLogicTest {

    @Test
    void idealWortProducesPerfectDistillate() {
        DistilleryLogic.Result result = DistilleryLogic.process(1000, 1, 32f, 200, 5);

        assertTrue(result.distilled());
        assertEquals(1000, result.amount());
        assertEquals(5, result.stars());
        assertEquals(40, result.alcohol());
    }

    @Test
    void incompleteOrUnheatedWortStaysInTheMachine() {
        assertFalse(DistilleryLogic.process(1000, 1, 32f, 199, 5).distilled());
        assertFalse(DistilleryLogic.process(1000, 0, 32f, 200, 5).distilled());
        assertFalse(DistilleryLogic.process(1000, 1, 0f, 200, 5).distilled());
    }

    @Test
    void imperfectDistillationStillProducesLowerQuality() {
        DistilleryLogic.Result result = DistilleryLogic.process(1000, 1, 24f, 260, 5);

        assertTrue(result.distilled());
        assertTrue(result.stars() < 5);
        assertEquals(40, result.alcohol());
    }

    @Test
    void insufficientWortCannotDistill() {
        assertFalse(DistilleryLogic.process(999, 1, 32f, 200, 5).distilled());
    }
}
