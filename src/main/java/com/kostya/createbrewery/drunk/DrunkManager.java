package com.kostya.createbrewery.drunk;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

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
            if (!level.isDrunk()) {
                settleHangover(player);
            }
        }
    }

    public static int getHangoverDebt(Player player) {
        return player.getData(ModAttachments.HANGOVER_DEBT.get());
    }

    /** Adds to the debt that comes due once the player is sober again. */
    public static void addHangoverDebt(Player player, int amount) {
        if (amount > 0) {
            player.setData(ModAttachments.HANGOVER_DEBT.get(), getHangoverDebt(player) + amount);
        }
    }

    /** Runs once, on the step into sobriety: a big enough debt becomes a hangover, then the debt is wiped. */
    private static void settleHangover(Player player) {
        int debt = getHangoverDebt(player);
        player.setData(ModAttachments.HANGOVER_DEBT.get(), 0);
        if (HangoverRules.hungOver(debt)) {
            int ticks = HangoverRules.durationTicks(debt);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks));
            player.displayClientMessage(Component.translatable("message.brewery.hangover"), true);
        }
    }

    /**
     * Water ends a hangover. A hangover is told apart from other sources by both of its effects
     * being on at once with its amplifier, so a lone mining fatigue from a guardian or a hunger
     * from a rotten bite is left alone.
     */
    public static void cureHangover(Player player) {
        MobEffectInstance fatigue = player.getEffect(MobEffects.DIG_SLOWDOWN);
        MobEffectInstance hunger = player.getEffect(MobEffects.HUNGER);
        if (fatigue != null && hunger != null && fatigue.getAmplifier() == 0 && hunger.getAmplifier() == 0) {
            player.removeEffect(MobEffects.DIG_SLOWDOWN);
            player.removeEffect(MobEffects.HUNGER);
        }
    }

    /** Back to sober, used on death and respawn. */
    public static void reset(Player player) {
        levelOf(player).reset();
        markDirty(player);
        player.setData(ModAttachments.KNOCKOUT_TICKS.get(), 0);
        player.setData(ModAttachments.HANGOVER_DEBT.get(), 0);
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

    /** A drunk player must have moved at least this far (horizontally) since the last check to be shoved. */
    private static final double WALKED_BLOCKS = 0.2;

    /**
     * Once a second: a drunk player who walked since the last check may be shoved sideways. Walking is
     * judged by how far the player moved since the previous check, not by vanilla's per-tick bookkeeping.
     * Standing still, sneaking, flying, riding and being knocked out are left alone, and the first
     * check after joining only records a position.
     */
    public static void staggerTick(Player player, RandomSource random) {
        Optional<Vec3> last = player.getData(ModAttachments.STAGGER_LAST_POS.get());
        player.setData(ModAttachments.STAGGER_LAST_POS.get(), Optional.of(player.position()));
        int level = getDrunk(player);
        if (last.isEmpty() || level <= StaggerRules.FROM || isKnockedOut(player)
                || player.isShiftKeyDown() || player.getAbilities().flying
                || player.isFallFlying() || player.isPassenger()) {
            return;
        }
        double dx = player.getX() - last.get().x;
        double dz = player.getZ() - last.get().z;
        if (dx * dx + dz * dz < WALKED_BLOCKS * WALKED_BLOCKS || !StaggerRules.shouldStagger(level, random.nextFloat())) {
            return;
        }
        double angle = random.nextFloat() * 2 * Math.PI;
        double strength = StaggerRules.strength(level);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.add(Math.cos(angle) * strength, 0, Math.sin(angle) * strength));
        player.hurtMarked = true;
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
