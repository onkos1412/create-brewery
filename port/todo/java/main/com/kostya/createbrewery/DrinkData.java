package com.kostya.createbrewery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persistent metadata carried by drink and intermediate-product stacks. */
public record DrinkData(int stars, int alcohol, String stage) {
    public static final int MIN_STARS = 1;
    public static final int MAX_STARS = 5;
    public static final int MIN_ALCOHOL = 0;
    public static final int MAX_ALCOHOL = 100;
    public static final int DEFAULT_ALCOHOL = 40;
    public static final String DEFAULT_STAGE = "whiskey";
    /** What a completely missed setting makes instead of the intended drink. */
    public static final String SPOILED_STAGE = "spoiled";

    /** Optional fields keep components written by the skeleton readable. */
    public static final Codec<DrinkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("stars").forGetter(DrinkData::stars),
            Codec.INT.optionalFieldOf("alcohol", DEFAULT_ALCOHOL).forGetter(DrinkData::alcohol),
            Codec.STRING.optionalFieldOf("stage", DEFAULT_STAGE).forGetter(DrinkData::stage)
    ).apply(instance, DrinkData::new));

    public DrinkData(int stars) {
        this(stars, DEFAULT_ALCOHOL, DEFAULT_STAGE);
    }

    public boolean isSpoiled() {
        return SPOILED_STAGE.equals(stage);
    }

    public DrinkData {
        stars = Math.max(MIN_STARS, Math.min(MAX_STARS, stars));
        alcohol = Math.max(MIN_ALCOHOL, Math.min(MAX_ALCOHOL, alcohol));
        stage = stage == null || stage.isBlank() ? DEFAULT_STAGE : stage;
    }
}
