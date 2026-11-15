package com.kostya.createbrewery.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Optional;

/** A player's best result per brewing stage for one drink. */
public record DrinkJournal(Optional<KettleRecord> kettle, Optional<StillRecord> still, Optional<BarrelRecord> barrel) {
    public static final DrinkJournal EMPTY = new DrinkJournal(Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<DrinkJournal> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            KettleRecord.CODEC.optionalFieldOf("kettle").forGetter(DrinkJournal::kettle),
            StillRecord.CODEC.optionalFieldOf("still").forGetter(DrinkJournal::still),
            BarrelRecord.CODEC.optionalFieldOf("barrel").forGetter(DrinkJournal::barrel)
    ).apply(instance, DrinkJournal::new));

    /** Network form (1.21.1: a {@code StreamCodec} of three optionals). */
    public void write(FriendlyByteBuf buf) {
        buf.writeOptional(kettle, (b, r) -> r.write(b));
        buf.writeOptional(still, (b, r) -> r.write(b));
        buf.writeOptional(barrel, (b, r) -> r.write(b));
    }

    public static DrinkJournal read(FriendlyByteBuf buf) {
        return new DrinkJournal(buf.readOptional(KettleRecord::read), buf.readOptional(StillRecord::read),
                buf.readOptional(BarrelRecord::read));
    }

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
