package com.kostya.createbrewery.block;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry of the mod's menu types. The client opens each from the machine's position, which the server
 * writes after the menu id ({@code NetworkHooks.openScreen(player, provider, pos)}).
 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CreateBrewery.MOD_ID);

    public static final RegistryObject<MenuType<KettleMenu>> KETTLE =
            MENUS.register("kettle", () -> IForgeMenuType.create(KettleMenu::new));

    public static final RegistryObject<MenuType<DistilleryMenu>> DISTILLERY =
            MENUS.register("distillery", () -> IForgeMenuType.create(DistilleryMenu::new));

    public static final RegistryObject<MenuType<BarrelMenu>> BARREL =
            MENUS.register("barrel", () -> IForgeMenuType.create(BarrelMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
