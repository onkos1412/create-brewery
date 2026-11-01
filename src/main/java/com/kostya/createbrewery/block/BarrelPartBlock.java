package com.kostya.createbrewery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Fills the rest of a big barrel's 2x2x2 space. It draws nothing, since the barrel block's
 * own model covers all eight cells, and passes clicks and automation on to that block.
 * The properties say which cell this is, in the barrel's unrotated layout.
 */
public class BarrelPartBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty X = BooleanProperty.create("x");
    public static final BooleanProperty Y = BooleanProperty.create("y");
    public static final BooleanProperty Z = BooleanProperty.create("z");

    public BarrelPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, false).setValue(Y, false).setValue(Z, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Where the barrel block of this part stands. */
    static BlockPos controllerPos(BlockPos pos, BlockState state) {
        return pos.subtract(BarrelBlock.cell(state.getValue(FACING), state.getValue(X) ? 1 : 0,
                state.getValue(Y) ? 1 : 0, state.getValue(Z) ? 1 : 0));
    }

    /** The barrel this part belongs to, or {@code null} when it has lost it. */
    @Nullable
    public static BarrelBlockEntity controller(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(controllerPos(pos, state)) instanceof BarrelBlockEntity barrel ? barrel : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BlockPos controller = controllerPos(pos, state);
        return level.getBlockState(controller).useWithoutItem(level, player, hit.withPosition(controller));
    }

    /** In creative mode the barrel goes without dropping anything. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos controller = controllerPos(pos, state);
        if (player.getAbilities().instabuild && level.getBlockState(controller).is(ModBlocks.BARREL.get())) {
            level.destroyBlock(controller, false, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Losing any cell breaks the whole barrel, which is what drops the item and the contents. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockPos controller = controllerPos(pos, state);
            if (level.getBlockState(controller).is(ModBlocks.BARREL.get())) {
                level.destroyBlock(controller, true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
