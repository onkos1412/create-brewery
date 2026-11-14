package com.kostya.createbrewery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The spout's automatic side: empty bottles fed into slot 0 are filled from the machine
 * it is mounted on, one at a time, and wait in slot 1 to be taken away. Right-clicking
 * with a bottle goes straight to the machine and does not involve this block entity.
 */
public class SpoutBlockEntity extends BlockEntity {
    private static final int EMPTY_SLOT = 0;
    private static final int FILLED_SLOT = 1;

    /** Funnels and hoppers reach both slots from every side (see {@link ModCapabilities}). */
    private final MachineInventory items = new MachineInventory(2) {
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
        protected boolean canExtract(int slot) {
            return slot == FILLED_SLOT;
        }
    };

    public SpoutBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SPOUT_BLOCK_ENTITY.get(), pos, state);
    }

    public MachineInventory getItems() {
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
            items.setStackInSlot(EMPTY_SLOT, empty.getCount() > 1 ? empty.copyWithCount(empty.getCount() - 1)
                    : ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
    }
}
