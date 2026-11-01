package com.kostya.createbrewery.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The road a recipe's wort can travel, read from the recipe's {@code drink} block.
 *
 * @param id               the drink bottled straight from the kettle, and the wort's identity
 * @param alcohol          strength of that drink
 * @param distilled        what the still makes of it; blank when it cannot be distilled
 * @param distilledAlcohol strength after distilling
 * @param aged             what the barrel makes of the distillate; blank when it is bottled right from the still
 */
public record DrinkProfile(String id, int alcohol, String distilled, int distilledAlcohol, String aged) {

    /** Recipes without a {@code drink} block, and wort saved before they had one, are beer. */
    public static final DrinkProfile BEER = new DrinkProfile("beer", 20, "whiskey", 40, "aged_whiskey");

    public static final Codec<DrinkProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(DrinkProfile::id),
            Codec.INT.fieldOf("alcohol").forGetter(DrinkProfile::alcohol),
            Codec.STRING.optionalFieldOf("distilled", "").forGetter(DrinkProfile::distilled),
            Codec.INT.optionalFieldOf("distilledAlcohol", 40).forGetter(DrinkProfile::distilledAlcohol),
            Codec.STRING.optionalFieldOf("aged", "").forGetter(DrinkProfile::aged)
    ).apply(instance, DrinkProfile::new));

    public boolean canDistill() {
        return !distilled.isEmpty();
    }

    public boolean canAge() {
        return !aged.isEmpty();
    }
}
