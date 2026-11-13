package com.kostya.createbrewery.block;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.recipe.MixRules;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModFluids;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The kettle's server-side state: water, wort, the waiting ingredients and brewing
 * progress.
 *
 * <p>Every server tick it reads the heat source beneath the kettle, finds the
 * {@link BrewingRecipe} the whole mix in the slots resembles most, advances progress and,
 * once the brew time has elapsed, eats the mix and turns the water into that recipe's
 * wort, or into muddy brew when the mix resembles nothing.
 */
public class KettleBlockEntity extends BlockEntity {

    /** How much water the kettle holds, in milli-buckets. */
    public static final int TANK_CAPACITY = 4000;
    /** How much wort a finished brew yields. */
    public static final int WORT_PER_BREW = 1000;
    private static final int WATER_COLOR = 0x3F76E4;
    /** The bare inside of the kettle, once its liquid has been tinted down to it. */
    public static final int EMPTY_COLOR = 0x2A1610;

    /** Muddy brew has no recipe, so it takes the usual water and time. */
    private static final int MUDDY_AMOUNT = 1000;
    private static final int MUDDY_BREW_TICKS = 200;

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
            return wortAmount > 0 ? wortStack(wortAmount, wortStars, wortDrink, wortMark) : FluidStack.EMPTY;
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
            int stars = wortStars;
            // Read first: draining the last of it clears the mark.
            BrewerMark mark = wortMark;
            int drained = action.execute() ? drainWort(maxDrain) : Math.min(maxDrain, wortAmount);
            return drained > 0 ? wortStack(drained, stars, wortDrink, mark) : FluidStack.EMPTY;
        }
    };

    private int waterAmount;
    private int wortAmount;
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

    public ItemStackHandler getInput() {
        return input;
    }

    public IFluidHandler getFluidHandler() {
        return fluidHandler;
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

    /** Wort carries its quality, drink and brewer on the stack, so they survive Create's pipes. */
    public static FluidStack wortStack(int amount, int stars, String drink, @Nullable BrewerMark mark) {
        FluidStack stack = new FluidStack(ModFluids.WORT.get(), amount);
        stack.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(stars, 0, drink));
        if (mark != null) {
            stack.set(ModDataComponents.BREWER.get(), mark);
        }
        return stack;
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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** A client just learned what is inside: the surface was baked with the old colour. */
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(connection, packet, registries);
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
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
        if (wortAmount == 0) {
            wortMark = null;
        }
        setChanged();
        return drained;
    }

    /**
     * Turns one of the stored wort into a bottle of the finished drink.
     *
     * @return the filled bottle, or {@link ItemStack#EMPTY} when there is no wort
     */
    public ItemStack bottleWort() {
        if (wortAmount < WORT_PER_BREW || level == null) {
            return ItemStack.EMPTY;
        }
        BrewerMark mark = wortMark;
        wortAmount -= WORT_PER_BREW;
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
        boolean roomForMore = sameWort && wortAmount + Math.max(brew.amount(), WORT_PER_BREW) <= TANK_CAPACITY;
        int blocked = KettleLogic.blockReason(!roomForMore, waterAmount >= brew.amount());
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
            wortMark = ownerName == null ? null : new BrewerMark(ownerName, BrewerMark.dayOf(level.getDayTime()));
        }
        wortDrink = brew.drink();
        waterAmount -= brew.amount();
        wortAmount += brew.amount();
        for (int slot = 0; slot < input.getSlots(); slot++) {
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
        List<RecipeHolder<BrewingRecipe>> recipes = level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.BREWING.get());
        List<MixRules.Candidate> candidates = recipes.stream()
                .map(holder -> new MixRules.Candidate(holder.id().toString(), holder.value().inputs(),
                        holder.value().idealHeatLevel()))
                .toList();
        MixRules.Choice choice = MixRules.best(candidates, mix, heatLevel);
        if (choice.index() < 0) {
            return new Brew(DrinkProfile.MUDDY.id(), null, choice.score());
        }
        RecipeHolder<BrewingRecipe> holder = recipes.get(choice.index());
        return new Brew(holder.id().toString(), holder.value(), choice.score());
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

        /** Water used up by one brew, which is also the wort it makes. */
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", input.serializeNBT(registries));
        tag.putInt("Water", waterAmount);
        tag.putInt("Wort", wortAmount);
        tag.putInt("WortStars", wortStars);
        tag.putString("WortDrink", wortDrink);
        if (wortMark != null) {
            BrewerMark.CODEC.encodeStart(NbtOps.INSTANCE, wortMark).ifSuccess(mark -> tag.put("WortBrewer", mark));
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
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Input")) {
            input.deserializeNBT(registries, tag.getCompound("Input"));
        }
        waterAmount = tag.getInt("Water");
        wortAmount = tag.getInt("Wort");
        wortStars = tag.contains("WortStars") ? tag.getInt("WortStars") : DrinkData.MAX_STARS;
        wortDrink = tag.contains("WortDrink") ? tag.getString("WortDrink") : DrinkProfile.BEER.id();
        wortMark = tag.contains("WortBrewer")
                ? BrewerMark.CODEC.parse(NbtOps.INSTANCE, tag.get("WortBrewer")).result().orElse(null)
                : null;
        ownerId = tag.hasUUID("OwnerId") ? tag.getUUID("OwnerId") : null;
        ownerName = ownerId == null ? null : tag.getString("OwnerName");
        brewProgressTicks = tag.getInt("BrewProgress");
        brewStars = tag.contains("BrewStars") ? tag.getInt("BrewStars") : DrinkData.MAX_STARS;
        ListTag mix = tag.getList("Contents", Tag.TAG_STRING);
        contents = java.util.stream.IntStream.range(0, mix.size()).mapToObj(mix::getString).toList();
    }
}
