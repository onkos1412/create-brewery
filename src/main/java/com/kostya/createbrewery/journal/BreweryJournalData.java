package com.kostya.createbrewery.journal;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Per-world brewer's book: each player's best brewing results per drink. */
public class BreweryJournalData extends SavedData {
    public static final String NAME = "brewery_journal";

    private record Entry(UUID player, Map<String, DrinkJournal> drinks) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(Entry::player),
                Codec.unboundedMap(Codec.STRING, DrinkJournal.CODEC).fieldOf("drinks").forGetter(Entry::drinks)
        ).apply(instance, Entry::new));
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, Map<String, DrinkJournal>> journals = new HashMap<>();

    public static BreweryJournalData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BreweryJournalData::load, BreweryJournalData::new, NAME);
    }

    public Map<String, DrinkJournal> journalOf(UUID player) {
        Map<String, DrinkJournal> drinks = journals.get(player);
        return drinks == null ? Map.of() : Collections.unmodifiableMap(drinks);
    }

    public void recordKettle(UUID player, String drinkId, KettleRecord record) {
        update(player, drinkId, j -> j.withKettle(record));
    }

    public void recordStill(UUID player, String drinkId, StillRecord record) {
        update(player, drinkId, j -> j.withStill(record));
    }

    public void recordBarrel(UUID player, String drinkId, BarrelRecord record) {
        update(player, drinkId, j -> j.withBarrel(record));
    }

    private void update(UUID player, String drinkId, UnaryOperator<DrinkJournal> change) {
        DrinkJournal old = journals.getOrDefault(player, Map.of()).getOrDefault(drinkId, DrinkJournal.EMPTY);
        DrinkJournal updated = change.apply(old);
        if (!updated.equals(old)) {
            journals.computeIfAbsent(player, p -> new HashMap<>()).put(drinkId, updated);
            setDirty();
        }
    }

    public static BreweryJournalData load(CompoundTag tag) {
        BreweryJournalData data = new BreweryJournalData();
        if (tag.get("players") instanceof ListTag list) {
            for (Tag element : list) {
                Entry.CODEC.parse(NbtOps.INSTANCE, element).resultOrPartial(LOGGER::error)
                        .ifPresent(e -> data.journals.put(e.player(), new HashMap<>(e.drinks())));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        journals.forEach((player, drinks) -> Entry.CODEC.encodeStart(NbtOps.INSTANCE, new Entry(player, Map.copyOf(drinks)))
                .resultOrPartial(LOGGER::error).ifPresent(list::add));
        tag.put("players", list);
        return tag;
    }
}
