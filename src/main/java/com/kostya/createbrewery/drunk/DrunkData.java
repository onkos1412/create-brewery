package com.kostya.createbrewery.drunk;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A player's drunk state, attached to every player as a Forge capability.
 *
 * <p>1.21.1 kept these values in five NeoForge data attachments; this one capability holds them all
 * and keeps the same persistence:
 * <ul>
 *     <li>{@code drunk_level}, {@code knockout_ticks}: saved, and synced to the owning client
 *     ({@link DrunkManager#sync});</li>
 *     <li>{@code hangover_debt}: saved, not synced;</li>
 *     <li>stagger last position, vomit cooldown: neither saved nor synced.</li>
 * </ul>
 * None of them was {@code copyOnDeath}, so a respawned player starts from defaults, while a clone
 * that is not a death (leaving the End) carries over the saved values (see
 * {@link DrunkEvents#onClone}), as NeoForge does for serializable attachments.
 */
@AutoRegisterCapability
public final class DrunkData {

    public static final Capability<DrunkData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    /** Where the capability is attached; the saved values live under {@code ForgeCaps} with this key. */
    public static final ResourceLocation ID = CreateBrewery.id("drunk");

    private static final String LEVEL_KEY = "drunk_level";
    private static final String KNOCKOUT_KEY = "knockout_ticks";
    private static final String DEBT_KEY = "hangover_debt";

    private final DrunkLevel level = new DrunkLevel(0);
    /** Ticks of blackout left. Saved, so logging out does not sober a player up. */
    private int knockoutTicks;
    /** Hangover debt built up by drinking, settled when the player sobers up. Saved, not synced. */
    private int hangoverDebt;
    /** Where the player stood at the last stagger check, to tell if they walked. Neither saved nor synced. */
    private Optional<Vec3> staggerLastPos = Optional.empty();
    /** Ticks left before the player can throw up again. Neither saved nor synced. */
    private int vomitCooldown;

    /** What the owning client was last told, so unchanged values are not sent again. Not saved. */
    private int syncedLevel = -1;
    private int syncedKnockoutTicks = -1;

    /**
     * The player's drunk data. A player whose capabilities are gone (removed after death) reads as a
     * fresh, sober player, as an absent attachment did; writes to it are dropped.
     */
    public static DrunkData of(Player player) {
        return player.getCapability(CAPABILITY).orElseGet(DrunkData::new);
    }

    public DrunkLevel level() {
        return level;
    }

    public int knockoutTicks() {
        return knockoutTicks;
    }

    public void setKnockoutTicks(int ticks) {
        knockoutTicks = ticks;
    }

    public int hangoverDebt() {
        return hangoverDebt;
    }

    public void setHangoverDebt(int debt) {
        hangoverDebt = debt;
    }

    public Optional<Vec3> staggerLastPos() {
        return staggerLastPos;
    }

    public void setStaggerLastPos(Optional<Vec3> pos) {
        staggerLastPos = pos;
    }

    public int vomitCooldown() {
        return vomitCooldown;
    }

    public void setVomitCooldown(int ticks) {
        vomitCooldown = ticks;
    }

    /** Whether the owning client needs a sync; records the values as sent when it does. */
    boolean markSynced(boolean force) {
        if (!force && syncedLevel == level.get() && syncedKnockoutTicks == knockoutTicks) {
            return false;
        }
        syncedLevel = level.get();
        syncedKnockoutTicks = knockoutTicks;
        return true;
    }

    /** The saved values only: the drunk level, the blackout and the hangover debt. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(LEVEL_KEY, level.get());
        tag.putInt(KNOCKOUT_KEY, knockoutTicks);
        tag.putInt(DEBT_KEY, hangoverDebt);
        return tag;
    }

    public void load(CompoundTag tag) {
        level.set(tag.getInt(LEVEL_KEY));
        knockoutTicks = tag.getInt(KNOCKOUT_KEY);
        hangoverDebt = tag.getInt(DEBT_KEY);
    }

    /** Copies what is saved, nothing else: what NeoForge copies on a clone that is not a death. */
    public void copySavedFrom(DrunkData other) {
        load(other.save());
    }

    /** Attaches one {@link DrunkData} to a player and saves it with the player. */
    static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final DrunkData data = new DrunkData();
        private final LazyOptional<DrunkData> optional = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return CAPABILITY.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            data.load(tag);
        }
    }
}
