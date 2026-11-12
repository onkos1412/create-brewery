package com.kostya.createbrewery.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/**
 * The road a recipe's wort can travel, read from the recipe's {@code drink} block.
 *
 * @param id               the drink bottled straight from the kettle, and the wort's identity
 * @param alcohol          strength of that drink
 * @param distilled        what the still makes of it; blank when it cannot be distilled
 * @param distilledAlcohol strength after distilling, or after aging for a drink that skips the still
 * @param aged             what the barrel makes of the distillate; blank when it is bottled right from the still
 * @param distillTicks     how long distilling takes
 * @param idealRpm         the speed that distills perfectly
 * @param agingDays        game days in the barrel
 * @param wood             the wood that ages it perfectly, such as {@code oak}
 * @param color            the wort's colour as 0xRRGGBB, written {@code "#c98a1e"} in a recipe
 * @param effects          what drinking each stage gives, keyed by stage: this recipe's id, distilled or aged
 */
public record DrinkProfile(String id, int alcohol, String distilled, int distilledAlcohol, String aged,
                           int distillTicks, int idealRpm, int agingDays, String wood, int color,
                           Map<String, List<DrinkEffect>> effects) {

    private static final int BEER_COLOR = 0xC98A1E;

    private static final Codec<Integer> COLOR_CODEC = Codec.STRING.comapFlatMap(text -> {
        try {
            return DataResult.success(Integer.parseUnsignedInt(text.replaceFirst("^#", ""), 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a colour: " + text);
        }
    }, color -> String.format("#%06x", color));

    /** Recipes without a {@code drink} block, and wort saved before they had one, are beer. */
    public static final DrinkProfile BEER =
            new DrinkProfile("beer", 20, "whiskey", 40, "aged_whiskey", 200, 32, 3, "oak", BEER_COLOR, Map.of());

    /** What a mix that matches no recipe closely enough becomes: weak, and a dead end for the still and the barrel. */
    public static final DrinkProfile MUDDY =
            new DrinkProfile("muddy", 2, "", 40, "", 200, 32, 3, "oak", 0x5B4A32, Map.of());

    public static final Codec<DrinkProfile> CODEC = RecordCodecBuilder.<DrinkProfile>create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(DrinkProfile::id),
            Codec.intRange(0, 100).fieldOf("alcohol").forGetter(DrinkProfile::alcohol),
            Codec.STRING.optionalFieldOf("distilled", "").forGetter(DrinkProfile::distilled),
            Codec.intRange(0, 100).optionalFieldOf("distilledAlcohol", 40).forGetter(DrinkProfile::distilledAlcohol),
            Codec.STRING.optionalFieldOf("aged", "").forGetter(DrinkProfile::aged),
            // The machines divide by these, so a bad datapack must fail on load rather than crash a tick.
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("distillTicks", 200).forGetter(DrinkProfile::distillTicks),
            Codec.intRange(1, 256).optionalFieldOf("idealRpm", 32).forGetter(DrinkProfile::idealRpm),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("agingDays", 3).forGetter(DrinkProfile::agingDays),
            Codec.STRING.optionalFieldOf("wood", "oak").forGetter(DrinkProfile::wood),
            COLOR_CODEC.optionalFieldOf("color", BEER_COLOR).forGetter(DrinkProfile::color),
            Codec.unboundedMap(Codec.STRING, DrinkEffect.CODEC.listOf())
                    .optionalFieldOf("effects", Map.of()).forGetter(DrinkProfile::effects)
    ).apply(instance, DrinkProfile::new)).validate(DrinkProfile::checkEffectStages);

    /** An effect keyed by a stage this recipe never makes could never be drunk, so the recipe is refused. */
    private static DataResult<DrinkProfile> checkEffectStages(DrinkProfile profile) {
        for (String stage : profile.effects.keySet()) {
            if (!stage.equals(profile.id) && !stage.equals(profile.distilled) && !stage.equals(profile.aged)) {
                return DataResult.error(() -> "Effects given for " + stage + ", which " + profile.id + " never makes");
            }
        }
        return DataResult.success(profile);
    }

    public boolean canDistill() {
        return !distilled.isEmpty();
    }

    public boolean canAge() {
        return !aged.isEmpty();
    }

    /** Wine and the like: bottled young from the kettle, then aged without ever seeing the still. */
    public boolean agesInTheBottle() {
        return canAge() && !canDistill();
    }
}
