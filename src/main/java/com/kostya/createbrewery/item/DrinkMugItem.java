package com.kostya.createbrewery.item;

import com.kostya.createbrewery.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/** A wooden mug of drink, poured from a keg. It drinks like a bottle, and the empty mug comes back. */
public class DrinkMugItem extends DrinkItem {
    public DrinkMugItem(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemStack emptyContainer() {
        return new ItemStack(ModItems.MUG.get());
    }
}
