package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.item.DistillateItem;
import com.kostya.createbrewery.item.DrinkItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry of the mod's items.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CreateBrewery.MOD_ID);

    /**
     * The drink bottle. New stacks carry five stars by default; the brewing pipeline
     * overwrites the component with the real quality once it exists.
     */
    public static final DeferredItem<Item> DRINK_BOTTLE = ITEMS.register("drink_bottle",
            () -> new DrinkItem(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<Item> DISTILLATE = ITEMS.register("distillate",
            () -> new DistillateItem(new Item.Properties().stacksTo(16)));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
