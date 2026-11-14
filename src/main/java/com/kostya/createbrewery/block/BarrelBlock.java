package com.kostya.createbrewery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * One barrel. Touching barrels that face the same way work as one bigger barrel: see
 * {@link BarrelBlockEntity#root()}. Along their axis they also join up visually.
 */
public class BarrelBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Whether another barrel continues this one past its tap end, so that head is not drawn. */
    public static final BooleanProperty OPEN_FRONT = BooleanProperty.create("open_front");
    /** Whether another barrel continues this one past its back end. */
    public static final BooleanProperty OPEN_BACK = BooleanProperty.create("open_back");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public BarrelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(OPEN_FRONT, false).setValue(OPEN_BACK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN_FRONT, OPEN_BACK);
    }

    /**
     * The tap faces whoever places the barrel, unless it is placed against another barrel,
     * which it then lines up with so that the two join.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState against = context.getLevel().getBlockState(
                context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        Direction facing = against.is(this)
                ? against.getValue(FACING)
                : context.getHorizontalDirection().getOpposite();
        return connected(defaultBlockState().setValue(FACING, facing), context.getLevel(), context.getClickedPos());
    }

    private BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(OPEN_FRONT, sameBarrel(level.getBlockState(pos.relative(facing)), facing))
                .setValue(OPEN_BACK, sameBarrel(level.getBlockState(pos.relative(facing.getOpposite())), facing));
    }

    private boolean sameBarrel(BlockState other, Direction facing) {
        return other.is(this) && other.getValue(FACING) == facing;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return direction.getAxis() == state.getValue(FACING).getAxis() ? connected(state, level, pos) : state;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
            BarrelBlockEntity root = barrel.root();
            NetworkHooks.openScreen(serverPlayer, menuProvider(root), root.getBlockPos());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock())) {
            BarrelBlockEntity.regroupAround(level, pos, state.getValue(FACING));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
            barrel.leaveGroup(state.getValue(FACING));
            ModCapabilities.dropContents(level, pos, barrel.getInput(), barrel.getOutput(), barrel.getBottles());
            BarrelBlockEntity.regroupAround(level, pos, state.getValue(FACING));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static MenuProvider menuProvider(BarrelBlockEntity barrel) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new BarrelMenu(containerId, inventory, barrel),
                Component.translatable("container.brewery.barrel"));
    }
}
