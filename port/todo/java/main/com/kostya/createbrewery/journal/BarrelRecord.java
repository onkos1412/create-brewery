package com.kostya.createbrewery.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Best barrel result: the wood, the days aged and the stars earned. */
public record BarrelRecord(String wood, int days, int stars) {
    public static final Codec<BarrelRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("wood").forGetter(BarrelRecord::wood),
            Codec.INT.fieldOf("days").forGetter(BarrelRecord::days),
            Codec.INT.fieldOf("stars").forGetter(BarrelRecord::stars)
    ).apply(instance, BarrelRecord::new));

    public static final StreamCodec<ByteBuf, BarrelRecord> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BarrelRecord::wood,
            ByteBufCodecs.VAR_INT, BarrelRecord::days,
            ByteBufCodecs.VAR_INT, BarrelRecord::stars,
            BarrelRecord::new);
}
