package com.kostya.createbrewery.recipe;

/**
 * A plain fluid reference (registry id + amount) used by {@link BrewingRecipe} so the
 * recipe's rules stay testable without Minecraft's fluid registry. Resolved into a
 * {@code FluidStack} only when the recipe runs in game.
 *
 * @param fluid registry id of the fluid, e.g. {@code minecraft:water}
 * @param amount amount in milli-buckets
 */
public record FluidKey(String fluid, int amount) {

    public String getFluid() {
        return fluid;
    }

    public int getAmount() {
        return amount;
    }
}
