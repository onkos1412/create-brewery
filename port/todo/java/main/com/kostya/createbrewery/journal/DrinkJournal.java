package com.kostya.createbrewery.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/** A player's best result per brewing stage for one drink. */
public record DrinkJournal(Optional<KettleRecord> kettle, Optional<StillRecord> still, Optional<BarrelRecord> barrel) {
    public static final DrinkJournal EMPTY = new DrinkJournal(Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<DrinkJournal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            KettleRecord.CODEC.optionalFieldOf("kettle").forGetter(DrinkJournal::kettle),
            StillRecord.CODEC.optionalFieldOf("still").forGetter(DrinkJournal::still),
            BarrelRecord.CODEC.optionalFieldOf("barrel").forGetter(DrinkJournal::barrel)
    ).apply(instance, DrinkJournal::new));

    public static final StreamCodec<ByteBuf, DrinkJournal> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(KettleRecord.STREAM_CODEC), DrinkJournal::kettle,
            ByteBufCodecs.optional(StillRecord.STREAM_CODEC), DrinkJournal::still,
            ByteBufCodecs.optional(BarrelRecord.STREAM_CODEC), DrinkJournal::barrel,
            DrinkJournal::new);

    public DrinkJournal withKettle(KettleRecord r) {
        if (kettle.isPresent() && r.stars() <= kettle.get().stars()) return this;
        return new DrinkJournal(Optional.of(r), still, barrel);
    }

    public DrinkJournal withStill(StillRecord r) {
        if (still.isPresent() && r.stars() <= still.get().stars()) return this;
        return new DrinkJournal(kettle, Optional.of(r), barrel);
    }

    public DrinkJournal withBarrel(BarrelRecord r) {
        if (barrel.isPresent() && r.stars() <= barrel.get().stars()) return this;
        return new DrinkJournal(kettle, still, Optional.of(r));
    }
}
