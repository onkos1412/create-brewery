package com.kostya.createbrewery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Signature of the player who brewed a drink: their name and the world day. */
public record BrewerMark(String name, long day) {
    public static final long TICKS_PER_DAY = 24000L;

    public static final Codec<BrewerMark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(BrewerMark::name),
            Codec.LONG.fieldOf("day").forGetter(BrewerMark::day)
    ).apply(instance, BrewerMark::new));

    public static final StreamCodec<ByteBuf, BrewerMark> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BrewerMark::name,
            ByteBufCodecs.VAR_LONG, BrewerMark::day,
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
