package com.kostya.createbrewery.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class MixRulesTest {

    private static final String COMB = "minecraft:honeycomb";
    private static final String WHEAT = "minecraft:wheat";
    private static final String APPLE = "minecraft:apple";
    private static final String FLESH = "minecraft:rotten_flesh";

    private static final List<String> MEAD = List.of(COMB, COMB);

    private static final MixRules.Candidate BEER = new MixRules.Candidate("brewery:beer", List.of(WHEAT, WHEAT), 1);
    private static final MixRules.Candidate CIDER = new MixRules.Candidate("brewery:cider", List.of(APPLE, APPLE), 1);

    @Test
    void exactMixScoresFull() {
        assertEquals(100, MixRules.score(MEAD, List.of(COMB, COMB)));
    }

    @Test
    void wrongCountCostsFifteenPerMissingOrExtraOfTwo() {
        assertEquals(85, MixRules.score(MEAD, List.of(COMB, COMB, COMB)));
        assertEquals(85, MixRules.score(MEAD, List.of(COMB)));
        assertEquals(70, MixRules.score(MEAD, List.of(COMB, COMB, COMB, COMB)));
    }

    @Test
    void foreignItemCostsThirtyFive() {
        assertEquals(65, MixRules.score(MEAD, List.of(COMB, COMB, APPLE)));
    }

    @Test
    void missingItemAndForeignItemStack() {
        assertEquals(5, MixRules.score(MEAD, List.of(FLESH)));
    }

    @Test
    void countPenaltyIsCappedAtSixty() {
        assertEquals(40, MixRules.score(MEAD, Collections.nCopies(20, COMB)));
    }

    @Test
    void bestPicksClosestRecipe() {
        MixRules.Choice choice = MixRules.best(List.of(BEER, CIDER), List.of(APPLE), 1);
        assertEquals(1, choice.index());
        assertEquals(85, choice.score());
    }

    @Test
    void foreignItemKeepsTheMatchingRecipe() {
        MixRules.Choice choice = MixRules.best(List.of(BEER, CIDER), List.of(WHEAT, WHEAT, APPLE), 1);
        assertEquals(0, choice.index());
        assertEquals(65, choice.score());
    }

    @Test
    void tieGoesToTheSmallerId() {
        List<String> given = List.of(WHEAT, WHEAT, APPLE, APPLE);
        MixRules.Choice choice = MixRules.best(List.of(CIDER, BEER), given, 1);
        assertEquals(1, choice.index());
        assertEquals(30, choice.score());
    }

    @Test
    void nothingCloseIsMuddy() {
        assertEquals(-1, MixRules.best(List.of(BEER, CIDER), List.of(FLESH), 1).index());
    }

    @Test
    void tooColdIsMuddy() {
        assertEquals(-1, MixRules.best(List.of(BEER, CIDER), List.of(WHEAT, WHEAT), 0).index());
    }

    @Test
    void aromaBands() {
        assertEquals(MixRules.AROMA_WEAK, MixRules.aroma(54));
        assertEquals(MixRules.AROMA_FAMILIAR, MixRules.aroma(55));
        assertEquals(MixRules.AROMA_FAMILIAR, MixRules.aroma(89));
        assertEquals(MixRules.AROMA_EXCELLENT, MixRules.aroma(90));
    }
}
