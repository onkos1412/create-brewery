package com.kostya.createbrewery.block;

import io.github.fabricators_of_create.porting_lib.transfer.TransferUtil;
import io.github.fabricators_of_create.porting_lib.transfer.item.SlotItemHandler;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.entity.player.Player;

/**
 * A menu slot over one of a machine's item slots. Forge's {@code SlotItemHandler} let a player pick up only what the
 * handler would give out (a sealed barrel batch stays put); Porting Lib's only checks the slot is not empty, so this
 * asks the slot, in a transaction that is thrown away, whether it would give one item.
 */
class MachineSlot extends SlotItemHandler {

    MachineSlot(MachineInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Override
    public boolean mayPickup(Player player) {
        if (!super.mayPickup(player)) {
            return false;
        }
        SingleSlotStorage<ItemVariant> slot = getItemHandler().getSlot(getContainerSlot());
        try (Transaction simulation = TransferUtil.getTransaction()) {
            return slot.extract(slot.getResource(), 1, simulation) > 0;
        }
    }
}
