package com.kostya.createbrewery.social;

import com.kostya.createbrewery.config.BreweryConfig;
import com.kostya.createbrewery.registry.ModEffects;
import com.kostya.createbrewery.registry.ModItems;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Clinking: a player holding a drink right-clicks another player who holds one too, and both get the toast marker.
 */
public final class ToastEvents {

    private ToastEvents() {
    }

    /** Forge's {@code EntityInteract} subscriber, on Fabric's {@link UseEntityCallback}. Called from the common entrypoint. */
    public static void register() {
        UseEntityCallback.EVENT.register(ToastEvents::onInteract);
    }

    /**
     * Fires on both sides. Returning anything but {@code PASS} cancels the interaction, as Forge's cancelled event did;
     * {@code SUCCESS} also swings the arm and, on the client, sends the interaction to the server.
     */
    public static InteractionResult onInteract(Player player, Level level, InteractionHand hand, Entity target,
                                               @Nullable EntityHitResult hitResult) {
        // Forge's EntityInteract is the plain interaction; Fabric also fires for the positioned one (hitResult set),
        // which vanilla tries first. Passing there leaves the clink to the plain one, which follows. Forge never fired
        // it for spectators; Fabric fires before the spectator check.
        if (hitResult != null || player.isSpectator() || hand != InteractionHand.MAIN_HAND || !(target instanceof Player other)) {
            return InteractionResult.PASS;
        }
        boolean clinked = level.isClientSide()
                // The client cannot tell a fake player, but must not start drinking either: the server decides.
                ? holdsDrink(player) && holdsDrink(other)
                : clink(player, other);
        return clinked ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * Clinks {@code a}'s drink with {@code b}'s on the server: both get (or refresh) the toast marker, with a clink
     * sound and particles over both. Returns whether they clinked.
     */
    public static boolean clink(Player a, Player b) {
        if (a.level().isClientSide() || a == b
                || !ToastRules.canClink(holdsDrink(a), holdsDrink(b), !(a instanceof FakePlayer), !(b instanceof FakePlayer))) {
            return false;
        }
        int ticks = BreweryConfig.toastSeconds() * 20;
        for (Player player : new Player[] {a, b}) {
            player.addEffect(new MobEffectInstance(ModEffects.TOAST.get(), ticks, 0, false, false, true));
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getEyeY() + 0.5, player.getZ(),
                        6, 0.3, 0.2, 0.3, 0.0);
            }
        }
        double x = (a.getX() + b.getX()) / 2;
        double y = (a.getEyeY() + b.getEyeY()) / 2;
        double z = (a.getZ() + b.getZ()) / 2;
        a.level().playSound(null, x, y, z, SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1.0f,
                1.6f + a.getRandom().nextFloat() * 0.2f);
        return true;
    }

    /** Holds a drink: a drink bottle or a filled mug in the main hand. */
    public static boolean holdsDrink(Player player) {
        ItemStack stack = player.getMainHandItem();
        return stack.is(ModItems.DRINK_BOTTLE.get()) || stack.is(ModItems.DRINK_MUG.get());
    }
}
