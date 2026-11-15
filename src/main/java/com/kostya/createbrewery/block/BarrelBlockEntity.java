package com.kostya.createbrewery.block;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.barrel.AgingRules;
import com.kostya.createbrewery.barrel.BarrelLogic;
import com.kostya.createbrewery.journal.BarrelRecord;
import com.kostya.createbrewery.journal.BreweryJournalData;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.registry.ModItems;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** One barrel, alone or in a group that shares one store of up to four stacks of one drink. */
public class BarrelBlockEntity extends BlockEntity {
    private static final int WOOD_SLOT = 1;
    private static final int FIRST_DRINK_SLOT = 2;
    private static final int BOTTLE_SLOT = 3;
    private static final int FIRST_EXTRA_INPUT = 4;
    private static final int FIRST_EXTRA_DRINK = 7;
    private static final int AUTOMATION_SLOTS = 10;
    /** The slots of {@link #input} that hold a batch; slot 1 is the wood. */
    private static final int[] BATCH_SLOTS = {0, 2, 3, 4};
    private static final int BARRELS_PER_STACK_STEP = 4;
    private static final int STACK_STEP = 16;
    /** Past this many barrels a group grows no further: stacks are saved with at most 99 items. */
    private static final int MAX_COUNTED_BARRELS = 24;
    private static final int WOOD_LIMIT = 64;

    /** The barrel holding this group's contents; the one barrel of a lone barrel. Worked out on demand. */
    @Nullable
    private BarrelBlockEntity root;
    /** How many barrels are in the group, as far as the root knows. */
    private int groupSize = 1;

    private long startedAt = -1L;
    private long finishedAt = -1L;
    /** Day time last seen. Clocks only ever add forward steps, so `/time set` backward cannot stall them. */
    private long lastDayTime = -1L;
    private long agedTicks;
    private long overAgedTicks;
    private long overAgedPeriods;
    private int finishedDays = 1;
    private String woodId = "";
    /** Set once automation feeds the barrel, so it keeps aging without the GUI button. */
    private boolean autoStart;
    private final MachineInventory input = new MachineInventory(5) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (startedAt >= 0) {
                return false;
            }
            return slot == WOOD_SLOT
                    ? AgingRules.isAcceptedWood(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                    : batchIndex(slot) < openStacks() && isAgeable(stack) && fitsTheBatch(slot, stack);
        }

        /** A running batch stays sealed, or its contents could be swapped out before it finishes. */
        @Override
        protected boolean canExtract(int slot) {
            return startedAt < 0;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == WOOD_SLOT ? WOOD_LIMIT : stackLimit();
        }

        /** Bigger groups stack past the item's own limit, which only a barrel's stack ever does. */
        @Override
        protected int getStackLimit(int slot, ItemVariant resource) {
            return getSlotLimit(slot);
        }
    };
    private final MachineInventory output = new MachineInventory(BATCH_SLOTS.length) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    /** Empty bottles waiting for the drink. The spout instead takes one from the player's hand. */
    private final MachineInventory bottles = new MachineInventory(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(Items.GLASS_BOTTLE);
        }
    };

    /**
     * Funnels may fill distillate and wood and empty bottles, and take only finished drinks,
     * which cost one bottle each. Slots 0 to 3 are the first distillate, the wood, the first
     * drink and the bottles, with the extra batches after them.
     */
    private final Automation automationItems = new Automation();

    /** Remembers, with the transaction that set it, that automation fed this barrel. */
    private final SnapshotParticipant<Boolean> autoStartChange = new SnapshotParticipant<>() {
        @Override
        protected Boolean createSnapshot() {
            return autoStart;
        }

        @Override
        protected void readSnapshot(Boolean snapshot) {
            autoStart = snapshot;
        }

        @Override
        protected void onFinalCommit() {
            setChanged();
        }
    };

    /** Which of the {@link #output} slots an automation or menu slot is, or -1 when it is none. */
    static int drinkSlot(int slot) {
        if (slot == FIRST_DRINK_SLOT) {
            return 0;
        }
        return slot >= FIRST_EXTRA_DRINK && slot < AUTOMATION_SLOTS ? slot - FIRST_EXTRA_DRINK + 1 : -1;
    }

    /** Which of the {@link #input} slots an automation or menu slot is. */
    private static int inputSlot(int slot) {
        return slot < FIRST_DRINK_SLOT ? slot : slot - FIRST_EXTRA_INPUT + 2;
    }

    public MachineInventory getBottles() {
        return bottles;
    }

    public int getBottleCount() {
        return bottles.getStackInSlot(0).getCount();
    }

    /** Pays for drinks taken without the spout. */
    public void spendBottles(int count) {
        if (count > 0) {
            bottles.extractItem(0, count, false);
        }
    }

    public BarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BARREL_BLOCK_ENTITY.get(), pos, state);
    }

    /** What this distillate ages into, or {@code null} when it does not belong in a barrel. */
    @Nullable
    private String agedStage(ItemStack distillate) {
        DrinkData data = DrinkData.get(distillate);
        if (data != null && data.isSpoiled()) {
            return null;
        }
        DrinkProfile drink = profileOf(distillate);
        return drink.canAge() ? drink.aged() : null;
    }

    private DrinkProfile profileOf(ItemStack product) {
        DrinkData data = DrinkData.get(product);
        if (data == null || level == null) {
            return DrinkProfile.BEER;
        }
        return product.is(ModItems.DRINK_BOTTLE.get())
                ? BrewingRecipe.profileOf(level, data.stage())
                : BrewingRecipe.profileDistilledTo(level, data.stage());
    }

    /** Which batch an {@link #input} slot holds. */
    private static int batchIndex(int slot) {
        return slot == 0 ? 0 : slot - 1;
    }

    /** How many of the four stacks are open: one per barrel. */
    public int openStacks() {
        return Math.min(groupSize, BATCH_SLOTS.length);
    }

    /** How much each open stack holds: another sixteen for every four barrels. */
    private int stackLimit() {
        int counted = Math.min(groupSize, MAX_COUNTED_BARRELS);
        return STACK_STEP * ((counted + BARRELS_PER_STACK_STEP - 1) / BARRELS_PER_STACK_STEP);
    }

    /** The barrel that holds this barrel's group's contents. */
    public BarrelBlockEntity root() {
        if (level == null || level.isClientSide()) {
            return this;
        }
        if (root == null || root.isRemoved()) {
            regroup();
        }
        return root;
    }

    /** Every barrel touching this one, directly or through others, that faces the same way. */
    private List<BarrelBlockEntity> members() {
        List<BarrelBlockEntity> found = new ArrayList<>(List.of(this));
        Direction facing = getBlockState().getValue(BarrelBlock.FACING);
        Set<BlockPos> seen = new HashSet<>(Set.of(worldPosition));
        for (int next = 0; next < found.size(); next++) {
            for (Direction direction : Direction.values()) {
                BlockPos pos = found.get(next).worldPosition.relative(direction);
                // The world's block decides, not the entity: one being broken still has an entity for a moment.
                BlockState state = level.getBlockState(pos);
                if (seen.add(pos) && state.is(ModBlocks.BARREL.get()) && state.getValue(BarrelBlock.FACING) == facing
                        && level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
                    found.add(barrel);
                }
            }
        }
        return found;
    }

    /** Picks the group's root: a running batch wins, then whoever holds the most, and folds the rest into it. */
    private void regroup() {
        List<BarrelBlockEntity> members = members();
        BarrelBlockEntity best = members.get(0);
        for (BarrelBlockEntity member : members) {
            if (member.rootScore() > best.rootScore()
                    || member.rootScore() == best.rootScore() && member.worldPosition.asLong() < best.worldPosition.asLong()) {
                best = member;
            }
        }
        best.groupSize = members.size();
        for (BarrelBlockEntity member : members) {
            member.root = best;
            if (member != best) {
                member.groupSize = 1;
                member.pourInto(best);
            }
        }
    }

    private long rootScore() {
        long held = 0;
        for (MachineInventory handler : List.of(input, output, bottles)) {
            for (int slot = 0; slot < handler.getSlotCount(); slot++) {
                held += handler.getStackInSlot(slot).getCount();
            }
        }
        return startedAt >= 0 || finishedAt >= 0 ? Long.MAX_VALUE / 2 + held : held;
    }

    /** Hands everything over to the group's root; what it has no room for falls out. Nothing is lost. */
    private void pourInto(BarrelBlockEntity target) {
        for (int slot = 0; slot < input.getSlotCount(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                input.setStackInSlot(slot, ItemStack.EMPTY);
                spill(target.input.insertItem(slot, stack, false));
            }
        }
        for (int slot = 0; slot < output.getSlotCount(); slot++) {
            ItemStack stack = output.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                output.setStackInSlot(slot, ItemStack.EMPTY);
                if (target.output.getStackInSlot(slot).isEmpty()) {
                    target.output.setStackInSlot(slot, stack);
                } else {
                    spill(stack);
                }
            }
        }
        ItemStack spare = bottles.getStackInSlot(0);
        if (!spare.isEmpty()) {
            bottles.setStackInSlot(0, ItemStack.EMPTY);
            spill(target.bottles.insertItem(0, spare, false));
        }
        target.autoStart |= autoStart;
        startedAt = -1L;
        finishedAt = -1L;
        autoStart = false;
        setChanged();
    }

    private void spill(ItemStack stack) {
        // Over-full stacks from a big group have to fall out in stacks the item allows.
        while (!stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(),
                    stack.split(stack.getMaxStackSize()));
        }
    }

    /**
     * Called as this barrel is broken. Its contents move on to a touching barrel, if there is
     * one; the block is already gone from the world, which is why it searches from here.
     */
    void leaveGroup(Direction facing) {
        if (root != null && root != this) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof BarrelBlockEntity next
                    && !next.isRemoved() && next.getBlockState().getValue(BarrelBlock.FACING) == facing) {
                next.takeOver(this);
                return;
            }
        }
    }

    private void takeOver(BarrelBlockEntity old) {
        for (int slot = 0; slot < input.getSlotCount(); slot++) {
            input.setStackInSlot(slot, old.input.getStackInSlot(slot));
            old.input.setStackInSlot(slot, ItemStack.EMPTY);
        }
        for (int slot = 0; slot < output.getSlotCount(); slot++) {
            output.setStackInSlot(slot, old.output.getStackInSlot(slot));
            old.output.setStackInSlot(slot, ItemStack.EMPTY);
        }
        bottles.setStackInSlot(0, old.bottles.getStackInSlot(0));
        old.bottles.setStackInSlot(0, ItemStack.EMPTY);
        startedAt = old.startedAt;
        finishedAt = old.finishedAt;
        lastDayTime = old.lastDayTime;
        agedTicks = old.agedTicks;
        overAgedTicks = old.overAgedTicks;
        overAgedPeriods = old.overAgedPeriods;
        finishedDays = old.finishedDays;
        woodId = old.woodId;
        autoStart = old.autoStart;
        setChanged();
    }

    /** A barrel came or went at {@code pos}: every group touching it must work itself out again. */
    static void regroupAround(Level level, BlockPos pos, Direction facing) {
        if (level.isClientSide()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof BarrelBlockEntity barrel
                    && barrel.getBlockState().getValue(BarrelBlock.FACING) == facing) {
                for (BarrelBlockEntity member : barrel.members()) {
                    member.root = null;
                }
            }
        }
    }

    private boolean isAgeable(ItemStack stack) {
        return isYoungDrink(stack) || stack.is(ModItems.DISTILLATE.get()) && agedStage(stack) != null;
    }

    /** One batch ages as one: every slot must hold the same drink, since they share wood and days. */
    private boolean fitsTheBatch(int slot, ItemStack stack) {
        for (int other : BATCH_SLOTS) {
            ItemStack held = input.getStackInSlot(other);
            if (other != slot && !held.isEmpty() && !profileOf(held).equals(profileOf(stack))) {
                return false;
            }
        }
        return true;
    }

    private ItemStack firstInBatch() {
        for (int slot : BATCH_SLOTS) {
            if (!input.getStackInSlot(slot).isEmpty()) {
                return input.getStackInSlot(slot);
            }
        }
        return ItemStack.EMPTY;
    }

    private boolean drinksWaiting() {
        for (int slot = 0; slot < output.getSlotCount(); slot++) {
            if (!output.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A bottle of something that matures here without distilling, like young wine. */
    private boolean isYoungDrink(ItemStack stack) {
        DrinkData data = DrinkData.get(stack);
        if (!stack.is(ModItems.DRINK_BOTTLE.get()) || data == null || level == null) {
            return false;
        }
        DrinkProfile drink = BrewingRecipe.profileOf(level, data.stage());
        return drink.id().equals(data.stage()) && drink.agesInTheBottle();
    }

    /** How long the batch inside takes. */
    public long getRequiredTicks() {
        return profileOf(firstInBatch()).agingDays() * AgingRules.TICKS_PER_DAY;
    }

    /** Funnels on any barrel of a group reach the group's store through this, from every side. */
    public SlottedStorage<ItemVariant> getAutomationItems() {
        return automationItems;
    }

    public MachineInventory getInput() {
        return input;
    }

    public MachineInventory getOutput() {
        return output;
    }

    public ItemStack takeFinishedBottle() {
        for (int slot = 0; slot < output.getSlotCount(); slot++) {
            if (!output.getStackInSlot(slot).isEmpty()) {
                return output.extractItem(slot, 1, false);
            }
        }
        return ItemStack.EMPTY;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getElapsedTicks() {
        return startedAt < 0 ? 0 : agedTicks;
    }

    /** Day time, not game time: sleeping skips it forward, and so ages the barrel. */
    private void advanceClocks() {
        long now = level.getDayTime();
        long step = lastDayTime < 0 ? 0L : Math.max(0L, now - lastDayTime);
        lastDayTime = now;
        if (startedAt >= 0) {
            agedTicks += step;
        }
        if (finishedAt >= 0) {
            overAgedTicks += step;
        }
        if (step > 0) {
            setChanged();
        }
    }

    public String getWoodId() {
        return woodId;
    }

    public boolean startAging() {
        ItemStack wood = input.getStackInSlot(WOOD_SLOT);
        if (startedAt >= 0 || firstInBatch().isEmpty() || !isAgeable(firstInBatch()) || wood.isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.ITEM.getKey(wood.getItem()).toString();
        // Non-oak wood is retained as a valid-but-lower-quality aging choice.
        if (!AgingRules.isAcceptedWood(id)) {
            return false;
        }
        woodId = id;
        startedAt = level == null ? 0L : level.getDayTime();
        agedTicks = 0L;
        lastDayTime = startedAt;
        setChanged();
        return true;
    }

    /** Takes stars off finished drinks nobody has collected, one for each aging period they wait. */
    private void overAge() {
        long periods = overAgedTicks / (finishedDays * AgingRules.TICKS_PER_DAY);
        if (finishedAt < 0 || periods <= overAgedPeriods) {
            return;
        }
        long lost = periods - overAgedPeriods;
        overAgedPeriods = periods;
        for (int slot = 0; slot < output.getSlotCount(); slot++) {
            ItemStack drink = output.getStackInSlot(slot);
            DrinkData data = DrinkData.get(drink);
            if (data != null) {
                ItemStack worse = drink.copy();
                int stars = (int) Math.max(DrinkData.MIN_STARS, data.stars() - lost);
                DrinkData.set(worse, new DrinkData(stars, data.alcohol(), data.stage()));
                output.setStackInSlot(slot, worse);
            }
        }
        setChanged();
    }

    public void serverTick() {
        if (level == null || level.isClientSide() || root() != this) {
            return;
        }
        advanceClocks();
        if (drinksWaiting()) {
            overAge();
            return;
        }
        finishedAt = -1L;
        if (startedAt < 0 && (!autoStart || !startAging())) {
            return;
        }
        DrinkProfile profile = profileOf(firstInBatch());
        if (firstInBatch().isEmpty() || getElapsedTicks() < profile.agingDays() * AgingRules.TICKS_PER_DAY) {
            return;
        }
        for (int index = 0; index < BATCH_SLOTS.length; index++) {
            ItemStack batch = input.getStackInSlot(BATCH_SLOTS[index]);
            if (!batch.isEmpty()) {
                finish(batch, profile, index);
                input.setStackInSlot(BATCH_SLOTS[index], ItemStack.EMPTY);
            }
        }
        finishedAt = level.getDayTime();
        overAgedTicks = 0L;
        overAgedPeriods = 0L;
        finishedDays = profile.agingDays();
        startedAt = -1L;
        setChanged();
    }

    /** Turns a whole stack into finished drinks. */
    private void finish(ItemStack batch, DrinkProfile profile, int outputSlot) {
        DrinkData data = DrinkData.getOrDefault(batch,
                new DrinkData(DrinkData.MAX_STARS, DrinkData.DEFAULT_ALCOHOL, "distillate"));
        BarrelLogic.Result result = BarrelLogic.process(data.stars(), data.alcohol(), woodId, profile.wood(),
                batch.getCount() * 1000, getElapsedTicks(), profile.agingDays());
        boolean young = batch.is(ModItems.DRINK_BOTTLE.get());
        ItemStack aged = new ItemStack(ModItems.DRINK_BOTTLE.get(), batch.getCount());
        DrinkData.set(aged, new DrinkData(result.stars(),
                young ? profile.distilledAlcohol() : result.alcohol(), profile.aged()));
        BrewerMark mark = BrewerMark.get(batch);
        if (mark != null) {
            BrewerMark.set(aged, mark);
            if (mark.owner().isPresent() && level.getServer() != null) {
                BreweryJournalData.get(level.getServer()).recordBarrel(mark.owner().get(), profile.id(),
                        new BarrelRecord(woodId, (int) (getElapsedTicks() / AgingRules.TICKS_PER_DAY), result.stars()));
            }
        }
        output.setStackInSlot(outputSlot, aged);
        if (young) {
            // The bottles they came in are empty now: they wait with the others, or fall out if those are full.
            ItemStack spare = bottles.insertItem(0, new ItemStack(Items.GLASS_BOTTLE, batch.getCount()), false);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), spare);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("StartedAt", startedAt);
        tag.putLong("FinishedAt", finishedAt);
        tag.putLong("LastDayTime", lastDayTime);
        tag.putLong("AgedTicks", agedTicks);
        tag.putLong("OverAgedTicks", overAgedTicks);
        tag.putLong("OverAgedPeriods", overAgedPeriods);
        tag.putInt("FinishedDays", finishedDays);
        tag.putString("Wood", woodId);
        tag.putBoolean("AutoStart", autoStart);
        tag.put("Input", input.serializeNBT());
        tag.put("Output", output.serializeNBT());
        tag.put("Bottles", bottles.serializeNBT());
    }

    /** Barrels saved before they grew have fewer slots; loading must not shrink them. */
    private static void load(MachineInventory handler, int size, CompoundTag tag) {
        handler.deserializeNBT(tag);
        if (handler.getSlotCount() < size) {
            ItemStack[] kept = new ItemStack[handler.getSlotCount()];
            for (int slot = 0; slot < kept.length; slot++) {
                kept[slot] = handler.getStackInSlot(slot);
            }
            handler.setSize(size);
            for (int slot = 0; slot < kept.length; slot++) {
                handler.setStackInSlot(slot, kept[slot]);
            }
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        startedAt = tag.contains("StartedAt") ? tag.getLong("StartedAt") : -1L;
        finishedAt = tag.contains("FinishedAt") ? tag.getLong("FinishedAt") : -1L;
        // Barrels saved before the clocks existed catch up from when their batch started.
        lastDayTime = tag.contains("LastDayTime") ? tag.getLong("LastDayTime") : startedAt;
        agedTicks = tag.getLong("AgedTicks");
        overAgedTicks = tag.getLong("OverAgedTicks");
        overAgedPeriods = tag.getLong("OverAgedPeriods");
        finishedDays = tag.contains("FinishedDays") ? tag.getInt("FinishedDays") : 1;
        woodId = tag.getString("Wood");
        autoStart = tag.getBoolean("AutoStart");
        if (tag.contains("Input")) {
            load(input, 5, tag.getCompound("Input"));
        }
        if (tag.contains("Output")) {
            load(output, BATCH_SLOTS.length, tag.getCompound("Output"));
        }
        if (tag.contains("Bottles")) {
            bottles.deserializeNBT(tag.getCompound("Bottles"));
        }
    }

    // ---- the Transfer API ------------------------------------------------------------

    /** The group's store as funnels see it: ten slots, filled and emptied in order, as Forge's handler was. */
    private final class Automation implements SlottedStorage<ItemVariant> {
        private final List<SingleSlotStorage<ItemVariant>> slots = new ArrayList<>();

        Automation() {
            for (int slot = 0; slot < AUTOMATION_SLOTS; slot++) {
                slots.add(new AutomationSlot(slot));
            }
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long inserted = 0;
            for (SingleSlotStorage<ItemVariant> slot : slots) {
                inserted += slot.insert(resource, maxAmount - inserted, transaction);
                if (inserted >= maxAmount) {
                    break;
                }
            }
            return inserted;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long extracted = 0;
            for (SingleSlotStorage<ItemVariant> slot : slots) {
                extracted += slot.extract(resource, maxAmount - extracted, transaction);
                if (extracted >= maxAmount) {
                    break;
                }
            }
            return extracted;
        }

        @Override
        public Iterator<StorageView<ItemVariant>> iterator() {
            return new ArrayList<StorageView<ItemVariant>>(slots).iterator();
        }

        @Override
        public int getSlotCount() {
            return AUTOMATION_SLOTS;
        }

        @Override
        public SingleSlotStorage<ItemVariant> getSlot(int slot) {
            return slots.get(slot);
        }
    }

    /**
     * One automation slot, read from and written to the group's root. Inserting and extracting go through the
     * root's own slots, which take part in the transaction, so an aborted one leaves everything as it was.
     */
    private final class AutomationSlot implements SingleSlotStorage<ItemVariant> {
        private final int slot;

        AutomationSlot(int slot) {
            this.slot = slot;
        }

        private ItemStack stack() {
            if (slot == BOTTLE_SLOT) {
                return root().bottles.getStackInSlot(0);
            }
            int drink = drinkSlot(slot);
            return drink >= 0 ? root().output.getStackInSlot(drink) : root().input.getStackInSlot(inputSlot(slot));
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (drinkSlot(slot) >= 0) {
                return 0;
            }
            BarrelBlockEntity root = root();
            if (slot == BOTTLE_SLOT) {
                return root.bottles.getSlot(0).insert(resource, maxAmount, transaction);
            }
            long inserted = root.input.getSlot(inputSlot(slot)).insert(resource, maxAmount, transaction);
            if (inserted > 0) {
                root.autoStartChange.updateSnapshots(transaction);
                root.autoStart = true;
            }
            return inserted;
        }

        /** Drinks only, and each costs one of the group's empty bottles. */
        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            int drink = drinkSlot(slot);
            BarrelBlockEntity root = root();
            SingleSlotStorage<ItemVariant> bottleSlot = root.bottles.getSlot(0);
            // Whatever glass bottles wait there pay, renamed ones included: the slot only ever holds glass bottles.
            ItemVariant glass = bottleSlot.getResource();
            long affordable = Math.min(maxAmount, bottleSlot.getAmount());
            if (drink < 0 || glass.isBlank() || affordable <= 0) {
                return 0;
            }
            // Drink and payment move together or not at all.
            try (Transaction both = transaction.openNested()) {
                long taken = root.output.getSlot(drink).extract(resource, affordable, both);
                if (taken <= 0 || bottleSlot.extract(glass, taken, both) != taken) {
                    return 0;
                }
                both.commit();
                return taken;
            }
        }

        @Override
        public boolean isResourceBlank() {
            return stack().isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(stack());
        }

        @Override
        public long getAmount() {
            return stack().getCount();
        }

        @Override
        public long getCapacity() {
            if (slot == BOTTLE_SLOT) {
                return root().bottles.getSlotLimit(0);
            }
            int drink = drinkSlot(slot);
            return drink >= 0 ? root().output.getSlotLimit(drink) : root().input.getSlotLimit(inputSlot(slot));
        }
    }
}
