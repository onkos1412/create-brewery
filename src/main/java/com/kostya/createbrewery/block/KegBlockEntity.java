package com.kostya.createbrewery.block;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.keg.KegRules;
import com.kostya.createbrewery.quality.StarLine;
import com.kostya.createbrewery.registry.ModItems;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
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

    /** Records the keg's contents with each transaction that changes them, so an aborted one puts them back. */
    private final Contents contents = new Contents();
    private final SingleSlotStorage<ItemVariant> input = new Input();
    private final SingleSlotStorage<ItemVariant> output = new Output();

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
        addPortion(bottle, keepEmpty);
        changed();
        return true;
    }

    private void addPortion(ItemStack bottle, boolean keepEmpty) {
        if (portions == 0) {
            drink = portion(bottle);
        }
        portions++;
        if (keepEmpty) {
            empties++;
        }
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

    /**
     * Bottles come in from the top and sides; the empty glass goes out below. Below only takes the empties out,
     * every other side (and none) pours in.
     */
    public SingleSlotStorage<ItemVariant> getItemHandler(@Nullable Direction side) {
        return side == Direction.DOWN ? output : input;
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

    // ---- the Transfer API ------------------------------------------------------------

    /** What a transfer may change, and a transaction that is rolled back puts back. */
    private record Snapshot(ItemStack drink, int portions, int empties) {
    }

    private final class Contents extends SnapshotParticipant<Snapshot> {
        @Override
        protected Snapshot createSnapshot() {
            return new Snapshot(drink, portions, empties);
        }

        @Override
        protected void readSnapshot(Snapshot snapshot) {
            drink = snapshot.drink();
            portions = snapshot.portions();
            empties = snapshot.empties();
        }

        @Override
        protected void onFinalCommit() {
            changed();
        }
    }

    /** Drink bottles go in, a portion each, their glass staying inside; nothing comes out this way. */
    private final class Input implements SingleSlotStorage<ItemVariant> {
        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            ItemStack bottle = resource.toStack();
            if (!canTake(bottle, true)) {
                return 0;
            }
            long room = Math.min(KegRules.CAPACITY - portions, KegRules.EMPTIES - empties);
            long taken = Math.min(room, maxAmount);
            if (taken <= 0) {
                return 0;
            }
            contents.updateSnapshots(transaction);
            for (int i = 0; i < taken; i++) {
                addPortion(bottle, true);
            }
            return taken;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public boolean supportsExtraction() {
            return false;
        }

        @Override
        public boolean isResourceBlank() {
            return true;
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.blank();
        }

        @Override
        public long getAmount() {
            return 0;
        }

        @Override
        public long getCapacity() {
            return KegRules.CAPACITY;
        }
    }

    /** The empty glass left by automated filling; nothing goes in this way. */
    private final class Output implements SingleSlotStorage<ItemVariant> {
        private static final ItemVariant GLASS = ItemVariant.of(Items.GLASS_BOTTLE);

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public boolean supportsInsertion() {
            return false;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (!resource.equals(GLASS)) {
                return 0;
            }
            long taken = Math.min(maxAmount, empties);
            if (taken <= 0) {
                return 0;
            }
            contents.updateSnapshots(transaction);
            empties -= (int) taken;
            return taken;
        }

        @Override
        public boolean isResourceBlank() {
            return empties <= 0;
        }

        @Override
        public ItemVariant getResource() {
            return empties > 0 ? GLASS : ItemVariant.blank();
        }

        @Override
        public long getAmount() {
            return empties;
        }

        @Override
        public long getCapacity() {
            return KegRules.EMPTIES;
        }
    }
}
