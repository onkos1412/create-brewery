package com.kostya.createbrewery.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;

/** Best barrel result: the wood, the days aged and the stars earned. */
public record BarrelRecord(String wood, int days, int stars) {
    public static final Codec<BarrelRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("wood").forGetter(BarrelRecord::wood),
            Codec.INT.fieldOf("days").forGetter(BarrelRecord::days),
            Codec.INT.fieldOf("stars").forGetter(BarrelRecord::stars)
    ).apply(instance, BarrelRecord::new));

    /** Network form (1.21.1: a {@code StreamCodec} of a UTF-8 string and two var-ints). */
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(wood);
        buf.writeVarInt(days);
        buf.writeVarInt(stars);
    }

    public static BarrelRecord read(FriendlyByteBuf buf) {
        return new BarrelRecord(buf.readUtf(), buf.readVarInt(), buf.readVarInt());
    }
}
