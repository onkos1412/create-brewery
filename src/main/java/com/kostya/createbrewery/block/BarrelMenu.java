package com.kostya.createbrewery.block;

import com.kostya.createbrewery.barrel.AgingRules;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class BarrelMenu extends AbstractContainerMenu {
    private static final int OUTPUT_SLOT = 2;
    private static final int BOTTLE_SLOT = 3;
    private static final int MACHINE_SLOTS = 4;

    private final BarrelBlockEntity barrel;
    private final ContainerData data = new Data();

    public BarrelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, find(inventory, buffer.readBlockPos()));
    }

    public BarrelMenu(int containerId, Inventory inventory, BarrelBlockEntity barrel) {
        super(ModMenus.BARREL.get(), containerId);
        this.barrel = barrel;
        var handler = barrel == null ? new net.neoforged.neoforge.items.ItemStackHandler(2) : barrel.getInput();
        addSlot(new SlotItemHandler(handler, 0, 44, 35));
        addSlot(new SlotItemHandler(handler, 1, 80, 35));
        addSlot(new SlotItemHandler(barrel == null ? new net.neoforged.neoforge.items.ItemStackHandler(1)
                : barrel.getOutput(), 0, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            /** Taking the drink here costs an empty bottle, the same as at a funnel or the spout. */
            @Override
            public boolean mayPickup(Player player) {
                return (barrel == null || barrel.hasBottle()) && super.mayPickup(player);
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                if (barrel != null) {
                    barrel.spendBottle();
                }
                super.onTake(player, stack);
            }
        });
        addSlot(new SlotItemHandler(barrel == null ? new net.neoforged.neoforge.items.ItemStackHandler(1)
                : barrel.getBottles(), 0, 116, 15));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    private static BarrelBlockEntity find(Inventory inventory, net.minecraft.core.BlockPos pos) {
        var entity = inventory.player.level().getBlockEntity(pos);
        return entity instanceof BarrelBlockEntity barrel ? barrel : null;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id == 0 && barrel != null && barrel.startAging();
    }

    public int getDays() {
        return data.get(0);
    }

    public int getPercent() {
        return data.get(2);
    }

    public boolean isAging() {
        return data.get(1) != 0;
    }

    public boolean isReady() {
        return slots.get(2).hasItem();
    }

    @Override
    public boolean stillValid(Player player) {
        return barrel == null || stillValid(
                net.minecraft.world.inventory.ContainerLevelAccess.create(barrel.getLevel(), barrel.getBlockPos()),
                player, ModBlocks.BARREL.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(stack, MACHINE_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            if (index == OUTPUT_SLOT && barrel != null) {
                barrel.spendBottle();
            }
        } else if (!moveItemStackTo(stack, 0, OUTPUT_SLOT, false)
                && !moveItemStackTo(stack, BOTTLE_SLOT, MACHINE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        // Go through the slot: the move edited the live stack, and the block entity must learn of it to save.
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    private final class Data implements ContainerData {
        private final int[] synced = new int[3];

        @Override
        public int get(int index) {
            // The client's block entity never learns these values; only the synced copy is right there.
            if (barrel == null || barrel.getLevel() == null || barrel.getLevel().isClientSide()) {
                return synced[index];
            }
            // Menu data travels as a short: three days of raw ticks would overflow it.
            long required = barrel.getRequiredTicks();
            long elapsed = Math.min(required, barrel.getElapsedTicks());
            return switch (index) {
                case 0 -> (int) (elapsed / AgingRules.TICKS_PER_DAY);
                case 1 -> barrel.getStartedAt() >= 0 ? 1 : 0;
                case 2 -> (int) (elapsed * 100 / required);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            synced[index] = value;
        }

        @Override
        public int getCount() {
            return synced.length;
        }
    }
}
