package com.kostya.createbrewery.drunk;

import net.minecraft.world.entity.player.Player;

/**
 * Read/write access to a player's drunk level, hiding the attachment plumbing.
 */
public final class DrunkManager {

    private DrunkManager() {
    }

    /** The player's current drunk level, {@code 0} when sober. */
    public static int getDrunk(Player player) {
        return levelOf(player).get();
    }

    /**
     * Raises the player's drunk level by {@code amount}, clamped to
     * {@link DrunkLevel#MAX}. Negative amounts are ignored.
     */
    public static void addDrunk(Player player, int amount) {
        levelOf(player).add(amount);
        markDirty(player);
    }

    /** Sobering tick: lowers the level by one step, stopping at zero. */
    public static void soberTick(Player player) {
        DrunkLevel level = levelOf(player);
        if (level.isDrunk()) {
            level.tick();
            markDirty(player);
        }
    }

    /** Back to sober, used on death and respawn. */
    public static void reset(Player player) {
        levelOf(player).reset();
        markDirty(player);
    }

    public static boolean isDrunk(Player player) {
        return levelOf(player).isDrunk();
    }

    private static DrunkLevel levelOf(Player player) {
        return player.getData(ModAttachments.DRUNK_LEVEL.get());
    }

    private static void markDirty(Player player) {
        // Attachment values are mutable, so nudge the holder so NeoForge syncs and saves
        // the new contents to the client and the save file.
        player.setData(ModAttachments.DRUNK_LEVEL.get(), levelOf(player));
    }
}
