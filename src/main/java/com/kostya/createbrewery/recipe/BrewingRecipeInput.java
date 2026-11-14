package com.kostya.createbrewery.recipe;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The input handed to {@link BrewingRecipe#matches}: the item stacks currently in the
 * kettle and the heat level beneath it.
 *
 * <p>1.20.1 recipes take a {@link Container} (1.21.1: {@code RecipeInput}), so this is a read-only view: the
 * writing methods do nothing.
 */
public record BrewingRecipeInput(List<ItemStack> items, int heatLevel) implements Container {

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    public int size() {
        return items.size();
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
    }
}
