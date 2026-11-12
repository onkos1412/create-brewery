package com.kostya.createbrewery.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A brewing recipe: the ingredients and heat that turn water into wort.
 *
 * <p>The fields are kept as plain data ({@link FluidKey}, item ids) so the matching
 * rules can be unit-tested without Minecraft's registries; the {@link Recipe} methods
 * needed by the game delegate to that pure {@link #matches(List, int)} rule.
 *
 * @param inputs        item ids accepted as one ingredient each, in order
 * @param waterInput    the fluid the kettle must contain to accept this recipe
 * @param wortOutput    the fluid produced when the brew finishes
 * @param brewTimeTicks ideal brewing duration in ticks
 * @param idealHeatLevel required heat: 0 = none, 1 = heated, 2 = superheated
 * @param drink         what the wort becomes on its way through the machines
 */
public record BrewingRecipe(
        List<String> inputs,
        FluidKey waterInput,
        FluidKey wortOutput,
        int brewTimeTicks,
        int idealHeatLevel,
        DrinkProfile drink) implements Recipe<BrewingRecipeInput> {

    /** The profile of the wort called {@code id}. */
    public static DrinkProfile profileOf(Level level, String id) {
        if (id.equals(DrinkProfile.MUDDY.id())) {
            return DrinkProfile.MUDDY;
        }
        return findProfile(level, drink -> drink.id().equals(id));
    }

    /** The profile that distills into {@code stage}; distillate saved before profiles existed is whiskey. */
    public static DrinkProfile profileDistilledTo(Level level, String stage) {
        return findProfile(level, drink -> drink.distilled().equals(stage));
    }

    private static DrinkProfile findProfile(Level level, Predicate<DrinkProfile> test) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.BREWING.get()).stream()
                .map(holder -> holder.value().drink())
                .filter(test)
                .findFirst()
                .orElse(DrinkProfile.BEER);
    }

    public static final int HEAT_NONE = BrewingRules.HEAT_NONE;
    public static final int HEAT_HEATED = BrewingRules.HEAT_HEATED;
    public static final int HEAT_SUPERHEATED = BrewingRules.HEAT_SUPERHEATED;

    /**
     * Maps a datapack heat-level name onto its numeric level.
     *
     * @throws IllegalArgumentException for an unknown name
     */
    public static int heatLevelFromName(String name) {
        return BrewingRules.heatLevelFromName(name);
    }

    /**
     * Whether the given ingredients and available heat satisfy this recipe. See
     * {@link BrewingRules#matches(List, List, int, int)} for the rules.
     *
     * @param itemIds       item ids currently in the kettle, one per item
     * @param availableHeat heat level of the heat source under the kettle
     */
    public boolean matches(List<String> itemIds, int availableHeat) {
        return BrewingRules.matches(inputs, itemIds, idealHeatLevel, availableHeat);
    }

    public boolean matches(String[] itemIds, int availableHeat) {
        return matches(List.of(itemIds), availableHeat);
    }

    public FluidKey getWort() {
        return wortOutput;
    }

    // ---- Recipe<BrewingRecipeInput> -------------------------------------------------

    @Override
    public boolean matches(BrewingRecipeInput input, Level level) {
        List<String> ids = input.items().stream()
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .toList();
        return matches(ids, input.heatLevel());
    }

    /** Brewing emits a fluid, not an item; the bottle is filled by the spout instead. */
    @Override
    public ItemStack assemble(BrewingRecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (String id : inputs) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            list.add(Ingredient.of(item));
        }
        return list;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BrewingRecipeSerializer.INSTANCE.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.BREWING.get();
    }
}
