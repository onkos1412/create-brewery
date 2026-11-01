package com.kostya.createbrewery.drunk;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Read/write access to a player's drunk level, hiding the attachment plumbing.
 */
public final class DrunkManager {

    /** How long passing out lasts: ten seconds. */
    public static final int KNOCKOUT_TICKS = 200;

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
        player.setData(ModAttachments.KNOCKOUT_TICKS.get(), 0);
    }

    public static boolean isKnockedOut(Player player) {
        return player.getData(ModAttachments.KNOCKOUT_TICKS.get()) > 0;
    }

    /** One server tick of the blackout: counts a running one down, or starts one on overdose. */
    public static void knockoutTick(Player player) {
        int remaining = player.getData(ModAttachments.KNOCKOUT_TICKS.get());
        if (remaining > 0) {
            player.setData(ModAttachments.KNOCKOUT_TICKS.get(), remaining - 1);
        } else if (getDrunk(player) >= DrunkLevel.PASS_OUT_AT) {
            passOut(player);
        }
    }

    private static void passOut(Player player) {
        levelOf(player).set(DrunkLevel.AFTER_PASS_OUT);
        markDirty(player);
        player.setData(ModAttachments.KNOCKOUT_TICKS.get(), KNOCKOUT_TICKS);
        player.stopUsingItem();
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, KNOCKOUT_TICKS + 20));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KNOCKOUT_TICKS, 6));
        player.displayClientMessage(Component.translatable("message.brewery.passed_out"), true);
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
