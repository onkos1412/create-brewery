package com.kostya.createbrewery.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link KettleLogic}, the Minecraft-free core of the kettle: how heat and
 * the closeness of the mix decide the wort's stars.
 */
class KettleLogicTest {

    @Test
    void onlyTheExactHeatIsPerfect() {
        assertEquals(5, KettleLogic.brewStars(KettleLogic.HEAT_HEATED, KettleLogic.HEAT_HEATED));
        assertEquals(3, KettleLogic.brewStars(KettleLogic.HEAT_SUPERHEATED, KettleLogic.HEAT_HEATED));
        assertEquals(5, KettleLogic.brewStars(KettleLogic.HEAT_SUPERHEATED, KettleLogic.HEAT_SUPERHEATED));
    }

    @Test
    void theWeakerOfMixAndHeatDecidesTheStars() {
        assertEquals(5, KettleLogic.mixStars(100, 5));
        assertEquals(4, KettleLogic.mixStars(85, 5), "a slightly wrong mix costs a star");
        assertEquals(3, KettleLogic.mixStars(100, 3), "overheating still costs stars with a perfect mix");
        assertEquals(2, KettleLogic.mixStars(40, 3));
        assertEquals(3, KettleLogic.mixStars(70, 4));
    }
}
