package com.kostya.createbrewery;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrewerMarkTest {

    @Test
    void dayStartsAtOne() {
        assertEquals(1, BrewerMark.dayOf(0));
        assertEquals(1, BrewerMark.dayOf(23999));
        assertEquals(2, BrewerMark.dayOf(24000));
        assertEquals(142, BrewerMark.dayOf(24000L * 141 + 5));
    }

    @Test
    void codecRoundTrip() {
        BrewerMark mark = new BrewerMark("Kostya", 142);

        var json = BrewerMark.CODEC.encodeStart(JsonOps.INSTANCE, mark).getOrThrow();
        BrewerMark parsed = BrewerMark.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(mark, parsed);
    }
}
