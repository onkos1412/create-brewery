package com.kostya.createbrewery.drunk;

import com.kostya.createbrewery.advancement.BreweryAdvancements;
import com.kostya.createbrewery.config.BreweryConfig;
import com.kostya.createbrewery.network.DrunkSyncPacket;
import com.kostya.createbrewery.network.ModNetwork;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Read/write access to a player's drunk level, hiding the capability plumbing ({@link DrunkData}).
 */
public final class DrunkManager {

    /** How long passing out lasts: ten seconds. */
    public static final int KNOCKOUT_TICKS = 200;

    /** Length of the per-second drunk tick (sobering, staggering, vomiting), in game ticks. */
    public static final int SECOND_TICKS = 20;

    /** A drunk player must have moved at least this far (horizontally) since the last check to be shoved. */
    private static final double WALKED_BLOCKS = 0.2;

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

    /** Whether the sobering step is due on this player tick: once every {@code secondsPerSoberStep} seconds. */
    public static boolean soberDue(int tickCount) {
        return tickCount % (SECOND_TICKS * BreweryConfig.secondsPerSoberStep()) == 0;
    }

    public static int getHangoverDebt(Player player) {
        return DrunkData.of(player).hangoverDebt();
    }

    /** Adds to the debt that comes due once the player is sober again. Builds nothing when hangovers are off. */
    public static void addHangoverDebt(Player player, int amount) {
        if (amount > 0 && BreweryConfig.hangover()) {
            DrunkData.of(player).setHangoverDebt(getHangoverDebt(player) + amount);
        }
    }

    /** Runs once, on the step into sobriety: a big enough debt becomes a hangover, then the debt is wiped. */
    private static void settleHangover(Player player) {
        int debt = getHangoverDebt(player);
        DrunkData.of(player).setHangoverDebt(0);
        if (BreweryConfig.hangover() && HangoverRules.hungOver(debt)) {
            int ticks = HangoverRules.durationTicks(debt);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks));
            player.displayClientMessage(Component.translatable("message.brewery.hangover"), true);
            if (player instanceof ServerPlayer serverPlayer) {
                BreweryAdvancements.award(serverPlayer, "hangover", "hungover");
            }
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
        DrunkData.of(player).setKnockoutTicks(0);
        DrunkData.of(player).setHangoverDebt(0);
        DrunkData.of(player).setVomitCooldown(0);
        markDirty(player);
    }

    public static boolean isKnockedOut(Player player) {
        return DrunkData.of(player).knockoutTicks() > 0;
    }

    /** One server tick of the blackout: counts a running one down, or starts one on overdose. */
    public static void knockoutTick(Player player) {
        int remaining = DrunkData.of(player).knockoutTicks();
        if (remaining > 0) {
            DrunkData.of(player).setKnockoutTicks(remaining - 1);
            sync(player);
        } else if (getDrunk(player) >= BreweryConfig.passOutAt()) {
            passOut(player);
        }
    }

    private static void passOut(Player player) {
        // With a threshold at or below AFTER_PASS_OUT the level must still fall under it, or the player
        // would black out again the moment the blackout ends.
        levelOf(player).set(Math.min(DrunkLevel.AFTER_PASS_OUT, BreweryConfig.passOutAt() - 1));
        DrunkData.of(player).setKnockoutTicks(KNOCKOUT_TICKS);
        markDirty(player);
        player.stopUsingItem();
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, KNOCKOUT_TICKS + 20));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KNOCKOUT_TICKS, 6));
        player.displayClientMessage(Component.translatable("message.brewery.passed_out"), true);
        if (player instanceof ServerPlayer serverPlayer) {
            BreweryAdvancements.award(serverPlayer, "passed_out", "passed_out");
        }
    }

    /**
     * Once a second: a drunk player who walked since the last check may be shoved sideways. Walking is
     * judged by how far the player moved since the previous check, not by vanilla's per-tick bookkeeping.
     * Standing still, sneaking, flying, riding, being in the air and being knocked out are left alone, and the first
     * check after joining only records a position.
     */
    public static void staggerTick(Player player, RandomSource random) {
        Optional<Vec3> last = DrunkData.of(player).staggerLastPos();
        DrunkData.of(player).setStaggerLastPos(Optional.of(player.position()));
        if (!BreweryConfig.stagger()) {
            return;
        }
        int level = getDrunk(player);
        if (last.isEmpty() || level <= StaggerRules.FROM || isKnockedOut(player)
                || player.isShiftKeyDown() || player.getAbilities().flying
                || player.isFallFlying() || !player.onGround() || player.isPassenger()) {
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

    /**
     * Once a second: the cooldown runs down by a second's worth of ticks (20), then a heavy drinker may
     * throw up. A knocked-out player never does.
     */
    public static void vomitTick(Player player, RandomSource random) {
        if (!BreweryConfig.vomit()) {
            return;
        }
        int cooldown = Math.max(0, DrunkData.of(player).vomitCooldown() - SECOND_TICKS);
        DrunkData.of(player).setVomitCooldown(cooldown);
        if (isKnockedOut(player)
                || !VomitRules.shouldVomit(getDrunk(player), getHangoverDebt(player), cooldown, random.nextFloat())) {
            return;
        }
        DrunkData.of(player).setVomitCooldown(VomitRules.COOLDOWN_TICKS);
        FoodData food = player.getFoodData();
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - VomitRules.FOOD_LOST));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100));
        // The level never reaches 0 here (VomitRules.FROM_LEVEL > SOBERS_BY), so hangover settlement stays in soberTick.
        levelOf(player).set(Math.max(0, getDrunk(player) - VomitRules.SOBERS_BY));
        markDirty(player);
        if (player.level() instanceof ServerLevel level) {
            Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(0.4)).add(0, -0.2, 0);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COCOA_BEANS)),
                    mouth.x, mouth.y, mouth.z, 12, 0.15, 0.1, 0.15, 0.05);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0f, 0.6f);
        }
        player.displayClientMessage(Component.translatable("message.brewery.vomited"), true);
    }

    public static boolean isDrunk(Player player) {
        return levelOf(player).isDrunk();
    }

    private static DrunkLevel levelOf(Player player) {
        return DrunkData.of(player).level();
    }

    private static void markDirty(Player player) {
        // 1.21.1 nudged the attachment holder here so NeoForge synced the new level; the capability is
        // saved with the player anyway, so only the owning client needs telling.
        sync(player);
    }

    /**
     * Sends the drunk level and the blackout to the owning client when either changed since the last send
     * (1.21.1 synced both attachments on every change). Players without a connection (mock players) are skipped.
     */
    public static void sync(Player player) {
        sync(player, false);
    }

    /** As {@link #sync(Player)}; {@code force} sends even unchanged values, for a client that has a new player. */
    public static void sync(Player player, boolean force) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            DrunkData data = DrunkData.of(player);
            if (data.markSynced(force)) {
                ModNetwork.sendTo(serverPlayer, new DrunkSyncPacket(data.level().get(), data.knockoutTicks()));
            }
        }
    }
}
