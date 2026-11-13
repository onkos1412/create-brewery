package com.kostya.createbrewery.drunk;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Random;

/**
 * Gameplay events for the drunk system: sobering over time and resetting on death.
 */
@EventBusSubscriber(modid = CreateBrewery.MOD_ID)
public final class DrunkEvents {

    /** How often (in ticks) a sober step is applied: once per second. */
    public static final int SOBER_INTERVAL_TICKS = 20;

    private DrunkEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (player.tickCount % SOBER_INTERVAL_TICKS == 0) {
            DrunkManager.soberTick(player);
            DrunkManager.staggerTick(player, player.getRandom());
        }
        DrunkManager.knockoutTick(player);
    }

    /** Drunk players slur; a player who has passed out says nothing at all. */
    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (DrunkManager.isKnockedOut(player)) {
            event.setCanceled(true);
            return;
        }
        int level = DrunkManager.getDrunk(player);
        if (level >= DrunkLevel.SLUR_FROM) {
            event.setMessage(Component.literal(DrunkSpeech.slur(event.getRawText(), level, new Random())));
        }
    }

    // The client already blocks these; the server refuses them from a modified client too.
    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(DrunkManager.isKnockedOut(event.getEntity()));
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        event.setCanceled(DrunkManager.isKnockedOut(event.getEntity()));
    }

    @SubscribeEvent
    public static void onHitBlock(PlayerInteractEvent.LeftClickBlock event) {
        event.setCanceled(DrunkManager.isKnockedOut(event.getEntity()));
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        event.setCanceled(DrunkManager.isKnockedOut(event.getEntity()));
    }

    /** A bottle of plain water clears a hangover. */
    @SubscribeEvent
    public static void onFinishDrinking(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide() && isWaterBottle(event.getItem())) {
            DrunkManager.cureHangover(player);
        }
    }

    private static boolean isWaterBottle(ItemStack stack) {
        return stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            DrunkManager.reset(player);
        }
    }
}
