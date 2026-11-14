package com.kostya.createbrewery.gametest;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.barrel.AgingRules;
import com.kostya.createbrewery.block.BarrelBlock;
import com.kostya.createbrewery.block.BarrelBlockEntity;
import com.kostya.createbrewery.block.BarrelMenu;
import com.kostya.createbrewery.block.DistilleryBlockEntity;
import com.kostya.createbrewery.block.KegBlock;
import com.kostya.createbrewery.block.KegBlockEntity;
import com.kostya.createbrewery.block.KettleBlockEntity;
import com.kostya.createbrewery.block.KettleLogic;
import com.kostya.createbrewery.block.ModBlocks;
import com.kostya.createbrewery.block.WortBottling;
import com.kostya.createbrewery.config.BreweryConfig;
import com.kostya.createbrewery.drunk.DrunkLevel;
import com.kostya.createbrewery.drunk.DrunkManager;
import com.kostya.createbrewery.drunk.ModAttachments;
import com.kostya.createbrewery.drunk.VomitRules;
import com.kostya.createbrewery.item.BookOwner;
import com.kostya.createbrewery.item.BrewerBookItem;
import com.kostya.createbrewery.journal.BarrelRecord;
import com.kostya.createbrewery.journal.BreweryJournalData;
import com.kostya.createbrewery.journal.DrinkJournal;
import com.kostya.createbrewery.journal.KettleRecord;
import com.kostya.createbrewery.journal.StillRecord;
import com.kostya.createbrewery.keg.KegRules;
import com.kostya.createbrewery.network.OpenBrewerBookPayload;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.BrewingRecipeSerializer;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.recipe.MixRules;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.kostya.createbrewery.registry.ModEffects;
import com.kostya.createbrewery.social.HospitalityData;
import com.kostya.createbrewery.social.HospitalityEvents;
import com.kostya.createbrewery.social.ToastEvents;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-world checks of what unit tests cannot reach: real block entities, capabilities and
 * Create's kinetics. Run with {@code gradlew runGameTestServer}.
 */
@GameTestHolder(CreateBrewery.MOD_ID)
@PrefixGameTestTemplate(false)
public class BreweryGameTests {
    /** Bad drinks bite, good ones do not, and the empty bottle always comes back. */
    @GameTest(template = "empty")
    public static void drinkingAppliesQualityEffects(GameTestHelper helper) {
        for (int stars : new int[] {1, 2, 3}) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
            bottle.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(stars, 40, "whiskey"));
            player.setItemInHand(InteractionHand.MAIN_HAND, bottle);

            ItemStack left = bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);

            helper.assertTrue(left.is(Items.GLASS_BOTTLE), "empty bottle lost, got " + left);
            helper.assertTrue(DrunkManager.getDrunk(player) == 8, "drunk " + DrunkManager.getDrunk(player));
            helper.assertTrue(player.hasEffect(MobEffects.CONFUSION) == (stars <= 2), stars + " stars nausea");
            helper.assertTrue(player.hasEffect(MobEffects.POISON) == (stars == 1), stars + " stars poison");
        }
        helper.succeed();
    }

    /** Overdosing blacks the player out for a fixed time, then leaves them drunk but awake. */
    @GameTest(template = "empty")
    public static void overdosePassesOutThenRecovers(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DrunkManager.addDrunk(player, DrunkLevel.PASS_OUT_AT - 1);
        DrunkManager.knockoutTick(player);
        helper.assertFalse(DrunkManager.isKnockedOut(player), "passed out below the limit");

        DrunkManager.addDrunk(player, 1);
        DrunkManager.knockoutTick(player);
        helper.assertTrue(DrunkManager.isKnockedOut(player), "overdose did not knock the player out");
        helper.assertTrue(player.hasEffect(MobEffects.BLINDNESS), "no blackout effect");
        helper.assertTrue(DrunkManager.getDrunk(player) == DrunkLevel.AFTER_PASS_OUT,
                "level after passing out: " + DrunkManager.getDrunk(player));

        for (int tick = 0; tick < DrunkManager.KNOCKOUT_TICKS; tick++) {
            DrunkManager.knockoutTick(player);
        }
        helper.assertFalse(DrunkManager.isKnockedOut(player), "blackout never ended");
        helper.succeed();
    }

    /** Overdosing into a blackout earns "Overdone". */
    @GameTest(template = "empty")
    public static void passingOutAwardsOverdone(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        DrunkManager.addDrunk(player, DrunkLevel.PASS_OUT_AT - 1);
        DrunkManager.knockoutTick(player);
        helper.assertFalse(advancementDone(helper, player, "passed_out"), "passed_out done while still awake");
        DrunkManager.addDrunk(player, 1);
        DrunkManager.knockoutTick(player);
        helper.assertTrue(advancementDone(helper, player, "passed_out"), "blackout gave no advancement");
        helper.succeed();
    }

    /** A hangover earns "The Morning After". */
    @GameTest(template = "empty")
    public static void hangoverAwardsMorningAfter(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        DrunkManager.addDrunk(player, 1);
        DrunkManager.addHangoverDebt(player, 100_000);
        DrunkManager.soberTick(player);
        helper.assertTrue(advancementDone(helper, player, "hangover"), "hangover gave no advancement");
        helper.succeed();
    }

    /**
     * A server-side player with its own advancement book, so no test depends on another. NeoForge's fake
     * players never earn advancements, so this is a plain {@link ServerPlayer} that is never added to the world.
     */
    private static ServerPlayer freshPlayer(GameTestHelper helper) {
        return freshPlayerWithId(helper, UUID.randomUUID());
    }

    private static ServerPlayer freshPlayerWithId(GameTestHelper helper, UUID id) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(id, "taster");
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault());
        // Granting an advancement and syncing the drunk level send packets, so the player needs a connection
        // that swallows them.
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {
            }
        };
        return player;
    }

    private static void drink(GameTestHelper helper, ServerPlayer player, DrinkData data) {
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        bottle.set(ModDataComponents.DRINK_DATA.get(), data);
        bottle.finishUsingItem(helper.getLevel(), player);
    }

    private static boolean advancementDone(GameTestHelper helper, ServerPlayer player, String id) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, id));
        helper.assertTrue(holder != null, "advancement " + id + " is not loaded");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** A drunk player who walked since the last check is shoved sideways by a stagger. */
    @GameTest(template = "empty")
    public static void drunkWalkerIsPushed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        DrunkManager.addDrunk(player, 90);
        player.setDeltaMovement(0, 0, 0);
        DrunkManager.staggerTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getDeltaMovement().x == 0, "first check pushed: " + player.getDeltaMovement());
        walk(player);
        DrunkManager.staggerTick(player, fixedRoll(0.0f));
        helper.assertTrue(Math.abs(player.getDeltaMovement().x) > 0.05,
                "walker was not pushed: " + player.getDeltaMovement());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void standingStillIsNotPushed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        DrunkManager.addDrunk(player, 90);
        player.setDeltaMovement(0.2, 0, 0);
        DrunkManager.staggerTick(player, fixedRoll(0.0f));
        DrunkManager.staggerTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getDeltaMovement().x == 0.2,
                "standing player was pushed: " + player.getDeltaMovement());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sneakingIsNotPushed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        DrunkManager.addDrunk(player, 90);
        player.setShiftKeyDown(true);
        player.setDeltaMovement(0.2, 0, 0);
        DrunkManager.staggerTick(player, fixedRoll(0.0f));
        walk(player);
        DrunkManager.staggerTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getDeltaMovement().x == 0.2, "sneaking player was pushed: " + player.getDeltaMovement());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void airbornePlayerIsNotPushed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(false);
        DrunkManager.addDrunk(player, 90);
        player.setDeltaMovement(0.2, 0, 0);
        DrunkManager.staggerTick(player, fixedRoll(0.0f));
        walk(player);
        DrunkManager.staggerTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getDeltaMovement().x == 0.2, "airborne player was pushed: " + player.getDeltaMovement());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void knockedOutPlayerIsNotPushed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        DrunkManager.addDrunk(player, 90);
        player.setData(ModAttachments.KNOCKOUT_TICKS.get(), 100);
        player.setDeltaMovement(0.2, 0, 0);
        DrunkManager.staggerTick(player, fixedRoll(0.0f));
        walk(player);
        DrunkManager.staggerTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getDeltaMovement().x == 0.2, "knocked-out player was pushed: " + player.getDeltaMovement());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unluckyRollIsNotPushed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setOnGround(true);
        DrunkManager.addDrunk(player, 90);
        player.setDeltaMovement(0.2, 0, 0);
        DrunkManager.staggerTick(player, fixedRoll(0.99f));
        walk(player);
        DrunkManager.staggerTick(player, fixedRoll(0.99f));
        helper.assertTrue(player.getDeltaMovement().x == 0.2, "pushed on a high roll: " + player.getDeltaMovement());
        helper.succeed();
    }

    /** Dying wipes the vomit cooldown too, so the respawned player can throw up at once. */
    @GameTest(template = "empty")
    public static void deathClearsVomitCooldown(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DrunkManager.addDrunk(player, 100);
        player.getFoodData().setFoodLevel(20);
        DrunkManager.vomitTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getFoodData().getFoodLevel() == 16, "first bout: " + player.getFoodData().getFoodLevel());
        DrunkManager.reset(player);
        DrunkManager.addDrunk(player, 100);
        DrunkManager.vomitTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getFoodData().getFoodLevel() == 12, "cooldown survived death: " + player.getFoodData().getFoodLevel());
        helper.succeed();
    }

    /** A heavily drunk player whose roll comes up throws up: food and drunkenness drop, nausea starts. */
    @GameTest(template = "empty")
    public static void heavyDrinkerThrowsUp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DrunkManager.addDrunk(player, 85);
        player.getFoodData().setFoodLevel(20);
        DrunkManager.vomitTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getFoodData().getFoodLevel() == 16, "food: " + player.getFoodData().getFoodLevel());
        helper.assertTrue(DrunkManager.getDrunk(player) == 70, "drunk: " + DrunkManager.getDrunk(player));
        helper.assertTrue(player.hasEffect(MobEffects.CONFUSION), "no nausea");
        helper.succeed();
    }

    /** Right after throwing up a player is safe for thirty seconds, then can do it again. */
    @GameTest(template = "empty")
    public static void vomitingHasACooldown(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DrunkManager.addDrunk(player, 100);
        player.getFoodData().setFoodLevel(20);
        DrunkManager.vomitTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getFoodData().getFoodLevel() == 16, "first bout: " + player.getFoodData().getFoodLevel());
        DrunkManager.addDrunk(player, 100);
        DrunkManager.vomitTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getFoodData().getFoodLevel() == 16, "vomited during the cooldown");
        for (int second = 0; second < VomitRules.COOLDOWN_TICKS / 20; second++) {
            DrunkManager.vomitTick(player, fixedRoll(0.99f));
        }
        DrunkManager.addDrunk(player, 100);
        DrunkManager.vomitTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getFoodData().getFoodLevel() == 12, "no second bout: " + player.getFoodData().getFoodLevel());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void knockedOutPlayerDoesNotVomit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        DrunkManager.addDrunk(player, 100);
        player.getFoodData().setFoodLevel(20);
        player.setData(ModAttachments.KNOCKOUT_TICKS.get(), 100);
        DrunkManager.vomitTick(player, fixedRoll(0.0f));
        helper.assertTrue(player.getFoodData().getFoodLevel() == 20, "knocked-out player vomited");
        helper.assertTrue(DrunkManager.getDrunk(player) == 100, "drunk changed: " + DrunkManager.getDrunk(player));
        helper.succeed();
    }

    /** Sets a config value for the length of {@code body}, then puts the default back whatever happens. */
    private static <T> void withConfig(ModConfigSpec.ConfigValue<T> value, T override, Runnable body) {
        if (!BreweryConfig.SPEC.isLoaded()) {
            throw new AssertionError("server config is not loaded");
        }
        T previous = value.get();
        value.set(override);
        try {
            body.run();
        } finally {
            value.set(previous);
        }
    }

    @GameTest(template = "empty")
    public static void staggerCanBeTurnedOff(GameTestHelper helper) {
        withConfig(BreweryConfig.STAGGER, false, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setOnGround(true);
            DrunkManager.addDrunk(player, 90);
            player.setDeltaMovement(0.2, 0, 0);
            DrunkManager.staggerTick(player, fixedRoll(0.0f));
            walk(player);
            DrunkManager.staggerTick(player, fixedRoll(0.0f));
            helper.assertTrue(player.getDeltaMovement().x == 0.2, "pushed with stagger off: " + player.getDeltaMovement());
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vomitCanBeTurnedOff(GameTestHelper helper) {
        withConfig(BreweryConfig.VOMIT, false, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            DrunkManager.addDrunk(player, 100);
            player.getFoodData().setFoodLevel(20);
            DrunkManager.vomitTick(player, fixedRoll(0.0f));
            helper.assertTrue(player.getFoodData().getFoodLevel() == 20, "vomited with vomit off");
            helper.assertTrue(DrunkManager.getDrunk(player) == 100, "drunk changed: " + DrunkManager.getDrunk(player));
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hangoverCanBeTurnedOff(GameTestHelper helper) {
        withConfig(BreweryConfig.HANGOVER, false, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            for (int i = 0; i < 3; i++) {
                drink(helper, player, 2, 40, "vodka");
            }
            helper.assertTrue(DrunkManager.getHangoverDebt(player) == 0,
                    "debt built up: " + DrunkManager.getHangoverDebt(player));
            // A debt that already existed before the switch (set directly, as addHangoverDebt is gated) must not
            // settle into a hangover either, and is wiped when the player sobers up.
            player.setData(ModAttachments.HANGOVER_DEBT.get(), 100_000);
            DrunkManager.addDrunk(player, 10);
            player.removeAllEffects();
            soberUp(player);
            helper.assertFalse(hungOver(player), "hangover with hangover off");
            helper.assertTrue(DrunkManager.getHangoverDebt(player) == 0,
                    "debt not wiped: " + DrunkManager.getHangoverDebt(player));
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void passOutThresholdIsConfigurable(GameTestHelper helper) {
        withConfig(BreweryConfig.PASS_OUT_AT, 60, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            DrunkManager.addDrunk(player, 59);
            DrunkManager.knockoutTick(player);
            helper.assertFalse(DrunkManager.isKnockedOut(player), "passed out below 60");
            DrunkManager.addDrunk(player, 1);
            DrunkManager.knockoutTick(player);
            helper.assertTrue(DrunkManager.isKnockedOut(player), "no blackout at 60");
            // 60 is not above the threshold any more, so the level must fall below it to avoid a loop.
            helper.assertTrue(DrunkManager.getDrunk(player) == 59, "level after: " + DrunkManager.getDrunk(player));
        });
        withConfig(BreweryConfig.PASS_OUT_AT, 100, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            DrunkManager.addDrunk(player, 99);
            DrunkManager.knockoutTick(player);
            helper.assertFalse(DrunkManager.isKnockedOut(player), "passed out at 99 with threshold 100");
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void soberStepFollowsConfig(GameTestHelper helper) {
        helper.assertTrue(DrunkManager.soberDue(20) && !DrunkManager.soberDue(40 + 1), "default step");
        withConfig(BreweryConfig.SECONDS_PER_SOBER_STEP, 3, () -> {
            helper.assertFalse(DrunkManager.soberDue(20), "due after one second");
            helper.assertFalse(DrunkManager.soberDue(40), "due after two seconds");
            helper.assertTrue(DrunkManager.soberDue(60), "not due after three seconds");
        });
        helper.succeed();
    }

    /** Moves the player a block along x, as a second of walking would. */
    private static void walk(Player player) {
        player.setPos(player.getX() + 1, player.getY(), player.getZ());
    }

    /** A random source whose every float is {@code value}, so the stagger roll and angle are known. */
    private static RandomSource fixedRoll(float value) {
        return new LegacyRandomSource(0L) {
            @Override
            public float nextFloat() {
                return value;
            }
        };
    }

    private static void drink(GameTestHelper helper, Player player, int stars, int alcohol, String stage) {
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        bottle.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(stars, alcohol, stage));
        bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);
    }

    private static void soberUp(Player player) {
        for (int i = 0; i < DrunkLevel.MAX + 1; i++) {
            DrunkManager.soberTick(player);
        }
    }

    private static boolean hungOver(Player player) {
        return player.hasEffect(MobEffects.DIG_SLOWDOWN) && player.hasEffect(MobEffects.HUNGER);
    }

    /** Three poor vodkas: nothing while drunk, then fatigue and hunger the moment the player sobers up. */
    @GameTest(template = "empty")
    public static void badDrinksLeaveAHangover(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < 3; i++) {
            drink(helper, player, 2, 40, "vodka");
        }
        player.removeAllEffects();
        helper.assertFalse(hungOver(player), "hangover while still drunk");
        soberUp(player);
        helper.assertTrue(DrunkManager.getDrunk(player) == 0, "still drunk");
        MobEffectInstance fatigue = player.getEffect(MobEffects.DIG_SLOWDOWN);
        helper.assertTrue(fatigue != null && fatigue.getDuration() == 6000 && fatigue.getAmplifier() == 0,
                "fatigue " + fatigue);
        helper.assertTrue(player.hasEffect(MobEffects.HUNGER), "no hunger");
        helper.assertTrue(DrunkManager.getHangoverDebt(player) == 0, "debt kept: " + DrunkManager.getHangoverDebt(player));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void goodDrinksLeaveNoHangover(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < 3; i++) {
            drink(helper, player, 5, 40, "vodka");
        }
        player.removeAllEffects();
        soberUp(player);
        helper.assertFalse(player.hasEffect(MobEffects.DIG_SLOWDOWN), "fatigue after good drinks");
        helper.assertFalse(player.hasEffect(MobEffects.HUNGER), "hunger after good drinks");
        helper.assertTrue(DrunkManager.getHangoverDebt(player) == 0, "debt kept: " + DrunkManager.getHangoverDebt(player));
        helper.succeed();
    }

    /** A bottle of water ends a hangover but leaves unrelated effects alone. */
    @GameTest(template = "empty")
    public static void waterCuresHangover(GameTestHelper helper) {
        ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        Player sick = helper.makeMockPlayer(GameType.SURVIVAL);
        sick.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 3000));
        sick.addEffect(new MobEffectInstance(MobEffects.HUNGER, 3000));
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(sick, water, 0, water));
        helper.assertFalse(hungOver(sick), "water did not cure the hangover");
        helper.assertFalse(sick.hasEffect(MobEffects.DIG_SLOWDOWN), "fatigue stayed");
        helper.assertFalse(sick.hasEffect(MobEffects.HUNGER), "hunger stayed");

        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        other.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 3000));
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(other, water, 0, water));
        helper.assertTrue(other.hasEffect(MobEffects.DIG_SLOWDOWN), "water stripped a foreign effect");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hangoverDebtAccumulatesUntilSober(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        drink(helper, player, 2, 40, "vodka");
        helper.assertTrue(DrunkManager.getHangoverDebt(player) == 8, "one: " + DrunkManager.getHangoverDebt(player));
        drink(helper, player, 2, 20, "vodka");
        helper.assertTrue(DrunkManager.getHangoverDebt(player) == 12, "two: " + DrunkManager.getHangoverDebt(player));
        DrunkManager.soberTick(player);
        helper.assertTrue(DrunkManager.getHangoverDebt(player) == 12, "debt lost while drunk");
        helper.succeed();
    }

    /** Kvass is too weak to raise the drunk level, so even a cellar of poor bottles builds no debt. */
    @GameTest(template = "empty")
    public static void weakDrinksBuildNoHangoverDebt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < 8; i++) {
            drink(helper, player, 1, 3, "kvass");
        }
        helper.assertTrue(DrunkManager.getDrunk(player) == 0, "drunk " + DrunkManager.getDrunk(player));
        helper.assertTrue(DrunkManager.getHangoverDebt(player) == 0, "debt: " + DrunkManager.getHangoverDebt(player));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deathClearsHangoverDebt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        drink(helper, player, 2, 40, "vodka");
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
        helper.assertTrue(DrunkManager.getHangoverDebt(player) == 0, "debt survived death");
        helper.succeed();
    }

/** The kettle owner's book gets this brew's drink, mix, heat and stars. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void ownersKettleRecordsDrink(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        UUID owner = UUID.randomUUID();
        kettle.setOwner(owner, "Kostya");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    Map<String, DrinkJournal> book = BreweryJournalData.get(helper.getLevel().getServer()).journalOf(owner);
                    DrinkJournal journal = book.get(kettle.getWortDrink());
                    helper.assertTrue(journal != null && journal.kettle().isPresent(), "book " + book);
                    KettleRecord record = journal.kettle().get();
                    helper.assertTrue(record.stars() == kettle.getWortStars(),
                            "recorded " + record.stars() + ", wort " + kettle.getWortStars());
                    helper.assertTrue(record.mix().equals(Map.of("minecraft:wheat", 2)), "mix " + record.mix());
                    helper.assertTrue(record.heat() == KettleLogic.HEAT_HEATED, "heat " + record.heat());
                })
                .thenSucceed();
    }

    /** Muddy brew is nobody's achievement. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void muddyBrewRecordsNothing(GameTestHelper helper) {
        KettleBlockEntity kettle = placeKettle(helper, Items.ROTTEN_FLESH);
        UUID owner = UUID.randomUUID();
        kettle.setOwner(owner, "Kostya");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() == 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    helper.assertTrue("muddy".equals(kettle.getWortDrink()), "brewed " + kettle.getWortDrink());
                    Map<String, DrinkJournal> book = BreweryJournalData.get(helper.getLevel().getServer()).journalOf(owner);
                    helper.assertTrue(book.isEmpty(), "muddy brew recorded: " + book);
                })
                .thenSucceed();
    }

    /** One undecodable entry is skipped; the other players' journals survive the load. */
    @GameTest(template = "empty")
    public static void journalSkipsCorruptEntry(GameTestHelper helper) {
        BreweryJournalData source = new BreweryJournalData();
        UUID good = UUID.randomUUID();
        source.recordKettle(good, "beer", new KettleRecord(KettleRecord.mixOf(List.of("minecraft:wheat")), 1, 3));
        CompoundTag saved = source.save(new CompoundTag(), helper.getLevel().registryAccess());
        CompoundTag garbage = new CompoundTag();
        garbage.putString("player", "not-a-uuid");
        ((ListTag) saved.get("players")).add(garbage);

        BreweryJournalData loaded = BreweryJournalData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.journalOf(good).equals(source.journalOf(good)),
                "valid journal lost: " + loaded.journalOf(good));
        helper.succeed();
    }

    /** A kettle nobody owns credits no one: the whole book is unchanged. Alone in its batch so nothing else writes. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "journal_ownerless")
    public static void ownerlessKettleRecordsNothing(GameTestHelper helper) {
        CompoundTag before = journalSnapshot(helper);
        KettleBlockEntity kettle = placeHeatedKettle(helper);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> helper.assertTrue(before.equals(journalSnapshot(helper)),
                        "ownerless brew changed the book"))
                .thenSucceed();
    }

    /** Distilling credits the mark's owner; a mark without an owner credits nobody. Alone in its batch. */
    @GameTest(template = "empty", timeoutTicks = 800, batch = "journal_still")
    public static void distilleryCreditsMarkOwner(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        DistilleryBlockEntity still = helper.getBlockEntity(DISTILLERY);
        driveStill(helper, DISTILLERY, 96);
        CompoundTag before = journalSnapshot(helper);
        fluids(helper, DISTILLERY).fill(KettleBlockEntity.wortStack(1000, 5, "potato_mash", new BrewerMark("Kostya", 7)),
                IFluidHandler.FluidAction.EXECUTE);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(still.getOutput().getStackInSlot(0).isEmpty(),
                        "no unowned distillate; rpm=" + still.getRpm()))
                .thenExecute(() -> {
                    helper.assertTrue(before.equals(journalSnapshot(helper)), "ownerless mark changed the book");
                    still.getOutput().setStackInSlot(0, ItemStack.EMPTY);
                    fluids(helper, DISTILLERY).fill(KettleBlockEntity.wortStack(1000, 5, "potato_mash",
                            new BrewerMark("Kostya", 7, Optional.of(owner))), IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> helper.assertFalse(still.getOutput().getStackInSlot(0).isEmpty(),
                        "no owned distillate; rpm=" + still.getRpm()))
                .thenExecute(() -> {
                    ItemStack distillate = still.getOutput().getStackInSlot(0);
                    Map<String, DrinkJournal> book = BreweryJournalData.get(helper.getLevel().getServer()).journalOf(owner);
                    DrinkJournal journal = book.get("potato_mash");
                    helper.assertTrue(journal != null && journal.still().isPresent(), "book " + book);
                    StillRecord record = journal.still().get();
                    helper.assertTrue(record.stars() == data(distillate).stars(),
                            "recorded " + record.stars() + ", made " + data(distillate).stars());
                    helper.assertTrue(record.rpm() == Math.round(Math.abs(still.getRpm())), "rpm " + record.rpm());
                })
                .thenSucceed();
    }

    /** Aging credits the mark's owner with the barrel's wood. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "aging_journal")
    public static void barrelCreditsMarkOwner(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);
        ItemStack whiskey = new ItemStack(ModItems.DISTILLATE.get());
        whiskey.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 40, "whiskey"));
        whiskey.set(ModDataComponents.BREWER.get(), new BrewerMark("Kostya", 7, Optional.of(owner)));
        barrel.getInput().setStackInSlot(0, whiskey);
        barrel.getInput().setStackInSlot(1, new ItemStack(Items.OAK_LOG));
        barrel.startAging();
        long period = barrel.getRequiredTicks();

        helper.startSequence()
                .thenExecute(() -> helper.getLevel().setDayTime(helper.getLevel().getDayTime() + period))
                .thenWaitUntil(() -> helper.assertFalse(barrel.getOutput().getStackInSlot(0).isEmpty(), "not finished"))
                .thenExecute(() -> {
                    String drink = BrewingRecipe.profileDistilledTo(helper.getLevel(), "whiskey").id();
                    Map<String, DrinkJournal> book = BreweryJournalData.get(helper.getLevel().getServer()).journalOf(owner);
                    DrinkJournal journal = book.get(drink);
                    helper.assertTrue(journal != null && journal.barrel().isPresent(), "book " + book);
                    BarrelRecord record = journal.barrel().get();
                    helper.assertTrue("minecraft:oak_log".equals(record.wood()), "wood " + record.wood());
                    helper.assertTrue(record.days() == period / AgingRules.TICKS_PER_DAY, "days " + record.days());
                    int stars = data(barrel.getOutput().getStackInSlot(0)).stars();
                    helper.assertTrue(record.stars() == stars, "recorded " + record.stars() + ", aged " + stars);
                })
                .thenSucceed();
    }

    /** A worse brew does not overwrite the best one in the book. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void worseRunKeepsBestRecord(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        BreweryJournalData journalData = BreweryJournalData.get(helper.getLevel().getServer());
        KettleRecord best = new KettleRecord(Map.of("minecraft:wheat", 2), KettleLogic.HEAT_HEATED, 5);
        journalData.recordKettle(owner, DrinkProfile.BEER.id(), best);
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        helper.setBlock(KETTLE.below(), Blocks.SOUL_CAMPFIRE);
        kettle.setOwner(owner, "Kostya");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() == 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    helper.assertTrue(DrinkProfile.BEER.id().equals(kettle.getWortDrink()), "brewed " + kettle.getWortDrink());
                    helper.assertTrue(kettle.getWortStars() < 5, "overheated brew got " + kettle.getWortStars());
                    DrinkJournal journal = journalData.journalOf(owner).get(DrinkProfile.BEER.id());
                    helper.assertTrue(journal != null && best.equals(journal.kettle().orElse(null)),
                            "book " + journal);
                })
                .thenSucceed();
    }

    /** The book saves and loads back unchanged. */
    @GameTest(template = "empty")
    public static void journalSurvivesSaveLoad(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        UUID owner = UUID.randomUUID();
        BreweryJournalData journalData = new BreweryJournalData();
        journalData.recordKettle(owner, "beer", new KettleRecord(Map.of("minecraft:wheat", 2), KettleLogic.HEAT_HEATED, 4));
        journalData.recordStill(owner, "potato_mash", new StillRecord(96, 5));
        journalData.recordBarrel(owner, "beer", new BarrelRecord("minecraft:oak_log", 3, 5));

        CompoundTag tag = journalData.save(new CompoundTag(), registries);
        BreweryJournalData loaded = BreweryJournalData.load(tag, registries);
        helper.assertFalse(journalData.journalOf(owner).isEmpty(), "nothing was recorded");
        helper.assertTrue(journalData.journalOf(owner).equals(loaded.journalOf(owner)),
                "loaded " + loaded.journalOf(owner) + ", saved " + journalData.journalOf(owner));
        helper.succeed();
    }

    /** The first real player to open an unsigned book signs it and reads it. */
    @GameTest(template = "empty")
    public static void bookBindsToFirstReader(GameTestHelper helper) {
        ServerPlayer reader = freshPlayer(helper);
        ItemStack book = new ItemStack(ModItems.BREWER_BOOK.get());
        OpenBrewerBookPayload payload = BrewerBookItem.payloadFor(reader, book);
        BookOwner owner = book.get(ModDataComponents.BOOK_OWNER.get());
        helper.assertTrue(owner != null && owner.id().equals(reader.getUUID())
                && owner.name().equals(reader.getGameProfile().getName()), "book owner " + owner);
        helper.assertTrue(payload.readable(), "the owner cannot read the book");
        helper.assertTrue(payload.ownerName().equals(reader.getGameProfile().getName()), "name " + payload.ownerName());
        helper.succeed();
    }

    /** Someone else's book opens blank and stays theirs. */
    @GameTest(template = "empty")
    public static void strangerGetsNothing(GameTestHelper helper) {
        ServerPlayer owner = freshPlayer(helper);
        ServerPlayer stranger = freshPlayer(helper);
        ItemStack book = new ItemStack(ModItems.BREWER_BOOK.get());
        BrewerBookItem.payloadFor(owner, book);
        OpenBrewerBookPayload payload = BrewerBookItem.payloadFor(stranger, book);
        helper.assertFalse(payload.readable(), "a stranger read the book");
        helper.assertTrue(payload.ownerName().isEmpty(), "stranger saw the name " + payload.ownerName());
        helper.assertTrue(payload.pages().isEmpty(), "stranger saw pages " + payload.pages());
        BookOwner bound = book.get(ModDataComponents.BOOK_OWNER.get());
        helper.assertTrue(bound != null && bound.id().equals(owner.getUUID()), "owner changed to " + bound);
        helper.succeed();
    }

    /** Create's deployer and other fake players never sign a book. */
    @GameTest(template = "empty")
    public static void fakePlayerDoesNotBind(GameTestHelper helper) {
        var fake = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack book = new ItemStack(ModItems.BREWER_BOOK.get());
        OpenBrewerBookPayload payload = BrewerBookItem.payloadFor(fake, book);
        helper.assertTrue(book.get(ModDataComponents.BOOK_OWNER.get()) == null,
                "fake player signed the book: " + book.get(ModDataComponents.BOOK_OWNER.get()));
        helper.assertFalse(payload.readable(), "fake player read the book");
        helper.succeed();
    }

    /** One page per drink, flagged with the stages it has, carrying what the owner has recorded. */
    @GameTest(template = "empty")
    public static void ownerSeesAllDrinksWithStageFlags(GameTestHelper helper) {
        ServerPlayer reader = freshPlayer(helper);
        BreweryJournalData.get(helper.getLevel().getServer()).recordKettle(reader.getUUID(), "beer",
                new KettleRecord(Map.of("minecraft:wheat", 2), KettleLogic.HEAT_HEATED, 4));
        OpenBrewerBookPayload payload = BrewerBookItem.payloadFor(reader, new ItemStack(ModItems.BREWER_BOOK.get()));

        List<String> drinks = helper.getLevel().getRecipeManager()
                .getAllRecipesFor(com.kostya.createbrewery.recipe.ModRecipeTypes.BREWING.get()).stream()
                .map(holder -> holder.value().drink().id()).distinct().toList();
        List<String> pageDrinks = payload.pages().stream().map(OpenBrewerBookPayload.Page::drinkId).toList();
        helper.assertTrue(pageDrinks.size() == drinks.size() && pageDrinks.containsAll(drinks)
                && pageDrinks.stream().distinct().count() == pageDrinks.size(), "pages " + pageDrinks);
        helper.assertFalse(pageDrinks.contains(DrinkProfile.MUDDY.id()), "muddy brew has a page");

        Map<String, OpenBrewerBookPayload.Page> byDrink = new java.util.HashMap<>();
        payload.pages().forEach(page -> byDrink.put(page.drinkId(), page));
        OpenBrewerBookPayload.Page kvass = byDrink.get("kvass");
        helper.assertTrue(kvass != null && !kvass.hasStill() && !kvass.hasBarrel(), "kvass page " + kvass);
        // Whiskey is distilled from beer, then aged.
        OpenBrewerBookPayload.Page beer = byDrink.get("beer");
        helper.assertTrue(beer != null && beer.hasStill() && beer.hasBarrel(), "beer page " + beer);
        OpenBrewerBookPayload.Page wine = byDrink.get("young_wine");
        helper.assertTrue(wine != null && !wine.hasStill() && wine.hasBarrel(), "wine page " + wine);

        helper.assertTrue(beer.journal().flatMap(DrinkJournal::kettle).map(KettleRecord::stars).orElse(0) == 4,
                "beer journal " + beer.journal());
        helper.assertTrue(kvass.journal().isEmpty(), "kvass journal " + kvass.journal());
        helper.succeed();
    }

    private static CompoundTag journalSnapshot(GameTestHelper helper) {
        return BreweryJournalData.get(helper.getLevel().getServer())
                .save(new CompoundTag(), helper.getLevel().registryAccess());
    }

    /** A mug drinks exactly like the bottle of the same drink, and hands the empty mug back. */
    @GameTest(template = "empty")
    public static void drinkingMugReturnsMug(GameTestHelper helper) {
        DrinkData whiskey = new DrinkData(5, 40, "whiskey");
        ServerPlayer withBottle = freshPlayer(helper);
        ItemStack bottle = drinkBottle(1, whiskey, null);
        ItemStack bottleLeft = bottle.getItem().finishUsingItem(bottle, helper.getLevel(), withBottle);
        helper.assertTrue(bottleLeft.is(Items.GLASS_BOTTLE), "bottle gave " + bottleLeft);

        ServerPlayer withMug = freshPlayer(helper);
        ItemStack mug = new ItemStack(ModItems.DRINK_MUG.get());
        mug.set(ModDataComponents.DRINK_DATA.get(), whiskey);
        ItemStack left = mug.getItem().finishUsingItem(mug, helper.getLevel(), withMug);
        helper.assertTrue(left.is(ModItems.MUG.get()), "mug gave " + left);
        helper.assertTrue(DrunkManager.getDrunk(withMug) > 0, "no drunk");
        helper.assertTrue(DrunkManager.getDrunk(withMug) == DrunkManager.getDrunk(withBottle),
                "mug " + DrunkManager.getDrunk(withMug) + " bottle " + DrunkManager.getDrunk(withBottle));
        helper.succeed();
    }

    private static DrinkData data(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.DRINK_DATA.get(), new DrinkData(DrinkData.MIN_STARS, 0, "none"));
    }

    private static ItemStack findBottle(GameTestHelper helper, Player player) {
        Item bottle = ModItems.DRINK_BOTTLE.get();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(bottle)) {
                return stack;
            }
        }
        helper.fail("the spout gave no drink");
        return ItemStack.EMPTY;
    }

    /** A player holding a drink, in the main hand. */
    private static ServerPlayer drinker(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DRINK_BOTTLE.get()));
        return player;
    }

    /** Two players with drinks clink: both get the toast marker for the toast window. */
    @GameTest(template = "empty")
    public static void clinkGivesBothToast(GameTestHelper helper) {
        ServerPlayer a = drinker(helper);
        ServerPlayer b = freshPlayer(helper);
        b.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DRINK_MUG.get()));
        helper.assertTrue(ToastEvents.clink(a, b), "two drinkers did not clink");
        for (ServerPlayer player : List.of(a, b)) {
            MobEffectInstance toast = player.getEffect(ModEffects.TOAST);
            helper.assertTrue(toast != null && toast.getDuration() == BreweryConfig.toastSeconds() * 20,
                    "toast " + toast);
        }
        helper.succeed();
    }

    /** No clink unless both players hold a drink in the main hand. */
    @GameTest(template = "empty")
    public static void clinkNeedsDrinksInBothHands(GameTestHelper helper) {
        ServerPlayer a = drinker(helper);
        ServerPlayer emptyHanded = freshPlayer(helper);
        helper.assertFalse(ToastEvents.clink(a, emptyHanded), "clinked with an empty hand");
        helper.assertFalse(ToastEvents.clink(emptyHanded, a), "an empty hand clinked");
        ServerPlayer mugOnly = freshPlayer(helper);
        mugOnly.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MUG.get()));
        helper.assertFalse(ToastEvents.clink(a, mugOnly), "clinked with an empty mug");
        ServerPlayer offHand = freshPlayer(helper);
        offHand.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.DRINK_BOTTLE.get()));
        helper.assertFalse(ToastEvents.clink(a, offHand), "clinked with a drink in the off hand");
        for (ServerPlayer player : List.of(a, emptyHanded, mugOnly, offHand)) {
            helper.assertFalse(player.hasEffect(ModEffects.TOAST), "toast without a clink");
        }
        helper.succeed();
    }

    /** A deployer or another fake player never clinks, even holding a drink. */
    @GameTest(template = "empty")
    public static void fakePlayerCannotClink(GameTestHelper helper) {
        ServerPlayer real = drinker(helper);
        var fake = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack held = fake.getMainHandItem();
        fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DRINK_BOTTLE.get()));
        try {
            helper.assertFalse(ToastEvents.clink(real, fake), "clinked with a fake player");
            helper.assertFalse(ToastEvents.clink(fake, real), "a fake player clinked");
            helper.assertFalse(real.hasEffect(ModEffects.TOAST), "toast from a fake player");
            helper.assertFalse(fake.hasEffect(ModEffects.TOAST), "fake player got a toast");
        } finally {
            fake.setItemInHand(InteractionHand.MAIN_HAND, held);
        }
        helper.succeed();
    }

    /** A toast halves the drink's hangover debt; the muddy brew spends the marker for nothing. */
    @GameTest(template = "empty")
    public static void toastHalvesHangoverDebt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.addEffect(new MobEffectInstance(ModEffects.TOAST, 200));
        drink(helper, player, 2, 40, "vodka");
        helper.assertTrue(DrunkManager.getHangoverDebt(player) == 4, "toasted debt " + DrunkManager.getHangoverDebt(player));
        helper.assertFalse(player.hasEffect(ModEffects.TOAST), "the drink did not spend the toast");
        Player muddy = helper.makeMockPlayer(GameType.SURVIVAL);
        muddy.addEffect(new MobEffectInstance(ModEffects.TOAST, 200));
        drink(helper, muddy, 3, 2, "muddy");
        helper.assertFalse(muddy.hasEffect(ModEffects.TOAST), "the muddy brew did not spend the toast");
        helper.succeed();
    }

    /** The real interact handler clinks two drinkers, cancels the event and does not start drinking. */
    @GameTest(template = "empty")
    public static void clinkDoesNotStartDrinking(GameTestHelper helper) {
        ServerPlayer a = drinker(helper);
        ServerPlayer b = drinker(helper);
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract(
                a, InteractionHand.MAIN_HAND, b);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(a.hasEffect(ModEffects.TOAST) && b.hasEffect(ModEffects.TOAST), "the handler gave no toast to both");
        helper.assertTrue(event.isCanceled(), "the event was not cancelled");
        helper.assertTrue(event.getCancellationResult() == net.minecraft.world.InteractionResult.SUCCESS,
                "result " + event.getCancellationResult());
        helper.assertFalse(a.isUsingItem(), "the clicking player started drinking");
        helper.succeed();
    }

    /** A bottle of beer signed by {@code owner}, drunk by {@code drinker}. */
    private static void drinkSigned(GameTestHelper helper, ServerPlayer drinker, UUID owner) {
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        bottle.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(4, 20, "beer"));
        bottle.set(ModDataComponents.BREWER.get(), new BrewerMark("brewer", 1, Optional.of(owner)));
        bottle.finishUsingItem(helper.getLevel(), drinker);
    }

    /** Puts a test player on the server's player list for the duration of {@code body}. */
    private static void whileOnline(GameTestHelper helper, ServerPlayer player, Runnable body) {
        // getPlayers() is a read-only view, so reach the real list.
        java.util.List<ServerPlayer> players;
        try {
            var field = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<ServerPlayer> list = (java.util.List<ServerPlayer>) field.get(helper.getLevel().getServer().getPlayerList());
            players = list;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        players.add(player);
        try {
            body.run();
        } finally {
            players.remove(player);
        }
    }

    /** A friend drinking the brewer's drink earns the online brewer "On the house!". */
    @GameTest(template = "empty")
    public static void drinkingFriendsDrinkCreditsBrewer(GameTestHelper helper) {
        ServerPlayer brewer = freshPlayer(helper);
        ServerPlayer friend = freshPlayer(helper);
        whileOnline(helper, brewer, () -> {
            helper.assertFalse(advancementDone(helper, brewer, "treat"), "treat done before anyone drank");
            drinkSigned(helper, friend, brewer.getUUID());
            helper.assertTrue(advancementDone(helper, brewer, "treat"), "the brewer was not credited");
        });
        helper.assertFalse(advancementDone(helper, friend, "treat"), "the drinker was credited");
        helper.succeed();
    }

    /** A spoiled or muddy drink does not count as treating anyone. */
    @GameTest(template = "empty")
    public static void spoiledDrinkCreditsNoTreat(GameTestHelper helper) {
        ServerPlayer brewer = freshPlayer(helper);
        ServerPlayer friend = freshPlayer(helper);
        whileOnline(helper, brewer, () -> {
            for (String stage : List.of("spoiled", "muddy")) {
                ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
                bottle.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(1, 20, stage));
                bottle.set(ModDataComponents.BREWER.get(), new BrewerMark("brewer", 1, Optional.of(brewer.getUUID())));
                bottle.finishUsingItem(helper.getLevel(), friend);
            }
            helper.assertFalse(advancementDone(helper, brewer, "treat"), "a bad drink earned treat");
        });
        helper.assertTrue(HospitalityData.get(helper.getLevel().getServer()).credit(brewer.getUUID(), friend.getUUID()) == 1,
                "a bad drink was counted before");  // credit() records the drinker, so 1 means nobody was recorded earlier
        helper.succeed();
    }

    /** Drinking one's own drink credits nothing. */
    @GameTest(template = "empty")
    public static void ownDrinkCreditsNothing(GameTestHelper helper) {
        ServerPlayer brewer = freshPlayer(helper);
        whileOnline(helper, brewer, () -> drinkSigned(helper, brewer, brewer.getUUID()));
        helper.assertFalse(advancementDone(helper, brewer, "treat"), "own drink earned treat");
        helper.assertTrue(HospitalityData.get(helper.getLevel().getServer()).credit(brewer.getUUID(), brewer.getUUID()) == 0,
                "own drink was counted");
        helper.succeed();
    }

    /** The same friend drinking again is still one friend. */
    @GameTest(template = "empty")
    public static void sameFriendCountsOnce(GameTestHelper helper) {
        ServerPlayer friend = freshPlayer(helper);
        UUID brewer = UUID.randomUUID();
        HospitalityData data = HospitalityData.get(helper.getLevel().getServer());
        drinkSigned(helper, friend, brewer);
        drinkSigned(helper, friend, brewer);
        helper.assertTrue(data.credit(brewer, friend.getUUID()) == 1, "the same friend counted twice");
        helper.succeed();
    }

    /** Five different drinkers make "Life of the party"; four do not. */
    @GameTest(template = "empty")
    public static void fiveFriendsMakeLifeOfTheParty(GameTestHelper helper) {
        ServerPlayer brewer = freshPlayer(helper);
        whileOnline(helper, brewer, () -> {
            for (int i = 0; i < HospitalityData.PARTY_SIZE - 1; i++) {
                drinkSigned(helper, freshPlayer(helper), brewer.getUUID());
            }
            helper.assertFalse(advancementDone(helper, brewer, "life_of_the_party"), "the party started with four");
            drinkSigned(helper, freshPlayer(helper), brewer.getUUID());
            helper.assertTrue(advancementDone(helper, brewer, "life_of_the_party"), "five friends gave no advancement");
        });
        helper.succeed();
    }

    /** An offline brewer is awarded on the next login, once. */
    @GameTest(template = "empty")
    public static void offlineBrewerGetsAwardOnLogin(GameTestHelper helper) {
        UUID brewerId = UUID.randomUUID();
        drinkSigned(helper, freshPlayer(helper), brewerId);
        HospitalityData data = HospitalityData.get(helper.getLevel().getServer());
        ServerPlayer brewer = freshPlayerWithId(helper, brewerId);
        helper.assertFalse(advancementDone(helper, brewer, "treat"), "awarded while away");
        HospitalityEvents.onLogin(brewer);
        helper.assertTrue(advancementDone(helper, brewer, "treat"), "login did not award");
        helper.assertTrue(data.takePending(brewerId).isEmpty(), "pending was not cleared");
        HospitalityEvents.onLogin(brewer);
        helper.assertTrue(data.takePending(brewerId).isEmpty(), "a second login left something pending");
        helper.assertFalse(advancementDone(helper, brewer, "life_of_the_party"), "login awarded the party");
        helper.succeed();
    }

    /** Drinkers and pending awards save and load back; a broken entry is skipped. */
    @GameTest(template = "empty")
    public static void hospitalitySurvivesSaveLoad(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        UUID brewer = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        HospitalityData source = new HospitalityData();
        source.credit(brewer, a);
        source.credit(brewer, b);
        source.addPending(brewer, "treat");
        CompoundTag saved = source.save(new CompoundTag(), registries);
        ((net.minecraft.nbt.ListTag) saved.get("brewers")).add(new CompoundTag());
        HospitalityData loaded = HospitalityData.load(saved, registries);
        helper.assertTrue(loaded.credit(brewer, a) == 2, "drinkers were lost");
        helper.assertTrue(loaded.credit(brewer, UUID.randomUUID()) == 3, "drinkers were not kept apart");
        helper.assertTrue(loaded.takePending(brewer).equals(java.util.Set.of("treat")), "pending was lost");
        helper.succeed();
    }
}
