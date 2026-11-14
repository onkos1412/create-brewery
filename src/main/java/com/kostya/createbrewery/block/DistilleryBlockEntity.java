package com.kostya.createbrewery.block;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.FluidUnits;
import com.kostya.createbrewery.distillery.DistilleryLogic;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** The distillery: a Create kinetic machine, driven through the shaft in its back. */
public class DistilleryBlockEntity extends KineticBlockEntity {

    private static final int INPUT_WORT = DistilleryLogic.WORT_REQUIRED;
    /** The cube counts in droplets, as Create Fabric's pipes do; one batch is still exactly 1000 mB. */
    private static final long INPUT_DROPLETS = FluidUnits.toDroplets(INPUT_WORT);
    private static final int SPOILED_ALCOHOL = 10;
    /** Wort in the cube, in droplets. */
    private long wortAmount;
    private int inputStars = DrinkData.MAX_STARS;
    private String wortDrink = DrinkProfile.BEER.id();
    /** Who brewed the wort in the cube; the first wort in signs the batch. */
    @Nullable
    private BrewerMark inputMark;
    private int progressTicks;
    private int heatLevel;

    private final MachineInventory output = new MachineInventory(1) {
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
    private final Tank tank = new Tank();

    public DistilleryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DISTILLERY_BLOCK_ENTITY.get(), pos, state);
    }

    /** The wort tank, which pipes fill and drain from any side, and which the column above hands on as well. */
    public SingleSlotStorage<FluidVariant> getFluidStorage() {
        return tank;
    }

    /** The distillate slot, which funnels empty from any side. */
    public MachineInventory getOutput() {
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

    /** Wort in the cube, in whole mB. */
    public int getWortAmount() {
        return (int) FluidUnits.toMb(wortAmount);
    }

    public long getWortDroplets() {
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
                wortAmount = INPUT_DROPLETS;
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
        if (wortAmount < INPUT_DROPLETS || !output.getStackInSlot(0).isEmpty()) {
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

        DistilleryLogic.Result result = DistilleryLogic.process(getWortAmount(), heatLevel, rpm, progressTicks,
                inputStars, drink.distillTicks(), drink.idealRpm());
        if (!result.distilled()) {
            return;
        }

        ItemStack distillate = new ItemStack(ModItems.DISTILLATE.get());
        DrinkData.set(distillate, result.spoiled()
                ? new DrinkData(DrinkData.MIN_STARS, SPOILED_ALCOHOL, DrinkData.SPOILED_STAGE)
                : new DrinkData(result.stars(), drink.distilledAlcohol(), drink.distilled()));
        if (inputMark != null) {
            BrewerMark.set(distillate, inputMark);
            // PORT(Task 5): the journal comes back with the brewer's book; Forge credited the brewer here:
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
        // Droplets. Fabric worlds never load Forge saves, so the key keeps its name with the finer unit.
        tag.putLong("Wort", wortAmount);
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
        wortAmount = tag.getLong("Wort");
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

    // ---- the Transfer API ------------------------------------------------------------

    /** What a transfer may change, and a transaction that is rolled back puts back. */
    private record TankSnapshot(long wort, int stars, String drink, @Nullable BrewerMark mark, int progress) {
    }

    /**
     * The wort tank. Pipes may pour in any amount of a distillable wort, the same drink as the batch inside, and
     * pump the batch back out, so a part batch or an unwanted drink never jams the cube. Every change is recorded
     * with its transaction, so Create's simulations leave the cube as it was.
     */
    private final class Tank extends SnapshotParticipant<TankSnapshot> implements SingleSlotStorage<FluidVariant> {
        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (!resource.getFluid().isSame(ModFluids.WORT.get())) {
                return 0;
            }
            DrinkData data = DrinkData.get(resource);
            String drink = data == null ? DrinkProfile.BEER.id() : data.stage();
            if (!canTake(drink)) {
                return 0;
            }
            long accepted = Math.min(maxAmount, INPUT_DROPLETS - wortAmount);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            int stars = data == null ? DrinkData.MAX_STARS : data.stars();
            // The weakest wort decides the batch, and the first one in signs it.
            inputStars = wortAmount > 0 ? Math.min(inputStars, stars) : stars;
            if (wortAmount == 0) {
                inputMark = BrewerMark.get(resource);
            }
            wortDrink = drink;
            wortAmount += accepted;
            return accepted;
        }

        /** Only the very wort inside comes out, the batch's drink, quality and brewer. */
        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (wortAmount <= 0 || !resource.equals(getResource())) {
                return 0;
            }
            long drained = Math.min(maxAmount, wortAmount);
            if (drained <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            wortAmount -= drained;
            if (wortAmount == 0) {
                inputMark = null;
            }
            progressTicks = 0;
            return drained;
        }

        @Override
        public boolean isResourceBlank() {
            return wortAmount <= 0;
        }

        @Override
        public FluidVariant getResource() {
            return wortAmount <= 0 ? FluidVariant.blank()
                    : KettleBlockEntity.wortVariant(inputStars, wortDrink, inputMark);
        }

        @Override
        public long getAmount() {
            return wortAmount;
        }

        @Override
        public long getCapacity() {
            return INPUT_DROPLETS;
        }

        @Override
        protected TankSnapshot createSnapshot() {
            return new TankSnapshot(wortAmount, inputStars, wortDrink, inputMark, progressTicks);
        }

        @Override
        protected void readSnapshot(TankSnapshot snapshot) {
            wortAmount = snapshot.wort();
            inputStars = snapshot.stars();
            wortDrink = snapshot.drink();
            inputMark = snapshot.mark();
            progressTicks = snapshot.progress();
        }

        @Override
        protected void onFinalCommit() {
            setChanged();
        }
    }
}
