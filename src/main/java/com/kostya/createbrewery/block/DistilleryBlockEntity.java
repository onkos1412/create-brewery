package com.kostya.createbrewery.block;

import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.distillery.DistilleryLogic;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Server state for the first, manually loaded whiskey distillery. */
public class DistilleryBlockEntity extends BlockEntity {

    private static final int INPUT_WORT = DistilleryLogic.WORT_REQUIRED;
    private int wortAmount;
    private int inputStars = DrinkData.MAX_STARS;
    private String wortDrink = DrinkProfile.BEER.id();
    private int progressTicks;
    private int heatLevel;
    private float rpm;

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

    /** Pipes can only fill the wort tank; the batch leaves as a distillate item. */
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return wortAmount > 0 ? KettleBlockEntity.wortStack(wortAmount, inputStars, wortDrink) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return INPUT_WORT;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.is(ModFluids.WORT.get());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource)) {
                return 0;
            }
            DrinkData data = resource.get(ModDataComponents.DRINK_DATA.get());
            String drink = data == null ? DrinkProfile.BEER.id() : data.stage();
            if (!canTake(drink)) {
                return 0;
            }
            int accepted = Math.min(resource.getAmount(), INPUT_WORT - wortAmount);
            if (accepted > 0 && action.execute()) {
                int stars = data == null ? DrinkData.MAX_STARS : data.stars();
                inputStars = wortAmount > 0 ? Math.min(inputStars, stars) : stars;
                wortDrink = drink;
                wortAmount += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    public DistilleryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DISTILLERY_BLOCK_ENTITY.get(), pos, state);
    }

    public IFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    public ItemStackHandler getOutput() {
        return output;
    }

    /** The cube runs one distillable wort at a time. */
    private boolean canTake(String drink) {
        return level != null && (wortAmount == 0 || drink.equals(wortDrink))
                && BrewingRecipe.profileOf(level, drink).canDistill();
    }

    /**
     * Bottles a finished distillate that needs no aging, like vodka.
     *
     * @return the bottle, or {@link ItemStack#EMPTY} when there is nothing to pour or it belongs in a barrel
     */
    public ItemStack takeFinishedBottle() {
        ItemStack distillate = output.getStackInSlot(0);
        if (distillate.isEmpty() || level == null) {
            return ItemStack.EMPTY;
        }
        DrinkData data = distillate.get(ModDataComponents.DRINK_DATA.get());
        if (data == null || BrewingRecipe.profileDistilledTo(level, data.stage()).canAge()) {
            return ItemStack.EMPTY;
        }
        output.extractItem(0, 1, false);
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        bottle.set(ModDataComponents.DRINK_DATA.get(), data);
        return bottle;
    }

    public int getInputStars() {
        return inputStars;
    }

    public int getWortAmount() {
        return wortAmount;
    }

    public int getProgressTicks() {
        return progressTicks;
    }

    public int getHeatLevel() {
        return heatLevel;
    }

    public float getRpm() {
        return rpm;
    }

    /** Pulls one 1000 mB batch from an adjacent kettle, if the cube is empty. */
    public boolean loadFromKettle() {
        if (wortAmount > 0 || !output.getStackInSlot(0).isEmpty() || level == null) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(direction));
            if (neighbour instanceof KettleBlockEntity kettle
                    && kettle.getWortAmount() >= INPUT_WORT
                    && canTake(kettle.getWortDrink())
                    && kettle.drainWort(INPUT_WORT) == INPUT_WORT) {
                wortAmount = INPUT_WORT;
                inputStars = kettle.getWortStars();
                wortDrink = kettle.getWortDrink();
                progressTicks = 0;
                setChanged();
                return true;
            }
        }
        return false;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Read before the early return, so the GUI shows live values while the cube is idle.
        heatLevel = KettleBlockEntity.readHeatLevel(level, worldPosition.below());
        rpm = readRpm(level, worldPosition);
        if (wortAmount < INPUT_WORT || !output.getStackInSlot(0).isEmpty()) {
            return;
        }
        if (!com.kostya.createbrewery.distillery.DistillingRules.canDistill(
                heatLevel, DistilleryLogic.IDEAL_HEAT, rpm)) {
            return;
        }

        progressTicks++;
        if (progressTicks < DistilleryLogic.IDEAL_TICKS) {
            setChanged();
            return;
        }

        DistilleryLogic.Result result = DistilleryLogic.process(
                wortAmount, heatLevel, rpm, progressTicks, inputStars);
        if (!result.distilled()) {
            return;
        }

        ItemStack distillate = new ItemStack(ModItems.DISTILLATE.get());
        DrinkProfile drink = BrewingRecipe.profileOf(level, wortDrink);
        distillate.set(ModDataComponents.DRINK_DATA.get(),
                new DrinkData(result.stars(), drink.distilledAlcohol(), drink.distilled()));
        output.setStackInSlot(0, distillate);
        wortAmount = 0;
        progressTicks = 0;
        setChanged();
    }

    private static float readRpm(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockEntity neighbour = level.getBlockEntity(pos.relative(direction));
            if (neighbour instanceof KineticBlockEntity kinetic) {
                return Math.abs(kinetic.getSpeed());
            }
        }
        return 0f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Wort", wortAmount);
        tag.putInt("InputStars", inputStars);
        tag.putString("WortDrink", wortDrink);
        tag.putInt("Progress", progressTicks);
        tag.put("Output", output.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wortAmount = tag.getInt("Wort");
        inputStars = tag.contains("InputStars") ? tag.getInt("InputStars") : DrinkData.MAX_STARS;
        wortDrink = tag.contains("WortDrink") ? tag.getString("WortDrink") : DrinkProfile.BEER.id();
        progressTicks = tag.getInt("Progress");
        if (tag.contains("Output")) {
            output.deserializeNBT(registries, tag.getCompound("Output"));
        }
    }
}
