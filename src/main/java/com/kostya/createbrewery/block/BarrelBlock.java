package com.kostya.createbrewery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BarrelBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Whether this barrel is the big 2x2x2 one, with {@link BarrelPartBlock}s around it. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public BarrelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    /**
     * Where a cell of the big barrel lies, from a cell position in its unrotated layout. It
     * turns the way the blockstate turns the model, which stays anchored on this block.
     */
    static BlockPos cell(Direction facing, int x, int y, int z) {
        return switch (facing) {
            case EAST -> new BlockPos(-z, y, x);
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            default -> new BlockPos(x, y, z);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    /** The head with the tap faces whoever places the barrel. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The head with the tap faces the player, and the barrel needs its whole 2x2x2 space.
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos pos = context.getClickedPos();
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos target = pos.offset(cell(facing, x, y, z));
                    if (target.getY() >= context.getLevel().getMaxBuildHeight()
                            || !context.getLevel().getBlockState(target).canBeReplaced(context)) {
                        return null;
                    }
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(FORMED, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!state.getValue(FORMED)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    if (x + y + z > 0) {
                        level.setBlock(pos.offset(cell(facing, x, y, z)), ModBlocks.BARREL_PART.get().defaultBlockState()
                                .setValue(BarrelPartBlock.FACING, facing).setValue(BarrelPartBlock.X, x == 1)
                                .setValue(BarrelPartBlock.Y, y == 1).setValue(BarrelPartBlock.Z, z == 1), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(BarrelBlock::new);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarrelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof BarrelBlockEntity barrel) {
                barrel.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
            player.openMenu(menuProvider(barrel), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
            ModCapabilities.dropContents(level, pos, barrel.getInput(), barrel.getOutput(), barrel.getBottles());
        }
        if (!state.is(newState.getBlock()) && state.getValue(FORMED)) {
            Direction facing = state.getValue(FACING);
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        BlockPos part = pos.offset(cell(facing, x, y, z));
                        if (level.getBlockState(part).is(ModBlocks.BARREL_PART.get())) {
                            level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static MenuProvider menuProvider(BarrelBlockEntity barrel) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new BarrelMenu(containerId, inventory, barrel),
                Component.translatable("container.brewery.barrel"));
    }
}
