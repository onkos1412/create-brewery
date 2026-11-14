package com.kostya.createbrewery.block;

import io.github.fabricators_of_create.porting_lib.transfer.item.ItemStackHandler;
import io.github.fabricators_of_create.porting_lib.transfer.item.ItemStackHandlerSlot;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.world.item.ItemStack;

/**
 * A machine's item slots: Porting Lib's {@link ItemStackHandler}, which is a Transfer API storage whose slots take
 * part in transactions, plus the rules Forge's {@code ItemStackHandler} let a machine override per slot (what may go
 * in, what may come out) and Forge's one-slot {@code insertItem}/{@code extractItem}, so the machines' own logic
 * reads as it did on Forge. Those two are for the machine itself: like {@link #setStackInSlot} they change the slot
 * at once, outside any transaction (a barrel regrouping in the middle of a funnel's simulation must not have its
 * moves rolled back halfway). Pipes and funnels go through the slots, which take part in their transactions.
 */
public class MachineInventory extends ItemStackHandler {

    public MachineInventory(int slots) {
        super(slots);
    }

    /** Whether {@code stack} may be put into {@code slot}; Forge's {@code isItemValid}. */
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    /** Whether anything may be taken out of {@code slot}; Forge machines refused in {@code extractItem}. */
    protected boolean canExtract(int slot) {
        return true;
    }

    @Override
    public boolean isItemValid(int slot, ItemVariant resource, int count) {
        return isItemValid(slot, resource.toStack(Math.max(1, count)));
    }

    @Override
    protected ItemStackHandlerSlot makeSlot(int index, ItemStack stack) {
        return new ItemStackHandlerSlot(index, this, stack) {
            @Override
            protected boolean canExtract(ItemVariant variant) {
                return MachineInventory.this.canExtract(index);
            }
        };
    }

    /**
     * Puts {@code stack} into one slot, as Forge's {@code insertItem} did, under the same rules as a funnel.
     *
     * @return what did not fit
     */
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!isItemValid(slot, stack)) {
            return stack;
        }
        ItemStack held = getStackInSlot(slot);
        int room = getStackLimit(slot, ItemVariant.of(stack));
        if (!held.isEmpty()) {
            if (!ItemStack.isSameItemSameTags(held, stack)) {
                return stack;
            }
            room -= held.getCount();
        }
        int moved = Math.min(Math.max(0, room), stack.getCount());
        if (moved <= 0) {
            return stack;
        }
        if (!simulate) {
            setStackInSlot(slot, (held.isEmpty() ? stack : held).copyWithCount(held.getCount() + moved));
        }
        return stack.copyWithCount(stack.getCount() - moved);
    }

    /**
     * Takes up to {@code amount} items out of one slot, as Forge's {@code extractItem} did, under the same rules as
     * a funnel.
     *
     * @return what was taken
     */
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack held = getStackInSlot(slot);
        if (amount <= 0 || held.isEmpty() || !canExtract(slot)) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(Math.min(amount, held.getCount()), held.getMaxStackSize());
        if (!simulate) {
            int left = held.getCount() - taken;
            setStackInSlot(slot, left > 0 ? held.copyWithCount(left) : ItemStack.EMPTY);
        }
        return held.copyWithCount(taken);
    }
}
