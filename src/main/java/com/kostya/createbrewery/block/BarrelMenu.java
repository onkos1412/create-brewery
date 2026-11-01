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
    private static final int BOTTLE_SLOT = 3;
    private static final int FIRST_EXTRA_DRINK = 7;
    private static final int MACHINE_SLOTS = 10;
    private static final double MAX_REACH_SQUARED = 32 * 32;

    private final BarrelBlockEntity barrel;
    private final ContainerData data = new Data();

    public BarrelMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, find(inventory, buffer.readBlockPos()));
    }

    public BarrelMenu(int containerId, Inventory inventory, BarrelBlockEntity barrel) {
        super(ModMenus.BARREL.get(), containerId);
        this.barrel = barrel;
        var handler = barrel == null ? new net.neoforged.neoforge.items.ItemStackHandler(5) : barrel.getInput();
        var drinks = barrel == null ? new net.neoforged.neoforge.items.ItemStackHandler(4) : barrel.getOutput();
        addSlot(new BatchSlot(handler, 0, 44, 35, 0));
        addSlot(new SlotItemHandler(handler, 1, 80, 35));
        addSlot(new DrinkSlot(drinks, 0, 116, 35));
        addSlot(new SlotItemHandler(barrel == null ? new net.neoforged.neoforge.items.ItemStackHandler(1)
                : barrel.getBottles(), 0, 152, 35));
        addSlot(new BatchSlot(handler, 2, 26, 35, 1));
        addSlot(new BatchSlot(handler, 3, 26, 17, 2));
        addSlot(new BatchSlot(handler, 4, 44, 17, 3));
        addSlot(new DrinkSlot(drinks, 1, 134, 35));
        addSlot(new DrinkSlot(drinks, 2, 116, 17));
        addSlot(new DrinkSlot(drinks, 3, 134, 17));
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
        for (int slot = 0; slot < MACHINE_SLOTS; slot++) {
            if (BarrelBlockEntity.drinkSlot(slot) >= 0 && slots.get(slot).hasItem()) {
                return true;
            }
        }
        return false;
    }

    /** A distillate stack, shown only once the group has barrels enough to open it. */
    private final class BatchSlot extends SlotItemHandler {
        private final int batch;

        BatchSlot(net.neoforged.neoforge.items.IItemHandler handler, int index, int x, int y, int batch) {
            super(handler, index, x, y);
            this.batch = batch;
        }

        @Override
        public boolean isActive() {
            return batch < data.get(3);
        }
    }

    /** Taking drinks here costs an empty bottle each, the same as at a funnel or the spout. */
    private final class DrinkSlot extends SlotItemHandler {
        private final int drinkIndex;

        DrinkSlot(net.neoforged.neoforge.items.IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
            drinkIndex = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isActive() {
            return drinkIndex < data.get(3);
        }

        @Override
        public boolean mayPickup(Player player) {
            int taken = Math.min(getItem().getCount(), getItem().getMaxStackSize());
            return (barrel == null || barrel.getBottleCount() >= taken) && super.mayPickup(player);
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            if (barrel != null) {
                barrel.spendBottles(stack.getCount());
            }
            super.onTake(player, stack);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        // Measured from the whole group's middle would be nicer, but the menu may be held open
        // from any of its barrels, so this only has to be generous enough for the largest one.
        return barrel == null || !barrel.isRemoved()
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(barrel.getBlockPos())) <= MAX_REACH_SQUARED;
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
            if (BarrelBlockEntity.drinkSlot(index) >= 0 && barrel != null) {
                barrel.spendBottles(original.getCount() - stack.getCount());
            }
        } else if (!moveItemStackTo(stack, 0, 2, false)
                && !moveItemStackTo(stack, BOTTLE_SLOT, FIRST_EXTRA_DRINK, false)) {
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
        private final int[] synced = new int[4];

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
                case 3 -> barrel.openStacks();
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
