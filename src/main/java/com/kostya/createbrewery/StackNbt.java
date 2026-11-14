package com.kostya.createbrewery;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Codec-backed values in item and fluid stack NBT: what replaces 1.21.1's data components.
 *
 * <p>Removing the last key drops the tag altogether, so a stack whose values were removed is equal to a fresh one:
 * Create's pipes and tanks compare fluid NBT, and bottles stack only when their NBT is equal.
 *
 * <p>Fluids on Fabric carry their NBT in the immutable Transfer API {@link FluidVariant}, which is what tanks,
 * pipes and spouts compare and move. Porting Lib's {@link FluidStack} keeps a separate, loose tag next to its
 * variant that the Transfer API never sees, so fluid values are never written into that tag: writing returns a
 * new variant, or a new stack built on one.
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

    /** The value under {@code key} in a fluid's variant, or {@code null}. */
    @Nullable
    public static <T> T read(FluidStack stack, String key, Codec<T> codec) {
        return read(stack.getType().getNbt(), key, codec);
    }

    /** {@code variant} with {@code value} stored under {@code key}; {@code null} removes it. */
    public static <T> FluidVariant with(FluidVariant variant, String key, Codec<T> codec, @Nullable T value) {
        CompoundTag tag = variant.copyNbt();
        if (value == null) {
            if (tag == null || !tag.contains(key)) {
                return variant;
            }
            tag.remove(key);
        } else {
            if (tag == null) {
                tag = new CompoundTag();
            }
            tag.put(key, encode(codec, value));
        }
        return FluidVariant.of(variant.getFluid(), tag.isEmpty() ? null : tag);
    }

    /** A copy of {@code stack} whose variant has {@code value} under {@code key}; {@code null} removes it. */
    public static <T> FluidStack with(FluidStack stack, String key, Codec<T> codec, @Nullable T value) {
        if (stack.isEmpty()) {
            return stack;
        }
        return new FluidStack(with(stack.getType(), key, codec, value), stack.getAmount());
    }

    private static <T> Tag encode(Codec<T> codec, T value) {
        return codec.encodeStart(NbtOps.INSTANCE, value).result()
                .orElseThrow(() -> new IllegalStateException("Cannot encode " + value));
    }
}
