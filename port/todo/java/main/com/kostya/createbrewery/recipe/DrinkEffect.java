package com.kostya.createbrewery.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * One status effect a drink gives, read from a recipe's {@code effects} block.
 *
 * <p>The effect id is checked against the registry by the recipe serializer, not here:
 * this codec runs without registries in unit tests.
 *
 * @param effect    the mob effect, such as {@code minecraft:speed}
 * @param seconds   how long it lasts on a five-star drink
 * @param amplifier its level minus one
 */
public record DrinkEffect(ResourceLocation effect, int seconds, int amplifier) {

    public static final Codec<DrinkEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("effect").forGetter(DrinkEffect::effect),
            Codec.intRange(1, Integer.MAX_VALUE / 20).fieldOf("seconds").forGetter(DrinkEffect::seconds),
            Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(DrinkEffect::amplifier)
    ).apply(instance, DrinkEffect::new));
}
