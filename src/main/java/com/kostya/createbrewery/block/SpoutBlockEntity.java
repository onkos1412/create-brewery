package com.kostya.createbrewery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The spout is stateless in the skeleton: it reads directly from an adjacent kettle on
 * use. The block entity exists so the block has a stable identity for later tank
 * support and for the spout's render-time lookup.
 */
public class SpoutBlockEntity extends BlockEntity {

    public SpoutBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SPOUT_BLOCK_ENTITY.get(), pos, state);
    }

    /** The adjacent kettle, or {@code null} when none is connected. */
    public KettleBlockEntity kettle() {
        if (level == null) {
            return null;
        }
        return SpoutBlock.findKettle(level, worldPosition);
    }
}
