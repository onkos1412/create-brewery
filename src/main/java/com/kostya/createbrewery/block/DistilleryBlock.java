package com.kostya.createbrewery.block;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/** A kinetic machine: its only shaft enters the back, opposite the side facing the player who placed it. */
public class DistilleryBlock extends HorizontalKineticBlock implements IBE<DistilleryBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public DistilleryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public Class<DistilleryBlockEntity> getBlockEntityClass() {
        return DistilleryBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DistilleryBlockEntity> getBlockEntityType() {
        return ModBlocks.DISTILLERY_BLOCK_ENTITY.get();
    }

    /** The gauge faces whoever places the still; the kinetic input is at the back. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The column needs room above; without it the still cannot be placed at all.
        BlockPos above = context.getClickedPos().above();
        if (above.getY() >= context.getLevel().getMaxBuildHeight()
                || !context.getLevel().getBlockState(above).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        level.setBlock(pos.above(), ModBlocks.DISTILLERY_TOP.get().defaultBlockState()
                .setValue(DistilleryTopBlock.FACING, state.getValue(FACING)), Block.UPDATE_ALL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof DistilleryBlockEntity distillery) {
            NetworkHooks.openScreen(serverPlayer, menuProvider(distillery), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DistilleryBlockEntity distillery) {
            ModCapabilities.dropContents(level, pos, distillery.getOutput());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static MenuProvider menuProvider(DistilleryBlockEntity distillery) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new DistilleryMenu(containerId, inventory, distillery),
                Component.translatable("container.brewery.distillery"));
    }
}
