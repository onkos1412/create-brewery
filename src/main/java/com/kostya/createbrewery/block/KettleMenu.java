package com.kostya.createbrewery.block;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The kettle's menu: four ingredient slots, one output slot that hands out a bottle
 * once wort is ready, and the player inventory.
 */
public class KettleMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOTS = 4;
    public static final int OUTPUT_SLOT = INPUT_SLOTS;
    private static final int SLOT_SIZE = 18;

    /** Star rating used by the skeleton; the real quality scale arrives with aging. */
    public static final int SKELETON_STARS = 5;

    private final KettleBlockEntity kettle;
    private final Container outputGhost = new SimpleContainer(1);

    /** Client-side constructor used by the menu type. */
    public KettleMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, (KettleBlockEntity) inventory.player.level()
                .getBlockEntity(buffer.readBlockPos()));
    }

    public KettleMenu(int containerId, Inventory inventory, KettleBlockEntity kettle) {
        super(ModMenus.KETTLE.get(), containerId);
        this.kettle = kettle;

        for (int i = 0; i < INPUT_SLOTS; i++) {
            addSlot(new SlotItemHandler(kettle.getInput(), i, 26 + i * SLOT_SIZE, 20));
        }

        addSlot(new Slot(outputGhost, 0, 128, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return kettle.getWortAmount() >= KettleBlockEntity.WORT_PER_BREW;
            }

            @Override
            public ItemStack remove(int amount) {
                if (!mayPickup(null)) {
                    return ItemStack.EMPTY;
                }
                return kettle.bottleWort(SKELETON_STARS);
            }
        });

        addPlayerInventory(inventory);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * SLOT_SIZE, 84 + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * SLOT_SIZE, 142));
        }
    }

    public KettleBlockEntity getKettle() {
        return kettle;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < OUTPUT_SLOT) {
            if (!moveItemStackTo(stack, OUTPUT_SLOT + 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index > OUTPUT_SLOT) {
            if (!moveItemStackTo(stack, 0, INPUT_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** Layout constants shared with {@link KettleScreen}. */
    public static final class Layout {
        public static final int WIDTH = 176;
        public static final int HEIGHT = 166;

        private Layout() {
        }
    }
}
