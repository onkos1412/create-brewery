package com.kostya.createbrewery.block;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.distillery.DistilleryLogic;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The distillery: a Create kinetic machine, driven through the shaft in its back. */
public class DistilleryBlockEntity extends KineticBlockEntity {

    private static final int INPUT_WORT = DistilleryLogic.WORT_REQUIRED;
    private static final int SPOILED_ALCOHOL = 10;
    private int wortAmount;
    private int inputStars = DrinkData.MAX_STARS;
    private String wortDrink = DrinkProfile.BEER.id();
    /** Who brewed the wort in the cube; the first wort in signs the batch. */
    @Nullable
    private BrewerMark inputMark;
    private int progressTicks;
    private int heatLevel;

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

    /** Pipes fill the wort tank; a finished batch leaves as a distillate item. */
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return wortAmount > 0 ? KettleBlockEntity.wortStack(wortAmount, inputStars, wortDrink, inputMark) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return INPUT_WORT;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.getFluid().isSame(ModFluids.WORT.get());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource)) {
                return 0;
            }
            DrinkData data = DrinkData.get(resource);
            String drink = data == null ? DrinkProfile.BEER.id() : data.stage();
            if (!canTake(drink)) {
                return 0;
            }
            int accepted = Math.min(resource.getAmount(), INPUT_WORT - wortAmount);
            if (accepted > 0 && action.execute()) {
                int stars = data == null ? DrinkData.MAX_STARS : data.stars();
                inputStars = wortAmount > 0 ? Math.min(inputStars, stars) : stars;
                if (wortAmount == 0) {
                    inputMark = BrewerMark.get(resource);
                }
                wortDrink = drink;
                wortAmount += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return isFluidValid(0, resource) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        /** A pump can pull wort back out, so a part batch or an unwanted drink never jams the cube. */
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            int drained = Math.min(maxDrain, wortAmount);
            if (drained <= 0) {
                return FluidStack.EMPTY;
            }
            FluidStack stack = KettleBlockEntity.wortStack(drained, inputStars, wortDrink, inputMark);
            if (action.execute()) {
                wortAmount -= drained;
                if (wortAmount == 0) {
                    inputMark = null;
                }
                progressTicks = 0;
                setChanged();
            }
            return stack;
        }
    };

    private final LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> fluidHandler);
    private final LazyOptional<ItemStackHandler> itemCapability = LazyOptional.of(() -> output);

    public DistilleryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DISTILLERY_BLOCK_ENTITY.get(), pos, state);
    }

    /** Pipes fill and drain the wort from any side; funnels take the distillate. */
    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        LazyOptional<T> machine = getMachineCapability(cap);
        return machine.isPresent() ? machine : super.getCapability(cap, side);
    }

    /** The wort tank and the distillate slot, which the column above hands on as well. */
    <T> LazyOptional<T> getMachineCapability(Capability<T> cap) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCapability.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCapability.invalidate();
        itemCapability.invalidate();
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
        DrinkData data = DrinkData.get(distillate);
        if (data == null || !data.isSpoiled() && BrewingRecipe.profileDistilledTo(level, data.stage()).canAge()) {
            return ItemStack.EMPTY;
        }
        BrewerMark mark = BrewerMark.get(distillate);
        output.extractItem(0, 1, false);
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkData.set(bottle, data);
        if (mark != null) {
            BrewerMark.set(bottle, mark);
        }
        return bottle;
    }

    /** The settings of the wort inside, for the GUI. */
    public DrinkProfile getProfile() {
        return level == null ? DrinkProfile.BEER : BrewingRecipe.profileOf(level, wortDrink);
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
        return Math.abs(getSpeed());
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
                    && canTake(kettle.getWortDrink())) {
                // Read before draining: taking the last of the kettle's wort clears its mark.
                BrewerMark mark = kettle.getWortMark();
                if (kettle.drainWort(INPUT_WORT) != INPUT_WORT) {
                    continue;
                }
                wortAmount = INPUT_WORT;
                inputStars = kettle.getWortStars();
                inputMark = mark;
                wortDrink = kettle.getWortDrink();
                progressTicks = 0;
                setChanged();
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) {
            return;
        }
        float rpm = getRpm();

        // Read before the early return, so the GUI shows live values while the cube is idle.
        heatLevel = KettleBlockEntity.readHeatLevel(level, worldPosition.below());
        if (wortAmount < INPUT_WORT || !output.getStackInSlot(0).isEmpty()) {
            return;
        }
        if (!com.kostya.createbrewery.distillery.DistillingRules.canDistill(
                heatLevel, DistilleryLogic.IDEAL_HEAT, rpm)) {
            return;
        }

        DrinkProfile drink = BrewingRecipe.profileOf(level, wortDrink);
        progressTicks++;
        if (progressTicks < drink.distillTicks()) {
            setChanged();
            return;
        }

        DistilleryLogic.Result result = DistilleryLogic.process(
                wortAmount, heatLevel, rpm, progressTicks, inputStars, drink.distillTicks(), drink.idealRpm());
        if (!result.distilled()) {
            return;
        }

        ItemStack distillate = new ItemStack(ModItems.DISTILLATE.get());
        DrinkData.set(distillate, result.spoiled()
                ? new DrinkData(DrinkData.MIN_STARS, SPOILED_ALCOHOL, DrinkData.SPOILED_STAGE)
                : new DrinkData(result.stars(), drink.distilledAlcohol(), drink.distilled()));
        if (inputMark != null) {
            BrewerMark.set(distillate, inputMark);
            // PORT: Task 5 - the journal comes back with the brewer's book:
            // if (!result.spoiled() && inputMark.owner().isPresent() && level.getServer() != null) {
            //     BreweryJournalData.get(level.getServer()).recordStill(inputMark.owner().get(), wortDrink,
            //             new StillRecord(Math.round(Math.abs(rpm)), result.stars()));
            // }
        }
        output.setStackInSlot(0, distillate);
        wortAmount = 0;
        inputMark = null;
        progressTicks = 0;
        setChanged();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putInt("Wort", wortAmount);
        tag.putInt("InputStars", inputStars);
        tag.putString("WortDrink", wortDrink);
        if (inputMark != null) {
            BrewerMark.CODEC.encodeStart(NbtOps.INSTANCE, inputMark).result().ifPresent(mark -> tag.put("InputBrewer", mark));
        }
        tag.putInt("Progress", progressTicks);
        tag.put("Output", output.serializeNBT());
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        wortAmount = tag.getInt("Wort");
        inputStars = tag.contains("InputStars") ? tag.getInt("InputStars") : DrinkData.MAX_STARS;
        wortDrink = tag.contains("WortDrink") ? tag.getString("WortDrink") : DrinkProfile.BEER.id();
        inputMark = tag.contains("InputBrewer")
                ? BrewerMark.CODEC.parse(NbtOps.INSTANCE, tag.get("InputBrewer")).result().orElse(null)
                : null;
        progressTicks = tag.getInt("Progress");
        if (tag.contains("Output")) {
            output.deserializeNBT(tag.getCompound("Output"));
        }
    }
}
