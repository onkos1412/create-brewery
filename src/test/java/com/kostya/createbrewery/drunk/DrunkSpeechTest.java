package com.kostya.createbrewery.drunk;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrunkSpeechTest {
    private static final String RUSSIAN = "сегодня самая хорошая погода";

    @Test
    void soberSpeechIsUntouched() {
        assertEquals(RUSSIAN, DrunkSpeech.slur(RUSSIAN, DrunkLevel.SLUR_FROM - 1, new Random(1)));
    }

    @Test
    void drunkSpeechSlursBothLanguages() {
        assertTrue(DrunkSpeech.slur(RUSSIAN, DrunkLevel.MAX, new Random(1)).contains("ш"));
        assertTrue(DrunkSpeech.slur("so sweet", DrunkLevel.MAX, new Random(1)).contains("sh"));
    }

    @Test
    void drunkerSpeechChangesMore() {
        int light = changedLength(DrunkLevel.SLUR_FROM);
        int heavy = changedLength(DrunkLevel.MAX);
        assertTrue(heavy > light, "light " + light + ", heavy " + heavy);
    }

    @Test
    void hiccupMatchesTheLanguage() {
        boolean russianHiccup = false;
        boolean englishHiccup = false;
        for (int seed = 0; seed < 50; seed++) {
            russianHiccup |= DrunkSpeech.slur("привет", DrunkLevel.MAX, new Random(seed)).endsWith("*ик*");
            englishHiccup |= DrunkSpeech.slur("hello", DrunkLevel.MAX, new Random(seed)).endsWith("*hic*");
        }
        assertTrue(russianHiccup);
        assertTrue(englishHiccup);
    }

    @Test
    void sameSeedGivesTheSameSlur() {
        assertEquals(DrunkSpeech.slur(RUSSIAN, 80, new Random(7)), DrunkSpeech.slur(RUSSIAN, 80, new Random(7)));
        assertNotEquals(RUSSIAN, DrunkSpeech.slur(RUSSIAN, 80, new Random(7)));
    }

    /** How many letters differ from the original, totalled over many attempts. */
    private static int changedLength(int level) {
        int total = 0;
        String english = "sizzling sausages sizzle";
        for (int seed = 0; seed < 100; seed++) {
            total += DrunkSpeech.slur(english, level, new Random(seed)).length() - english.length();
        }
        return total;
    }
}
