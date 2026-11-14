package com.kostya.createbrewery.block;

import com.kostya.createbrewery.CreateBrewery;
import io.github.fabricators_of_create.porting_lib.util.LazyRegistrar;
import io.github.fabricators_of_create.porting_lib.util.RegistryObject;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.inventory.MenuType;

/**
 * Registry of the mod's menu types. The client opens each from the machine's position, which the server
 * writes after the menu id: Fabric's extended screen handlers stand in for Forge's {@code IForgeMenuType} and
 * {@code NetworkHooks.openScreen(player, provider, pos)}.
 */
public final class ModMenus {

    public static final LazyRegistrar<MenuType<?>> MENUS = LazyRegistrar.create(Registries.MENU, CreateBrewery.MOD_ID);

    public static final RegistryObject<MenuType<KettleMenu>> KETTLE =
            MENUS.register("kettle", () -> new ExtendedScreenHandlerType<>(KettleMenu::new));

    public static final RegistryObject<MenuType<DistilleryMenu>> DISTILLERY =
            MENUS.register("distillery", () -> new ExtendedScreenHandlerType<>(DistilleryMenu::new));

    public static final RegistryObject<MenuType<BarrelMenu>> BARREL =
            MENUS.register("barrel", () -> new ExtendedScreenHandlerType<>(BarrelMenu::new));

    private ModMenus() {
    }

    public static void register() {
        MENUS.register();
    }

    /** Opens a machine's menu for {@code player}, telling the client where the machine stands. */
    static void open(ServerPlayer player, BlockPos pos, Component title, MenuConstructor menu) {
        player.openMenu(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayer target, FriendlyByteBuf buf) {
                buf.writeBlockPos(pos);
            }

            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return menu.createMenu(containerId, inventory, opener);
            }
        });
    }
}
