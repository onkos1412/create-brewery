package com.kostya.createbrewery.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** The player a brewer's book is signed to: their UUID, and their name for the tooltip. */
public record BookOwner(UUID id, String name) {
    public static final Codec<BookOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(BookOwner::id),
            Codec.STRING.fieldOf("name").forGetter(BookOwner::name)
    ).apply(instance, BookOwner::new));

    public static final StreamCodec<ByteBuf, BookOwner> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BookOwner::id,
            ByteBufCodecs.STRING_UTF8, BookOwner::name,
            BookOwner::new);
}
