package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's creative tab, holding every block and item it adds.
 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateBrewery.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BREWERY =
            TABS.register("brewery", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.brewery"))
                    .icon(() -> new ItemStack(ModItems.DRINK_BOTTLE.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.KETTLE_ITEM.get());
                        output.accept(ModItems.DRINK_BOTTLE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
