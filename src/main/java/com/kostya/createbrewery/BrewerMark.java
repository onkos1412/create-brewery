package com.kostya.createbrewery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * Signature of the player who brewed a drink: their name, the world day and, for marks made
 * since the brewer's book, their UUID so later stages credit the right journal.
 */
public record BrewerMark(String name, long day, Optional<UUID> owner) {
    public static final long TICKS_PER_DAY = 24000L;

    /** A mark with no known owner, as made before the brewer's book. */
    public BrewerMark(String name, long day) {
        this(name, day, Optional.empty());
    }

    public static final Codec<BrewerMark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(BrewerMark::name),
            Codec.LONG.fieldOf("day").forGetter(BrewerMark::day),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(BrewerMark::owner)
    ).apply(instance, BrewerMark::new));

    public static final StreamCodec<ByteBuf, BrewerMark> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BrewerMark::name,
            ByteBufCodecs.VAR_LONG, BrewerMark::day,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), BrewerMark::owner,
            BrewerMark::new);

    /** Day 1 is the first day of a world. */
    public static long dayOf(long dayTime) {
        return dayTime / TICKS_PER_DAY + 1;
    }

    public Component tooltip() {
        return Component.translatable("tooltip.brewery.brewer", name, day)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
    }
}
