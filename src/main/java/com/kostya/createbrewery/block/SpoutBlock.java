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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The bottling spout. Place it against a kettle and right-click while holding an empty
 * glass bottle to draw one bottle of the finished drink.
 *
 * <p>The spout finds its kettle among its six neighbours, so it can be mounted on any
 * side of the kettle.
 */
public class SpoutBlock extends Block implements EntityBlock {

    public SpoutBlock(Properties properties) {
        super(properties);
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

        KettleBlockEntity kettle = findKettle(level, pos);
        boolean hasWort = kettle != null && kettle.getWortAmount() >= KettleBlockEntity.WORT_PER_BREW;
        if (!SpoutLogic.fillBottle(hasWort, true)) {
            return ItemInteractionResult.FAIL;
        }

        ItemStack bottle = kettle.bottleWort(KettleMenu.SKELETON_STARS);
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
