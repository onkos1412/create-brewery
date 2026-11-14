package com.kostya.createbrewery.item;

import com.kostya.createbrewery.StackNbt;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The player a brewer's book is signed to: their UUID, and their name for the tooltip.
 *
 * <p>Stored in the book's NBT under {@link #NBT_KEY} (1.21.1: the {@code brewery:book_owner} data component).
 */
public record BookOwner(UUID id, String name) {
    /** Where the owner sits in the book's NBT. */
    public static final String NBT_KEY = "brewery:book_owner";

    public static final Codec<BookOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(BookOwner::id),
            Codec.STRING.fieldOf("name").forGetter(BookOwner::name)
    ).apply(instance, BookOwner::new));

    @Nullable
    public static BookOwner get(ItemStack book) {
        return StackNbt.read(book.getTag(), NBT_KEY, CODEC);
    }

    /** Signs {@code book} to {@code owner}; {@code null} removes the signature. */
    public static void set(ItemStack book, @Nullable BookOwner owner) {
        StackNbt.write(book, NBT_KEY, CODEC, owner);
    }
}
