package com.kostya.createbrewery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The distillery column's block entity. It stores nothing: pipes and funnels on the column get the
 * storages of the machine below it (see {@link ModCapabilities}). 1.21.1 did this with a block capability on the
 * column; Fabric's block lookups could too, but the block entity is kept so the column works as on 1.20.1 Forge.
 */
public class DistilleryTopBlockEntity extends BlockEntity {

    public DistilleryTopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DISTILLERY_TOP_BLOCK_ENTITY.get(), pos, state);
    }

    /** The machine this column belongs to, or {@code null} when it stands alone for a moment. */
    @Nullable
    DistilleryBlockEntity still() {
        return level != null && level.getBlockEntity(worldPosition.below()) instanceof DistilleryBlockEntity still
                ? still : null;
    }
}
