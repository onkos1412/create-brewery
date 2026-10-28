package com.kostya.createbrewery.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.kostya.createbrewery.CreateBrewery;

/**
 * Registry of the mod's menu types.
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CreateBrewery.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<KettleMenu>> KETTLE =
            MENUS.register("kettle", () -> IMenuTypeExtension.create(KettleMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<DistilleryMenu>> DISTILLERY =
            MENUS.register("distillery", () -> IMenuTypeExtension.create(DistilleryMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
