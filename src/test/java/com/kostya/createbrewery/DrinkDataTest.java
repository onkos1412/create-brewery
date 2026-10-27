package com.kostya.createbrewery;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrinkDataTest {

    @Test
    void starsClampToRange() {
        assertEquals(1, new DrinkData(0).stars());
        assertEquals(5, new DrinkData(6).stars());
        assertEquals(3, new DrinkData(3).stars());
        assertEquals(1, new DrinkData(-10).stars());
    }

    @Test
    void equalsComparesClampedValue() {
        assertEquals(new DrinkData(7), new DrinkData(5));
    }

    @Test
    void codecRoundTripsStars() {
        var ops = JsonOps.INSTANCE;
        var json = DrinkData.CODEC.encodeStart(ops, new DrinkData(4)).getOrThrow();
        assertEquals(4, DrinkData.CODEC.parse(ops, json).getOrThrow().stars());
    }
}
