package com.kostya.createbrewery.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.StrictCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

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
 *   "heatLevel": "heated",
 *   "drink": {"id": "beer", "alcohol": 20, "distilled": "whiskey", "distilledAlcohol": 40, "aged": "aged_whiskey"}
 * }
 * }</pre>
 *
 * <p>1.20.1 serializers read JSON and the network buffer by hand ({@code fromJson}/{@code fromNetwork}); the JSON
 * side still goes through the 1.21.1 codec, given the recipe id because 1.20.1 recipes carry their own.
 */
public final class BrewingRecipeSerializer implements RecipeSerializer<BrewingRecipe> {

    /** A fluid reference in JSON: {@code {"fluid": "id", "amount": n}}. */
    public record FluidKeyCodec(String fluid, int amount) {
        public static final MapCodec<FluidKeyCodec> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.STRING.fieldOf("fluid").forGetter(FluidKeyCodec::fluid),
                StrictCodecs.optionalField(Codec.INT, "amount", 1000).forGetter(FluidKeyCodec::amount)
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

    /** The JSON codec of the recipe called {@code id}. */
    public static MapCodec<BrewingRecipe> codec(ResourceLocation id) {
        return StrictCodecs.validate(RecordCodecBuilder.<BrewingRecipe>mapCodec(instance -> instance.group(
                IngredientCodec.CODEC.codec().listOf().fieldOf("inputs")
                        .forGetter(recipe -> recipe.inputs().stream().map(IngredientCodec::new).toList()),
                FluidKeyCodec.CODEC.fieldOf("water")
                        .forGetter(recipe -> FluidKeyCodec.fromKey(recipe.waterInput())),
                FluidKeyCodec.CODEC.fieldOf("result")
                        .forGetter(recipe -> FluidKeyCodec.fromKey(recipe.wortOutput())),
                Codec.INT.fieldOf("brewTimeTicks").forGetter(BrewingRecipe::brewTimeTicks),
                Codec.STRING.xmap(BrewingRecipe::heatLevelFromName, BrewingRecipeSerializer::heatLevelToName)
                        .fieldOf("heatLevel").forGetter(BrewingRecipe::idealHeatLevel),
                StrictCodecs.optionalField(DrinkProfile.CODEC, "drink", DrinkProfile.BEER).forGetter(BrewingRecipe::drink)
        ).apply(instance, (inputs, water, result, ticks, heat, drink) -> new BrewingRecipe(
                id,
                inputs.stream().map(IngredientCodec::item).toList(),
                FluidKeyCodec.toKey(water),
                FluidKeyCodec.toKey(result),
                ticks,
                heat,
                drink))), BrewingRecipeSerializer::checkEffectsExist);
    }

    /** The drink codec runs without registries in unit tests, so unknown effect ids are refused here, in game. */
    private static DataResult<BrewingRecipe> checkEffectsExist(BrewingRecipe recipe) {
        for (List<DrinkEffect> effects : recipe.drink().effects().values()) {
            for (DrinkEffect effect : effects) {
                if (!BuiltInRegistries.MOB_EFFECT.containsKey(effect.effect())) {
                    return DataResult.error(() -> "Unknown effect " + effect.effect() + " in " + recipe.drink().id());
                }
            }
        }
        return DataResult.success(recipe);
    }

    private static String heatLevelToName(int level) {
        return switch (level) {
            case BrewingRecipe.HEAT_NONE -> "none";
            case BrewingRecipe.HEAT_SUPERHEATED -> "superheated";
            default -> "heated";
        };
    }

    /**
     * Reads a datapack recipe. Errors are thrown as {@link JsonSyntaxException}, the kind the recipe manager logs
     * and skips (a plain runtime exception would abort the whole reload).
     */
    @Override
    public BrewingRecipe fromJson(ResourceLocation id, JsonObject json) {
        DataResult<BrewingRecipe> result;
        try {
            result = codec(id).codec().parse(JsonOps.INSTANCE, json);
        } catch (IllegalArgumentException e) {
            // An unknown heat level name throws from the xmap rather than returning an error.
            throw new JsonSyntaxException(e.getMessage(), e);
        }
        return result.result().orElseThrow(() -> new JsonSyntaxException(
                result.error().map(DataResult.PartialResult::message).orElse("Invalid brewing recipe")));
    }

    @Override
    public BrewingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        List<String> inputs = buf.readList(FriendlyByteBuf::readUtf);
        FluidKey water = readFluidKey(buf);
        FluidKey wort = readFluidKey(buf);
        int ticks = buf.readVarInt();
        int heat = buf.readVarInt();
        DrinkProfile drink = buf.readJsonWithCodec(DrinkProfile.CODEC);
        return new BrewingRecipe(id, inputs, water, wort, ticks, heat, drink);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, BrewingRecipe recipe) {
        buf.writeCollection(recipe.inputs(), FriendlyByteBuf::writeUtf);
        writeFluidKey(buf, recipe.waterInput());
        writeFluidKey(buf, recipe.wortOutput());
        buf.writeVarInt(recipe.brewTimeTicks());
        buf.writeVarInt(recipe.idealHeatLevel());
        buf.writeJsonWithCodec(DrinkProfile.CODEC, recipe.drink());
    }

    private static FluidKey readFluidKey(FriendlyByteBuf buf) {
        return new FluidKey(buf.readUtf(), buf.readVarInt());
    }

    private static void writeFluidKey(FriendlyByteBuf buf, FluidKey key) {
        buf.writeUtf(key.fluid());
        buf.writeVarInt(key.amount());
    }

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateBrewery.MOD_ID);

    public static final RegistryObject<BrewingRecipeSerializer> INSTANCE =
            SERIALIZERS.register("brewing", BrewingRecipeSerializer::new);

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
