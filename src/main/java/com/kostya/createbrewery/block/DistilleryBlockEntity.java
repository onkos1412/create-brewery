package com.kostya.createbrewery.block;

import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.distillery.DistilleryLogic;
import com.kostya.createbrewery.registry.ModDataComponents;
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
import net.neoforged.neoforge.items.ItemStackHandler;

/** Server state for the first, manually loaded whiskey distillery. */
public class DistilleryBlockEntity extends BlockEntity {

    private static final int INPUT_WORT = DistilleryLogic.WORT_REQUIRED;
    private int wortAmount;
    private int inputStars = DrinkData.MAX_STARS;
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

    public DistilleryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DISTILLERY_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStackHandler getOutput() {
        return output;
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
                    && kettle.drainWort(INPUT_WORT) == INPUT_WORT) {
                wortAmount = INPUT_WORT;
                inputStars = DrinkData.MAX_STARS;
                progressTicks = 0;
                setChanged();
                return true;
            }
        }
        return false;
    }

    public void serverTick() {
        if (level == null || level.isClientSide() || wortAmount < INPUT_WORT
                || !output.getStackInSlot(0).isEmpty()) {
            return;
        }

        heatLevel = KettleBlockEntity.readHeatLevel(level, worldPosition.below());
        rpm = readRpm(level, worldPosition);
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
        distillate.set(ModDataComponents.DRINK_DATA.get(),
                new DrinkData(result.stars(), result.alcohol(), "distillate"));
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
        tag.putInt("Progress", progressTicks);
        tag.put("Output", output.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wortAmount = tag.getInt("Wort");
        inputStars = tag.contains("InputStars") ? tag.getInt("InputStars") : DrinkData.MAX_STARS;
        progressTicks = tag.getInt("Progress");
        if (tag.contains("Output")) {
            output.deserializeNBT(registries, tag.getCompound("Output"));
        }
    }
}
