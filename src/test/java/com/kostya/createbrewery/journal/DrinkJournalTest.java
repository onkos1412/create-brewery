package com.kostya.createbrewery.journal;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrinkJournalTest {

    private static KettleRecord k(int stars) {
        return new KettleRecord(Map.of("minecraft:wheat", stars), 2, stars);
    }

    private static BarrelRecord b(int stars) {
        return new BarrelRecord("minecraft:oak", 5, stars);
    }

    @Test
    void firstRecordIsKept() {
        assertEquals(3, DrinkJournal.EMPTY.withKettle(k(3)).kettle().get().stars());
    }

    @Test
    void betterReplacesWorseDoesNot() {
        assertEquals(4, DrinkJournal.EMPTY.withKettle(k(3)).withKettle(k(4)).kettle().get().stars());
        assertEquals(4, DrinkJournal.EMPTY.withKettle(k(4)).withKettle(k(2)).kettle().get().stars());

        KettleRecord older = new KettleRecord(Map.of("minecraft:wheat", 1), 2, 3);
        KettleRecord newer = new KettleRecord(Map.of("minecraft:sugar", 1), 2, 3);
        assertEquals(older.mix(), DrinkJournal.EMPTY.withKettle(older).withKettle(newer).kettle().get().mix());
    }

    @Test
    void stagesAreIndependent() {
        DrinkJournal j = DrinkJournal.EMPTY.withKettle(k(5)).withBarrel(b(2));
        assertEquals(5, j.kettle().get().stars());
        assertEquals(2, j.barrel().get().stars());
        assertEquals(Optional.empty(), j.still());
    }

    @Test
    void mixOfCounts() {
        assertEquals(Map.of("minecraft:sugar", 1, "minecraft:wheat", 2),
                KettleRecord.mixOf(List.of("minecraft:wheat", "minecraft:wheat", "minecraft:sugar")));
    }

    @Test
    void codecRoundTrip() {
        DrinkJournal full = DrinkJournal.EMPTY.withKettle(k(3)).withStill(new StillRecord(64, 2)).withBarrel(b(4));
        for (DrinkJournal j : List.of(full, DrinkJournal.EMPTY)) {
            var json = DrinkJournal.CODEC.encodeStart(JsonOps.INSTANCE, j).getOrThrow();
            assertEquals(j, DrinkJournal.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
    }
}
