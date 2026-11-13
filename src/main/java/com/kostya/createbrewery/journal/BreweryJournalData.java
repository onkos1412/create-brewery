package com.kostya.createbrewery.journal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    private static final Codec<List<Entry>> LIST_CODEC = Entry.CODEC.listOf();

    public static final SavedData.Factory<BreweryJournalData> FACTORY =
            new SavedData.Factory<>(BreweryJournalData::new, BreweryJournalData::load, null);

    private final Map<UUID, Map<String, DrinkJournal>> journals = new HashMap<>();

    public static BreweryJournalData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
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

    public static BreweryJournalData load(CompoundTag tag, HolderLookup.Provider registries) {
        BreweryJournalData data = new BreweryJournalData();
        Tag list = tag.get("players");
        if (list != null) {
            LIST_CODEC.parse(NbtOps.INSTANCE, list).result().ifPresent(entries -> {
                for (Entry e : entries) {
                    data.journals.put(e.player(), new HashMap<>(e.drinks()));
                }
            });
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        List<Entry> entries = new ArrayList<>();
        journals.forEach((player, drinks) -> entries.add(new Entry(player, Map.copyOf(drinks))));
        LIST_CODEC.encodeStart(NbtOps.INSTANCE, entries).result().ifPresent(t -> tag.put("players", t));
        return tag;
    }
}
