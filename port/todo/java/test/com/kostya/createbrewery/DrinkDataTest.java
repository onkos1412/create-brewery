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
    void legacyCodecDataGetsSafeDefaults() {
        var json = com.google.gson.JsonParser.parseString("{\"stars\": 4}");

        DrinkData data = DrinkData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(4, data.stars());
        assertEquals(DrinkData.DEFAULT_ALCOHOL, data.alcohol());
        assertEquals(DrinkData.DEFAULT_STAGE, data.stage());
    }

    @Test
    void newDrinkDataClampsAlcoholAndKeepsStage() {
        DrinkData data = new DrinkData(5, 140, "aged_whiskey");

        assertEquals(5, data.stars());
        assertEquals(DrinkData.MAX_ALCOHOL, data.alcohol());
        assertEquals("aged_whiskey", data.stage());
    }

    @Test
    void emptyStageFallsBackToWhiskey() {
        assertEquals(DrinkData.DEFAULT_STAGE, new DrinkData(3, -10, "").stage());
        assertEquals(0, new DrinkData(3, -10, "").alcohol());
    }

    @Test
    void codecRoundTripsAllDrinkData() {
        var ops = JsonOps.INSTANCE;
        var json = DrinkData.CODEC.encodeStart(ops, new DrinkData(4, 40, "aged_whiskey")).getOrThrow();

        DrinkData data = DrinkData.CODEC.parse(ops, json).getOrThrow();

        assertEquals(new DrinkData(4, 40, "aged_whiskey"), data);
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
