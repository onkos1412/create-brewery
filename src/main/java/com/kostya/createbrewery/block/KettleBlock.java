package com.kostya.createbrewery.block;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BREWING, false);
    }

    /** Whoever places the kettle owns it, and signs what it brews. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && !(player instanceof FakePlayer) && level.getBlockEntity(pos) instanceof KettleBlockEntity kettle) {
            kettle.setOwner(player.getUUID(), player.getGameProfile().getName());
        }
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

    /**
     * A water bucket fills the kettle; anything else opens it. (1.21.1 split this into {@code useItemOn} and
     * {@code useWithoutItem}; 1.20.1 has the one {@code use}.)
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level.getBlockEntity(pos) instanceof KettleBlockEntity kettle)) {
            return InteractionResult.PASS;
        }
        if (stack.is(Items.WATER_BUCKET)) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            int accepted = kettle.fillWater(1000);
            if (accepted > 0 && !player.getAbilities().instabuild) {
                // The bucket is spent even on a partial pour so water cannot be duplicated.
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ModMenus.open(serverPlayer, pos, Component.translatable("container.brewery.kettle"),
                    (containerId, inventory, opener) -> new KettleMenu(containerId, inventory, kettle));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof KettleBlockEntity kettle) {
            ModCapabilities.dropContents(level, pos, kettle.getInput());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
