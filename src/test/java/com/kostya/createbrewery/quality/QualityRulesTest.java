package com.kostya.createbrewery.quality;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualityRulesTest {

    @Test
    void convertsScoreBoundariesToStars() {
        assertEquals(1, QualityRules.starsFromScore(0));
        assertEquals(1, QualityRules.starsFromScore(29));
        assertEquals(2, QualityRules.starsFromScore(30));
        assertEquals(2, QualityRules.starsFromScore(54));
        assertEquals(3, QualityRules.starsFromScore(55));
        assertEquals(3, QualityRules.starsFromScore(74));
        assertEquals(4, QualityRules.starsFromScore(75));
        assertEquals(4, QualityRules.starsFromScore(89));
        assertEquals(5, QualityRules.starsFromScore(90));
        assertEquals(5, QualityRules.starsFromScore(100));
    }

    @Test
    void clampsScoresBeforeConverting() {
        assertEquals(1, QualityRules.starsFromScore(-20));
        assertEquals(5, QualityRules.starsFromScore(150));
    }

    @Test
    void onlyPoorDrinksHaveSideEffects() {
        assertEquals(0, QualityRules.nauseaTicks(3));
        assertEquals(200, QualityRules.nauseaTicks(2));
        assertEquals(0, QualityRules.poisonTicks(2));
        assertEquals(100, QualityRules.poisonTicks(1));
    }

    @Test
    void finalQualityUsesTheWeakestRequiredStage() {
        assertEquals(4, QualityRules.finalStars(List.of(100, 80, 95)));
        assertEquals(5, QualityRules.finalStars(List.of(100, 100, 100)));
    }
}
