package com.kostya.createbrewery.client;

import com.kostya.createbrewery.block.DistilleryBlock;
import com.kostya.createbrewery.block.DistilleryBlockEntity;
import com.kostya.createbrewery.block.ModBlocks;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Spins the half shaft in the distillery's back. Create draws it through Flywheel when that
 * can run, and through this renderer when it cannot, so both are registered.
 */
final class DistilleryRenderer extends KineticBlockEntityRenderer<DistilleryBlockEntity> {

    DistilleryRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    private static Direction back(BlockState state) {
        return state.getValue(DistilleryBlock.FACING).getOpposite();
    }

    @Override
    protected SuperByteBuffer getRotatedModel(DistilleryBlockEntity still, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, back(state));
    }

    static void registerVisual() {
        SimpleBlockEntityVisualizer.builder(ModBlocks.DISTILLERY_BLOCK_ENTITY.get())
                .factory((context, still, partialTick) -> new OrientedRotatingVisual<>(context, still, partialTick,
                        Direction.SOUTH, back(still.getBlockState()), Models.partial(AllPartialModels.SHAFT_HALF)))
                .skipVanillaRender(still -> false)
                .apply();
    }
}
