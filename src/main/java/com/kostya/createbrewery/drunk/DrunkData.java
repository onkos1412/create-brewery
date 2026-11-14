package com.kostya.createbrewery.drunk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * A player's drunk state, held by every player (a field added by {@code PlayerMixin}; Forge attached it as a
 * capability).
 *
 * <p>1.21.1 kept these values in five NeoForge data attachments; this one object holds them all
 * and keeps the same persistence:
 * <ul>
 *     <li>{@code drunk_level}, {@code knockout_ticks}: saved, and synced to the owning client
 *     ({@link DrunkManager#sync});</li>
 *     <li>{@code hangover_debt}: saved, not synced;</li>
 *     <li>stagger last position, vomit cooldown: neither saved nor synced.</li>
 * </ul>
 * None of them was {@code copyOnDeath}, so a respawned player starts from defaults, while a clone
 * that is not a death (leaving the End) carries over the saved values (see
 * {@link DrunkEvents#onCopyFrom}), as NeoForge does for serializable attachments.
 *
 * <p>The saved values live in the player's NBT under {@link #NBT_KEY} (Forge kept them under {@code ForgeCaps}
 * with the same id; loader saves are not shared).
 */
public final class DrunkData {

    /** The key in the player's saved data that holds {@link #save()}. */
    public static final String NBT_KEY = "brewery:drunk";

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

    /** The player's drunk data, which every player carries from creation (see {@link Holder}). */
    public static DrunkData of(Player player) {
        return ((Holder) player).brewery$drunkData();
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

    /** Implemented by every {@link Player} through {@code PlayerMixin}: the player's own drunk data. */
    public interface Holder {
        DrunkData brewery$drunkData();
    }
}
