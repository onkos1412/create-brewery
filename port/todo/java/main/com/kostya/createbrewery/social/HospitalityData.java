package com.kostya.createbrewery.social;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-world hospitality: which different players have drunk each brewer's drinks, and the advancements an offline
 * brewer has earned meanwhile.
 */
public class HospitalityData extends SavedData {
    public static final String NAME = "brewery_hospitality";
    /** How many different players must drink a brewer's drinks for "Life of the party". */
    public static final int PARTY_SIZE = 5;

    private record Entry(UUID brewer, List<UUID> drinkers, List<String> pending) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("brewer").forGetter(Entry::brewer),
                UUIDUtil.CODEC.listOf().fieldOf("drinkers").forGetter(Entry::drinkers),
                Codec.STRING.listOf().fieldOf("pending").forGetter(Entry::pending)
        ).apply(instance, Entry::new));
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final SavedData.Factory<HospitalityData> FACTORY =
            new SavedData.Factory<>(HospitalityData::new, HospitalityData::load, null);

    private final Map<UUID, Set<UUID>> drinkers = new HashMap<>();
    private final Map<UUID, Set<String>> pending = new HashMap<>();

    public static HospitalityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /**
     * Records that {@code drinker} drank a drink of {@code brewer}; returns how many different players have now.
     * Drinking one's own drink counts for nothing.
     */
    public int credit(UUID brewer, UUID drinker) {
        if (brewer.equals(drinker)) {
            return drinkers.getOrDefault(brewer, Set.of()).size();
        }
        Set<UUID> set = drinkers.computeIfAbsent(brewer, b -> new HashSet<>());
        if (set.add(drinker)) {
            setDirty();
        }
        return set.size();
    }

    public void addPending(UUID brewer, String advancement) {
        if (pending.computeIfAbsent(brewer, b -> new HashSet<>()).add(advancement)) {
            setDirty();
        }
    }

    /** The advancements waiting for {@code brewer}, which are forgotten as they are returned. */
    public Set<String> takePending(UUID brewer) {
        Set<String> taken = pending.remove(brewer);
        if (taken == null) {
            return Set.of();
        }
        setDirty();
        return taken;
    }

    public static HospitalityData load(CompoundTag tag, HolderLookup.Provider registries) {
        HospitalityData data = new HospitalityData();
        if (tag.get("brewers") instanceof ListTag list) {
            for (Tag element : list) {
                Entry.CODEC.parse(NbtOps.INSTANCE, element).resultOrPartial(LOGGER::error).ifPresent(e -> {
                    if (!e.drinkers().isEmpty()) {
                        data.drinkers.put(e.brewer(), new HashSet<>(e.drinkers()));
                    }
                    if (!e.pending().isEmpty()) {
                        data.pending.put(e.brewer(), new HashSet<>(e.pending()));
                    }
                });
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Set<UUID> brewers = new HashSet<>(drinkers.keySet());
        brewers.addAll(pending.keySet());
        ListTag list = new ListTag();
        for (UUID brewer : brewers) {
            Entry entry = new Entry(brewer, List.copyOf(drinkers.getOrDefault(brewer, Set.of())),
                    List.copyOf(pending.getOrDefault(brewer, Set.of())));
            Entry.CODEC.encodeStart(NbtOps.INSTANCE, entry).resultOrPartial(LOGGER::error).ifPresent(list::add);
        }
        tag.put("brewers", list);
        return tag;
    }
}
