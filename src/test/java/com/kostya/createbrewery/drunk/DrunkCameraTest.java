package com.kostya.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DrunkCamera}, the Minecraft-free camera sway maths.
 */
class DrunkCameraTest {

    @Test
    void soberCameraDoesNotMove() {
        DrunkCamera.Angles angles = DrunkCamera.angles(12.34f, 0f);

        assertEquals(0f, angles.roll(), 1e-6);
        assertEquals(0f, angles.pitch(), 1e-6);
        assertEquals(0f, angles.yaw(), 1e-6);
    }

    @Test
    void swayIsBoundedByTheConfiguredMaximum() {
        for (int i = 0; i < 200; i++) {
            DrunkCamera.Angles angles = DrunkCamera.angles(i * 0.13f, 1f);
            assertTrue(Math.abs(angles.roll()) <= DrunkCamera.MAX_ROLL_DEGREES + 1e-4);
            assertTrue(Math.abs(angles.pitch()) <= DrunkCamera.MAX_PITCH_DEGREES + 1e-4);
            assertTrue(Math.abs(angles.yaw()) <= DrunkCamera.MAX_YAW_DEGREES + 1e-4);
        }
    }

    @Test
    void swayScalesWithStrength() {
        DrunkCamera.Angles full = DrunkCamera.angles(3.7f, 1f);
        DrunkCamera.Angles half = DrunkCamera.angles(3.7f, 0.5f);

        assertEquals(full.roll() * 0.5f, half.roll(), 1e-4);
        assertEquals(full.pitch() * 0.5f, half.pitch(), 1e-4);
        assertEquals(full.yaw() * 0.5f, half.yaw(), 1e-4);
    }

    @Test
    void strengthIsClampedIntoRange() {
        DrunkCamera.Angles full = DrunkCamera.angles(2.1f, 1f);

        assertEquals(full.roll(), DrunkCamera.angles(2.1f, 3f).roll(), 1e-6);
        assertEquals(0f, DrunkCamera.angles(2.1f, -1f).roll(), 1e-6);
    }

    @Test
    void swayMovesSmoothlyWithTimeInsteadOfResettingEveryTick() {
        // One tick is 1/20 s. The sway must keep changing between ticks and must not
        // jump back to the tick-zero value, which is what using partialTick as time did.
        DrunkCamera.Angles now = DrunkCamera.angles(5.0f, 1f);
        DrunkCamera.Angles nextTick = DrunkCamera.angles(5.05f, 1f);
        DrunkCamera.Angles atZero = DrunkCamera.angles(0f, 1f);

        assertNotEquals(now.roll(), nextTick.roll());
        assertNotEquals(now.roll(), atZero.roll());
        assertTrue(Math.abs(now.roll() - nextTick.roll()) < 1f, "sway must be gradual");
    }

    @Test
    void rollOscillatesOverTime() {
        // Over one full roll period the roll crosses zero and changes sign.
        float periodSeconds = (float) (2.0 * Math.PI / DrunkCamera.ROLL_ANGULAR_SPEED);
        boolean sawPositive = false;
        boolean sawNegative = false;
        for (int i = 0; i <= 40; i++) {
            float roll = DrunkCamera.angles(periodSeconds * i / 40f, 1f).roll();
            sawPositive |= roll > 0.1f;
            sawNegative |= roll < -0.1f;
        }

        assertTrue(sawPositive && sawNegative, "roll must swing both ways");
    }
}
