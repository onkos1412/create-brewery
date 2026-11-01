package com.kostya.createbrewery.drunk;

import java.util.Random;

/**
 * The pure, Minecraft-free slurred speech rule: hissing consonants turn to "sh", vowels
 * drag on and hiccups interrupt, all more often the drunker the speaker is.
 */
public final class DrunkSpeech {

    private static final String VOWELS = "aeiouyаеёиоуыэюя";

    private DrunkSpeech() {
    }

    /** Returns the message as a player at {@code level} says it, unchanged below {@link DrunkLevel#SLUR_FROM}. */
    public static String slur(String message, int level, Random random) {
        if (level < DrunkLevel.SLUR_FROM) {
            return message;
        }
        double chance = 0.2 + 0.6 * (level - DrunkLevel.SLUR_FROM) / (DrunkLevel.MAX - DrunkLevel.SLUR_FROM);
        StringBuilder spoken = new StringBuilder(message.length() + 8);
        for (char letter : message.toCharArray()) {
            String soft = soften(letter);
            if (soft != null && random.nextDouble() < chance) {
                spoken.append(soft);
            } else {
                spoken.append(letter);
                if (VOWELS.indexOf(Character.toLowerCase(letter)) >= 0 && random.nextDouble() < chance / 4) {
                    spoken.append(letter);
                }
            }
        }
        if (!message.isBlank() && random.nextDouble() < chance / 2) {
            spoken.append(isCyrillic(message) ? " *ик*" : " *hic*");
        }
        return spoken.toString();
    }

    private static String soften(char letter) {
        return switch (letter) {
            case 'с', 'з' -> "ш";
            case 'С', 'З' -> "Ш";
            case 's' -> "sh";
            case 'S' -> "Sh";
            case 'z' -> "zh";
            case 'Z' -> "Zh";
            default -> null;
        };
    }

    private static boolean isCyrillic(String message) {
        return message.chars().anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC);
    }
}
