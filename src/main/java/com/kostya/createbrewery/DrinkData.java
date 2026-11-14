package com.kostya.createbrewery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent metadata carried by drink and intermediate-product stacks.
 *
 * <p>On 1.21.1 this was the {@code brewery:drink_data} data component. On 1.20.1 it lives in the stack's NBT
 * under {@link #NBT_KEY}, read and written through {@link #get(ItemStack)} / {@link #set(ItemStack, DrinkData)}
 * and the {@link FluidStack} twins. Items that implement {@link Carrier} report a default value when the stack
 * has none, as the item's default component did, and setting that default stores nothing, so a fresh stack and
 * one set to the default stay equal and stack together, as they did on 1.21.1.
 */
public record DrinkData(int stars, int alcohol, String stage) {
    public static final int MIN_STARS = 1;
    public static final int MAX_STARS = 5;
    public static final int MIN_ALCOHOL = 0;
    public static final int MAX_ALCOHOL = 100;
    public static final int DEFAULT_ALCOHOL = 40;
    public static final String DEFAULT_STAGE = "whiskey";
    /** What a completely missed setting makes instead of the intended drink. */
    public static final String SPOILED_STAGE = "spoiled";
    /** Where the data sits in an item or fluid stack's NBT. */
    public static final String NBT_KEY = "brewery:drink";

    /** Optional fields keep components written by the skeleton readable. */
    public static final Codec<DrinkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("stars").forGetter(DrinkData::stars),
            StrictCodecs.optionalField(Codec.INT, "alcohol", DEFAULT_ALCOHOL).forGetter(DrinkData::alcohol),
            StrictCodecs.optionalField(Codec.STRING, "stage", DEFAULT_STAGE).forGetter(DrinkData::stage)
    ).apply(instance, DrinkData::new));

    /** An item whose stacks carry drink data even before any is set, like 1.21.1's default component. */
    public interface Carrier {
        DrinkData defaultDrinkData();
    }

    public DrinkData(int stars) {
        this(stars, DEFAULT_ALCOHOL, DEFAULT_STAGE);
    }

    public boolean isSpoiled() {
        return SPOILED_STAGE.equals(stage);
    }

    public DrinkData {
        stars = Math.max(MIN_STARS, Math.min(MAX_STARS, stars));
        alcohol = Math.max(MIN_ALCOHOL, Math.min(MAX_ALCOHOL, alcohol));
        stage = stage == null || stage.isBlank() ? DEFAULT_STAGE : stage;
    }

    // ---- NBT accessors (1.21.1: stack.get/set(ModDataComponents.DRINK_DATA)) ----------------------

    /** The stack's drink data, the item's default when it has none stored, or {@code null}. */
    @Nullable
    public static DrinkData get(ItemStack stack) {
        DrinkData stored = StackNbt.read(stack.getTag(), NBT_KEY, CODEC);
        if (stored != null) {
            return stored;
        }
        return stack.getItem() instanceof Carrier carrier ? carrier.defaultDrinkData() : null;
    }

    public static DrinkData getOrDefault(ItemStack stack, DrinkData fallback) {
        DrinkData data = get(stack);
        return data == null ? fallback : data;
    }

    public static boolean has(ItemStack stack) {
        return get(stack) != null;
    }

    /** Stores {@code data}; {@code null}, or the item's own default, stores nothing. */
    public static void set(ItemStack stack, @Nullable DrinkData data) {
        boolean isDefault = data != null && stack.getItem() instanceof Carrier carrier
                && data.equals(carrier.defaultDrinkData());
        StackNbt.write(stack, NBT_KEY, CODEC, isDefault ? null : data);
    }

    /** The fluid's drink data, or {@code null}: fluids have no default. */
    @Nullable
    public static DrinkData get(FluidStack stack) {
        return StackNbt.read(stack.getTag(), NBT_KEY, CODEC);
    }

    /** Stores {@code data} on the fluid; {@code null} removes it. */
    public static void set(FluidStack stack, @Nullable DrinkData data) {
        StackNbt.write(stack, NBT_KEY, CODEC, data);
    }
}
