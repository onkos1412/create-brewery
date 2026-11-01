package com.kostya.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DrunkLevel}, the Minecraft-free drunk counter.
 */
class DrunkLevelTest {

    @Test
    void zeroDrunkHasNoEffect() {
        DrunkLevel level = new DrunkLevel();

        assertEquals(0, level.get());
        assertFalse(level.isDrunk());
    }

    @Test
    void addDrunkIncrements() {
        DrunkLevel level = new DrunkLevel();

        level.add(30);

        assertEquals(30, level.get());
        assertTrue(level.isDrunk());
    }

    @Test
    void addDrunkClampsToMax() {
        DrunkLevel level = new DrunkLevel();

        level.add(200);

        assertEquals(DrunkLevel.MAX, level.get());
    }

    @Test
    void soberTickDecrements() {
        DrunkLevel level = new DrunkLevel();
        level.add(50);

        level.tick();

        assertTrue(level.get() < 50, "sober tick must lower the level");
        assertTrue(level.get() >= 0);
    }

    @Test
    void soberTickStopsAtZero() {
        DrunkLevel level = new DrunkLevel();
        level.add(1);

        for (int i = 0; i < 100; i++) {
            level.tick();
        }

        assertEquals(0, level.get());
        assertFalse(level.isDrunk());
    }

    @Test
    void negativeAddIsIgnored() {
        DrunkLevel level = new DrunkLevel();
        level.add(20);

        level.add(-50);

        assertEquals(20, level.get(), "drinking must never sober you up");
    }

    @Test
    void distortionStrengthScalesWithLevel() {
        DrunkLevel level = new DrunkLevel();

        assertEquals(0f, level.distortionStrength(), 1e-6);

        level.add(DrunkLevel.MAX);
        assertEquals(1f, level.distortionStrength(), 1e-6);

        level.reset();
        level.add(DrunkLevel.MAX / 2);
        assertEquals(0.5f, level.distortionStrength(), 1e-3);
    }

    @Test
    void aSingleDrinkIsAlreadyVisible() {
        DrunkLevel level = new DrunkLevel();

        level.add(5);

        assertTrue(level.distortionStrength() >= 0.3f,
                "one drink must produce a clearly visible sway, got " + level.distortionStrength());
    }

    @Test
    void wakingUpLeavesRoomBeforeTheNextPassOut() {
        assertTrue(DrunkLevel.SLUR_FROM < DrunkLevel.AFTER_PASS_OUT);
        assertTrue(DrunkLevel.AFTER_PASS_OUT < DrunkLevel.PASS_OUT_AT);
        assertTrue(DrunkLevel.PASS_OUT_AT <= DrunkLevel.MAX);
    }

    @Test
    void resetClearsTheLevel() {
        DrunkLevel level = new DrunkLevel();
        level.add(80);

        level.reset();

        assertEquals(0, level.get());
    }

    @Test
    void setClampsIntoRange() {
        DrunkLevel level = new DrunkLevel();

        level.set(500);
        assertEquals(DrunkLevel.MAX, level.get());

        level.set(-5);
        assertEquals(0, level.get());
    }
}
