package com.kostya.createbrewery;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

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

        var json = BrewerMark.CODEC.encodeStart(JsonOps.INSTANCE, mark).getOrThrow(false, error -> { });
        BrewerMark parsed = BrewerMark.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, error -> { });

        assertEquals(mark, parsed);
    }

    @Test
    void oldMarkWithoutOwnerLoads() {
        var json = JsonParser.parseString("{\"name\":\"Kostya\",\"day\":7}");
        BrewerMark parsed = BrewerMark.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, error -> { });

        assertEquals(new BrewerMark("Kostya", 7, Optional.empty()), parsed);
    }

    @Test
    void codecRoundTripWithOwner() {
        BrewerMark mark = new BrewerMark("Kostya", 142,
                Optional.of(UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e")));

        var json = BrewerMark.CODEC.encodeStart(JsonOps.INSTANCE, mark).getOrThrow(false, error -> { });
        BrewerMark parsed = BrewerMark.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, error -> { });

        assertEquals(mark, parsed);
    }
}
