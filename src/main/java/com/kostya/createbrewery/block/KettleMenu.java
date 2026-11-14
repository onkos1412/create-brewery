package com.kostya.createbrewery.block;

import com.kostya.createbrewery.DrinkData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.IntSupplier;

/**
 * The kettle's menu: four ingredient slots, the player inventory and the kettle's
 * brew state (water, wort, progress) shared with the client through
 * {@link ContainerData} so the screen can draw a live progress bar.
 */
public class KettleMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOTS = 4;

    /** Data-slot ids. */
    public static final int DATA_WATER = 0;
    public static final int DATA_WORT = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_HEAT = 3;
    public static final int DATA_BREW_TIME = 4;
    /** A colour does not fit one data slot, which travels as a short. */
    public static final int DATA_COLOR_HIGH = 5;
    public static final int DATA_COLOR_LOW = 6;
    public static final int DATA_STARS = 7;
    public static final int DATA_AROMA = 8;
    public static final int DATA_BLOCKED = 9;
    public static final int DATA_COUNT = 10;

    private static final int SLOT_SIZE = 18;

    private final Container kettleContainer;
    private final ContainerData data = new KettleData();
    private final KettleBlockEntity kettle;

    /** Client-side constructor used by the menu type. */
    public KettleMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, findKettle(inventory, buffer.readBlockPos()));
    }

    public KettleMenu(int containerId, Inventory inventory, KettleBlockEntity kettle) {
        super(ModMenus.KETTLE.get(), containerId);
        this.kettle = kettle;
        this.kettleContainer = kettle == null ? new SimpleContainer(INPUT_SLOTS) : null;
        bindKettleData(kettle);
        addSlots(inventory, kettle == null ? null : kettle.getInput());
    }

    private void addSlots(Inventory inventory, MachineInventory handler) {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (handler != null) {
                addSlot(new MachineSlot(handler, i, 26 + i * SLOT_SIZE, 20));
            } else {
                addSlot(new Slot(kettleContainer, i, 26 + i * SLOT_SIZE, 20));
            }
        }

        addPlayerInventory(inventory);
        addDataSlots(data);
    }

    private void bindKettleData(KettleBlockEntity kettle) {
        if (kettle == null) {
            return;
        }
        KettleData bound = (KettleData) data;
        bound.water = kettle::getWaterAmount;
        bound.wort = kettle::getWortAmount;
        bound.progress = kettle::getBrewProgressTicks;
        bound.heat = kettle::getHeatLevel;
        bound.brewTime = kettle::getBrewTimeTicks;
        bound.colorHigh = () -> kettle.getWortColor() >>> 16;
        bound.colorLow = () -> kettle.getWortColor() & 0xFFFF;
        bound.stars = kettle::getWortStars;
        bound.aroma = kettle::getAroma;
        bound.blocked = kettle::getBlockReason;
    }

    private static KettleBlockEntity findKettle(Inventory inventory, net.minecraft.core.BlockPos pos) {
        BlockEntity entity = inventory.player.level().getBlockEntity(pos);
        return entity instanceof KettleBlockEntity kettle ? kettle : null;
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * SLOT_SIZE, 84 + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * SLOT_SIZE, 142));
        }
    }

    /** Water in the kettle, in milli-buckets. */
    public int getWaterAmount() {
        return data.get(DATA_WATER);
    }

    /** Wort in the kettle, in milli-buckets. */
    public int getWortAmount() {
        return data.get(DATA_WORT);
    }

    /** Current brew progress in ticks. */
    public int getBrewProgress() {
        return data.get(DATA_PROGRESS);
    }

    /** Heat level of the source under the kettle. */
    public int getHeat() {
        return data.get(DATA_HEAT);
    }

    /** How long the current brew takes; never zero, since the screen divides by it. */
    public int getBrewTime() {
        return Math.max(1, data.get(DATA_BREW_TIME));
    }

    /** The colour of the wort inside. */
    public int getWortColor() {
        return (data.get(DATA_COLOR_HIGH) & 0xFF) << 16 | data.get(DATA_COLOR_LOW) & 0xFFFF;
    }

    /** The quality of the wort inside. */
    public int getWortStars() {
        return Mth.clamp(data.get(DATA_STARS), DrinkData.MIN_STARS, DrinkData.MAX_STARS);
    }

    /** How close the brewing mix smells to a recipe; 0 when nothing is brewing. */
    public int getAroma() {
        return data.get(DATA_AROMA);
    }

    /** What the mix over the fire waits for, one of {@code KettleLogic.BLOCKED_*}. */
    public int getBlockReason() {
        return data.get(DATA_BLOCKED);
    }

    /** Whether the kettle holds enough wort for one bottle. */
    public boolean hasBottle() {
        return getWortAmount() >= KettleBlockEntity.WORT_PER_BREW;
    }

    @Override
    public boolean stillValid(Player player) {
        if (kettle != null) {
            return stillValid(net.minecraft.world.inventory.ContainerLevelAccess.create(kettle.getLevel(),
                    kettle.getBlockPos()), player, ModBlocks.KETTLE.get());
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < INPUT_SLOTS) {
            // Kettle -> player inventory.
            if (!moveItemStackTo(stack, INPUT_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player inventory -> kettle input.
            if (!moveItemStackTo(stack, 0, INPUT_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** Layout constants shared with {@link KettleScreen}. */
    public static final class Layout {
        public static final int WIDTH = 176;
        public static final int HEIGHT = 166;

        private Layout() {
        }
    }

    /**
     * Brew state shared with the client. On the server the getters read the block
     * entity; on the client they read the values synced by the menu.
     */
    private static final class KettleData implements ContainerData {
        private IntSupplier water = () -> 0;
        private IntSupplier wort = () -> 0;
        private IntSupplier progress = () -> 0;
        private IntSupplier heat = () -> 0;
        private IntSupplier brewTime = () -> 0;
        private IntSupplier colorHigh = () -> 0;
        private IntSupplier colorLow = () -> 0;
        private IntSupplier stars = () -> 0;
        private IntSupplier aroma = () -> 0;
        private IntSupplier blocked = () -> 0;
        private final int[] synced = new int[DATA_COUNT];

        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_WATER -> water.getAsInt();
                case DATA_WORT -> wort.getAsInt();
                case DATA_PROGRESS -> progress.getAsInt();
                case DATA_HEAT -> heat.getAsInt();
                case DATA_BREW_TIME -> brewTime.getAsInt();
                case DATA_COLOR_HIGH -> colorHigh.getAsInt();
                case DATA_COLOR_LOW -> colorLow.getAsInt();
                case DATA_STARS -> stars.getAsInt();
                case DATA_AROMA -> aroma.getAsInt();
                case DATA_BLOCKED -> blocked.getAsInt();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index >= 0 && index < synced.length) {
                synced[index] = value;
            }
            switch (index) {
                case DATA_WATER -> water = () -> synced[DATA_WATER];
                case DATA_WORT -> wort = () -> synced[DATA_WORT];
                case DATA_PROGRESS -> progress = () -> synced[DATA_PROGRESS];
                case DATA_HEAT -> heat = () -> synced[DATA_HEAT];
                case DATA_BREW_TIME -> brewTime = () -> synced[DATA_BREW_TIME];
                case DATA_COLOR_HIGH -> colorHigh = () -> synced[DATA_COLOR_HIGH];
                case DATA_COLOR_LOW -> colorLow = () -> synced[DATA_COLOR_LOW];
                case DATA_STARS -> stars = () -> synced[DATA_STARS];
                case DATA_AROMA -> aroma = () -> synced[DATA_AROMA];
                case DATA_BLOCKED -> blocked = () -> synced[DATA_BLOCKED];
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
