package com.kostya.createbrewery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Data component describing the quality of a drink, measured in "stars".
 *
 * <p>The number of stars is clamped into the range {@code [1, 5]}: anything below
 * 1 becomes 1 and anything above 5 becomes 5. In the skeleton this is fixed at 5
 * by the drink bottle; the real quality scale is introduced in a later task.
 *
 * @param stars the quality of the drink, clamped to {@code [1, 5]}
 */
public record DrinkData(int stars) {
    public static final int MIN_STARS = 1;
    public static final int MAX_STARS = 5;

    public static final Codec<DrinkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("stars").forGetter(DrinkData::stars)
    ).apply(instance, DrinkData::new));

    /**
     * Canonical constructor which clamps {@code stars} into {@code [1, 5]}.
     */
    public DrinkData {
        stars = Math.max(MIN_STARS, Math.min(MAX_STARS, stars));
    }
}
