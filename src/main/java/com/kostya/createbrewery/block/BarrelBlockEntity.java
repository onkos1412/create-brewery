package com.kostya.createbrewery.block;

import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.barrel.AgingRules;
import com.kostya.createbrewery.barrel.BarrelLogic;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/** A barrel ageing up to four stacks of one drink at a time, with one kind of wood. */
public class BarrelBlockEntity extends BlockEntity {
    private static final int WOOD_SLOT = 1;
    private static final int FIRST_DRINK_SLOT = 2;
    private static final int BOTTLE_SLOT = 3;
    private static final int FIRST_EXTRA_INPUT = 4;
    private static final int FIRST_EXTRA_DRINK = 7;
    private static final int AUTOMATION_SLOTS = 10;
    /** The slots of {@link #input} that hold a batch; slot 1 is the wood. */
    private static final int[] BATCH_SLOTS = {0, 2, 3, 4};

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
    private final ItemStackHandler input = new ItemStackHandler(5) {
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
                    : isAgeable(stack) && fitsTheBatch(slot, stack);
        }

        /** A running batch stays sealed, or its contents could be swapped out before it finishes. */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return startedAt >= 0 ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }
    };
    private final ItemStackHandler output = new ItemStackHandler(BATCH_SLOTS.length) {
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
    private final ItemStackHandler bottles = new ItemStackHandler(1) {
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
    private final IItemHandler automationItems = new IItemHandler() {
        @Override
        public int getSlots() {
            return AUTOMATION_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == BOTTLE_SLOT) {
                return bottles.getStackInSlot(0);
            }
            int drink = drinkSlot(slot);
            return drink >= 0 ? output.getStackInSlot(drink) : input.getStackInSlot(inputSlot(slot));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (drinkSlot(slot) >= 0) {
                return stack;
            }
            if (slot == BOTTLE_SLOT) {
                return bottles.insertItem(0, stack, simulate);
            }
            ItemStack remainder = input.insertItem(inputSlot(slot), stack, simulate);
            if (!simulate && remainder.getCount() < stack.getCount()) {
                autoStart = true;
                setChanged();
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            int drink = drinkSlot(slot);
            int paid = Math.min(amount, bottles.getStackInSlot(0).getCount());
            if (drink < 0 || paid <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = output.extractItem(drink, paid, simulate);
            if (!simulate) {
                spendBottles(taken.getCount());
            }
            return taken;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == BOTTLE_SLOT) {
                return bottles.getSlotLimit(0);
            }
            int drink = drinkSlot(slot);
            return drink >= 0 ? output.getSlotLimit(drink) : input.getSlotLimit(inputSlot(slot));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == BOTTLE_SLOT) {
                return bottles.isItemValid(0, stack);
            }
            return drinkSlot(slot) < 0 && input.isItemValid(inputSlot(slot), stack);
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

    public ItemStackHandler getBottles() {
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
        DrinkData data = distillate.get(ModDataComponents.DRINK_DATA.get());
        if (data != null && data.isSpoiled()) {
            return null;
        }
        DrinkProfile drink = profileOf(distillate);
        return drink.canAge() ? drink.aged() : null;
    }

    private DrinkProfile profileOf(ItemStack product) {
        DrinkData data = product.get(ModDataComponents.DRINK_DATA.get());
        if (data == null || level == null) {
            return DrinkProfile.BEER;
        }
        return product.is(ModItems.DRINK_BOTTLE.get())
                ? BrewingRecipe.profileOf(level, data.stage())
                : BrewingRecipe.profileDistilledTo(level, data.stage());
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
        for (int slot = 0; slot < output.getSlots(); slot++) {
            if (!output.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A bottle of something that matures here without distilling, like young wine. */
    private boolean isYoungDrink(ItemStack stack) {
        DrinkData data = stack.get(ModDataComponents.DRINK_DATA.get());
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

    public IItemHandler getAutomationItems() {
        return automationItems;
    }

    public ItemStackHandler getInput() {
        return input;
    }

    public ItemStackHandler getOutput() {
        return output;
    }

    public ItemStack takeFinishedBottle() {
        for (int slot = 0; slot < output.getSlots(); slot++) {
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
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack drink = output.getStackInSlot(slot);
            DrinkData data = drink.get(ModDataComponents.DRINK_DATA.get());
            if (data != null) {
                ItemStack worse = drink.copy();
                int stars = (int) Math.max(DrinkData.MIN_STARS, data.stars() - lost);
                worse.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(stars, data.alcohol(), data.stage()));
                output.setStackInSlot(slot, worse);
            }
        }
        setChanged();
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
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
        DrinkData data = batch.getOrDefault(ModDataComponents.DRINK_DATA.get(),
                new DrinkData(DrinkData.MAX_STARS, DrinkData.DEFAULT_ALCOHOL, "distillate"));
        BarrelLogic.Result result = BarrelLogic.process(data.stars(), data.alcohol(), woodId, profile.wood(),
                batch.getCount() * 1000, getElapsedTicks(), profile.agingDays());
        boolean young = batch.is(ModItems.DRINK_BOTTLE.get());
        ItemStack aged = new ItemStack(ModItems.DRINK_BOTTLE.get(), batch.getCount());
        aged.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(result.stars(),
                young ? profile.distilledAlcohol() : result.alcohol(), profile.aged()));
        output.setStackInSlot(outputSlot, aged);
        if (young) {
            // The bottles they came in are empty now: they wait with the others, or fall out if those are full.
            ItemStack spare = bottles.insertItem(0, new ItemStack(Items.GLASS_BOTTLE, batch.getCount()), false);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), spare);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("StartedAt", startedAt);
        tag.putLong("FinishedAt", finishedAt);
        tag.putLong("LastDayTime", lastDayTime);
        tag.putLong("AgedTicks", agedTicks);
        tag.putLong("OverAgedTicks", overAgedTicks);
        tag.putLong("OverAgedPeriods", overAgedPeriods);
        tag.putInt("FinishedDays", finishedDays);
        tag.putString("Wood", woodId);
        tag.putBoolean("AutoStart", autoStart);
        tag.put("Input", input.serializeNBT(registries));
        tag.put("Output", output.serializeNBT(registries));
        tag.put("Bottles", bottles.serializeNBT(registries));
    }

    /** Barrels saved before they grew have fewer slots; loading must not shrink them. */
    private static void load(ItemStackHandler handler, int size, CompoundTag tag, HolderLookup.Provider registries) {
        handler.deserializeNBT(registries, tag);
        if (handler.getSlots() < size) {
            ItemStack[] kept = new ItemStack[handler.getSlots()];
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
            load(input, 5, tag.getCompound("Input"), registries);
        }
        if (tag.contains("Output")) {
            load(output, BATCH_SLOTS.length, tag.getCompound("Output"), registries);
        }
        if (tag.contains("Bottles")) {
            bottles.deserializeNBT(registries, tag.getCompound("Bottles"));
        }
    }
}
