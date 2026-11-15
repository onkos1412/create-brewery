package com.kostya.createbrewery.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;

/** Best still result: the rotation speed and the stars earned. */
public record StillRecord(int rpm, int stars) {
    public static final Codec<StillRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("rpm").forGetter(StillRecord::rpm),
            Codec.INT.fieldOf("stars").forGetter(StillRecord::stars)
    ).apply(instance, StillRecord::new));

    /** Network form (1.21.1: a {@code StreamCodec} of two var-ints). */
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(rpm);
        buf.writeVarInt(stars);
    }

    public static StillRecord read(FriendlyByteBuf buf) {
        return new StillRecord(buf.readVarInt(), buf.readVarInt());
    }
}
