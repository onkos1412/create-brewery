package com.kostya.createbrewery.network;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.journal.DrinkJournal;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;

/**
 * Opens the brewer's book on the client. A stranger's copy is unreadable and carries nothing.
 *
 * @param readable  whether the reader owns the book
 * @param ownerName whose book it is, empty when unreadable
 * @param pages     one page per drink, in recipe-id order
 */
public record OpenBrewerBookPayload(boolean readable, String ownerName, List<Page> pages) implements CustomPacketPayload {
    public static final Type<OpenBrewerBookPayload> TYPE = new Type<>(CreateBrewery.id("open_brewer_book"));

    /**
     * One drink in the book.
     *
     * @param drinkId   the recipe's drink id
     * @param hasStill  whether the drink can be distilled
     * @param hasBarrel whether the drink can be aged
     * @param journal   the owner's best results for it, if any
     */
    public record Page(String drinkId, boolean hasStill, boolean hasBarrel, Optional<DrinkJournal> journal) {
        public static final StreamCodec<ByteBuf, Page> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Page::drinkId,
                ByteBufCodecs.BOOL, Page::hasStill,
                ByteBufCodecs.BOOL, Page::hasBarrel,
                ByteBufCodecs.optional(DrinkJournal.STREAM_CODEC), Page::journal,
                Page::new);
    }

    public static final StreamCodec<ByteBuf, OpenBrewerBookPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenBrewerBookPayload::readable,
            ByteBufCodecs.STRING_UTF8, OpenBrewerBookPayload::ownerName,
            Page.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenBrewerBookPayload::pages,
            OpenBrewerBookPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
