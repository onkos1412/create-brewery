package com.kostya.createbrewery.block;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class DistilleryMenu extends AbstractContainerMenu {
    private static final int SLOT_SIZE = 18;
    private final DistilleryBlockEntity distillery;
    private final ContainerData data = new Data();

    public DistilleryMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, find(inventory, buffer.readBlockPos()));
    }

    public DistilleryMenu(int containerId, Inventory inventory, DistilleryBlockEntity distillery) {
        super(ModMenus.DISTILLERY.get(), containerId);
        this.distillery = distillery;
        addSlot(new MachineSlot(distillery == null ? new MachineInventory(1)
                : distillery.getOutput(), 0, 66, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9,
                        8 + col * SLOT_SIZE, 84 + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * SLOT_SIZE, 142));
        }
        addDataSlots(data);
    }

    private static DistilleryBlockEntity find(Inventory inventory, net.minecraft.core.BlockPos pos) {
        var entity = inventory.player.level().getBlockEntity(pos);
        return entity instanceof DistilleryBlockEntity distillery ? distillery : null;
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getHeat() {
        return data.get(1);
    }

    public int getRpm() {
        return data.get(2);
    }

    public int getWort() {
        return data.get(3);
    }

    /** How long the wort inside takes; never zero, since the screen divides by it. */
    public int getIdealTicks() {
        return Math.max(1, data.get(4));
    }

    /** The colour of the wort inside; it takes two data slots, which travel as shorts. */
    public int getWortColor() {
        return (data.get(6) & 0xFF) << 16 | data.get(7) & 0xFFFF;
    }

    /** The quality of the wort inside. */
    public int getWortStars() {
        return net.minecraft.util.Mth.clamp(data.get(8), com.kostya.createbrewery.DrinkData.MIN_STARS,
                com.kostya.createbrewery.DrinkData.MAX_STARS);
    }

    @Override
    public boolean stillValid(Player player) {
        return distillery == null || stillValid(
                net.minecraft.world.inventory.ContainerLevelAccess.create(distillery.getLevel(), distillery.getBlockPos()),
                player, ModBlocks.DISTILLERY.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return id == 0 && distillery != null && distillery.loadFromKettle();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
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
        private final int[] synced = new int[9];

        @Override
        public int get(int index) {
            // The client's block entity never learns these values; only the synced copy is right there.
            if (distillery == null || distillery.getLevel() == null || distillery.getLevel().isClientSide()) {
                return synced[index];
            }
            return switch (index) {
                case 0 -> distillery.getProgressTicks();
                case 1 -> distillery.getHeatLevel();
                case 2 -> Math.round(distillery.getRpm());
                case 3 -> distillery.getWortAmount();
                case 4 -> distillery.getProfile().distillTicks();
                case 6 -> distillery.getProfile().color() >>> 16;
                case 7 -> distillery.getProfile().color() & 0xFFFF;
                case 8 -> distillery.getInputStars();
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
