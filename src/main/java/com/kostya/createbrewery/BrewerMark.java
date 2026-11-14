package com.kostya.createbrewery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Signature of the player who brewed a drink: their name, the world day and, for marks made
 * since the brewer's book, their UUID so later stages credit the right journal.
 *
 * <p>Stored in stack NBT under {@link #NBT_KEY} (1.21.1: the {@code brewery:brewer} data component). Item stack
 * NBT reaches clients with the stack, so tooltips can show it without a stream codec.
 */
public record BrewerMark(String name, long day, Optional<UUID> owner) {
    public static final long TICKS_PER_DAY = 24000L;
    /** Where the mark sits in an item or fluid stack's NBT. */
    public static final String NBT_KEY = "brewery:brewer";

    /** A mark with no known owner, as made before the brewer's book. */
    public BrewerMark(String name, long day) {
        this(name, day, Optional.empty());
    }

    public static final Codec<BrewerMark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(BrewerMark::name),
            Codec.LONG.fieldOf("day").forGetter(BrewerMark::day),
            StrictCodecs.optionalField(UUIDUtil.CODEC, "owner").forGetter(BrewerMark::owner)
    ).apply(instance, BrewerMark::new));

    /** Day 1 is the first day of a world. */
    public static long dayOf(long dayTime) {
        return dayTime / TICKS_PER_DAY + 1;
    }

    public Component tooltip() {
        return Component.translatable("tooltip.brewery.brewer", name, day)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }

    // ---- NBT accessors (1.21.1: stack.get/set(ModDataComponents.BREWER)) ---------------------------

    @Nullable
    public static BrewerMark get(ItemStack stack) {
        return StackNbt.read(stack.getTag(), NBT_KEY, CODEC);
    }

    /** Stores {@code mark}; {@code null} removes it. */
    public static void set(ItemStack stack, @Nullable BrewerMark mark) {
        StackNbt.write(stack, NBT_KEY, CODEC, mark);
    }

    @Nullable
    public static BrewerMark get(FluidStack stack) {
        return StackNbt.read(stack, NBT_KEY, CODEC);
    }

    /** A copy of the fluid carrying {@code mark}; {@code null} removes it (see {@link StackNbt} for why a copy). */
    public static FluidStack with(FluidStack stack, @Nullable BrewerMark mark) {
        return StackNbt.with(stack, NBT_KEY, CODEC, mark);
    }

    @Nullable
    public static BrewerMark get(FluidVariant variant) {
        return StackNbt.read(variant.getNbt(), NBT_KEY, CODEC);
    }

    /** {@code variant} carrying {@code mark}; {@code null} removes it. */
    public static FluidVariant with(FluidVariant variant, @Nullable BrewerMark mark) {
        return StackNbt.with(variant, NBT_KEY, CODEC, mark);
    }
}
