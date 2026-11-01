package com.kostya.createbrewery.block;

import com.kostya.createbrewery.CreateBrewery;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

/** Exposes the machines to Create's pipes and funnels, and to any other automation. */
@EventBusSubscriber(modid = CreateBrewery.MOD_ID)
public final class ModCapabilities {
    private static final double STILL_STRESS = 4.0;

    private ModCapabilities() {
    }

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlocks.KETTLE_BLOCK_ENTITY.get(),
                (kettle, side) -> kettle.getFluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlocks.KETTLE_BLOCK_ENTITY.get(),
                (kettle, side) -> kettle.getInput());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlocks.DISTILLERY_BLOCK_ENTITY.get(),
                (distillery, side) -> distillery.getFluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlocks.DISTILLERY_BLOCK_ENTITY.get(),
                (distillery, side) -> distillery.getOutput());
        // Pipes and funnels on the distillery's column reach the machine below it.
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, entity, side) ->
                level.getBlockEntity(pos.below()) instanceof DistilleryBlockEntity still ? still.getFluidHandler() : null,
                ModBlocks.DISTILLERY_TOP.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, entity, side) ->
                level.getBlockEntity(pos.below()) instanceof DistilleryBlockEntity still ? still.getOutput() : null,
                ModBlocks.DISTILLERY_TOP.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, entity, side) -> {
            BarrelBlockEntity barrel = BarrelPartBlock.controller(level, pos, state);
            return barrel == null ? null : barrel.getAutomationItems();
        }, ModBlocks.BARREL_PART.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlocks.BARREL_BLOCK_ENTITY.get(),
                (barrel, side) -> barrel.getAutomationItems());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlocks.SPOUT_BLOCK_ENTITY.get(),
                (spout, side) -> spout.getItems());
    }

    /** The distillery loads a network as much as a millstone does. */
    @SubscribeEvent
    public static void registerStress(FMLCommonSetupEvent event) {
        BlockStressValues.IMPACTS.register(ModBlocks.DISTILLERY.get(), () -> STILL_STRESS);
    }

    /** Spills a machine's inventories into the world when its block is removed. */
    static void dropContents(Level level, BlockPos pos, IItemHandler... inventories) {
        for (IItemHandler inventory : inventories) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        inventory.getStackInSlot(slot).copy());
            }
        }
    }
}
