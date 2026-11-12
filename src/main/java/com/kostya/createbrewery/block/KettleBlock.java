package com.kostya.createbrewery.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The brewing kettle. Brews wort from water and ingredients while a heat source sits
 * directly beneath it.
 *
 * <p>Right-clicking opens the kettle menu; the {@code brewing} blockstate drives the
 * particle/animation state on the client.
 */
public class KettleBlock extends BaseEntityBlock {

    /** True while the kettle is brewing anything, muddy brew included: a mix, heat, water and room for the wort. */
    public static final BooleanProperty BREWING = BooleanProperty.create("brewing");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public KettleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BREWING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BREWING);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(KettleBlock::new);
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BREWING, false);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KettleBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof KettleBlockEntity kettle) {
                kettle.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof KettleBlockEntity kettle) {
            player.openMenu(menuProvider(kettle), pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
                                                                BlockState state, Level level, BlockPos pos,
                                                                Player player,
                                                                net.minecraft.world.InteractionHand hand,
                                                                BlockHitResult hit) {
        if (stack.is(net.minecraft.world.item.Items.WATER_BUCKET)
                && level.getBlockEntity(pos) instanceof KettleBlockEntity kettle) {
            if (level.isClientSide()) {
                return net.minecraft.world.ItemInteractionResult.SUCCESS;
            }
            int accepted = kettle.fillWater(1000);
            if (accepted > 0 && !player.getAbilities().instabuild) {
                // The bucket is spent even on a partial pour so water cannot be duplicated.
                player.setItemInHand(hand, new net.minecraft.world.item.ItemStack(
                        net.minecraft.world.item.Items.BUCKET));
            }
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof KettleBlockEntity kettle) {
            ModCapabilities.dropContents(level, pos, kettle.getInput());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static MenuProvider menuProvider(KettleBlockEntity kettle) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new KettleMenu(containerId, inventory, kettle),
                Component.translatable("container.brewery.kettle"));
    }
}
