package com.kostya.createbrewery.block;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.function.IntSupplier;

/**
 * The kettle's menu: four ingredient slots, the player inventory and the kettle's
 * brew state (water, wort, progress) shared with the client through
 * {@link ContainerData} so the screen can draw a live progress bar.
 */
public class KettleMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOTS = 4;

    /** Star rating used by the skeleton; the real quality scale arrives with aging. */
    public static final int SKELETON_STARS = 5;

    /** Data-slot ids. */
    public static final int DATA_WATER = 0;
    public static final int DATA_WORT = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_HEAT = 3;
    public static final int DATA_COUNT = 4;

    private static final int SLOT_SIZE = 18;

    private final Container kettleContainer;
    private final ContainerData data = new KettleData();
    private final KettleBlockEntity kettle;

    /** Client-side constructor used by the menu type. */
    public KettleMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, findKettle(inventory, buffer.readBlockPos()));
    }

    public KettleMenu(int containerId, Inventory inventory, KettleBlockEntity kettle) {
        super(ModMenus.KETTLE.get(), containerId);
        this.kettle = kettle;
        this.kettleContainer = kettle == null ? new SimpleContainer(INPUT_SLOTS) : null;
        bindKettleData(kettle);
        addSlots(inventory, kettle == null ? null : kettle.getInput());
    }

    private void addSlots(Inventory inventory, net.neoforged.neoforge.items.IItemHandler handler) {
        for (int i = 0; i < INPUT_SLOTS; i++) {
            if (handler != null) {
                addSlot(new SlotItemHandler(handler, i, 26 + i * SLOT_SIZE, 20));
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
        private final int[] synced = new int[DATA_COUNT];

        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_WATER -> water.getAsInt();
                case DATA_WORT -> wort.getAsInt();
                case DATA_PROGRESS -> progress.getAsInt();
                case DATA_HEAT -> heat.getAsInt();
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
