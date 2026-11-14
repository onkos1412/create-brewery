package com.kostya.createbrewery.drunk;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageDecoratorEvent;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Gameplay events for the drunk system: sobering over time and resetting on death. This also carries the
 * {@link DrunkData} over a clone that is not a death, and syncs it to the client when the client gets a new
 * player, which NeoForge's attachments did on their own.
 *
 * <p>Forge subscribed these to its event bus; on Fabric {@link #register()} hooks each one to the Fabric API
 * callback that matches it. Finishing an item has no Fabric callback that sees the item before it was used up, so
 * {@code LivingEntityMixin} calls {@link #onFinishUsingItem} where Forge fired {@code LivingEntityUseItemEvent.Finish}.
 */
public final class DrunkEvents {

    private DrunkEvents() {
    }

    /** Hooks every drunk event; called once from the common entry point. */
    public static void register() {
        ServerPlayerEvents.COPY_FROM.register(DrunkEvents::onCopyFrom);
        // A client gets a new player object on login, respawn and dimension change, so it is told again.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> DrunkManager.sync(handler.player, true));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> DrunkManager.sync(newPlayer, true));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> DrunkManager.sync(player, true));
        ServerTickEvents.END_SERVER_TICK.register(DrunkEvents::onServerTick);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> !DrunkManager.isKnockedOut(sender));
        ServerMessageDecoratorEvent.EVENT.register(ServerMessageDecoratorEvent.CONTENT_PHASE, DrunkEvents::slur);
        // The client already blocks these; the server refuses them from a modified client too. FAIL stops the
        // action, as a cancelled Forge event did.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> refuseIfKnockedOut(player));
        UseItemCallback.EVENT.register((player, level, hand) -> DrunkManager.isKnockedOut(player)
                ? InteractionResultHolder.fail(player.getItemInHand(hand))
                : InteractionResultHolder.pass(ItemStack.EMPTY));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> refuseIfKnockedOut(player));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> refuseIfKnockedOut(player));
        ServerLivingEntityEvents.AFTER_DEATH.register(DrunkEvents::onDeath);
    }

    /**
     * NeoForge copied serializable attachments to the new player unless the clone was a death and the
     * attachment was {@code copyOnDeath}; none of the drunk ones was. So: a death starts from defaults, while
     * leaving the End ({@code alive}) keeps the saved values (level, blackout, hangover debt), and never the
     * stagger position or vomit cooldown.
     */
    public static void onCopyFrom(ServerPlayer original, ServerPlayer copy, boolean alive) {
        if (alive) {
            DrunkData.of(copy).copySavedFrom(DrunkData.of(original));
        }
    }

    /** Forge's server-side {@code PlayerTickEvent} (end phase), once per server tick for every player. */
    private static void onServerTick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            onPlayerTick(player);
        }
    }

    private static void onPlayerTick(Player player) {
        if (player.tickCount % DrunkManager.SECOND_TICKS == 0) {
            // Sobering may be slowed down by the config; staggering and vomiting stay once a second.
            if (DrunkManager.soberDue(player.tickCount)) {
                DrunkManager.soberTick(player);
            }
            DrunkManager.staggerTick(player, player.getRandom());
            DrunkManager.vomitTick(player, player.getRandom());
        }
        DrunkManager.knockoutTick(player);
    }

    /** Drunk players slur; a player who has passed out says nothing at all (refused in ALLOW_CHAT_MESSAGE). */
    private static CompletableFuture<Component> slur(ServerPlayer sender, Component message) {
        if (sender != null) {
            int level = DrunkManager.getDrunk(sender);
            if (level >= DrunkLevel.SLUR_FROM) {
                return CompletableFuture.completedFuture(
                        Component.literal(DrunkSpeech.slur(message.getString(), level, new Random())));
            }
        }
        return CompletableFuture.completedFuture(message);
    }

    private static InteractionResult refuseIfKnockedOut(Player player) {
        return DrunkManager.isKnockedOut(player) ? InteractionResult.FAIL : InteractionResult.PASS;
    }

    /**
     * Forge's {@code LivingEntityUseItemEvent.Finish}: {@code item} is a copy of the stack taken before it was used.
     * A bottle of plain water clears a hangover.
     */
    public static void onFinishUsingItem(LivingEntity entity, ItemStack item) {
        if (entity instanceof Player player && !player.level().isClientSide() && isWaterBottle(item)) {
            DrunkManager.cureHangover(player);
        }
    }

    private static boolean isWaterBottle(ItemStack stack) {
        return stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER;
    }

    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof Player player && !player.level().isClientSide()) {
            DrunkManager.reset(player);
        }
    }
}
