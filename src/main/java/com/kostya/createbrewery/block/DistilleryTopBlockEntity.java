package com.kostya.createbrewery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The distillery column's block entity. It stores nothing: pipes and funnels on the column get the
 * capabilities of the machine below it. 1.21.1 did this with a block capability on the column, which
 * 1.20.1 does not have.
 */
public class DistilleryTopBlockEntity extends BlockEntity {

    public DistilleryTopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DISTILLERY_TOP_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (level != null && level.getBlockEntity(worldPosition.below()) instanceof DistilleryBlockEntity still) {
            LazyOptional<T> forwarded = still.getMachineCapability(cap);
            if (forwarded.isPresent()) {
                return forwarded;
            }
        }
        return super.getCapability(cap, side);
    }
}
