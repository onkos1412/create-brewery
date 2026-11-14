package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.item.BrewerBookItem;
import com.kostya.createbrewery.item.DistillateItem;
import com.kostya.createbrewery.item.DrinkItem;
import com.kostya.createbrewery.item.DrinkMugItem;
import io.github.fabricators_of_create.porting_lib.util.LazyRegistrar;
import io.github.fabricators_of_create.porting_lib.util.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

/**
 * Registry of the mod's items.
 */
public final class ModItems {
    public static final LazyRegistrar<Item> ITEMS = LazyRegistrar.create(Registries.ITEM, CreateBrewery.MOD_ID);

    /**
     * The drink bottle. New stacks carry five stars by default; the brewing pipeline
     * overwrites the component with the real quality once it exists.
     */
    public static final RegistryObject<Item> DRINK_BOTTLE = ITEMS.register("drink_bottle",
            () -> new DrinkItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> DISTILLATE = ITEMS.register("distillate",
            () -> new DistillateItem(new Item.Properties().stacksTo(16)));

    /** The brewer's book: signed by its first reader, it shows them their brewing journal. */
    public static final RegistryObject<Item> BREWER_BOOK = ITEMS.register("brewer_book",
            () -> new BrewerBookItem(new Item.Properties().stacksTo(1)));

    /** An empty wooden mug: use it on a keg to fill it with one portion. */
    public static final RegistryObject<Item> MUG = ITEMS.register("mug",
            () -> new Item(new Item.Properties().stacksTo(16)));

    /** A mug of drink; drinking it hands the mug back. */
    public static final RegistryObject<Item> DRINK_MUG = ITEMS.register("drink_mug",
            () -> new DrinkMugItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register() {
        ITEMS.register();
    }
}
