package com.kostya.createbrewery.client;

import com.kostya.createbrewery.network.OpenBrewerBookPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrewerBookLayoutTest {

    @Test
    void readableBookHasATitleSpreadAndOneSpreadPerDrink() {
        List<OpenBrewerBookPayload.Page> pages = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            pages.add(new OpenBrewerBookPayload.Page("drink" + i, true, true, Optional.empty()));
        }
        assertEquals(12, BrewerBookScreen.spreadCount(new OpenBrewerBookPayload(true, "Steve", pages)));
    }

    @Test
    void unreadableBookHasThreeSpreadsOfScribbles() {
        assertEquals(3, BrewerBookScreen.spreadCount(new OpenBrewerBookPayload(false, "", List.of())));
    }
}
