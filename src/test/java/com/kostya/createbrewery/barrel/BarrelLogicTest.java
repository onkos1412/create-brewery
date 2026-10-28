package com.kostya.createbrewery.barrel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrelLogicTest {

    @Test
    void perfectDistillateAndOakProducesAgedWhiskey() {
        BarrelLogic.Result result = BarrelLogic.process(5, 40, "minecraft:oak_planks", 1000,
                AgingRules.REQUIRED_TICKS);

        assertTrue(result.ready());
        assertEquals(5, result.stars());
        assertEquals(40, result.alcohol());
        assertEquals(1000, result.amount());
    }

    @Test
    void barrelWaitsUntilThreeDays() {
        assertFalse(BarrelLogic.process(5, 40, "minecraft:oak_planks", 1000,
                AgingRules.REQUIRED_TICKS - 1).ready());
    }

    @Test
    void wrongWoodStillProducesLowerQuality() {
        BarrelLogic.Result result = BarrelLogic.process(5, 40, "minecraft:spruce_planks", 1000,
                AgingRules.REQUIRED_TICKS);

        assertTrue(result.ready());
        assertTrue(result.stars() < 5);
    }
}
