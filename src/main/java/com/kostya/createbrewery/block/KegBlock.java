package com.kostya.createbrewery.block;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.keg.KegRules;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A small keg on a stand, its tap toward whoever placed it. Use a drink bottle on it to pour the bottle in; a
 * window on its side shows how full it is (see {@link KegBlockEntity}).
 */
public class KegBlock extends Block implements EntityBlock {
    /** The direction the tap points. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** How high the drink stands in the side window, 0 to {@link KegRules#WINDOW_STEPS}. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, KegRules.WINDOW_STEPS);

    private static final VoxelShape ALONG_Z = Block.box(2, 0, 1, 14, 14, 15);
    private static final VoxelShape ALONG_X = Block.box(1, 0, 2, 15, 14, 14);

    public KegBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEVEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ALONG_Z : ALONG_X;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KegBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModItems.MUG.get())) {
            return pourMug(stack, level, pos, player, hand);
        }
        if (!stack.is(ModItems.DRINK_BOTTLE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof KegBlockEntity keg) || !keg.pourIn(stack, false)) {
            return ItemInteractionResult.FAIL;
        }
        // Like filling a cauldron: the bottle comes back empty, and a creative player keeps theirs.
        player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        return ItemInteractionResult.SUCCESS;
    }

    /** An empty mug takes one portion: a mug of the same drink, quality and signature included. */
    private static ItemInteractionResult pourMug(ItemStack mug, Level level, BlockPos pos, Player player,
                                                 InteractionHand hand) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof KegBlockEntity keg)) {
            return ItemInteractionResult.FAIL;
        }
        ItemStack poured = keg.pourOut();
        if (poured.isEmpty()) {
            return ItemInteractionResult.FAIL;
        }
        ItemStack filled = new ItemStack(ModItems.DRINK_MUG.get());
        filled.set(ModDataComponents.DRINK_DATA.get(), poured.get(ModDataComponents.DRINK_DATA.get()));
        BrewerMark mark = poured.get(ModDataComponents.BREWER.get());
        if (mark != null) {
            filled.set(ModDataComponents.BREWER.get(), mark);
        }
        player.setItemInHand(hand, ItemUtils.createFilledResult(mug, player, filled));
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        return ItemInteractionResult.SUCCESS;
    }

    /** A broken keg spills its portions as bottles, and the empty glass it kept. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof KegBlockEntity keg) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                    keg.getDrink().copyWithCount(keg.getPortions()));
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                    new ItemStack(Items.GLASS_BOTTLE, keg.getEmpties()));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends Block> codec() {
        return simpleCodec(KegBlock::new);
    }
}
