package com.kostya.createbrewery.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

/**
 * The input handed to {@link BrewingRecipe#matches}: the item stacks currently in the
 * kettle and the heat level beneath it.
 */
public record BrewingRecipeInput(List<ItemStack> items, int heatLevel) implements RecipeInput {

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }
}
