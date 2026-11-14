package com.kostya.createbrewery;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Codec-backed values in item and fluid stack NBT: what replaces 1.21.1's data components.
 *
 * <p>Removing the last key drops the tag altogether, so a stack whose values were removed is equal to a fresh one:
 * Create's pipes and tanks compare fluid NBT, and bottles stack only when their NBT is equal.
 */
public final class StackNbt {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StackNbt() {
    }

    /** The value under {@code key}, or {@code null} when it is absent or unreadable (which is logged). */
    @Nullable
    public static <T> T read(@Nullable CompoundTag tag, String key, Codec<T> codec) {
        if (tag == null || !tag.contains(key)) {
            return null;
        }
        Tag value = tag.get(key);
        DataResult<T> result = codec.parse(NbtOps.INSTANCE, value);
        result.error().ifPresent(error -> LOGGER.warn("Ignoring unreadable {} {}: {}", key, value, error.message()));
        return result.result().orElse(null);
    }

    /** Stores {@code value} under {@code key}; {@code null} removes it. */
    public static <T> void write(ItemStack stack, String key, Codec<T> codec, @Nullable T value) {
        if (value == null) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(key);
                if (tag.isEmpty()) {
                    stack.setTag(null);
                }
            }
            return;
        }
        stack.getOrCreateTag().put(key, encode(codec, value));
    }

    /** Stores {@code value} under {@code key} on a fluid; {@code null} removes it. */
    public static <T> void write(FluidStack stack, String key, Codec<T> codec, @Nullable T value) {
        if (value == null) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(key);
                if (tag.isEmpty()) {
                    stack.setTag(null);
                }
            }
            return;
        }
        stack.getOrCreateTag().put(key, encode(codec, value));
    }

    private static <T> Tag encode(Codec<T> codec, T value) {
        return codec.encodeStart(NbtOps.INSTANCE, value).result()
                .orElseThrow(() -> new IllegalStateException("Cannot encode " + value));
    }
}
