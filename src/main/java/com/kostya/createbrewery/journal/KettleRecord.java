package com.kostya.createbrewery.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Best kettle result: what went in (item id to count), the heat and the stars earned. */
public record KettleRecord(Map<String, Integer> mix, int heat, int stars) {
    public KettleRecord {
        mix = Collections.unmodifiableSortedMap(new TreeMap<>(mix));
    }

    public static final Codec<KettleRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(m -> (Map<String, Integer>) new TreeMap<>(m), m -> m)
                    .fieldOf("mix").forGetter(KettleRecord::mix),
            Codec.INT.fieldOf("heat").forGetter(KettleRecord::heat),
            Codec.INT.fieldOf("stars").forGetter(KettleRecord::stars)
    ).apply(instance, KettleRecord::new));

    /** Network form (1.21.1: a {@code StreamCodec} of a string-to-var-int map and two var-ints). */
    public void write(FriendlyByteBuf buf) {
        buf.writeMap(mix, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
        buf.writeVarInt(heat);
        buf.writeVarInt(stars);
    }

    public static KettleRecord read(FriendlyByteBuf buf) {
        Map<String, Integer> mix = buf.readMap(size -> new TreeMap<>(), FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt);
        return new KettleRecord(mix, buf.readVarInt(), buf.readVarInt());
    }

    /** One id per item in, counted per distinct id. */
    public static Map<String, Integer> mixOf(List<String> ids) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String id : ids) {
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }
}
