package com.kostya.createbrewery.block;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.keg.KegRules;
import com.kostya.createbrewery.quality.StarLine;
import com.kostya.createbrewery.registry.ModItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A keg on the bar: up to {@link KegRules#CAPACITY} portions of one drink, poured in a bottle at a time and poured
 * out a portion at a time. Bottles fed in by automation leave their empty glass inside, to be taken from below.
 */
public class KegBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final String INDENT = "    ";

    /** One bottle of what the keg holds, or empty when it holds nothing. */
    private ItemStack drink = ItemStack.EMPTY;
    private int portions;
    private int empties;

    private final IItemHandler input = new Input();
    private final IItemHandler output = new Output();
    private final LazyOptional<IItemHandler> inputCapability = LazyOptional.of(() -> input);
    private final LazyOptional<IItemHandler> outputCapability = LazyOptional.of(() -> output);

    public KegBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.KEG_BLOCK_ENTITY.get(), pos, state);
    }

    /** One bottle of the drink inside, count 1, or empty. */
    public ItemStack getDrink() {
        return drink.copy();
    }

    public int getPortions() {
        return portions;
    }

    public int getEmpties() {
        return empties;
    }

    /**
     * Pours one drink bottle in. The bottle itself is left alone: the caller takes it from the player or the
     * conveyor. With {@code keepEmpty} the empty glass stays in the keg, which then needs room for it.
     *
     * @return whether the keg took the drink
     */
    public boolean pourIn(ItemStack bottle, boolean keepEmpty) {
        if (!canTake(bottle, keepEmpty)) {
            return false;
        }
        if (portions == 0) {
            drink = portion(bottle);
        }
        portions++;
        if (keepEmpty) {
            empties++;
        }
        changed();
        return true;
    }

    /** Takes one portion out as a bottle of the drink, or empty when the keg is empty. */
    public ItemStack pourOut() {
        if (portions <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack poured = drink.copy();
        portions--;
        if (portions == 0) {
            drink = ItemStack.EMPTY;
        }
        changed();
        return poured;
    }

    /** Bottles come in from the top and sides; the empty glass goes out below. */
    public IItemHandler getItemHandler(@Nullable Direction side) {
        return side == Direction.DOWN ? output : input;
    }

    /** As {@link #getItemHandler}: below only takes the empties out, every other side (and none) pours in. */
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return (side == Direction.DOWN ? outputCapability : inputCapability).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inputCapability.invalidate();
        outputCapability.invalidate();
    }

    /** Whether a bottle would be taken by hand: the same drink, with room left. */
    public boolean accepts(ItemStack bottle) {
        return canTake(bottle, false);
    }

    private boolean canTake(ItemStack bottle, boolean keepEmpty) {
        if (!bottle.is(ModItems.DRINK_BOTTLE.get()) || !DrinkData.has(bottle)) {
            return false;
        }
        return KegRules.accepts(portions, sameDrink(bottle), keepEmpty ? empties : 0);
    }

    /** The same drink: equal quality, strength and stage, and the same signature or none on both. */
    private boolean sameDrink(ItemStack bottle) {
        return Objects.equals(DrinkData.get(drink), DrinkData.get(bottle))
                && Objects.equals(BrewerMark.get(drink), BrewerMark.get(bottle));
    }

    /** A clean bottle carrying only what makes the drink, so a renamed bottle passes no name on to every portion. */
    private static ItemStack portion(ItemStack bottle) {
        ItemStack portion = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkData.set(portion, DrinkData.get(bottle));
        BrewerMark mark = BrewerMark.get(bottle);
        if (mark != null) {
            BrewerMark.set(portion, mark);
        }
        return portion;
    }

    /** Saves, shows the new level in the window and tells watching clients. */
    private void changed() {
        setChanged();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        int window = KegRules.windowLevel(portions);
        if (state.hasProperty(KegBlock.LEVEL) && state.getValue(KegBlock.LEVEL) != window) {
            state = state.setValue(KegBlock.LEVEL, window);
            level.setBlock(worldPosition, state, Block.UPDATE_ALL);
        }
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal(INDENT).append(Component.translatable("block.brewery.keg")));
        if (portions <= 0) {
            tooltip.add(Component.literal(INDENT).append(Component.translatable("gui.brewery.keg.empty")
                    .withStyle(ChatFormatting.GRAY)));
            return true;
        }
        DrinkData data = DrinkData.get(drink);
        if (data != null) {
            tooltip.add(Component.literal(INDENT).append(Component.translatable("drink.brewery." + data.stage())
                    .withStyle(ChatFormatting.GRAY)));
            tooltip.add(Component.literal(INDENT).append(StarLine.of(data.stars())));
        }
        tooltip.add(Component.literal(INDENT).append(Component.translatable("gui.brewery.keg.portions",
                portions, KegRules.CAPACITY).withStyle(ChatFormatting.GOLD)));
        BrewerMark mark = BrewerMark.get(drink);
        if (mark != null) {
            tooltip.add(Component.literal(INDENT).append(mark.tooltip()));
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!drink.isEmpty()) {
            tag.put("Drink", drink.save(new CompoundTag()));
        }
        tag.putInt("Portions", portions);
        tag.putInt("Empties", empties);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        drink = tag.contains("Drink") ? ItemStack.of(tag.getCompound("Drink")) : ItemStack.EMPTY;
        portions = Math.max(0, Math.min(KegRules.CAPACITY, tag.getInt("Portions")));
        empties = Math.max(0, Math.min(KegRules.EMPTIES, tag.getInt("Empties")));
        if (drink.isEmpty()) {
            portions = 0;
        }
        if (portions == 0) {
            drink = ItemStack.EMPTY;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Drink bottles go in, a portion each; nothing comes out this way. */
    private final class Input implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !canTake(stack, true)) {
                return stack;
            }
            int room = Math.min(KegRules.CAPACITY - portions, KegRules.EMPTIES - empties);
            int taken = Math.min(room, stack.getCount());
            if (!simulate) {
                for (int i = 0; i < taken; i++) {
                    pourIn(stack, true);
                }
            }
            return stack.copyWithCount(stack.getCount() - taken);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return KegRules.CAPACITY;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.DRINK_BOTTLE.get());
        }
    }

    /** The empty glass left by automated filling; nothing goes in this way. */
    private final class Output implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return empties > 0 ? new ItemStack(Items.GLASS_BOTTLE, empties) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int taken = Math.min(amount, empties);
            if (taken <= 0) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                empties -= taken;
                changed();
            }
            return new ItemStack(Items.GLASS_BOTTLE, taken);
        }

        @Override
        public int getSlotLimit(int slot) {
            return KegRules.EMPTIES;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
