package com.kostya.createbrewery.drunk;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Gameplay events for the drunk system: sobering over time and resetting on death. On 1.20.1 this also
 * attaches the {@link DrunkData} capability, carries it over a clone that is not a death, and syncs it to
 * the client when the client gets a new player, which NeoForge's attachments did on their own.
 */
@Mod.EventBusSubscriber(modid = CreateBrewery.MOD_ID)
public final class DrunkEvents {

    private DrunkEvents() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(DrunkData.ID, new DrunkData.Provider());
        }
    }

    /**
     * NeoForge copied serializable attachments to the new player unless the clone was a death and the
     * attachment was {@code copyOnDeath}; none of the drunk ones was. So: a death starts from defaults, while
     * leaving the End keeps the saved values (level, blackout, hangover debt), and never the stagger position
     * or vomit cooldown.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            return;
        }
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            DrunkData.of(event.getEntity()).copySavedFrom(DrunkData.of(original));
        } finally {
            original.invalidateCaps();
        }
    }

    // A client gets a new player object on login, respawn and dimension change, so it is told again.
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        DrunkManager.sync(event.getEntity(), true);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        DrunkManager.sync(event.getEntity(), true);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        DrunkManager.sync(event.getEntity(), true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide()) {
            return;
        }
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
        return stack.is(Items.POTION) && PotionUtils.getPotion(stack) == Potions.WATER;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            DrunkManager.reset(player);
        }
    }
}
