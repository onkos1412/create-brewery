package com.kostya.createbrewery.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Best still result: the rotation speed and the stars earned. */
public record StillRecord(int rpm, int stars) {
    public static final Codec<StillRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("rpm").forGetter(StillRecord::rpm),
            Codec.INT.fieldOf("stars").forGetter(StillRecord::stars)
    ).apply(instance, StillRecord::new));

    public static final StreamCodec<ByteBuf, StillRecord> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StillRecord::rpm,
            ByteBufCodecs.VAR_INT, StillRecord::stars,
            StillRecord::new);
}
