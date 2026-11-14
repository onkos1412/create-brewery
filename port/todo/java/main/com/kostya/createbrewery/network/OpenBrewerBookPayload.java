package com.kostya.createbrewery.network;

import com.kostya.createbrewery.journal.DrinkJournal;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Opens the brewer's book on the client. A stranger's copy is unreadable and carries nothing.
 *
 * <p>1.21.1 sent this as a {@code CustomPacketPayload} ({@code brewery:open_brewer_book}); on 1.20.1 it is a
 * message on {@link ModNetwork#CHANNEL}, with the same fields in the same order.
 *
 * @param readable  whether the reader owns the book
 * @param ownerName whose book it is, empty when unreadable
 * @param pages     one page per drink, in recipe-id order
 */
public record OpenBrewerBookPayload(boolean readable, String ownerName, List<Page> pages) {

    /**
     * One drink in the book.
     *
     * @param drinkId   the recipe's drink id
     * @param hasStill  whether the drink can be distilled
     * @param hasBarrel whether the drink can be aged
     * @param journal   the owner's best results for it, if any
     */
    public record Page(String drinkId, boolean hasStill, boolean hasBarrel, Optional<DrinkJournal> journal) {
        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(drinkId);
            buf.writeBoolean(hasStill);
            buf.writeBoolean(hasBarrel);
            buf.writeOptional(journal, (b, j) -> j.write(b));
        }

        public static Page read(FriendlyByteBuf buf) {
            return new Page(buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readOptional(DrinkJournal::read));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(readable);
        buf.writeUtf(ownerName);
        buf.writeCollection(pages, (b, page) -> page.write(b));
    }

    public static OpenBrewerBookPayload decode(FriendlyByteBuf buf) {
        return new OpenBrewerBookPayload(buf.readBoolean(), buf.readUtf(), buf.readList(Page::read));
    }

    /** Runs on the main thread. The client class is named only inside the client-only lambda. */
    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.kostya.createbrewery.client.BrewerBookClient.open(this));
    }
}
