package com.kostya.createbrewery.recipe;

import com.kostya.createbrewery.CreateBrewery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Datapack codec for {@link BrewingRecipe}.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "inputs": [{"item": "minecraft:wheat"}, {"item": "minecraft:wheat"}],
 *   "water":  {"fluid": "minecraft:water", "amount": 1000},
 *   "result": {"fluid": "brewery:wort",   "amount": 1000},
 *   "brewTimeTicks": 200,
 *   "heatLevel": "heated"
 * }
 * }</pre>
 */
public final class BrewingRecipeSerializer implements RecipeSerializer<BrewingRecipe> {

    /** A fluid reference in JSON: {@code {"fluid": "id", "amount": n}}. */
    public record FluidKeyCodec(String fluid, int amount) {
        public static final MapCodec<FluidKeyCodec> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("fluid").forGetter(FluidKeyCodec::fluid),
                Codec.INT.optionalFieldOf("amount", 1000).forGetter(FluidKeyCodec::amount)
        ).apply(instance, FluidKeyCodec::new));

        static FluidKey toKey(FluidKeyCodec codec) {
            return new FluidKey(codec.fluid, codec.amount);
        }

        static FluidKeyCodec fromKey(FluidKey key) {
            return new FluidKeyCodec(key.fluid(), key.amount());
        }
    }

    /** An ingredient in JSON: {@code {"item": "id"}}. */
    public record IngredientCodec(String item) {
        public static final MapCodec<IngredientCodec> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("item").forGetter(IngredientCodec::item)
        ).apply(instance, IngredientCodec::new));
    }

    public static final MapCodec<BrewingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            IngredientCodec.CODEC.codec().listOf().fieldOf("inputs")
                    .forGetter(recipe -> recipe.inputs().stream().map(IngredientCodec::new).toList()),
            FluidKeyCodec.CODEC.fieldOf("water")
                    .forGetter(recipe -> FluidKeyCodec.fromKey(recipe.waterInput())),
            FluidKeyCodec.CODEC.fieldOf("result")
                    .forGetter(recipe -> FluidKeyCodec.fromKey(recipe.wortOutput())),
            Codec.INT.fieldOf("brewTimeTicks").forGetter(BrewingRecipe::brewTimeTicks),
            Codec.STRING.xmap(BrewingRecipe::heatLevelFromName, BrewingRecipeSerializer::heatLevelToName)
                    .fieldOf("heatLevel").forGetter(BrewingRecipe::idealHeatLevel)
    ).apply(instance, (inputs, water, result, ticks, heat) -> new BrewingRecipe(
            inputs.stream().map(IngredientCodec::item).toList(),
            FluidKeyCodec.toKey(water),
            FluidKeyCodec.toKey(result),
            ticks,
            heat)));

    private static String heatLevelToName(int level) {
        return switch (level) {
            case BrewingRecipe.HEAT_NONE -> "none";
            case BrewingRecipe.HEAT_SUPERHEATED -> "superheated";
            default -> "heated";
        };
    }

    @Override
    public MapCodec<BrewingRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, BrewingRecipe> streamCodec() {
        StreamCodec<RegistryFriendlyByteBuf, List<String>> inputs =
                ByteBufCodecs.fromCodecWithRegistries(Codec.STRING.listOf());
        StreamCodec<RegistryFriendlyByteBuf, FluidKey> fluidKey =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, FluidKey::fluid,
                        ByteBufCodecs.VAR_INT, FluidKey::amount,
                        FluidKey::new);
        return StreamCodec.composite(
                inputs, BrewingRecipe::inputs,
                fluidKey, BrewingRecipe::waterInput,
                fluidKey, BrewingRecipe::wortOutput,
                ByteBufCodecs.VAR_INT, BrewingRecipe::brewTimeTicks,
                ByteBufCodecs.VAR_INT, BrewingRecipe::idealHeatLevel,
                BrewingRecipe::new);
    }

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateBrewery.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, BrewingRecipeSerializer> INSTANCE =
            SERIALIZERS.register("brewing", BrewingRecipeSerializer::new);

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
