package com.kostya.createbrewery.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Best kettle result: what went in (item id to count), the heat and the stars earned. */
public record KettleRecord(Map<String, Integer> mix, int heat, int stars) {
    public KettleRecord {
        mix = new TreeMap<>(mix);
    }

    public static final Codec<KettleRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(m -> (Map<String, Integer>) new TreeMap<>(m), m -> m)
                    .fieldOf("mix").forGetter(KettleRecord::mix),
            Codec.INT.fieldOf("heat").forGetter(KettleRecord::heat),
            Codec.INT.fieldOf("stars").forGetter(KettleRecord::stars)
    ).apply(instance, KettleRecord::new));

    public static final StreamCodec<ByteBuf, KettleRecord> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(
                    size -> new TreeMap<>(), ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), KettleRecord::mix,
            ByteBufCodecs.VAR_INT, KettleRecord::heat,
            ByteBufCodecs.VAR_INT, KettleRecord::stars,
            KettleRecord::new);

    /** One id per item in, counted per distinct id. */
    public static Map<String, Integer> mixOf(List<String> ids) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String id : ids) {
            counts.merge(id, 1, Integer::sum);
        }
        return counts;
    }
}
