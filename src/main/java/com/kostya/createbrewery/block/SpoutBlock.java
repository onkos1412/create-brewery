package com.kostya.createbrewery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The bottling spout. Place it against a machine and right-click while holding an empty
 * glass bottle to draw one bottle of the finished drink, or feed it empty bottles by
 * funnel and take the filled ones away (see {@link SpoutBlockEntity}).
 *
 * <p>The spout finds its kettle among its six neighbours, so it can be mounted on any
 * side of the kettle. {@code facing} is the direction the tap points, away from the
 * block it is mounted on, and drives the block model.
 */
public class SpoutBlock extends Block implements EntityBlock {

    /** The direction the tap points, away from its mounting block. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SpoutBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> Block.box(0, 1, 3, 13, 14, 13);
            case SOUTH -> Block.box(3, 1, 0, 13, 14, 13);
            case WEST -> Block.box(3, 1, 3, 16, 14, 13);
            default -> Block.box(3, 1, 3, 13, 14, 16);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        // Mounted on a wall the tap points away from that wall. On a floor or ceiling
        // there is no wall to align to, so point it the way the player is looking.
        Direction facing = clickedFace.getAxis().isHorizontal()
                ? clickedFace
                : context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpoutBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.GLASS_BOTTLE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        ItemStack bottle = pour(level, pos);
        if (bottle.isEmpty()) {
            return ItemInteractionResult.FAIL;
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (!player.getInventory().add(bottle)) {
            player.drop(bottle, false);
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        return ItemInteractionResult.SUCCESS;
    }

    /** Takes one filled bottle from a neighbouring machine: barrel first, then still, then kettle. */
    static ItemStack pour(Level level, BlockPos pos) {
        BarrelBlockEntity barrel = findBarrel(level, pos);
        ItemStack bottle = barrel == null ? ItemStack.EMPTY : barrel.takeFinishedBottle();
        if (bottle.isEmpty() && findDistillery(level, pos) instanceof DistilleryBlockEntity still) {
            bottle = still.takeFinishedBottle();
        }
        if (bottle.isEmpty() && findKettle(level, pos) instanceof KettleBlockEntity kettle) {
            bottle = kettle.bottleWort();
        }
        return bottle;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof SpoutBlockEntity spout) {
                spout.serverTick();
            }
        };
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SpoutBlockEntity spout) {
            ModCapabilities.dropContents(level, pos, spout.getItems());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** The barrel this spout pours from, or {@code null} when there is none adjacent. */
    @Nullable
    static BarrelBlockEntity findBarrel(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(pos.relative(direction));
            if (neighbour instanceof BarrelBlockEntity barrel) {
                return barrel;
            }
        }
        return null;
    }

    /** The still this spout pours from, or {@code null} when there is none adjacent. */
    @Nullable
    static DistilleryBlockEntity findDistillery(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(pos.relative(direction));
            if (neighbour instanceof DistilleryBlockEntity still) {
                return still;
            }
        }
        return null;
    }

    /** The kettle this spout pours from, or {@code null} when there is none adjacent. */
    @Nullable
    static KettleBlockEntity findKettle(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(pos.relative(direction));
            if (neighbour instanceof KettleBlockEntity kettle) {
                return kettle;
            }
        }
        return null;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends Block> codec() {
        return simpleCodec(SpoutBlock::new);
    }
}
