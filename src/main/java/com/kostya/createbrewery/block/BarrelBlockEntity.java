package com.kostya.createbrewery.block;

import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.barrel.AgingRules;
import com.kostya.createbrewery.barrel.BarrelLogic;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Server state for a single manually loaded whiskey barrel. */
public class BarrelBlockEntity extends BlockEntity {
    private static final String OAK_PLANKS = "minecraft:oak_planks";
    private static final String OAK_LOG = "minecraft:oak_log";

    private long startedAt = -1L;
    private String woodId = "";
    private final ItemStackHandler input = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
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

    public BarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BARREL_BLOCK_ENTITY.get(), pos, state);
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
        return Math.max(0L, level.getGameTime() - startedAt);
    }

    public String getWoodId() {
        return woodId;
    }

    public boolean startAging() {
        ItemStack distillate = input.getStackInSlot(0);
        ItemStack wood = input.getStackInSlot(1);
        if (startedAt >= 0 || distillate.isEmpty() || !distillate.is(ModItems.DISTILLATE.get()) || wood.isEmpty()) {
            return false;
        }
        woodId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(wood.getItem()).toString();
        if (!AgingRules.isValidWood(woodId) && !woodId.endsWith("_log")) {
            // Non-oak wood is retained as a valid-but-lower-quality aging choice.
            if (!woodId.endsWith("_planks")) {
                return false;
            }
        }
        startedAt = level == null ? 0L : level.getGameTime();
        input.setStackInSlot(1, wood.copyWithCount(1));
        setChanged();
        return true;
    }

    public void serverTick() {
        if (level == null || level.isClientSide() || startedAt < 0 || !output.getStackInSlot(0).isEmpty()) {
            return;
        }
        if (!AgingRules.isComplete(getElapsedTicks())) {
            return;
        }
        ItemStack distillate = input.getStackInSlot(0);
        DrinkData data = distillate.getOrDefault(ModDataComponents.DRINK_DATA.get(),
                new DrinkData(DrinkData.MAX_STARS, DrinkData.DEFAULT_ALCOHOL, "distillate"));
        BarrelLogic.Result result = BarrelLogic.process(data.stars(), data.alcohol(), woodId,
                distillate.getCount() * 1000, getElapsedTicks());
        if (!result.ready()) {
            return;
        }
        ItemStack aged = new ItemStack(ModItems.DRINK_BOTTLE.get());
        aged.set(ModDataComponents.DRINK_DATA.get(),
                new DrinkData(result.stars(), result.alcohol(), "aged_whiskey"));
        output.setStackInSlot(0, aged);
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
        tag.put("Input", input.serializeNBT(registries));
        tag.put("Output", output.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        startedAt = tag.contains("StartedAt") ? tag.getLong("StartedAt") : -1L;
        woodId = tag.getString("Wood");
        if (tag.contains("Input")) {
            input.deserializeNBT(registries, tag.getCompound("Input"));
        }
        if (tag.contains("Output")) {
            output.deserializeNBT(registries, tag.getCompound("Output"));
        }
    }
}
