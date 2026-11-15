package com.kostya.createbrewery.block;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.FluidUnits;
import com.kostya.createbrewery.journal.BreweryJournalData;
import com.kostya.createbrewery.journal.KettleRecord;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.recipe.MixRules;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import com.kostya.createbrewery.registry.ModFluids;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import io.github.fabricators_of_create.porting_lib.block.CustomDataPacketHandlingBlockEntity;
import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The kettle's server-side state: water, wort, the waiting ingredients and brewing
 * progress.
 *
 * <p>Every server tick it reads the heat source beneath the kettle, finds the
 * {@link BrewingRecipe} the whole mix in the slots resembles most, advances progress and,
 * once the brew time has elapsed, eats the mix and turns the water into that recipe's
 * wort, or into muddy brew when the mix resembles nothing.
 *
 * <p>The tanks count in droplets, as Create Fabric's pipes do, so whatever amount a pipe moves is kept exactly;
 * brewing rules still count in mB and are converted with {@link FluidUnits}.
 */
public class KettleBlockEntity extends BlockEntity implements CustomDataPacketHandlingBlockEntity {

    /** How much water the kettle holds, in milli-buckets. */
    public static final int TANK_CAPACITY = 4000;
    /** How much wort a finished brew yields. */
    public static final int WORT_PER_BREW = 1000;
    private static final long CAPACITY_DROPLETS = FluidUnits.toDroplets(TANK_CAPACITY);
    private static final int WATER_COLOR = 0x3F76E4;
    /** The bare inside of the kettle, once its liquid has been tinted down to it. */
    public static final int EMPTY_COLOR = 0x2A1610;

    /** Muddy brew has no recipe, so it takes the usual water and time. */
    private static final int MUDDY_AMOUNT = 1000;
    private static final int MUDDY_BREW_TICKS = 200;

    private final MachineInventory input = new MachineInventory(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** Pipes fill the water tank and drain the wort tank (see {@link Tanks}). */
    private final Tanks tanks = new Tanks();

    /** Water in the tank, in droplets. */
    private long waterAmount;
    /** Wort in the tank, in droplets. */
    private long wortAmount;
    private int wortStars = DrinkData.MAX_STARS;
    private String wortDrink = DrinkProfile.BEER.id();
    /** Who brewed the wort in the tank and on which day; {@code null} when nobody signed it or it is empty. */
    @Nullable
    private BrewerMark wortMark;
    /** The player who placed the kettle; brews made in it carry their name. */
    @Nullable
    private UUID ownerId;
    @Nullable
    private String ownerName;
    /** The colour clients were last told about, so they are only told when it changes. */
    private int sentLiquidColor = EMPTY_COLOR;
    private int brewProgressTicks;
    private int brewTimeTicks = 200;
    private int brewStars = DrinkData.MAX_STARS;
    /** The mix last tick, so a change to it restarts the brew; saved, so a reload does not. */
    private List<String> contents = List.of();
    /**
     * The recipe id (or muddy) the mix brewed by last tick, so another winner restarts the brew.
     * Not saved: after a reload it is unknown, and an unknown winner keeps the progress.
     */
    @Nullable
    private String winner;
    private int aroma;
    /** Why the mix over the fire is not brewing, one of {@code KettleLogic.BLOCKED_*}. */
    private int blockReason = KettleLogic.BLOCKED_NONE;
    private int heatLevel;

    public KettleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.KETTLE_BLOCK_ENTITY.get(), pos, state);
    }

    public MachineInventory getInput() {
        return input;
    }

    /** The water and wort tanks as pipes see them, from every side. */
    public Storage<FluidVariant> getFluidStorage() {
        return tanks;
    }

    public int getWortStars() {
        return wortStars;
    }

    public String getWortDrink() {
        return wortDrink;
    }

    @Nullable
    public BrewerMark getWortMark() {
        return wortMark;
    }

    /** Makes the player the kettle's owner, who signs every brew made in it from now on. */
    public void setOwner(UUID id, String name) {
        ownerId = id;
        ownerName = name;
        setChanged();
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    /**
     * Wort carries its quality, drink and brewer in its fluid variant, so they survive Create's pipes, and two
     * different worts never pass for one another in a tank.
     */
    public static FluidVariant wortVariant(int stars, String drink, @Nullable BrewerMark mark) {
        FluidVariant wort = DrinkData.with(FluidVariant.of(ModFluids.WORT.get()), new DrinkData(stars, 0, drink));
        return mark == null ? wort : BrewerMark.with(wort, mark);
    }

    /** {@code amount} mB of that wort, held in droplets. */
    public static FluidStack wortStack(int amount, int stars, String drink, @Nullable BrewerMark mark) {
        return new FluidStack(wortVariant(stars, drink, mark), FluidUnits.toDroplets(amount));
    }

    /** Water in the kettle, in whole mB. */
    public int getWaterAmount() {
        return (int) FluidUnits.toMb(waterAmount);
    }

    /** Wort in the kettle, in whole mB. */
    public int getWortAmount() {
        return (int) FluidUnits.toMb(wortAmount);
    }

    public long getWaterDroplets() {
        return waterAmount;
    }

    public long getWortDroplets() {
        return wortAmount;
    }

    public int getBrewProgressTicks() {
        return brewProgressTicks;
    }

    /** How long the current brew takes, for the GUI. */
    public int getBrewTimeTicks() {
        return brewTimeTicks;
    }

    /** How close the brewing mix smells to a recipe, from {@link MixRules#AROMA_WEAK} up; 0 when not brewing. */
    public int getAroma() {
        return aroma;
    }

    /** What a mix over a fire is waiting for before it can brew, for the GUI. */
    public int getBlockReason() {
        return blockReason;
    }

    /** The wort's colour, whether or not there is any wort. */
    public int getWortColor() {
        return level == null ? DrinkProfile.BEER.color() : BrewingRecipe.profileOf(level, wortDrink).color();
    }

    /** What the surface in the kettle looks like: wort over water, or the kettle's own bottom. */
    public int getLiquidColor() {
        if (wortAmount > 0) {
            return getWortColor();
        }
        return waterAmount > 0 ? WATER_COLOR : EMPTY_COLOR;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * A client just learned what is inside: the surface was baked with the old colour. Porting Lib hands the
     * packet here in place of the vanilla load, so it is loaded here first, as Forge's default did.
     */
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            load(tag);
        }
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    public int getHeatLevel() {
        return heatLevel;
    }

    /** Adds water to the tank, in mB (used by right-clicking with a water bucket); returns how much went in. */
    public int fillWater(int amount) {
        int accepted = (int) Math.min(amount, FluidUnits.toMb(CAPACITY_DROPLETS - waterAmount - wortAmount));
        if (accepted <= 0) {
            return 0;
        }
        waterAmount += FluidUnits.toDroplets(accepted);
        setChanged();
        return accepted;
    }

    /** Drains up to {@code amount} mB of brewed wort; returns how many whole mB were removed. */
    public int drainWort(int amount) {
        long drained = Math.min(FluidUnits.toDroplets(amount), wortAmount);
        if (drained <= 0) {
            return 0;
        }
        wortAmount -= drained;
        if (wortAmount == 0) {
            wortMark = null;
        }
        setChanged();
        return (int) FluidUnits.toMb(drained);
    }

    /**
     * Turns one of the stored wort into a bottle of the finished drink.
     *
     * @return the filled bottle, or {@link ItemStack#EMPTY} when there is no wort
     */
    public ItemStack bottleWort() {
        if (wortAmount < FluidUnits.toDroplets(WORT_PER_BREW) || level == null) {
            return ItemStack.EMPTY;
        }
        BrewerMark mark = wortMark;
        wortAmount -= FluidUnits.toDroplets(WORT_PER_BREW);
        if (wortAmount == 0) {
            wortMark = null;
        }
        setChanged();
        return WortBottling.bottle(level, wortStars, wortDrink, mark);
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

        int color = getLiquidColor();
        if (color != sentLiquidColor) {
            sentLiquidColor = color;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }

        heatLevel = readHeatLevel(level, worldPosition.below());
        // A different mix is a different brew: the count starts again so the change is judged too.
        List<String> mix = inputIds();
        if (!mix.equals(contents)) {
            contents = mix;
            brewProgressTicks = 0;
            setChanged();
        }
        if (heatLevel <= KettleLogic.HEAT_NONE || mix.isEmpty()) {
            stopBrewing();
            return;
        }

        Brew brew = chooseBrew(mix);
        // The progress belongs to one recipe: if another wins (say the fire changed), it starts over,
        // and so does the worst heat, which is taken afresh on the first tick.
        if (KettleLogic.brewRestarts(winner, brew.id())) {
            brewProgressTicks = 0;
        }
        winner = brew.id();
        // The tank holds one wort at a time: a different drink waits until it has been drawn off.
        boolean sameWort = wortAmount == 0 || brew.drink().equals(wortDrink);
        boolean roomForMore = sameWort
                && wortAmount + FluidUnits.toDroplets(Math.max(brew.amount(), WORT_PER_BREW)) <= CAPACITY_DROPLETS;
        int blocked = KettleLogic.blockReason(!roomForMore, waterAmount >= FluidUnits.toDroplets(brew.amount()));
        if (blocked != KettleLogic.BLOCKED_NONE) {
            stopBrewing();
            blockReason = blocked;
            return;
        }

        blockReason = KettleLogic.BLOCKED_NONE;
        brewProgressTicks++;
        brewTimeTicks = brew.ticks();
        aroma = brew.aroma();
        // The worst heat of the whole brew counts, not just the heat on its last tick.
        brewStars = brewProgressTicks == 1 ? brew.heatStars(heatLevel) : Math.min(brewStars, brew.heatStars(heatLevel));
        setBrewingState(true);
        setChanged();
        if (brewProgressTicks < brew.ticks()) {
            return;
        }

        int stars = brew.stars(brewStars);
        // Batches share one tank, so the weakest one decides the quality of the lot.
        wortStars = wortAmount > 0 ? Math.min(wortStars, stars) : stars;
        // The first brew in the tank signs the lot; later batches only top it up.
        if (wortAmount == 0) {
            wortMark = ownerName == null ? null
                    : new BrewerMark(ownerName, BrewerMark.dayOf(level.getDayTime()), Optional.ofNullable(ownerId));
        }
        // The owner's book keeps this brew's own result, not the tank's weakest.
        if (ownerId != null && !DrinkProfile.MUDDY.id().equals(brew.drink()) && level.getServer() != null) {
            BreweryJournalData.get(level.getServer()).recordKettle(ownerId, brew.drink(),
                    new KettleRecord(KettleRecord.mixOf(contents), heatLevel, stars));
        }
        wortDrink = brew.drink();
        waterAmount -= FluidUnits.toDroplets(brew.amount());
        wortAmount += FluidUnits.toDroplets(brew.amount());
        for (int slot = 0; slot < input.getSlotCount(); slot++) {
            input.setStackInSlot(slot, ItemStack.EMPTY);
        }
        contents = List.of();
        stopBrewing();
    }

    private void stopBrewing() {
        brewProgressTicks = 0;
        aroma = 0;
        blockReason = KettleLogic.BLOCKED_NONE;
        setBrewingState(false);
    }

    /** Brews the mix by the recipe it resembles most, or into muddy brew when it resembles none. */
    private Brew chooseBrew(List<String> mix) {
        List<BrewingRecipe> recipes = level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.BREWING.get());
        List<MixRules.Candidate> candidates = recipes.stream()
                .map(recipe -> new MixRules.Candidate(recipe.getId().toString(), recipe.inputs(),
                        recipe.idealHeatLevel()))
                .toList();
        MixRules.Choice choice = MixRules.best(candidates, mix, heatLevel);
        if (choice.index() < 0) {
            return new Brew(DrinkProfile.MUDDY.id(), null, choice.score());
        }
        BrewingRecipe recipe = recipes.get(choice.index());
        return new Brew(recipe.getId().toString(), recipe, choice.score());
    }

    /**
     * What the current mix brews into.
     *
     * @param id     the winning recipe's id, or the muddy brew's
     * @param recipe the closest recipe, or {@code null} for muddy brew
     * @param score  how close the mix is to that recipe
     */
    private record Brew(String id, @Nullable BrewingRecipe recipe, int score) {
        String drink() {
            return recipe == null ? DrinkProfile.MUDDY.id() : recipe.drink().id();
        }

        /** Water used up by one brew, in mB, which is also the wort it makes. */
        int amount() {
            return recipe == null ? MUDDY_AMOUNT : recipe.getWort().getAmount();
        }

        int ticks() {
            return recipe == null ? MUDDY_BREW_TICKS : recipe.brewTimeTicks();
        }

        int aroma() {
            return recipe == null ? MixRules.AROMA_WEAK : MixRules.aroma(score);
        }

        int heatStars(int heat) {
            return recipe == null ? DrinkData.MIN_STARS : KettleLogic.brewStars(heat, recipe.idealHeatLevel());
        }

        /** Muddy brew is always the worst; anything else is as good as its mix and its fire allow. */
        int stars(int worstHeatStars) {
            return recipe == null ? DrinkData.MIN_STARS : KettleLogic.mixStars(score, worstHeatStars);
        }
    }

    private void setBrewingState(boolean brewing) {
        BlockState state = getBlockState();
        if (state.hasProperty(KettleBlock.BREWING) && state.getValue(KettleBlock.BREWING) != brewing) {
            level.setBlock(worldPosition, state.setValue(KettleBlock.BREWING, brewing), 3);
        }
    }

    /** Every item in the slots, one entry each and sorted, so the same mix always reads the same. */
    private List<String> inputIds() {
        return inputContents().stream()
                .flatMap(stack -> java.util.stream.Stream.generate(() -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                        .limit(stack.getCount()))
                .sorted()
                .toList();
    }

    private List<ItemStack> inputContents() {
        return List.of(input.getStackInSlot(0), input.getStackInSlot(1),
                input.getStackInSlot(2), input.getStackInSlot(3)).stream()
                .filter(stack -> !stack.isEmpty())
                .toList();
    }

    // ---- persistence ---------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Input", input.serializeNBT());
        // Droplets. Fabric worlds never load Forge saves, so the keys keep their names with the finer unit.
        tag.putLong("Water", waterAmount);
        tag.putLong("Wort", wortAmount);
        tag.putInt("WortStars", wortStars);
        tag.putString("WortDrink", wortDrink);
        if (wortMark != null) {
            BrewerMark.CODEC.encodeStart(NbtOps.INSTANCE, wortMark).result().ifPresent(mark -> tag.put("WortBrewer", mark));
        }
        if (ownerId != null) {
            tag.putUUID("OwnerId", ownerId);
            tag.putString("OwnerName", ownerName == null ? "" : ownerName);
        }
        tag.putInt("BrewProgress", brewProgressTicks);
        tag.putInt("BrewStars", brewStars);
        ListTag mix = new ListTag();
        contents.forEach(id -> mix.add(StringTag.valueOf(id)));
        tag.put("Contents", mix);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Input")) {
            input.deserializeNBT(tag.getCompound("Input"));
        }
        waterAmount = tag.getLong("Water");
        wortAmount = tag.getLong("Wort");
        wortStars = tag.contains("WortStars") ? tag.getInt("WortStars") : DrinkData.MAX_STARS;
        wortDrink = tag.contains("WortDrink") ? tag.getString("WortDrink") : DrinkProfile.BEER.id();
        wortMark = tag.contains("WortBrewer")
                ? BrewerMark.CODEC.parse(NbtOps.INSTANCE, tag.get("WortBrewer")).result().orElse(null)
                : null;
        ownerId = tag.hasUUID("OwnerId") ? tag.getUUID("OwnerId") : null;
        String savedName = tag.getString("OwnerName");
        ownerName = ownerId == null || savedName.isBlank() ? null : savedName;
        brewProgressTicks = tag.getInt("BrewProgress");
        brewStars = tag.contains("BrewStars") ? tag.getInt("BrewStars") : DrinkData.MAX_STARS;
        ListTag mix = tag.getList("Contents", Tag.TAG_STRING);
        contents = java.util.stream.IntStream.range(0, mix.size()).mapToObj(mix::getString).toList();
    }

    // ---- the Transfer API ------------------------------------------------------------

    /** The wort in the tank as a fluid, carrying its drink, quality and brewer. */
    private FluidVariant currentWort() {
        return wortVariant(wortStars, wortDrink, wortMark);
    }

    /** What a transfer may change, and a transaction that is rolled back puts back. */
    private record TankSnapshot(long water, long wort, @Nullable BrewerMark mark) {
    }

    /**
     * The water tank and the wort tank as one storage, the same from every side: water goes in, the wort comes out.
     * Every change is recorded with the transaction it happens in, so the simulations Create runs (transactions it
     * aborts) leave the kettle exactly as it was.
     */
    private final class Tanks extends SnapshotParticipant<TankSnapshot> implements Storage<FluidVariant> {
        private final List<StorageView<FluidVariant>> views = List.of(new TankView(true), new TankView(false));

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (!resource.getFluid().isSame(Fluids.WATER)) {
                return 0;
            }
            long accepted = Math.min(maxAmount, CAPACITY_DROPLETS - waterAmount - wortAmount);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            waterAmount += accepted;
            return accepted;
        }

        /** Only the very wort inside comes out: drink, quality and brewer have to match. */
        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (wortAmount <= 0 || !resource.equals(currentWort())) {
                return 0;
            }
            long drained = Math.min(maxAmount, wortAmount);
            if (drained <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            wortAmount -= drained;
            if (wortAmount == 0) {
                wortMark = null;
            }
            return drained;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            return views.iterator();
        }

        @Override
        protected TankSnapshot createSnapshot() {
            return new TankSnapshot(waterAmount, wortAmount, wortMark);
        }

        @Override
        protected void readSnapshot(TankSnapshot snapshot) {
            waterAmount = snapshot.water();
            wortAmount = snapshot.wort();
            wortMark = snapshot.mark();
        }

        @Override
        protected void onFinalCommit() {
            setChanged();
        }
    }

    /** One of the two tanks, read live. Water cannot be pumped back out, as on Forge. */
    private final class TankView implements StorageView<FluidVariant> {
        private final boolean water;

        TankView(boolean water) {
            this.water = water;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            return water ? 0 : tanks.extract(resource, maxAmount, transaction);
        }

        @Override
        public boolean isResourceBlank() {
            return getAmount() <= 0;
        }

        @Override
        public FluidVariant getResource() {
            if (isResourceBlank()) {
                return FluidVariant.blank();
            }
            return water ? FluidVariant.of(Fluids.WATER) : currentWort();
        }

        @Override
        public long getAmount() {
            return water ? waterAmount : wortAmount;
        }

        @Override
        public long getCapacity() {
            return CAPACITY_DROPLETS;
        }
    }
}
