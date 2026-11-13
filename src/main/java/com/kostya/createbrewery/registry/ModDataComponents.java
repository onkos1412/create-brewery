package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry of custom {@link DataComponentType data component types}.
 */
public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateBrewery.MOD_ID);

    /**
     * The quality ("stars") of a drink. Persistent, so it survives save/load.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DrinkData>> DRINK_DATA =
            DATA_COMPONENTS.register("drink_data", () -> DataComponentType.<DrinkData>builder()
                    .persistent(DrinkData.CODEC)
                    .build());

    /**
     * Who brewed a drink and on which world day. Synced so client tooltips can show it.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BrewerMark>> BREWER =
            DATA_COMPONENTS.register("brewer", () -> DataComponentType.<BrewerMark>builder()
                    .persistent(BrewerMark.CODEC)
                    .networkSynchronized(BrewerMark.STREAM_CODEC)
                    .build());

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
