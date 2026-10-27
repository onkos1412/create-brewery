package com.kostya.createbrewery.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SpoutLogic}, the Minecraft-free rule deciding when the spout
 * can fill a bottle.
 */
class SpoutLogicTest {

    @Test
    void noWortNoBottle() {
        assertFalse(SpoutLogic.fillBottle(false, true));
    }

    @Test
    void wortButNoBottle() {
        assertFalse(SpoutLogic.fillBottle(true, false));
    }

    @Test
    void emptyNoBottle() {
        assertFalse(SpoutLogic.fillBottle(false, false));
    }

    @Test
    void wortAndBottleFills() {
        assertTrue(SpoutLogic.fillBottle(true, true));
    }
}
