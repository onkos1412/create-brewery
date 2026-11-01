package com.kostya.createbrewery.block;

import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The kettle's server-side state: water, wort, the waiting ingredients and brewing
 * progress.
 *
 * <p>Every server tick it reads the heat source beneath the kettle, finds a matching
 * {@link BrewingRecipe}, advances progress and, once the brew time has elapsed, turns
 * the water into wort.
 */
public class KettleBlockEntity extends BlockEntity {

    /** How much water the kettle holds, in milli-buckets. */
    public static final int TANK_CAPACITY = 4000;
    /** How much wort a finished brew yields. */
    public static final int WORT_PER_BREW = 1000;
    /** Strength of a bottle of beer, half that of whiskey. */
    public static final int BEER_ALCOHOL = 20;

    /** How much extra room is needed to start a brew with this recipe. */
    private static int brewRoom(KettleLogic.RecipeView recipe) {
        return Math.max(recipe.wortAmount(), WORT_PER_BREW);
    }

    private final ItemStackHandler input = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** Pipes fill the water tank (0) and drain the wort tank (1). */
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank == 0) {
                return waterAmount > 0 ? new FluidStack(Fluids.WATER, waterAmount) : FluidStack.EMPTY;
            }
            return wortAmount > 0 ? new FluidStack(ModFluids.WORT.get(), wortAmount) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && stack.is(Fluids.WATER);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!resource.is(Fluids.WATER)) {
                return 0;
            }
            return action.execute()
                    ? fillWater(resource.getAmount())
                    : Math.max(0, Math.min(resource.getAmount(), TANK_CAPACITY - waterAmount - wortAmount));
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return resource.is(ModFluids.WORT.get()) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            int drained = action.execute() ? drainWort(maxDrain) : Math.min(maxDrain, wortAmount);
            return drained > 0 ? new FluidStack(ModFluids.WORT.get(), drained) : FluidStack.EMPTY;
        }
    };

    private int waterAmount;
    private int wortAmount;
    private int brewProgressTicks;
    private int heatLevel;

    public KettleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.KETTLE_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStackHandler getInput() {
        return input;
    }

    public IFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public int getWaterAmount() {
        return waterAmount;
    }

    public int getWortAmount() {
        return wortAmount;
    }

    public int getBrewProgressTicks() {
        return brewProgressTicks;
    }

    public int getHeatLevel() {
        return heatLevel;
    }

    /** Adds water to the tank (used by right-clicking with a water bucket and by pipes). */
    public int fillWater(int amount) {
        int accepted = Math.min(amount, TANK_CAPACITY - waterAmount - wortAmount);
        if (accepted <= 0) {
            return 0;
        }
        waterAmount += accepted;
        setChanged();
        return accepted;
    }

    /** Drains brewed wort; returns how much was removed. */
    public int drainWort(int amount) {
        int drained = Math.min(amount, wortAmount);
        if (drained <= 0) {
            return 0;
        }
        wortAmount -= drained;
        setChanged();
        return drained;
    }

    /**
     * Turns one of the stored wort into a bottle of the finished drink.
     *
     * @return the filled bottle, or {@link ItemStack#EMPTY} when there is no wort
     */
    public ItemStack bottleWort(int stars) {
        if (wortAmount < WORT_PER_BREW) {
            return ItemStack.EMPTY;
        }
        wortAmount -= WORT_PER_BREW;
        setChanged();
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        // Wort bottled straight from the kettle is beer; distilling it is what makes whiskey.
        bottle.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(stars, BEER_ALCOHOL, "beer"));
        return bottle;
    }

    /** Reads the heat level of the block directly beneath the kettle. */
    static int readHeatLevel(Level level, BlockPos below) {
        BlockState belowState = level.getBlockState(below);
        if (belowState.is(Blocks.FIRE) || belowState.is(Blocks.CAMPFIRE) || belowState.is(Blocks.LAVA)) {
            return KettleLogic.HEAT_HEATED;
        }
        if (belowState.is(Blocks.SOUL_CAMPFIRE) || belowState.is(Blocks.SOUL_FIRE)) {
            return KettleLogic.HEAT_SUPERHEATED;
        }
        if (belowState.getBlock() instanceof BlazeBurnerBlock) {
            BlazeBurnerBlock.HeatLevel blaze = BlazeBurnerBlock.getHeatLevelOf(belowState);
            if (blaze.isAtLeast(BlazeBurnerBlock.HeatLevel.SEETHING)) {
                return KettleLogic.HEAT_SUPERHEATED;
            }
            if (blaze.isAtLeast(BlazeBurnerBlock.HeatLevel.FADING)) {
                return KettleLogic.HEAT_HEATED;
            }
        }
        return KettleLogic.HEAT_NONE;
    }

    /** One server tick of the kettle. */
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        heatLevel = readHeatLevel(level, worldPosition.below());
        RecipeHolder<BrewingRecipe> recipe = findRecipe(heatLevel);

        if (recipe == null) {
            brewProgressTicks = 0;
            setBrewingState(false);
            return;
        }

        KettleLogic.RecipeView view = toView(recipe.value());
        List<String> itemIds = inputIds();

        // Everything is computed against this tick's snapshot, so the state cannot
        // drift between the progress check and the actual brew.
        boolean roomForMore = wortAmount + brewRoom(view) <= TANK_CAPACITY;
        int nextProgress = KettleLogic.advanceProgress(view, heatLevel, itemIds, waterAmount, brewProgressTicks);

        if (roomForMore && nextProgress > brewProgressTicks) {
            brewProgressTicks = nextProgress;
            setBrewingState(true);
            setChanged();
        } else {
            brewProgressTicks = 0;
            setBrewingState(roomForMore && waterAmount >= view.wortAmount());
        }

        if (brewProgressTicks < recipe.value().brewTimeTicks()) {
            return;
        }

        KettleLogic.Result result = KettleLogic.tryBrew(view, heatLevel, itemIds, waterAmount, brewProgressTicks);
        if (!result.brewed()) {
            return;
        }

        waterAmount -= view.wortAmount();
        wortAmount += result.wortAmount();
        result.consumed().forEach(this::consumeOne);
        brewProgressTicks = 0;
        setBrewingState(false);
        setChanged();
    }

    private void setBrewingState(boolean brewing) {
        BlockState state = getBlockState();
        if (state.hasProperty(KettleBlock.BREWING) && state.getValue(KettleBlock.BREWING) != brewing) {
            level.setBlock(worldPosition, state.setValue(KettleBlock.BREWING, brewing), 3);
        }
    }

    private void consumeOne(String itemId) {
        for (int slot = 0; slot < input.getSlots(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                continue;
            }
            // Shrink a copy: ItemStackHandler hands out the live stack, so mutating it in
            // place and then calling setStackInSlot would leave one object in two slots.
            ItemStack leftover = stack.copy();
            leftover.shrink(1);
            input.setStackInSlot(slot, leftover);
            return;
        }
    }

    private List<String> inputIds() {
        return inputContents().stream()
                .flatMap(stack -> java.util.stream.Stream.generate(() -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                        .limit(stack.getCount()))
                .toList();
    }

    /** Items from the input slots expanded to one entry per item, as the rules expect. */
    private List<ItemStack> inputContents() {
        return List.of(input.getStackInSlot(0), input.getStackInSlot(1),
                input.getStackInSlot(2), input.getStackInSlot(3)).stream()
                .filter(stack -> !stack.isEmpty())
                .toList();
    }

    @Nullable
    private RecipeHolder<BrewingRecipe> findRecipe(int heat) {
        if (heat <= KettleLogic.HEAT_NONE || level == null) {
            return null;
        }
        List<String> ids = inputIds();
        var recipes = level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.BREWING.get());
        for (RecipeHolder<BrewingRecipe> holder : recipes) {
            if (holder.value().matches(ids, heat)) {
                return holder;
            }
        }
        return null;
    }

    private static KettleLogic.RecipeView toView(BrewingRecipe recipe) {
        return new KettleLogic.RecipeView(recipe.inputs(), recipe.getWort().getFluid(),
                recipe.getWort().getAmount(), recipe.brewTimeTicks(), recipe.idealHeatLevel());
    }

    // ---- persistence ---------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", input.serializeNBT(registries));
        tag.putInt("Water", waterAmount);
        tag.putInt("Wort", wortAmount);
        tag.putInt("BrewProgress", brewProgressTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Input")) {
            input.deserializeNBT(registries, tag.getCompound("Input"));
        }
        waterAmount = tag.getInt("Water");
        wortAmount = tag.getInt("Wort");
        brewProgressTicks = tag.getInt("BrewProgress");
    }
}
