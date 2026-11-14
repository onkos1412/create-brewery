package com.kostya.createbrewery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The spout's automatic side: empty bottles fed into slot 0 are filled from the machine
 * it is mounted on, one at a time, and wait in slot 1 to be taken away. Right-clicking
 * with a bottle goes straight to the machine and does not involve this block entity.
 */
public class SpoutBlockEntity extends BlockEntity {
    private static final int EMPTY_SLOT = 0;
    private static final int FILLED_SLOT = 1;

    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == EMPTY_SLOT && stack.is(Items.GLASS_BOTTLE);
        }

        /** Empty bottles only leave by being filled, so a funnel cannot pull them straight back out. */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == FILLED_SLOT ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    };

    public SpoutBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SPOUT_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return items;
    }

    /** One server tick: pours a drink into the next empty bottle, if the machine has one ready. */
    public void serverTick() {
        ItemStack empty = items.getStackInSlot(EMPTY_SLOT);
        if (level == null || empty.isEmpty() || !items.getStackInSlot(FILLED_SLOT).isEmpty()) {
            return;
        }
        ItemStack drink = SpoutBlock.pour(level, worldPosition);
        if (!drink.isEmpty()) {
            items.setStackInSlot(FILLED_SLOT, drink);
            items.setStackInSlot(EMPTY_SLOT, empty.copyWithCount(empty.getCount() - 1));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
    }
}
