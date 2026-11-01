package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mod's creative tab, holding every block and item it adds.
 */
public final class ModCreativeTabs {

    /** A five-star bottle of each shipped drink, with its strength; recipes are not loaded when the tab builds. */
    private static final Map<String, Integer> TAB_DRINKS = orderedDrinks();

    private static Map<String, Integer> orderedDrinks() {
        Map<String, Integer> drinks = new LinkedHashMap<>();
        drinks.put("beer", 20);
        drinks.put("aged_whiskey", 40);
        drinks.put("cider", 15);
        drinks.put("aged_calvados", 40);
        drinks.put("vodka", 40);
        drinks.put("aged_rum", 45);
        drinks.put("wine", 25);
        drinks.put("mead", 20);
        return drinks;
    }

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateBrewery.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BREWERY =
            TABS.register("brewery", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.brewery"))
                    .icon(() -> new ItemStack(ModItems.DRINK_BOTTLE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.KETTLE_ITEM.get());
                        output.accept(ModBlocks.SPOUT_ITEM.get());
                        output.accept(ModBlocks.DISTILLERY_ITEM.get());
                        output.accept(ModBlocks.BARREL_ITEM.get());
                        output.accept(ModItems.DISTILLATE.get());
                        for (Map.Entry<String, Integer> drink : TAB_DRINKS.entrySet()) {
                            ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
                            bottle.set(ModDataComponents.DRINK_DATA.get(),
                                    new DrinkData(DrinkData.MAX_STARS, drink.getValue(), drink.getKey()));
                            output.accept(bottle);
                        }
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
