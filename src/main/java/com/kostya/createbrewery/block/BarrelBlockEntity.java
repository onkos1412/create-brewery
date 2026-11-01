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

/** Server state for a single manually loaded whiskey barrel. */
public class BarrelBlockEntity extends BlockEntity {
    private static final int WOOD_SLOT = 1;
    private static final int OUTPUT_SLOT = 2;
    private static final int BOTTLE_SLOT = 3;

    private long startedAt = -1L;
    private String woodId = "";
    /** Set once automation feeds the barrel, so it keeps aging without the GUI button. */
    private boolean autoStart;
    private final ItemStackHandler input = new ItemStackHandler(2) {
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
                    : isYoungDrink(stack) || stack.is(ModItems.DISTILLATE.get()) && agedStage(stack) != null;
        }

        /** A running batch stays sealed, or its contents could be swapped out before it finishes. */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return startedAt >= 0 ? ItemStack.EMPTY : super.extractItem(slot, amount, simulate);
        }
    };
    private final ItemStackHandler output = new ItemStackHandler(1) {
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
     * Funnels may fill distillate (0), wood (1) and empty bottles (3), and take only the
     * finished drink (2), which costs one of those bottles.
     */
    private final IItemHandler automationItems = new IItemHandler() {
        @Override
        public int getSlots() {
            return 4;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return switch (slot) {
                case OUTPUT_SLOT -> output.getStackInSlot(0);
                case BOTTLE_SLOT -> bottles.getStackInSlot(0);
                default -> input.getStackInSlot(slot);
            };
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot == OUTPUT_SLOT) {
                return stack;
            }
            if (slot == BOTTLE_SLOT) {
                return bottles.insertItem(0, stack, simulate);
            }
            ItemStack remainder = input.insertItem(slot, stack, simulate);
            if (!simulate && remainder.getCount() < stack.getCount()) {
                autoStart = true;
                setChanged();
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != OUTPUT_SLOT || !hasBottle()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = output.extractItem(0, amount, simulate);
            if (!simulate && !taken.isEmpty()) {
                spendBottle();
            }
            return taken;
        }

        @Override
        public int getSlotLimit(int slot) {
            return switch (slot) {
                case OUTPUT_SLOT -> output.getSlotLimit(0);
                case BOTTLE_SLOT -> bottles.getSlotLimit(0);
                default -> input.getSlotLimit(slot);
            };
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case OUTPUT_SLOT -> false;
                case BOTTLE_SLOT -> bottles.isItemValid(0, stack);
                default -> input.isItemValid(slot, stack);
            };
        }
    };

    public ItemStackHandler getBottles() {
        return bottles;
    }

    public boolean hasBottle() {
        return !bottles.getStackInSlot(0).isEmpty();
    }

    /** Pays for a drink taken without the spout. */
    public void spendBottle() {
        bottles.extractItem(0, 1, false);
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
        return profileOf(input.getStackInSlot(0)).agingDays() * AgingRules.TICKS_PER_DAY;
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
        return output.extractItem(0, 1, false);
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getElapsedTicks() {
        if (startedAt < 0 || level == null) {
            return 0;
        }
        // Day time, not game time: sleeping skips it forward, and so ages the barrel.
        return Math.max(0L, level.getDayTime() - startedAt);
    }

    public String getWoodId() {
        return woodId;
    }

    public boolean startAging() {
        ItemStack distillate = input.getStackInSlot(0);
        ItemStack wood = input.getStackInSlot(1);
        if (startedAt >= 0 || distillate.isEmpty() || !input.isItemValid(0, distillate) || wood.isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.ITEM.getKey(wood.getItem()).toString();
        // Non-oak wood is retained as a valid-but-lower-quality aging choice.
        if (!AgingRules.isAcceptedWood(id)) {
            return false;
        }
        woodId = id;
        startedAt = level == null ? 0L : level.getDayTime();
        setChanged();
        return true;
    }

    public void serverTick() {
        if (level == null || level.isClientSide() || !output.getStackInSlot(0).isEmpty()) {
            return;
        }
        if (startedAt < 0 && (!autoStart || !startAging())) {
            return;
        }
        ItemStack distillate = input.getStackInSlot(0);
        DrinkProfile profile = profileOf(distillate);
        if (getElapsedTicks() < profile.agingDays() * AgingRules.TICKS_PER_DAY) {
            return;
        }
        DrinkData data = distillate.getOrDefault(ModDataComponents.DRINK_DATA.get(),
                new DrinkData(DrinkData.MAX_STARS, DrinkData.DEFAULT_ALCOHOL, "distillate"));
        BarrelLogic.Result result = BarrelLogic.process(data.stars(), data.alcohol(), woodId, profile.wood(),
                distillate.getCount() * 1000, getElapsedTicks(), profile.agingDays());
        if (!result.ready()) {
            return;
        }
        String stage = agedStage(distillate);
        ItemStack aged = new ItemStack(ModItems.DRINK_BOTTLE.get());
        boolean young = distillate.is(ModItems.DRINK_BOTTLE.get());
        aged.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(result.stars(),
                young ? profile.distilledAlcohol() : result.alcohol(),
                stage == null ? DrinkProfile.BEER.aged() : stage));
        output.setStackInSlot(0, aged);
        if (young) {
            // The bottle it came in is empty now: it waits with the others, or falls out if they are full.
            ItemStack spare = bottles.insertItem(0, new ItemStack(Items.GLASS_BOTTLE), false);
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), spare);
        }
        distillate.shrink(1);
        input.setStackInSlot(0, distillate);
        startedAt = -1L;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("StartedAt", startedAt);
        tag.putString("Wood", woodId);
        tag.putBoolean("AutoStart", autoStart);
        tag.put("Input", input.serializeNBT(registries));
        tag.put("Output", output.serializeNBT(registries));
        tag.put("Bottles", bottles.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        startedAt = tag.contains("StartedAt") ? tag.getLong("StartedAt") : -1L;
        woodId = tag.getString("Wood");
        autoStart = tag.getBoolean("AutoStart");
        if (tag.contains("Input")) {
            input.deserializeNBT(registries, tag.getCompound("Input"));
        }
        if (tag.contains("Output")) {
            output.deserializeNBT(registries, tag.getCompound("Output"));
        }
        if (tag.contains("Bottles")) {
            bottles.deserializeNBT(registries, tag.getCompound("Bottles"));
        }
    }
}
