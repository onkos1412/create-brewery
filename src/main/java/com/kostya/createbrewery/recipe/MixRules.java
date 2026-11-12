package com.kostya.createbrewery.recipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pure, Minecraft-free rules for brewing an arbitrary mix: how close a set of items is
 * to a recipe, and which recipe a mix most resembles. Kept free of game classes so the
 * rules can be unit-tested in a plain JVM.
 */
public final class MixRules {

    /** Below this score the brew turns out muddy instead of resembling any recipe. */
    public static final int MUDDY_THRESHOLD = 30;

    public static final int AROMA_WEAK = 1;
    public static final int AROMA_FAMILIAR = 2;
    public static final int AROMA_EXCELLENT = 3;

    private static final int FAMILIAR_FROM = 55;
    private static final int EXCELLENT_FROM = 90;

    private static final double COUNT_PENALTY = 30.0;
    private static final int COUNT_PENALTY_CAP = 60;
    private static final int MISSING_PENALTY = 60;
    private static final int FOREIGN_PENALTY = 35;

    private MixRules() {
    }

    /** A recipe a mix can be compared with. */
    public record Candidate(String id, List<String> inputs, int idealHeat) {
    }

    /** The recipe a mix resembles most: its index among the candidates, or -1 for muddy. */
    public record Choice(int index, int score) {
    }

    /**
     * How close {@code given} is to {@code required}, from 100 (exact) down to 0.
     *
     * <p>Each recipe item costs 30 × |given − required| / required points, at most 60; an
     * item missing entirely costs 60; each piece of an item the recipe does not use costs 35.
     */
    public static int score(List<String> required, List<String> given) {
        Map<String, Integer> need = count(required);
        Map<String, Integer> have = count(given);
        double penalty = 0;
        for (Map.Entry<String, Integer> entry : need.entrySet()) {
            int r = entry.getValue();
            int g = have.getOrDefault(entry.getKey(), 0);
            penalty += g == 0
                    ? MISSING_PENALTY
                    : Math.min(COUNT_PENALTY_CAP, COUNT_PENALTY * Math.abs(g - r) / r);
        }
        for (Map.Entry<String, Integer> entry : have.entrySet()) {
            if (!need.containsKey(entry.getKey())) {
                penalty += FOREIGN_PENALTY * entry.getValue();
            }
        }
        return Math.max(0, 100 - (int) Math.round(penalty));
    }

    /**
     * Picks the candidate the mix scores highest against among those the heat can brew.
     * Ties go to the smaller id. Returns index -1 when nothing scores at least
     * {@link #MUDDY_THRESHOLD} or no candidate is hot enough.
     */
    public static Choice best(List<Candidate> candidates, List<String> given, int heat) {
        int bestIndex = -1;
        int bestScore = -1;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (candidate.idealHeat() > heat) {
                continue;
            }
            int score = score(candidate.inputs(), given);
            boolean better = score > bestScore || (score == bestScore
                    && candidate.id().compareTo(candidates.get(bestIndex).id()) < 0);
            if (better) {
                bestIndex = i;
                bestScore = score;
            }
        }
        if (bestIndex < 0 || bestScore < MUDDY_THRESHOLD) {
            return new Choice(-1, Math.max(bestScore, 0));
        }
        return new Choice(bestIndex, bestScore);
    }

    /** Maps a score onto how pleasant the brew smells. */
    public static int aroma(int score) {
        if (score >= EXCELLENT_FROM) {
            return AROMA_EXCELLENT;
        }
        return score >= FAMILIAR_FROM ? AROMA_FAMILIAR : AROMA_WEAK;
    }

    private static Map<String, Integer> count(List<String> items) {
        Map<String, Integer> counts = new HashMap<>();
        for (String item : items) {
            counts.merge(item, 1, Integer::sum);
        }
        return counts;
    }
}
