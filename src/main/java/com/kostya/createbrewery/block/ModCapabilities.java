package com.kostya.createbrewery.block;

import com.simibubi.create.api.stress.BlockStressValues;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Exposes the machines to Create's pipes and funnels, and to any other automation, through the Fabric Transfer API
 * (Forge's capabilities on 1.20.1 Forge). The sides are as on Forge: the keg pours in from every side and from no
 * side, and gives its empty glass out only below; every other machine is the same from every side, and the
 * distillery's column hands everything on to the still below it.
 */
public final class ModCapabilities {
    private static final double STILL_STRESS = 4.0;

    private ModCapabilities() {
    }

    /** Called once the block entity types are registered. */
    public static void register() {
        FluidStorage.SIDED.registerForBlockEntity((kettle, side) -> kettle.getFluidStorage(),
                ModBlocks.KETTLE_BLOCK_ENTITY.get());
        ItemStorage.SIDED.registerForBlockEntity((kettle, side) -> kettle.getInput(),
                ModBlocks.KETTLE_BLOCK_ENTITY.get());

        FluidStorage.SIDED.registerForBlockEntity((still, side) -> still.getFluidStorage(),
                ModBlocks.DISTILLERY_BLOCK_ENTITY.get());
        ItemStorage.SIDED.registerForBlockEntity((still, side) -> still.getOutput(),
                ModBlocks.DISTILLERY_BLOCK_ENTITY.get());
        FluidStorage.SIDED.registerForBlockEntity((column, side) -> {
            DistilleryBlockEntity still = column.still();
            return still == null ? null : still.getFluidStorage();
        }, ModBlocks.DISTILLERY_TOP_BLOCK_ENTITY.get());
        ItemStorage.SIDED.registerForBlockEntity((column, side) -> {
            DistilleryBlockEntity still = column.still();
            return still == null ? null : still.getOutput();
        }, ModBlocks.DISTILLERY_TOP_BLOCK_ENTITY.get());

        ItemStorage.SIDED.registerForBlockEntity((barrel, side) -> barrel.getAutomationItems(),
                ModBlocks.BARREL_BLOCK_ENTITY.get());
        ItemStorage.SIDED.registerForBlockEntity((spout, side) -> spout.getItems(),
                ModBlocks.SPOUT_BLOCK_ENTITY.get());
        ItemStorage.SIDED.registerForBlockEntity(KegBlockEntity::getItemHandler, ModBlocks.KEG_BLOCK_ENTITY.get());

        // Lets Create's spout, and anything else that fills containers, pour wort into an empty glass bottle.
        // The combined provider keeps Fabric's own water bottle working beside it.
        FluidStorage.combinedItemApiProvider(Items.GLASS_BOTTLE).register(WortBottling.BottleStorage::new);

        // The distillery loads a network as much as a millstone does.
        BlockStressValues.IMPACTS.register(ModBlocks.DISTILLERY.get(), () -> STILL_STRESS);
    }

    /** Spills a machine's inventories into the world when its block is removed. */
    static void dropContents(Level level, BlockPos pos, MachineInventory... inventories) {
        for (MachineInventory inventory : inventories) {
            for (int slot = 0; slot < inventory.getSlotCount(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        inventory.getStackInSlot(slot).copy());
            }
        }
    }
}
