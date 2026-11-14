package com.kostya.createbrewery.gametest;

import com.google.gson.JsonObject;
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
import com.kostya.createbrewery.keg.KegRules;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.BrewingRecipeSerializer;
import com.kostya.createbrewery.recipe.MixRules;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import com.kostya.createbrewery.registry.ModEffects;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-world checks of what unit tests cannot reach: real block entities, capabilities and
 * Create's kinetics. Run with {@code gradlew runGameTestServer}.
 *
 * <p>PORT: the 1.21.1 tests come back slice by slice; the ones not ported yet are parked in
 * {@code port/todo/java/main/com/kostya/createbrewery/gametest/BreweryGameTests.java}.
 */
@GameTestHolder(CreateBrewery.MOD_ID)
@PrefixGameTestTemplate(false)
public class BreweryGameTests {

    /**
     * Old-style data folders fail silently when misnamed, so every shipped brewing recipe must load, as must every
     * shipped advancement and every other recipe.
     */
    @GameTest(template = "empty")
    public static void allRecipesAndAdvancementsLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RecipeManager recipes = server.getRecipeManager();
        Map<ResourceLocation, Resource> recipeFiles = server.getResourceManager().listResources("recipes",
                file -> file.getNamespace().equals(CreateBrewery.MOD_ID) && file.getPath().endsWith(".json"));
        int brewingFiles = 0;
        for (Map.Entry<ResourceLocation, Resource> file : recipeFiles.entrySet()) {
            ResourceLocation id = idOf(file.getKey(), "recipes/");
            JsonObject json = readJson(file.getValue());
            if ("brewery:brewing".equals(GsonHelper.getAsString(json, "type", ""))) {
                brewingFiles++;
            }
            helper.assertTrue(recipes.byKey(id).isPresent(), "recipe " + id + " did not load");
        }
        int brewingLoaded = recipes.getAllRecipesFor(ModRecipeTypes.BREWING.get()).size();
        helper.assertTrue(brewingFiles > 0, "no brewing recipe files found");
        helper.assertTrue(brewingLoaded == brewingFiles,
                brewingLoaded + " brewing recipes loaded from " + brewingFiles + " files");

        Map<ResourceLocation, Resource> advancementFiles = server.getResourceManager().listResources("advancements",
                file -> file.getNamespace().equals(CreateBrewery.MOD_ID) && file.getPath().endsWith(".json"));
        helper.assertTrue(!advancementFiles.isEmpty(), "no advancement files found");
        for (ResourceLocation file : advancementFiles.keySet()) {
            ResourceLocation id = idOf(file, "advancements/");
            helper.assertTrue(server.getAdvancements().getAdvancement(id) != null, "advancement " + id + " did not load");
        }
        CreateBrewery.LOGGER.info("Loaded {} brewing recipes from {} brewery recipe files, {} brewery advancements",
                brewingLoaded, recipeFiles.size(), advancementFiles.size());
        helper.succeed();
    }

    /** {@code brewery:recipes/beer.json} is the recipe {@code brewery:beer}. */
    private static ResourceLocation idOf(ResourceLocation file, String folder) {
        String path = file.getPath();
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path.substring(folder.length(), path.length() - ".json".length()));
    }

    private static JsonObject readJson(Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            return GsonHelper.parse(reader);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Drink data lives in stack NBT on 1.20.1: a fresh bottle equals one set to the default, other data keeps
     * stacks apart and survives copying and saving, and fluids carry it too.
     */
    @GameTest(template = "empty")
    public static void drinkDataLivesInStackNbt(GameTestHelper helper) {
        BrewerMark mark = new BrewerMark("Kostya", 7, Optional.of(UUID.randomUUID()));
        DrinkData vodka = new DrinkData(4, 40, "vodka");

        ItemStack fresh = new ItemStack(ModItems.DRINK_BOTTLE.get());
        ItemStack setToDefault = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkData.set(setToDefault, new DrinkData(DrinkData.MAX_STARS));
        helper.assertTrue(new DrinkData(DrinkData.MAX_STARS).equals(DrinkData.get(fresh)), "fresh bottle " + DrinkData.get(fresh));
        helper.assertTrue(fresh.getTag() == null && setToDefault.getTag() == null, "the default was stored");
        helper.assertTrue(ItemStack.isSameItemSameTags(fresh, setToDefault), "default bottles do not stack");

        ItemStack signed = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkData.set(signed, vodka);
        BrewerMark.set(signed, mark);
        helper.assertFalse(ItemStack.isSameItemSameTags(fresh, signed), "different drinks stack");
        ItemStack copy = signed.copy();
        helper.assertTrue(ItemStack.isSameItemSameTags(signed, copy), "a copy does not stack with its original");
        ItemStack loaded = ItemStack.of(signed.save(new CompoundTag()));
        helper.assertTrue(vodka.equals(DrinkData.get(loaded)) && mark.equals(BrewerMark.get(loaded)),
                "saved " + DrinkData.get(loaded) + " " + BrewerMark.get(loaded));
        DrinkData.set(signed, null);
        BrewerMark.set(signed, null);
        helper.assertTrue(signed.getTag() == null, "removing left a tag " + signed.getTag());
        helper.assertTrue(DrinkData.get(new ItemStack(Items.GLASS_BOTTLE)) == null, "a glass bottle holds a drink");

        FluidStack wort = new FluidStack(ModFluids.WORT.get(), 1000);
        FluidStack plain = new FluidStack(ModFluids.WORT.get(), 1000);
        DrinkData.set(wort, vodka);
        BrewerMark.set(wort, mark);
        helper.assertTrue(DrinkData.get(plain) == null, "plain wort holds a drink");
        helper.assertFalse(wort.isFluidEqual(plain), "different wort mixes");
        helper.assertTrue(wort.isFluidEqual(wort.copy()), "a copy of wort does not mix with it");
        FluidStack loadedWort = FluidStack.loadFluidStackFromNBT(wort.writeToNBT(new CompoundTag()));
        helper.assertTrue(vodka.equals(DrinkData.get(loadedWort)) && mark.equals(BrewerMark.get(loadedWort)),
                "saved wort " + DrinkData.get(loadedWort) + " " + BrewerMark.get(loadedWort));
        DrinkData.set(wort, null);
        BrewerMark.set(wort, null);
        helper.assertTrue(wort.isFluidEqual(plain), "wort with its data removed still differs");
        helper.succeed();
    }

    /** Vodka taken out of the still by hand can still reach a bottle. */
    @GameTest(template = "empty")
    public static void vodkaPoursFromTheHand(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack vodka = new ItemStack(ModItems.DISTILLATE.get());
        DrinkData.set(vodka, new DrinkData(4, 40, "vodka"));
        player.setItemInHand(InteractionHand.MAIN_HAND, vodka);

        vodka.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertFalse(player.getMainHandItem().isEmpty(), "poured with no bottle to pour into");

        player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        vodka.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        DrinkData drink = data(findBottle(helper, player));
        helper.assertTrue("vodka".equals(drink.stage()) && drink.stars() == 4, "poured " + drink);
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 0, "bottle not spent");

        ItemStack whiskey = new ItemStack(ModItems.DISTILLATE.get());
        DrinkData.set(whiskey, new DrinkData(5, 40, "whiskey"));
        player.setItemInHand(InteractionHand.MAIN_HAND, whiskey);
        player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        whiskey.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "whiskey skipped the barrel");
        helper.succeed();
    }

    /** A drink gives its own effect, shorter for a poorer star count and none for a bad one. */
    @GameTest(template = "empty")
    public static void drinkingTequilaGivesSpeed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkData.set(bottle, new DrinkData(5, 40, "tequila"));
        bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);
        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(speed != null && Math.abs(speed.getDuration() - 1200) <= 20,
                "5 stars speed " + (speed == null ? "none" : speed.getDuration()));

        Player poor = helper.makeMockPlayer();
        ItemStack weak = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkData.set(weak, new DrinkData(2, 40, "tequila"));
        weak.getItem().finishUsingItem(weak, helper.getLevel(), poor);
        helper.assertTrue(!poor.hasEffect(MobEffects.MOVEMENT_SPEED), "2 stars gave speed");
        helper.succeed();
    }

    /** Muddy drinks never give the effect of any stage. */
    @GameTest(template = "empty")
    public static void muddyGivesNoEffects(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkData.set(bottle, new DrinkData(5, 2, "muddy"));
        bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);
        helper.assertTrue(player.getActiveEffects().isEmpty(), "muddy gave " + player.getActiveEffects());
        helper.succeed();
    }

    /** Drinking the muddy brew from a mixed-up kettle earns "Something's Off". */
    @GameTest(template = "empty")
    public static void drinkingMuddyAwardsAdvancement(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        helper.assertFalse(advancementDone(helper, player, "muddy"), "muddy done before drinking");
        drink(helper, player, new DrinkData(3, 2, "muddy"));
        helper.assertTrue(advancementDone(helper, player, "muddy"), "muddy brew gave no advancement");
        helper.assertFalse(advancementDone(helper, player, "master_brewer"), "muddy brew made a master");
        helper.succeed();
    }

    /** A five-star drink makes a master brewer, a four-star one does not. */
    @GameTest(template = "empty")
    public static void fiveStarDrinkAwardsMasterBrewer(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        drink(helper, player, new DrinkData(4, 20, "beer"));
        helper.assertFalse(advancementDone(helper, player, "master_brewer"), "four stars made a master");
        drink(helper, player, new DrinkData(5, 20, "beer"));
        helper.assertTrue(advancementDone(helper, player, "master_brewer"), "five stars gave no advancement");
        helper.succeed();
    }

    /** An aged drink earns "Patience"; the young drink of the same recipe does not. */
    @GameTest(template = "empty")
    public static void agedDrinkAwardsPatience(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        drink(helper, player, new DrinkData(3, 40, "whiskey"));
        helper.assertFalse(advancementDone(helper, player, "aged"), "plain whiskey counted as aged");
        drink(helper, player, new DrinkData(3, 40, "aged_whiskey"));
        helper.assertTrue(advancementDone(helper, player, "aged"), "aged whiskey gave no advancement");
        helper.succeed();
    }

    /** "Taster" needs all thirteen drinks: twelve do not finish it, the thirteenth does. */
    @GameTest(template = "empty")
    public static void tasterNeedsEveryDrink(GameTestHelper helper) {
        List<String> stages = List.of("beer", "cider", "kvass", "wine", "mead", "stout", "aged_whiskey",
                "aged_calvados", "aged_rum", "aged_tequila", "vodka", "absinthe", "firefly_liqueur");
        ServerPlayer player = freshPlayer(helper);
        for (int i = 0; i < stages.size() - 1; i++) {
            drink(helper, player, new DrinkData(3, 20, stages.get(i)));
        }
        helper.assertFalse(advancementDone(helper, player, "taster"), "taster done after twelve drinks");
        drink(helper, player, new DrinkData(3, 20, stages.get(stages.size() - 1)));
        helper.assertTrue(advancementDone(helper, player, "taster"), "taster not done after all thirteen");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void effectMultiplierScalesDuration(GameTestHelper helper) {
        // Five-star vodka gives fire resistance for 90 seconds.
        Player player = helper.makeMockPlayer();
        drink(helper, player, 5, 40, "vodka");
        MobEffectInstance normal = player.getEffect(MobEffects.FIRE_RESISTANCE);
        helper.assertTrue(normal != null && normal.getDuration() == 1800, "default " + normal);
        withConfig(BreweryConfig.EFFECT_MULTIPLIER, 2.0, () -> {
            Player doubled = helper.makeMockPlayer();
            drink(helper, doubled, 5, 40, "vodka");
            MobEffectInstance effect = doubled.getEffect(MobEffects.FIRE_RESISTANCE);
            helper.assertTrue(effect != null && effect.getDuration() == 3600, "doubled " + effect);
        });
        withConfig(BreweryConfig.EFFECT_MULTIPLIER, 0.0, () -> {
            Player none = helper.makeMockPlayer();
            drink(helper, none, 5, 40, "vodka");
            helper.assertFalse(none.hasEffect(MobEffects.FIRE_RESISTANCE), "effect with multiplier 0");
        });
        helper.succeed();
    }

    /** A recipe that names an effect the game does not have must fail to load. */
    @GameTest(template = "empty")
    public static void unknownDrinkEffectStopsTheRecipeLoading(GameTestHelper helper) {
        String template = "{\"inputs\": [{\"item\": \"minecraft:bread\"}], "
                + "\"water\": {\"fluid\": \"minecraft:water\", \"amount\": 1000}, "
                + "\"result\": {\"fluid\": \"brewery:wort\", \"amount\": 1000}, "
                + "\"brewTimeTicks\": 200, \"heatLevel\": \"heated\", "
                + "\"drink\": {\"id\": \"kvass\", \"alcohol\": 3, "
                + "\"effects\": {\"kvass\": [{\"effect\": \"%s\", \"seconds\": 60}]}}}";
        var good = com.google.gson.JsonParser.parseString(template.formatted("minecraft:speed"));
        var bad = com.google.gson.JsonParser.parseString(template.formatted("brewery:no_such_effect"));
        helper.assertTrue(BrewingRecipeSerializer.codec(CreateBrewery.id("test")).codec().parse(com.mojang.serialization.JsonOps.INSTANCE, good).result().isPresent(),
                "a known effect was refused");
        helper.assertTrue(BrewingRecipeSerializer.codec(CreateBrewery.id("test")).codec().parse(com.mojang.serialization.JsonOps.INSTANCE, bad).error().isPresent(),
                "an unknown effect was accepted");
        helper.succeed();
    }

    /** Vodka poured by hand from the distillate keeps who brewed it. */
    @GameTest(template = "empty")
    public static void handBottledDistillateKeepsMark(GameTestHelper helper) {
        BrewerMark kostya = new BrewerMark("Kostya", 7);
        Player player = helper.makeMockPlayer();
        ItemStack vodka = new ItemStack(ModItems.DISTILLATE.get());
        DrinkData.set(vodka, new DrinkData(4, 40, "vodka"));
        BrewerMark.set(vodka, kostya);
        player.setItemInHand(InteractionHand.MAIN_HAND, vodka);
        player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));

        vodka.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        ItemStack bottle = findBottle(helper, player);
        helper.assertTrue(kostya.equals(BrewerMark.get(bottle)),
                "hand-poured mark " + BrewerMark.get(bottle));
        helper.succeed();
    }

    /** The same drink lasts half as long again after a toast, spends the marker and earns "Cheers!". */
    @GameTest(template = "empty")
    public static void toastedDrinkLastsLonger(GameTestHelper helper) {
        // Five-star vodka gives fire resistance for 90 seconds.
        ServerPlayer alone = freshPlayer(helper);
        drink(helper, alone, new DrinkData(5, 40, "vodka"));
        ServerPlayer toasted = freshPlayer(helper);
        toasted.addEffect(new MobEffectInstance(ModEffects.TOAST.get(), 200));
        drink(helper, toasted, new DrinkData(5, 40, "vodka"));
        MobEffectInstance plain = alone.getEffect(MobEffects.FIRE_RESISTANCE);
        MobEffectInstance longer = toasted.getEffect(MobEffects.FIRE_RESISTANCE);
        helper.assertTrue(plain != null && longer != null && longer.getDuration() * 2 == plain.getDuration() * 3,
                "alone " + plain + ", toasted " + longer);
        helper.assertFalse(toasted.hasEffect(ModEffects.TOAST.get()), "the drink did not spend the toast");
        helper.assertTrue(advancementDone(helper, toasted, "toast"), "toast gave no advancement");
        helper.assertFalse(advancementDone(helper, alone, "toast"), "drinking alone gave the toast advancement");
        helper.succeed();
    }

    /** A toasted muddy or spoiled drink still spends the marker but earns no "Cheers!". */
    @GameTest(template = "empty")
    public static void toastedMuddyGivesNoCheers(GameTestHelper helper) {
        for (String stage : List.of("muddy", "spoiled")) {
            ServerPlayer player = freshPlayer(helper);
            player.addEffect(new MobEffectInstance(ModEffects.TOAST.get(), 200));
            drink(helper, player, new DrinkData(3, 2, stage));
            helper.assertFalse(player.hasEffect(ModEffects.TOAST.get()), stage + " did not spend the toast");
            helper.assertFalse(advancementDone(helper, player, "toast"), stage + " earned the toast advancement");
        }
        helper.succeed();
    }

    /** Once the toast window runs out, the next drink is an ordinary one. */
    @GameTest(template = "empty")
    public static void expiredToastGivesNoBonus(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.addEffect(new MobEffectInstance(ModEffects.TOAST.get(), 1));
        player.baseTick();
        helper.assertFalse(player.hasEffect(ModEffects.TOAST.get()), "toast did not run out");
        drink(helper, player, 5, 40, "vodka");
        MobEffectInstance effect = player.getEffect(MobEffects.FIRE_RESISTANCE);
        helper.assertTrue(effect != null && effect.getDuration() == 1800, "expired toast still counted: " + effect);
        helper.succeed();
    }

    /** With the bonus turned off, clinking still counts for "Cheers!" but the drink is an ordinary one. */
    @GameTest(template = "empty", batch = "toast_bonus_off")
    public static void toastBonusOffStillAwards(GameTestHelper helper) {
        withConfig(BreweryConfig.TOAST_BONUS, false, () -> {
            ServerPlayer player = freshPlayer(helper);
            player.addEffect(new MobEffectInstance(ModEffects.TOAST.get(), 200));
            drink(helper, player, new DrinkData(5, 40, "vodka"));
            MobEffectInstance effect = player.getEffect(MobEffects.FIRE_RESISTANCE);
            helper.assertTrue(effect != null && effect.getDuration() == 1800, "bonus while off: " + effect);
            helper.assertFalse(player.hasEffect(ModEffects.TOAST.get()), "the drink did not spend the toast");
            helper.assertTrue(advancementDone(helper, player, "toast"), "toast gave no advancement with the bonus off");
        });
        helper.succeed();
    }

    // ---- machines (Task 3) ----------------------------------------------------------

    private static final BlockPos KETTLE = new BlockPos(1, 2, 1);

    private static final BlockPos DISTILLERY = new BlockPos(3, 2, 1);

    private static final BlockPos BARREL = new BlockPos(5, 2, 1);

    /** Kettle → distillery → barrel → spout through capabilities, as pipes and funnels would. */
    @GameTest(template = "empty", timeoutTicks = 1200, batch = "aging_whiskey")
    public static void perfectWhiskeyPathGivesFiveStars(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);

        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        driveStill(helper, DISTILLERY, 32);
        DistilleryBlockEntity distillery = blockEntity(helper, DISTILLERY);

        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);
        helper.setBlock(BARREL.above(), ModBlocks.SPOUT.get());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    FluidStack wort = fluids(helper, KETTLE).drain(1000, IFluidHandler.FluidAction.EXECUTE);
                    int moved = fluids(helper, DISTILLERY).fill(wort, IFluidHandler.FluidAction.EXECUTE);
                    helper.assertTrue(moved == 1000, "distillery accepted " + moved + " mB of wort");
                })
                .thenWaitUntil(() -> helper.assertFalse(distillery.getOutput().getStackInSlot(0).isEmpty(),
                        "no distillate; rpm=" + distillery.getRpm() + " heat=" + distillery.getHeatLevel()))
                .thenExecute(() -> {
                    ItemStack distillate = items(helper, DISTILLERY).extractItem(0, 1, false);
                    helper.assertTrue(data(distillate).stars() == DrinkData.MAX_STARS,
                            "distillate stars " + data(distillate).stars());

                    IItemHandler barrelItems = items(helper, BARREL);
                    helper.assertTrue(barrelItems.insertItem(0, new ItemStack(Items.STONE), false).getCount() == 1,
                            "barrel accepted stone as distillate");
                    helper.assertTrue(barrelItems.insertItem(0, distillate, false).isEmpty(),
                            "barrel refused distillate");
                    helper.assertTrue(barrelItems.insertItem(1, new ItemStack(Items.OAK_PLANKS, 4), false).isEmpty(),
                            "barrel refused oak");
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(barrel.getStartedAt() >= 0, "automated barrel did not start aging");
                    helper.assertTrue(barrel.getInput().getStackInSlot(1).getCount() == 4, "spare wood was lost");
                    helper.assertTrue(barrel.getInput().extractItem(0, 1, false).isEmpty(),
                            "distillate was removable while aging");
                    // What sleeping does: day time jumps ahead while game time carries on.
                    helper.getLevel().setDayTime(helper.getLevel().getDayTime() + AgingRules.REQUIRED_TICKS);
                })
                .thenWaitUntil(() -> helper.assertFalse(barrel.getOutput().getStackInSlot(0).isEmpty(),
                        "barrel did not finish aging"))
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
                    helper.useBlock(BARREL.above(), player);
                    DrinkData drink = data(findBottle(helper, player));
                    helper.assertTrue(drink.stars() == DrinkData.MAX_STARS, "whiskey stars " + drink.stars());
                    helper.assertTrue("aged_whiskey".equals(drink.stage()), "whiskey stage " + drink.stage());
                    helper.assertFalse(player.getMainHandItem().is(Items.GLASS_BOTTLE), "glass bottle was not spent");
                })
                .thenSucceed();
    }

    /** Wrong speed lowers the rating instead of blocking the process. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void wrongRpmLowersQuality(GameTestHelper helper) {
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        driveStill(helper, DISTILLERY, 16);
        DistilleryBlockEntity distillery = blockEntity(helper, DISTILLERY);
        fluids(helper, DISTILLERY).fill(new FluidStack(ModFluids.WORT.get(), 1000), IFluidHandler.FluidAction.EXECUTE);

        helper.succeedWhen(() -> {
            ItemStack output = distillery.getOutput().getStackInSlot(0);
            helper.assertFalse(output.isEmpty(), "no distillate at 16 RPM; rpm=" + distillery.getRpm());
            helper.assertTrue(data(output).stars() == 2, "stars at 16 RPM: " + data(output).stars());
        });
    }

    /** Far off but not a complete miss: the worst rating, but still the intended drink. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void farOffRpmMakesOneStar(GameTestHelper helper) {
        DistilleryBlockEntity distillery = distilleryAt(helper, 56);

        helper.succeedWhen(() -> {
            ItemStack output = distillery.getOutput().getStackInSlot(0);
            helper.assertFalse(output.isEmpty(), "no distillate; rpm=" + distillery.getRpm());
            helper.assertTrue(data(output).stars() == 1, "stars at 56 RPM: " + data(output).stars());
            helper.assertTrue("whiskey".equals(data(output).stage()), "made " + data(output).stage());
        });
    }

    /** A complete miss spoils the batch: no barrel takes it, it pours, and drinking it hurts. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void wildRpmSpoilsTheBatch(GameTestHelper helper) {
        DistilleryBlockEntity distillery = distilleryAt(helper, 256);
        helper.setBlock(BARREL, ModBlocks.BARREL.get());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(distillery.getOutput().getStackInSlot(0).isEmpty(),
                        "no output; rpm=" + distillery.getRpm()))
                .thenExecute(() -> {
                    ItemStack spoiled = distillery.getOutput().getStackInSlot(0).copy();
                    helper.assertTrue(data(spoiled).isSpoiled(), "made " + data(spoiled).stage());
                    helper.assertTrue(items(helper, BARREL).insertItem(0, spoiled.copy(), true).getCount() == 1,
                            "barrel took a spoiled batch");

                    Player player = helper.makeMockPlayer();
                    player.setItemInHand(InteractionHand.MAIN_HAND, spoiled);
                    player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
                    spoiled.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                    ItemStack bottle = findBottle(helper, player);
                    helper.assertTrue(data(bottle).isSpoiled(), "poured " + data(bottle).stage());

                    bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);
                    helper.assertTrue(player.hasEffect(MobEffects.POISON) && player.hasEffect(MobEffects.HUNGER),
                            "spoiled drink did not hurt");
                })
                .thenSucceed();
    }

    /** A motor against the shaft in the still's back, which faces south by default. */
    private static void driveStill(GameTestHelper helper, BlockPos still, int rpm) {
        BlockPos motor = still.south();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.NORTH));
        BlockEntityBehaviour.get(blockEntity(helper, motor), ScrollValueBehaviour.TYPE).setValue(rpm);
    }

    private static DistilleryBlockEntity distilleryAt(GameTestHelper helper, int rpm) {
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        driveStill(helper, DISTILLERY, rpm);
        fluids(helper, DISTILLERY).fill(new FluidStack(ModFluids.WORT.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        return blockEntity(helper, DISTILLERY);
    }

    /** Wine goes kettle, bottle, barrel: the still refuses it, and the bottle comes back empty. */
    @GameTest(template = "empty", timeoutTicks = 800, batch = "aging_wine")
    public static void berriesAgeIntoWineInTheBottle(GameTestHelper helper) {
        KettleBlockEntity kettle = placeKettle(helper, Items.SWEET_BERRIES);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    helper.assertTrue(fluids(helper, DISTILLERY).fill(
                                    fluids(helper, KETTLE).getFluidInTank(1).copy(), IFluidHandler.FluidAction.SIMULATE) == 0,
                            "still took wine must");
                    ItemStack young = kettle.bottleWort();
                    helper.assertTrue("young_wine".equals(data(young).stage()), "bottled " + data(young).stage());
                    IItemHandler barrelItems = items(helper, BARREL);
                    helper.assertTrue(barrelItems.insertItem(0, young, false).isEmpty(), "barrel refused young wine");
                    barrelItems.insertItem(1, new ItemStack(Items.OAK_PLANKS), false);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(barrel.getStartedAt() >= 0, "barrel did not start");
                    helper.getLevel().setDayTime(helper.getLevel().getDayTime() + barrel.getRequiredTicks());
                })
                .thenWaitUntil(() -> helper.assertFalse(barrel.getOutput().getStackInSlot(0).isEmpty(),
                        "barrel did not finish"))
                .thenExecute(() -> {
                    DrinkData wine = data(barrel.getOutput().getStackInSlot(0));
                    helper.assertTrue("wine".equals(wine.stage()), "aged into " + wine.stage());
                    helper.assertTrue(wine.stars() == DrinkData.MAX_STARS, "wine stars " + wine.stars());
                    helper.assertTrue(wine.alcohol() == 25, "wine strength " + wine.alcohol());
                    helper.assertTrue(barrel.getBottles().getStackInSlot(0).is(Items.GLASS_BOTTLE),
                            "the empty bottle did not come back");
                })
                .thenSucceed();
    }

    /** No player at all: a funnel feeds the spout bottles, a real hopper takes the beer away. */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void spoutBottlesWithoutAPlayer(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        BlockPos spout = KETTLE.east();
        helper.setBlock(spout, ModBlocks.SPOUT.get());
        helper.setBlock(spout.above(), AllBlocks.ANDESITE_FUNNEL.get().defaultBlockState()
                .setValue(FunnelBlock.FACING, Direction.UP).setValue(FunnelBlock.POWERED, false));
        helper.setBlock(spout.below(), Blocks.HOPPER);
        helper.spawnItem(Items.GLASS_BOTTLE, spout.getX() + 0.5f, spout.getY() + 1.6f, spout.getZ() + 0.5f);

        helper.succeedWhen(() -> {
            HopperBlockEntity hopper = blockEntity(helper, spout.below());
            helper.assertTrue(data(hopper.getItem(0)).stage().equals("beer"), "hopper holds " + hopper.getItem(0));
            helper.assertTrue(kettle.getWortAmount() == 0, "wort left: " + kettle.getWortAmount());
            helper.assertTrue(items(helper, spout).getStackInSlot(0).isEmpty(), "bottle not spent");
        });
    }

    /** Empty bottles stay put, and nothing is poured until there is a drink to pour. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void spoutHoldsEmptyBottlesBackFromFunnels(GameTestHelper helper) {
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BlockPos spout = BARREL.east();
        helper.setBlock(spout, ModBlocks.SPOUT.get());
        IItemHandler items = items(helper, spout);
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.STONE), false).getCount() == 1, "took stone");
        helper.assertTrue(items.insertItem(0, new ItemStack(Items.GLASS_BOTTLE, 3), false).isEmpty(), "refused bottles");

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(items.extractItem(0, 1, false).isEmpty(), "empty bottle pulled back out");
            helper.assertTrue(items.getStackInSlot(0).getCount() == 3, "bottles spent on nothing");
            BarrelBlockEntity barrel = blockEntity(helper, BARREL);
            ItemStack rum = new ItemStack(ModItems.DRINK_BOTTLE.get());
            DrinkData.set(rum, new DrinkData(5, 45, "aged_rum"));
            barrel.getOutput().setStackInSlot(0, rum);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(data(items.getStackInSlot(1)).stage().equals("aged_rum"), "barrel drink not poured");
            helper.assertTrue(items.getStackInSlot(0).getCount() == 2, "bottle not spent");
        });
    }

    /** Collected on time a drink stays perfect; left two more aging periods it loses two stars. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "aging_over")
    public static void uncollectedDrinkOverAges(GameTestHelper helper) {
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);
        ItemStack whiskey = new ItemStack(ModItems.DISTILLATE.get());
        DrinkData.set(whiskey, new DrinkData(5, 40, "whiskey"));
        barrel.getInput().setStackInSlot(0, whiskey);
        barrel.getInput().setStackInSlot(1, new ItemStack(Items.OAK_LOG));
        barrel.startAging();
        long period = barrel.getRequiredTicks();

        helper.startSequence()
                .thenExecute(() -> helper.getLevel().setDayTime(helper.getLevel().getDayTime() + period))
                .thenWaitUntil(() -> helper.assertFalse(barrel.getOutput().getStackInSlot(0).isEmpty(), "not finished"))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(data(barrel.getOutput().getStackInSlot(0)).stars() == 5, "lost stars at once");
                    helper.getLevel().setDayTime(helper.getLevel().getDayTime() + 2 * period);
                })
                .thenWaitUntil(() -> {
                    int stars = data(barrel.getOutput().getStackInSlot(0)).stars();
                    helper.assertTrue(stars == 3, "stars after over-aging: " + stars);
                })
                .thenSucceed();
    }

    /** `/time set` backward must not stall a batch: the time already aged still counts. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "aging_time_set")
    public static void settingTheClockBackDoesNotFreezeAging(GameTestHelper helper) {
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);
        ItemStack whiskey = new ItemStack(ModItems.DISTILLATE.get());
        DrinkData.set(whiskey, new DrinkData(5, 40, "whiskey"));
        barrel.getInput().setStackInSlot(0, whiskey);
        barrel.getInput().setStackInSlot(1, new ItemStack(Items.OAK_LOG));
        barrel.startAging();
        long period = barrel.getRequiredTicks();

        helper.startSequence()
                .thenExecute(() -> helper.getLevel().setDayTime(helper.getLevel().getDayTime() + period - 100))
                .thenExecuteAfter(2, () -> helper.getLevel().setDayTime(1000))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(barrel.getElapsedTicks() >= period - 100, "lost " + barrel.getElapsedTicks());
                    helper.getLevel().setDayTime(helper.getLevel().getDayTime() + 200);
                })
                .thenWaitUntil(() -> helper.assertFalse(barrel.getOutput().getStackInSlot(0).isEmpty(),
                        "batch frozen at " + barrel.getElapsedTicks()))
                .thenSucceed();
    }

    /** A part batch left in the still can be pumped back out, making room for another drink. */
    @GameTest(template = "empty")
    public static void partBatchDrainsFromTheStill(GameTestHelper helper) {
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        DistilleryBlockEntity still = blockEntity(helper, DISTILLERY);
        FluidStack cider = new FluidStack(ModFluids.WORT.get(), 600);
        DrinkData.set(cider, new DrinkData(4, 0, "cider"));
        IFluidHandler tank = fluids(helper, DISTILLERY);
        tank.fill(cider, IFluidHandler.FluidAction.EXECUTE);

        FluidStack beer = new FluidStack(ModFluids.WORT.get(), 1000);
        DrinkData.set(beer, new DrinkData(5, 0, "beer"));
        helper.assertTrue(tank.fill(beer, IFluidHandler.FluidAction.SIMULATE) == 0, "worts mixed");

        FluidStack drained = tank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(drained.getAmount() == 600, "drained " + drained.getAmount());
        helper.assertTrue("cider".equals(DrinkData.get(drained).stage()), "lost its drink");
        helper.assertTrue(tank.fill(beer, IFluidHandler.FluidAction.EXECUTE) == 1000, "still stayed jammed");
        helper.assertTrue(still.getWortAmount() == 1000, "wort " + still.getWortAmount());
        helper.succeed();
    }

    /** Overheating part of a brew counts even when the fire is right by the end. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void overheatingMidBrewStillCostsStars(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        helper.setBlock(KETTLE.below(), Blocks.SOUL_CAMPFIRE);
        helper.runAfterDelay(100, () -> helper.setBlock(KETTLE.below(), Blocks.CAMPFIRE));

        helper.succeedWhen(() -> {
            helper.assertTrue(kettle.getWortAmount() == 1000, "no wort yet");
            helper.assertTrue(kettle.getWortStars() == 3, "wort stars " + kettle.getWortStars());
        });
    }

    /** The still stands two blocks tall: its column forwards everything and shares its fate. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void distilleryColumnActsAsTheMachine(GameTestHelper helper) {
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        BlockPos abs = helper.absolutePos(DISTILLERY);
        var state = helper.getLevel().getBlockState(abs);
        state.getBlock().setPlacedBy(helper.getLevel(), abs, state, null, new ItemStack(ModBlocks.DISTILLERY_ITEM.get()));
        BlockPos top = DISTILLERY.above();
        helper.assertBlockPresent(ModBlocks.DISTILLERY_TOP.get(), top);

        DistilleryBlockEntity still = blockEntity(helper, DISTILLERY);
        fluids(helper, top).fill(new FluidStack(ModFluids.WORT.get(), 300), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(still.getWortAmount() == 300, "column did not reach the tank: " + still.getWortAmount());

        helper.setBlock(DISTILLERY, Blocks.AIR);
        helper.assertBlockNotPresent(ModBlocks.DISTILLERY_TOP.get(), top);
        helper.succeed();
    }

    /** Breaking the column by hand takes the machine with it. */
    @GameTest(template = "empty")
    public static void breakingTheColumnBreaksTheStill(GameTestHelper helper) {
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        BlockPos abs = helper.absolutePos(DISTILLERY);
        var state = helper.getLevel().getBlockState(abs);
        state.getBlock().setPlacedBy(helper.getLevel(), abs, state, null, new ItemStack(ModBlocks.DISTILLERY_ITEM.get()));

        BlockPos topAbs = abs.above();
        var topState = helper.getLevel().getBlockState(topAbs);
        topState.getBlock().playerWillDestroy(helper.getLevel(), topAbs, topState, helper.makeMockPlayer());

        helper.assertBlockNotPresent(ModBlocks.DISTILLERY.get(), DISTILLERY);
        helper.assertBlockNotPresent(ModBlocks.DISTILLERY_TOP.get(), DISTILLERY.above());
        helper.succeed();
    }

    private static BarrelBlockEntity placeBarrels(GameTestHelper helper, BlockPos... positions) {
        for (BlockPos pos : positions) {
            helper.setBlock(pos, ModBlocks.BARREL.get());
        }
        return blockEntity(helper, positions[0]);
    }

    /** A lone barrel holds one stack; each barrel beside it opens another, and then deepens them. */
    @GameTest(template = "empty")
    public static void barrelsBesideEachOtherGrowTogether(GameTestHelper helper) {
        BarrelBlockEntity first = placeBarrels(helper, BARREL);
        ItemStack rum = new ItemStack(ModItems.DISTILLATE.get(), 16);
        DrinkData.set(rum, new DrinkData(5, 45, "rum"));
        helper.assertTrue(first.root().openStacks() == 1, "lone barrel opens " + first.root().openStacks());
        helper.assertTrue(items(helper, BARREL).insertItem(4, rum.copy(), true).getCount() == 16, "locked stack took items");

        // Four in a square open all four stacks, reachable from any of them.
        placeBarrels(helper, BARREL.east(), BARREL.above(), BARREL.east().above());
        helper.assertTrue(first.root().openStacks() == 4, "four barrels open " + first.root().openStacks());
        helper.assertTrue(first.root() == ((BarrelBlockEntity) helper.getBlockEntity(BARREL.east().above())).root(), "two roots");
        helper.assertTrue(items(helper, BARREL.east()).insertItem(4, rum.copy(), false).isEmpty(), "second barrel refused");
        helper.assertTrue(items(helper, BARREL).getStackInSlot(4).getCount() == 16, "barrels do not share a store");

        // A fifth makes each stack hold more than the item's own limit.
        placeBarrels(helper, BARREL.south());
        items(helper, BARREL).insertItem(0, rum.copy(), false);
        items(helper, BARREL).insertItem(0, rum.copy(), false);
        int held = items(helper, BARREL).getStackInSlot(0).getCount();
        helper.assertTrue(held == 32, "stack stopped at " + held);
        helper.succeed();
    }

    /** A barrel facing another way stays on its own. */
    @GameTest(template = "empty")
    public static void barrelsFacingApartStaySeparate(GameTestHelper helper) {
        BarrelBlockEntity first = placeBarrels(helper, BARREL);
        helper.setBlock(BARREL.east(), ModBlocks.BARREL.get().defaultBlockState().setValue(BarrelBlock.FACING, Direction.EAST));
        BarrelBlockEntity other = blockEntity(helper, BARREL.east());
        helper.assertTrue(first.root() == first && other.root() == other, "barrels facing apart joined");
        helper.assertTrue(first.root().openStacks() == 1, "still opens " + first.root().openStacks());
        helper.succeed();
    }

    /** Breaking the barrel holding everything passes it on; breaking the last one drops it. */
    @GameTest(template = "empty")
    public static void breakingABarrelKeepsTheContents(GameTestHelper helper) {
        BarrelBlockEntity first = placeBarrels(helper, BARREL, BARREL.east());
        BarrelBlockEntity store = first.root();
        BlockPos storePos = store == first ? BARREL : BARREL.east();
        BlockPos otherPos = store == first ? BARREL.east() : BARREL;
        store.getInput().setStackInSlot(1, new ItemStack(Items.OAK_LOG, 3));

        helper.setBlock(storePos, Blocks.AIR);
        BarrelBlockEntity survivor = blockEntity(helper, otherPos);
        helper.assertTrue(survivor.root().getInput().getStackInSlot(1).getCount() == 3, "contents did not pass on");
        helper.assertTrue(survivor.root().openStacks() == 1, "survivor still opens " + survivor.root().openStacks());

        helper.setBlock(otherPos, Blocks.AIR);
        BlockPos abs = helper.absolutePos(otherPos);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(abs).inflate(2))
                .stream().anyMatch(item -> item.getItem().is(Items.OAK_LOG)), "last barrel lost its contents");
        helper.succeed();
    }

    /** Barrels along their axis read as one long barrel: their touching heads are not drawn. */
    @GameTest(template = "empty")
    public static void barrelsInLineOpenTowardEachOther(GameTestHelper helper) {
        placeBarrels(helper, BARREL, BARREL.north());
        var back = helper.getBlockState(BARREL);
        var front = helper.getBlockState(BARREL.north());
        helper.assertTrue(back.getValue(BarrelBlock.OPEN_FRONT) && !back.getValue(BarrelBlock.OPEN_BACK), "back barrel " + back);
        helper.assertTrue(front.getValue(BarrelBlock.OPEN_BACK) && !front.getValue(BarrelBlock.OPEN_FRONT), "front barrel " + front);
        helper.succeed();
    }

    /** Whole stacks age together, and each keeps its own rating. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "aging_big_batch")
    public static void barrelAgesFourStacksTogether(GameTestHelper helper) {
        BarrelBlockEntity barrel = placeBarrels(helper, BARREL, BARREL.east(), BARREL.above(), BARREL.east().above());
        BarrelBlockEntity store = barrel.root();
        IItemHandler items = items(helper, BARREL);
        ItemStack rum5 = new ItemStack(ModItems.DISTILLATE.get(), 16);
        DrinkData.set(rum5, new DrinkData(5, 45, "rum"));
        ItemStack rum3 = new ItemStack(ModItems.DISTILLATE.get(), 7);
        DrinkData.set(rum3, new DrinkData(3, 45, "rum"));
        ItemStack whiskey = new ItemStack(ModItems.DISTILLATE.get());
        DrinkData.set(whiskey, new DrinkData(5, 40, "whiskey"));

        helper.assertTrue(items.insertItem(0, rum5, false).isEmpty(), "first stack refused");
        helper.assertTrue(items.insertItem(4, rum3, false).isEmpty(), "second stack refused");
        helper.assertTrue(items.insertItem(5, whiskey, false).getCount() == 1, "mixed whiskey into a rum batch");
        store.getInput().setStackInSlot(1, new ItemStack(Items.DARK_OAK_LOG));
        helper.assertTrue(store.startAging(), "barrel did not start");

        helper.startSequence()
                .thenExecute(() -> helper.getLevel().setDayTime(helper.getLevel().getDayTime() + store.getRequiredTicks()))
                .thenWaitUntil(() -> helper.assertFalse(items.getStackInSlot(2).isEmpty(), "not finished"))
                .thenExecute(() -> {
                    helper.assertTrue(items.getStackInSlot(2).getCount() == 16, "first stack " + items.getStackInSlot(2));
                    helper.assertTrue(data(items.getStackInSlot(2)).stars() == 5, "first stack stars");
                    helper.assertTrue(items.getStackInSlot(7).getCount() == 7, "second stack " + items.getStackInSlot(7));
                    helper.assertTrue(data(items.getStackInSlot(7)).stars() == 3, "second stack stars");
                    helper.assertTrue(items.extractItem(2, 16, false).isEmpty(), "drinks taken without bottles");
                    items.insertItem(3, new ItemStack(Items.GLASS_BOTTLE, 5), false);
                    helper.assertTrue(items.extractItem(2, 16, false).getCount() == 5, "bottles did not limit the take");
                    helper.assertTrue(store.getBottleCount() == 0, "bottles not spent");
                })
                .thenSucceed();
    }

    /** A barrel saved with the old two slots keeps its items and gains the new ones. */
    @GameTest(template = "empty")
    public static void oldBarrelSaveGrows(GameTestHelper helper) {
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);
        var tag = barrel.saveWithFullMetadata();
        var oldInput = new net.minecraftforge.items.ItemStackHandler(2);
        oldInput.setStackInSlot(1, new ItemStack(Items.OAK_LOG, 2));
        tag.put("Input", oldInput.serializeNBT());
        tag.put("Output", new net.minecraftforge.items.ItemStackHandler(1).serializeNBT());

        barrel.load(tag);
        helper.assertTrue(barrel.getInput().getSlots() == 5 && barrel.getOutput().getSlots() == 4, "barrel stayed small");
        helper.assertTrue(barrel.getInput().getStackInSlot(1).getCount() == 2, "old wood lost");
        helper.succeed();
    }

    /** The surface shows what is inside: nothing, water, then the drink's own wort. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void kettleLiquidTakesTheDrinksColor(GameTestHelper helper) {
        helper.setBlock(KETTLE.below(), Blocks.CAMPFIRE);
        helper.setBlock(KETTLE, ModBlocks.KETTLE.get());
        KettleBlockEntity kettle = blockEntity(helper, KETTLE);
        helper.assertTrue(kettle.getLiquidColor() == KettleBlockEntity.EMPTY_COLOR, "empty kettle colour");
        fluids(helper, KETTLE).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        int water = kettle.getLiquidColor();
        helper.assertTrue(water != KettleBlockEntity.EMPTY_COLOR, "water shows no colour");
        items(helper, KETTLE).insertItem(0, new ItemStack(Items.SWEET_BERRIES, 2), false);

        helper.succeedWhen(() -> {
            helper.assertTrue(kettle.getWortAmount() == 1000, "no wort yet");
            helper.assertTrue(kettle.getLiquidColor() == 0x8E1F3A, "wine wort colour " + kettle.getLiquidColor());
            // A client joining later must be told the drink as well as the amount.
            var tag = kettle.getUpdateTag();
            helper.assertTrue("young_wine".equals(tag.getString("WortDrink")), "update tag " + tag);
        });
    }

    /** Create's own spout bottles wort off a depot: it waits for a full bottle's worth, and quality survives. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void createSpoutBottlesWort(GameTestHelper helper) {
        BlockPos depot = new BlockPos(2, 1, 2);
        BlockPos spout = depot.above(2);
        helper.setBlock(depot, AllBlocks.DEPOT.get());
        helper.setBlock(spout, AllBlocks.SPOUT.get());
        IFluidHandler tank = fluids(helper, spout);
        tank.fill(KettleBlockEntity.wortStack(500, 3, "young_wine", null), IFluidHandler.FluidAction.EXECUTE);
        items(helper, depot).insertItem(0, new ItemStack(Items.GLASS_BOTTLE), false);
        ItemStack loose = new ItemStack(Items.GLASS_BOTTLE);
        var bottle = loose.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(IllegalStateException::new);
        helper.assertTrue(bottle.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE) == 0,
                "bottle handler took water");

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(items(helper, depot).getStackInSlot(0).is(Items.GLASS_BOTTLE), "filled from half a bottle's wort");
            tank.fill(KettleBlockEntity.wortStack(500, 3, "young_wine", null), IFluidHandler.FluidAction.EXECUTE);
            helper.succeedWhen(() -> {
                ItemStack result = items(helper, depot).getStackInSlot(0);
                helper.assertTrue(result.is(ModItems.DRINK_BOTTLE.get()), "not bottled: " + result);
                DrinkData data = data(result);
                helper.assertTrue(data.stars() == 3 && "young_wine".equals(data.stage()), "wrong drink " + data);
                helper.assertTrue(data.alcohol() == BrewingRecipe.profileOf(helper.getLevel(), "young_wine").alcohol(), "alcohol " + data);
                helper.assertTrue(tank.getFluidInTank(0).isEmpty(), "wort left in the spout");
            });
        });
    }

    /** Spec: contents survive a world restart. Each machine is saved and read back into a new block entity. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void machineStateSurvivesSaving(GameTestHelper helper) {
        KettleBlockEntity kettle = placeKettle(helper, Items.APPLE);
        kettle.getInput().setStackInSlot(1, new ItemStack(Items.APPLE, 5));
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);
        barrel.getBottles().setStackInSlot(0, new ItemStack(Items.GLASS_BOTTLE, 7));
        barrel.getInput().setStackInSlot(1, new ItemStack(Items.BIRCH_LOG, 3));
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        DistilleryBlockEntity still = blockEntity(helper, DISTILLERY);
        FluidStack wort = new FluidStack(ModFluids.WORT.get(), 600);
        DrinkData.set(wort, new DrinkData(3, 0, "cider"));
        still.getFluidHandler().fill(wort, IFluidHandler.FluidAction.EXECUTE);

        // Saved part-way through a brew, which must carry on rather than start over.
        helper.runAfterDelay(20, () -> {
            KettleBlockEntity kettle2 = new KettleBlockEntity(kettle.getBlockPos(), kettle.getBlockState());
            kettle2.load(kettle.saveWithFullMetadata());
            helper.assertTrue(kettle2.getWaterAmount() == 1000, "water " + kettle2.getWaterAmount());
            helper.assertTrue(kettle2.getInput().getStackInSlot(1).getCount() == 5, "kettle items lost");
            int progress = kettle.getBrewProgressTicks();
            helper.assertTrue(progress > 0, "kettle never started brewing");
            kettle2.setLevel(helper.getLevel());
            kettle2.serverTick();
            helper.assertTrue(kettle2.getBrewProgressTicks() == progress + 1,
                    "brew progress after loading: " + kettle2.getBrewProgressTicks() + ", before: " + progress);

            BarrelBlockEntity barrel2 = new BarrelBlockEntity(barrel.getBlockPos(), barrel.getBlockState());
            barrel2.load(barrel.saveWithFullMetadata());
            helper.assertTrue(barrel2.getBottles().getStackInSlot(0).getCount() == 7, "bottles lost");
            helper.assertTrue(barrel2.getInput().getStackInSlot(1).is(Items.BIRCH_LOG), "wood lost");

            DistilleryBlockEntity still2 = new DistilleryBlockEntity(still.getBlockPos(), still.getBlockState());
            still2.load(still.saveWithFullMetadata());
            helper.assertTrue(still2.getWortAmount() == 600, "wort " + still2.getWortAmount());
            helper.assertTrue(still2.getInputStars() == 3, "stars " + still2.getInputStars());
            helper.succeed();
        });
    }

    /** The whole wine line with no player: kettle, spout, a real hopper, barrel, bottle back. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "aging_wine_line")
    public static void wineLineRunsWithoutAPlayer(GameTestHelper helper) {
        placeKettle(helper, Items.SWEET_BERRIES);
        BlockPos spout = KETTLE.east();
        BlockPos hopper = spout.below();
        BlockPos barrelPos = hopper.east();
        helper.setBlock(spout, ModBlocks.SPOUT.get());
        helper.setBlock(barrelPos, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, barrelPos);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        items(helper, spout).insertItem(0, new ItemStack(Items.GLASS_BOTTLE), false);
        items(helper, barrelPos).insertItem(1, new ItemStack(Items.OAK_LOG), false);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(barrel.getStartedAt() >= 0, "young wine never reached the barrel"))
                .thenExecute(() -> helper.getLevel().setDayTime(helper.getLevel().getDayTime() + barrel.getRequiredTicks()))
                .thenWaitUntil(() -> helper.assertFalse(barrel.getOutput().getStackInSlot(0).isEmpty(), "not aged"))
                .thenExecute(() -> {
                    DrinkData wine = data(barrel.getOutput().getStackInSlot(0));
                    helper.assertTrue("wine".equals(wine.stage()) && wine.stars() == 5, "made " + wine);
                    helper.assertTrue(barrel.getBottles().getStackInSlot(0).is(Items.GLASS_BOTTLE), "bottle not back");
                })
                .thenSucceed();
    }

    /** Rotation only enters through the shaft in the back, and the still loads the network. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void stillTakesRotationThroughItsBack(GameTestHelper helper) {
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        DistilleryBlockEntity still = blockEntity(helper, DISTILLERY);
        helper.setBlock(DISTILLERY.above(), AllBlocks.CREATIVE_MOTOR.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.DOWN));
        helper.setBlock(DISTILLERY.west(), AllBlocks.CREATIVE_MOTOR.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));

        helper.startSequence()
                .thenExecuteAfter(10, () -> helper.assertTrue(still.getRpm() == 0f, "side motors drove it: " + still.getRpm()))
                .thenExecute(() -> driveStill(helper, DISTILLERY, 48))
                .thenWaitUntil(() -> helper.assertTrue(still.getRpm() == 48f, "rpm " + still.getRpm()))
                .thenExecute(() -> helper.assertTrue(still.calculateStressApplied() == 4f,
                        "stress impact " + still.calculateStressApplied()))
                .thenSucceed();
    }

    /** A shaft with nothing driving it leaves the batch waiting. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void unpoweredShaftLeavesTheBatchWaiting(GameTestHelper helper) {
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        DistilleryBlockEntity still = blockEntity(helper, DISTILLERY);
        fluids(helper, DISTILLERY).fill(new FluidStack(ModFluids.WORT.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        BlockPos shaft = DISTILLERY.south();
        helper.setBlock(shaft, AllBlocks.SHAFT.get().defaultBlockState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z));
        KineticBlockEntity kinetic = blockEntity(helper, shaft);
        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(still.getRpm() == 0f, "unpowered still spins at " + still.getRpm());
                    helper.assertTrue(still.getProgressTicks() == 0, "progress without power: " + still.getProgressTicks());
                    helper.assertTrue(kinetic.getSpeed() == 0f, "shaft spins with no source");
                })
                .thenSucceed();
    }

    /** The whole whiskey line with no player: pump, still, funnels, barrel, spout. */
    @GameTest(template = "empty", timeoutTicks = 1200, batch = "aging_whiskey_line")
    public static void whiskeyLineRunsWithoutAPlayer(GameTestHelper helper) {
        placeHeatedKettle(helper);
        helper.setBlock(KETTLE.east(), AllBlocks.MECHANICAL_PUMP.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(KETTLE.east().above(), AllBlocks.COGWHEEL.get().defaultBlockState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        helper.setBlock(KETTLE.above(), AllBlocks.CREATIVE_MOTOR.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));

        BlockPos still = new BlockPos(3, 2, 1);
        helper.setBlock(still.below(), Blocks.CAMPFIRE);
        helper.setBlock(still, ModBlocks.DISTILLERY.get());
        driveStill(helper, still, 32);

        // The still's funnel drops the distillate onto the barrel's funnel.
        helper.setBlock(still.east(), AllBlocks.ANDESITE_FUNNEL.get().defaultBlockState()
                .setValue(FunnelBlock.FACING, Direction.EAST).setValue(FunnelBlock.POWERED, false)
                .setValue(FunnelBlock.EXTRACTING, true));
        BlockPos barrelPos = still.east().below(2);
        helper.setBlock(barrelPos, ModBlocks.BARREL.get());
        helper.setBlock(barrelPos.above(), AllBlocks.ANDESITE_FUNNEL.get().defaultBlockState()
                .setValue(FunnelBlock.FACING, Direction.UP).setValue(FunnelBlock.POWERED, false));
        BarrelBlockEntity barrel = blockEntity(helper, barrelPos);
        items(helper, barrelPos).insertItem(1, new ItemStack(Items.OAK_LOG), false);

        BlockPos spout = barrelPos.east();
        helper.setBlock(spout, ModBlocks.SPOUT.get());
        items(helper, spout).insertItem(0, new ItemStack(Items.GLASS_BOTTLE), false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(barrel.getStartedAt() >= 0, "distillate never reached the barrel"))
                .thenExecute(() -> helper.getLevel().setDayTime(helper.getLevel().getDayTime() + barrel.getRequiredTicks()))
                .thenWaitUntil(() -> {
                    DrinkData whiskey = data(items(helper, spout).getStackInSlot(1));
                    helper.assertTrue("aged_whiskey".equals(whiskey.stage()) && whiskey.stars() == 5, "made " + whiskey);
                })
                .thenSucceed();
    }

    /** Drinks that are already finished must not go into the barrel a second time. */
    @GameTest(template = "empty")
    public static void barrelRefusesFinishedBottles(GameTestHelper helper) {
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        for (String stage : java.util.List.of("wine", "beer", "aged_whiskey")) {
            ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
            DrinkData.set(bottle, new DrinkData(5, 20, stage));
            helper.assertTrue(items(helper, BARREL).insertItem(0, bottle, true).getCount() == 1,
                    "barrel took " + stage);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void kettleBottleIsBeer(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        helper.setBlock(KETTLE.east(), ModBlocks.SPOUT.get());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
                    helper.useBlock(KETTLE.east(), player);
                    DrinkData drink = data(findBottle(helper, player));
                    helper.assertTrue("beer".equals(drink.stage()), "kettle bottle stage " + drink.stage());
                    helper.assertTrue(drink.alcohol() < DrinkData.DEFAULT_ALCOHOL, "beer as strong as whiskey");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void applesBrewCider(GameTestHelper helper) {
        KettleBlockEntity kettle = placeKettle(helper, Items.APPLE);
        helper.setBlock(KETTLE.east(), ModBlocks.SPOUT.get());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
                    helper.useBlock(KETTLE.east(), player);
                    DrinkData drink = data(findBottle(helper, player));
                    helper.assertTrue("cider".equals(drink.stage()), "apple bottle: " + drink.stage());
                    helper.assertTrue(drink.alcohol() == 15, "cider strength " + drink.alcohol());
                })
                .thenSucceed();
    }

    /** Vodka skips the barrel: it refuses the distillate, and a spout on the still pours it. */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void potatoesDistillToVodkaWithoutABarrel(GameTestHelper helper) {
        KettleBlockEntity kettle = placeKettle(helper, Items.POTATO);
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        driveStill(helper, DISTILLERY, 32);
        DistilleryBlockEntity distillery = blockEntity(helper, DISTILLERY);
        helper.setBlock(DISTILLERY.east(), ModBlocks.SPOUT.get());
        helper.setBlock(BARREL, ModBlocks.BARREL.get());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    FluidStack wort = fluids(helper, KETTLE).drain(1000, IFluidHandler.FluidAction.EXECUTE);
                    fluids(helper, DISTILLERY).fill(wort, IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> helper.assertFalse(distillery.getOutput().getStackInSlot(0).isEmpty(),
                        "no distillate"))
                .thenExecute(() -> {
                    ItemStack distillate = distillery.getOutput().getStackInSlot(0);
                    helper.assertTrue(items(helper, BARREL).insertItem(0, distillate.copy(), true).getCount() == 1,
                            "barrel accepted vodka");

                    Player player = helper.makeMockPlayer();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
                    helper.useBlock(DISTILLERY.east(), player);
                    DrinkData drink = data(findBottle(helper, player));
                    helper.assertTrue("vodka".equals(drink.stage()), "still bottle: " + drink.stage());
                    helper.assertTrue(distillery.getOutput().getStackInSlot(0).isEmpty(), "vodka was not poured");
                })
                .thenSucceed();
    }

    /** A second drink waits while the tank still holds the first, so worts never mix. */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void differentWortWaitsForAnEmptyTank(GameTestHelper helper) {
        KettleBlockEntity kettle = placeKettle(helper, Items.WHEAT);
        fluids(helper, KETTLE).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() == 1000, "kettle brewed no wort"))
                .thenExecute(() -> items(helper, KETTLE).insertItem(0, new ItemStack(Items.APPLE, 2), false))
                .thenExecuteAfter(250, () -> {
                    helper.assertTrue(kettle.getWortAmount() == 1000, "worts mixed: " + kettle.getWortAmount());
                    helper.assertTrue("beer".equals(kettle.getWortDrink()), "tank turned " + kettle.getWortDrink());
                    helper.assertTrue(kettle.getBlockReason() == KettleLogic.BLOCKED_BY_WORT,
                            "kettle waits for " + kettle.getBlockReason());
                })
                .thenExecute(() -> kettle.drainWort(1000))
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() == 1000, "apples never brewed"))
                .thenExecute(() -> helper.assertTrue("cider".equals(kettle.getWortDrink()),
                        "tank holds " + kettle.getWortDrink()))
                .thenSucceed();
    }

    /** A mix over a fire with too little water waits for water and says so. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void dryKettleWaitsForWater(GameTestHelper helper) {
        helper.setBlock(KETTLE.below(), Blocks.CAMPFIRE);
        helper.setBlock(KETTLE, ModBlocks.KETTLE.get());
        KettleBlockEntity dry = blockEntity(helper, KETTLE);
        items(helper, KETTLE).insertItem(0, new ItemStack(Items.WHEAT, 2), false);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(dry.getBrewProgressTicks() == 0, "brewing without water");
                    helper.assertTrue(dry.getBlockReason() == KettleLogic.BLOCKED_BY_WATER,
                            "kettle waits for " + dry.getBlockReason());
                })
                .thenExecute(() -> dry.fillWater(1000))
                .thenWaitUntil(() -> helper.assertTrue(dry.getBrewProgressTicks() > 0, "water did not start the brew"))
                .thenExecute(() -> helper.assertTrue(dry.getBlockReason() == KettleLogic.BLOCKED_NONE,
                        "still waits for " + dry.getBlockReason()))
                .thenSucceed();
    }

    /** Spec: without fire the kettle brews nothing at all, not even muddy brew. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void noFireBrewsNothing(GameTestHelper helper) {
        helper.setBlock(KETTLE.below(), Blocks.STONE);
        helper.setBlock(KETTLE, ModBlocks.KETTLE.get());
        KettleBlockEntity kettle = blockEntity(helper, KETTLE);
        items(helper, KETTLE).insertItem(0, new ItemStack(Items.ROTTEN_FLESH, 2), false);
        fluids(helper, KETTLE).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);

        helper.runAfterDelay(300, () -> {
            helper.assertTrue(kettle.getWortAmount() == 0, "brewed " + kettle.getWortAmount() + " mB without fire");
            helper.assertTrue(kettle.getWaterAmount() == 1000, "water " + kettle.getWaterAmount());
            ItemStack flesh = kettle.getInput().getStackInSlot(0);
            helper.assertTrue(flesh.is(Items.ROTTEN_FLESH) && flesh.getCount() == 2, "slots touched: " + flesh);
            helper.assertTrue(kettle.getBrewProgressTicks() == 0, "progress " + kettle.getBrewProgressTicks());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 1500, batch = "aging_calvados")
    public static void applesAgeIntoCalvadosInBirch(GameTestHelper helper) {
        agedRoad(helper, Items.APPLE, 64, Items.BIRCH_PLANKS, "aged_calvados", DrinkData.MAX_STARS);
    }

    @GameTest(template = "empty", timeoutTicks = 1500, batch = "aging_rum")
    public static void caneAgesIntoRumInDarkOak(GameTestHelper helper) {
        agedRoad(helper, Items.SUGAR_CANE, 48, Items.DARK_OAK_LOG, "aged_rum", DrinkData.MAX_STARS);
    }

    /** Each drink has its own wood: what makes perfect whiskey only makes a decent rum. */
    @GameTest(template = "empty", timeoutTicks = 1500, batch = "aging_rum_wood")
    public static void wrongWoodLowersRum(GameTestHelper helper) {
        agedRoad(helper, Items.SUGAR_CANE, 48, Items.OAK_PLANKS, "aged_rum", 3);
    }

    private static void agedRoad(GameTestHelper helper, Item ingredient, int rpm, Item wood, String aged, int stars) {
        KettleBlockEntity kettle = placeKettle(helper, ingredient);
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        driveStill(helper, DISTILLERY, rpm);
        DistilleryBlockEntity distillery = blockEntity(helper, DISTILLERY);
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> fluids(helper, DISTILLERY).fill(
                        fluids(helper, KETTLE).drain(1000, IFluidHandler.FluidAction.EXECUTE),
                        IFluidHandler.FluidAction.EXECUTE))
                .thenWaitUntil(() -> helper.assertFalse(distillery.getOutput().getStackInSlot(0).isEmpty(),
                        "no distillate; rpm=" + distillery.getRpm()))
                .thenExecute(() -> {
                    IItemHandler barrelItems = items(helper, BARREL);
                    barrelItems.insertItem(0, items(helper, DISTILLERY).extractItem(0, 1, false), false);
                    barrelItems.insertItem(1, new ItemStack(wood), false);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(barrel.getStartedAt() >= 0, "barrel did not start");
                    helper.getLevel().setDayTime(helper.getLevel().getDayTime() + barrel.getRequiredTicks());
                })
                .thenWaitUntil(() -> helper.assertFalse(barrel.getOutput().getStackInSlot(0).isEmpty(),
                        "barrel did not finish"))
                .thenExecute(() -> {
                    DrinkData drink = data(barrel.getOutput().getStackInSlot(0));
                    helper.assertTrue(aged.equals(drink.stage()), "aged into " + drink.stage());
                    helper.assertTrue(drink.stars() == stars, drink.stage() + " stars " + drink.stars());
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void breadBrewsKvass(GameTestHelper helper) {
        KettleBlockEntity kettle = placeMixKettle(helper, Blocks.CAMPFIRE.defaultBlockState(), Items.BREAD, Items.BREAD);
        helper.setBlock(KETTLE.east(), ModBlocks.SPOUT.get());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    helper.assertTrue("kvass".equals(kettle.getWortDrink()), "bread brewed " + kettle.getWortDrink());
                    helper.assertTrue(kettle.getWortStars() == DrinkData.MAX_STARS, "kvass stars " + kettle.getWortStars());
                    Player player = helper.makeMockPlayer();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
                    helper.useBlock(KETTLE.east(), player);
                    DrinkData drink = data(findBottle(helper, player));
                    helper.assertTrue("kvass".equals(drink.stage()), "bread bottle: " + drink.stage());
                    helper.assertTrue(drink.alcohol() == 3, "kvass strength " + drink.alcohol());
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void wheatAndCocoaBrewStout(GameTestHelper helper) {
        KettleBlockEntity kettle = placeMixKettle(helper, Blocks.CAMPFIRE.defaultBlockState(),
                Items.WHEAT, Items.WHEAT, Items.COCOA_BEANS);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    helper.assertTrue("young_stout".equals(kettle.getWortDrink()), "brewed " + kettle.getWortDrink());
                    helper.assertTrue(kettle.getWortStars() == DrinkData.MAX_STARS, "stout stars " + kettle.getWortStars());
                })
                .thenSucceed();
    }

    /** Wormwood needs more than a campfire: too cold it turns muddy, over a seething Blaze Burner it brews. */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void wormwoodNeedsSuperheat(GameTestHelper helper) {
        KettleBlockEntity kettle = placeMixKettle(helper, Blocks.CAMPFIRE.defaultBlockState(), Items.FERN, Items.SUGAR);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "campfire brewed no wort"))
                .thenExecute(() -> {
                    helper.assertTrue("muddy".equals(kettle.getWortDrink()), "campfire brewed " + kettle.getWortDrink());
                    kettle.drainWort(1000);
                    setFire(helper, superheater());
                    items(helper, KETTLE).insertItem(0, new ItemStack(Items.FERN), false);
                    items(helper, KETTLE).insertItem(1, new ItemStack(Items.SUGAR), false);
                    fluids(helper, KETTLE).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "burner brewed no wort"))
                .thenExecute(() -> {
                    helper.assertTrue("wormwood_mash".equals(kettle.getWortDrink()), "burner brewed " + kettle.getWortDrink());
                    helper.assertTrue(kettle.getWortStars() == DrinkData.MAX_STARS, "mash stars " + kettle.getWortStars());
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 800)
    public static void wormwoodDistillsToAbsinthe(GameTestHelper helper) {
        KettleBlockEntity kettle = placeMixKettle(helper, superheater(), Items.FERN, Items.SUGAR);
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        driveStill(helper, DISTILLERY, 112);
        DistilleryBlockEntity distillery = blockEntity(helper, DISTILLERY);
        helper.setBlock(DISTILLERY.east(), ModBlocks.SPOUT.get());
        helper.setBlock(BARREL, ModBlocks.BARREL.get());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> fluids(helper, DISTILLERY).fill(
                        fluids(helper, KETTLE).drain(1000, IFluidHandler.FluidAction.EXECUTE),
                        IFluidHandler.FluidAction.EXECUTE))
                .thenWaitUntil(() -> helper.assertFalse(distillery.getOutput().getStackInSlot(0).isEmpty(),
                        "no distillate; rpm=" + distillery.getRpm()))
                .thenExecute(() -> {
                    ItemStack distillate = distillery.getOutput().getStackInSlot(0);
                    helper.assertTrue(items(helper, BARREL).insertItem(0, distillate.copy(), true).getCount() == 1,
                            "barrel accepted absinthe");

                    Player player = helper.makeMockPlayer();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
                    helper.useBlock(DISTILLERY.east(), player);
                    DrinkData drink = data(findBottle(helper, player));
                    helper.assertTrue("absinthe".equals(drink.stage()), "still bottle: " + drink.stage());
                    helper.assertTrue(distillery.getOutput().getStackInSlot(0).isEmpty(), "absinthe was not poured");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 1500, batch = "aging_tequila")
    public static void pulqueAgesIntoTequilaInAcacia(GameTestHelper helper) {
        agedRoad(helper, Items.CACTUS, 72, Items.ACACIA_PLANKS, "aged_tequila", DrinkData.MAX_STARS);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void glowBerriesBrewGlowMash(GameTestHelper helper) {
        KettleBlockEntity kettle = placeKettle(helper, Items.GLOW_BERRIES);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    helper.assertTrue("glow_mash".equals(kettle.getWortDrink()), "brewed " + kettle.getWortDrink());
                    helper.assertTrue(kettle.getWortStars() == DrinkData.MAX_STARS, "mash stars " + kettle.getWortStars());
                })
                .thenSucceed();
    }

    /** A real funnel takes items dropped on it into the kettle and the barrel. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void createFunnelsFillMachines(GameTestHelper helper) {
        helper.setBlock(KETTLE, ModBlocks.KETTLE.get());
        KettleBlockEntity kettle = blockEntity(helper, KETTLE);
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);
        BlockState funnel = AllBlocks.ANDESITE_FUNNEL.get().defaultBlockState()
                .setValue(FunnelBlock.FACING, Direction.UP).setValue(FunnelBlock.POWERED, false);
        helper.setBlock(KETTLE.above(), funnel);
        helper.setBlock(BARREL.above(), funnel);

        helper.spawnItem(Items.WHEAT, KETTLE.getX() + 0.5f, KETTLE.getY() + 1.6f, KETTLE.getZ() + 0.5f);
        helper.spawnItem(Items.OAK_LOG, BARREL.getX() + 0.5f, BARREL.getY() + 1.6f, BARREL.getZ() + 0.5f);
        helper.succeedWhen(() -> {
            helper.assertTrue(kettle.getInput().getStackInSlot(0).is(Items.WHEAT), "funnel skipped the kettle");
            helper.assertTrue(barrel.getInput().getStackInSlot(1).is(Items.OAK_LOG), "funnel skipped the barrel");
        });
    }

    /** Wort through a real pipe, not just a pump touching both machines. */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void createPipeCarriesWort(GameTestHelper helper) {
        placeHeatedKettle(helper);
        BlockPos pump = KETTLE.east();
        BlockPos pipe = pump.east();
        BlockPos still = pipe.east();
        helper.setBlock(pump, AllBlocks.MECHANICAL_PUMP.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(pump.above(), AllBlocks.COGWHEEL.get().defaultBlockState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        helper.setBlock(KETTLE.above(), AllBlocks.CREATIVE_MOTOR.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(still, ModBlocks.DISTILLERY.get());
        DistilleryBlockEntity distillery = blockEntity(helper, still);
        helper.setBlock(pipe, AllBlocks.FLUID_PIPE.get().updateBlockState(AllBlocks.FLUID_PIPE.getDefaultState(),
                Direction.EAST, null, helper.getLevel(), helper.absolutePos(pipe)));

        helper.succeedWhen(() -> helper.assertTrue(distillery.getWortAmount() == 1000,
                "wort at the far end: " + distillery.getWortAmount()));
    }

    /** Every way of taking a drink from the barrel costs an empty bottle. */
    @GameTest(template = "empty")
    public static void takingADrinkCostsABottle(GameTestHelper helper) {
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);
        IItemHandler funnelView = items(helper, BARREL);
        barrel.getOutput().setStackInSlot(0, new ItemStack(ModItems.DRINK_BOTTLE.get()));

        helper.assertTrue(funnelView.extractItem(2, 1, false).isEmpty(), "funnel took a drink for free");
        Player player = helper.makeMockPlayer();
        BarrelMenu menu = new BarrelMenu(0, player.getInventory(), barrel);
        helper.assertFalse(menu.getSlot(2).mayPickup(player), "GUI gave a drink for free");

        helper.assertTrue(funnelView.insertItem(3, new ItemStack(Items.GLASS_BOTTLE, 2), false).isEmpty(),
                "bottles refused");
        helper.assertTrue(funnelView.insertItem(3, new ItemStack(Items.STONE), false).getCount() == 1,
                "bottle slot took stone");
        helper.assertTrue(menu.getSlot(2).mayPickup(player), "GUI refused a paid drink");
        helper.assertFalse(funnelView.extractItem(2, 1, false).isEmpty(), "funnel refused a paid drink");
        helper.assertTrue(barrel.getBottles().getStackInSlot(0).getCount() == 1, "bottle not spent by the funnel");

        barrel.getOutput().setStackInSlot(0, new ItemStack(ModItems.DRINK_BOTTLE.get()));
        menu.quickMoveStack(player, 2);
        helper.assertTrue(barrel.getBottles().getStackInSlot(0).isEmpty(), "bottle not spent by shift-click");
        helper.succeed();
    }

    /** One wheat too many still brews beer, a star poorer, and the extra wheat goes in with the rest. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void extraWheatCostsAStar(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        items(helper, KETTLE).insertItem(0, new ItemStack(Items.WHEAT), false);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getBrewProgressTicks() > 0, "never started"))
                .thenExecute(() -> helper.assertTrue(kettle.getAroma() == MixRules.AROMA_FAMILIAR,
                        "aroma " + kettle.getAroma()))
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() == 1000, "wort: " + kettle.getWortAmount()))
                .thenExecute(() -> {
                    helper.assertTrue("beer".equals(kettle.getWortDrink()), "brewed " + kettle.getWortDrink());
                    helper.assertTrue(kettle.getWortStars() == 4, "wort stars " + kettle.getWortStars());
                    helper.assertTrue(kettle.getInput().getStackInSlot(0).isEmpty(),
                            "wheat left: " + kettle.getInput().getStackInSlot(0).getCount());
                })
                .thenSucceed();
    }

    /** A funnel that overfills the kettle gets one weak brew out of everything, not a stock for later. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void wholeContentsAreEaten(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        items(helper, KETTLE).insertItem(0, new ItemStack(Items.WHEAT, 12), false);

        helper.succeedWhen(() -> {
            helper.assertTrue(kettle.getWortAmount() == 1000, "wort: " + kettle.getWortAmount());
            helper.assertTrue("beer".equals(kettle.getWortDrink()), "brewed " + kettle.getWortDrink());
            int wheat = kettle.getInput().getStackInSlot(0).getCount();
            helper.assertTrue(wheat == 0, "wheat left: " + wheat);
        });
    }

    /** A mix like no recipe still brews, into the one-star muddy brew. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void strangeMixBrewsMuddy(GameTestHelper helper) {
        KettleBlockEntity kettle = placeKettle(helper, Items.ROTTEN_FLESH);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getBrewProgressTicks() > 0, "never started"))
                .thenExecute(() -> helper.assertTrue(kettle.getAroma() == MixRules.AROMA_WEAK,
                        "aroma " + kettle.getAroma()))
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() == 1000, "wort: " + kettle.getWortAmount()))
                .thenExecute(() -> {
                    helper.assertTrue("muddy".equals(kettle.getWortDrink()), "brewed " + kettle.getWortDrink());
                    helper.assertTrue(kettle.getWortStars() == DrinkData.MIN_STARS,
                            "wort stars " + kettle.getWortStars());
                    helper.assertTrue(kettle.getInput().getStackInSlot(0).isEmpty(), "rotten flesh left over");
                })
                .thenSucceed();
    }

    /** Changing the mix mid-brew starts the count again, so the late item is judged too. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void addingAnItemRestartsTheBrew(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);

        helper.startSequence()
                .thenExecuteAfter(150, () -> items(helper, KETTLE).insertItem(0, new ItemStack(Items.WHEAT), false))
                .thenExecuteAfter(60, () -> {
                    helper.assertTrue(kettle.getWortAmount() == 0, "finished the old brew: " + kettle.getWortAmount());
                    int progress = kettle.getBrewProgressTicks();
                    helper.assertTrue(progress > 0 && progress <= 60, "progress did not restart: " + progress);
                })
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() == 1000, "never brewed"))
                .thenExecute(() -> helper.assertTrue(kettle.getWortStars() == 4, "wort stars " + kettle.getWortStars()))
                .thenSucceed();
    }

    /** Muddy brew is a dead end: the still will not take it, nor the barrel its bottle. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void muddyBrewGoesNowhere(GameTestHelper helper) {
        KettleBlockEntity kettle = placeKettle(helper, Items.ROTTEN_FLESH);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        helper.setBlock(BARREL, ModBlocks.BARREL.get());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() == 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    FluidStack muddy = fluids(helper, KETTLE).drain(1000, IFluidHandler.FluidAction.SIMULATE);
                    helper.assertTrue("muddy".equals(DrinkData.get(muddy).stage()),
                            "kettle holds " + kettle.getWortDrink());
                    int filled = fluids(helper, DISTILLERY).fill(muddy, IFluidHandler.FluidAction.EXECUTE);
                    helper.assertTrue(filled == 0, "still took " + filled + " mB of muddy brew");
                    ItemStack bottle = kettle.bottleWort();
                    helper.assertTrue("muddy".equals(data(bottle).stage()), "bottled " + data(bottle).stage());
                    helper.assertTrue(items(helper, BARREL).insertItem(0, bottle, true).getCount() == 1,
                            "barrel took muddy brew");
                })
                .thenSucceed();
    }

    /** A real Create pump moves overheated wort into the still, and its low rating goes with it. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void createPumpCarriesWortAndItsQuality(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        helper.setBlock(KETTLE.below(), Blocks.SOUL_CAMPFIRE);
        helper.setBlock(KETTLE.east(), AllBlocks.MECHANICAL_PUMP.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(KETTLE.east().above(), AllBlocks.COGWHEEL.get().defaultBlockState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        helper.setBlock(KETTLE.above(), AllBlocks.CREATIVE_MOTOR.get().defaultBlockState()
                .setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        DistilleryBlockEntity distillery = blockEntity(helper, DISTILLERY);

        helper.succeedWhen(() -> {
            helper.assertTrue(distillery.getWortAmount() == 1000, "wort in still: " + distillery.getWortAmount()
                    + ", in kettle: " + kettle.getWortAmount());
            helper.assertTrue(kettle.getWaterAmount() == 0 && kettle.getWortAmount() == 0, "kettle not drained");
            helper.assertTrue(distillery.getInputStars() == 3, "stars in still: " + distillery.getInputStars());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void overheatedBeerLosesStars(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        helper.setBlock(KETTLE.below(), Blocks.SOUL_CAMPFIRE);

        helper.succeedWhen(() -> {
            helper.assertTrue(kettle.getWortAmount() == 1000, "no wort yet");
            DrinkData beer = data(kettle.bottleWort());
            helper.assertTrue(beer.stars() == 3, "overheated beer stars: " + beer.stars());
        });
    }

    @GameTest(template = "empty")
    public static void brokenMachineDropsContents(GameTestHelper helper) {
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);
        barrel.getInput().setStackInSlot(1, new ItemStack(Items.OAK_LOG, 3));
        helper.setBlock(BARREL, Blocks.AIR);

        helper.succeedWhen(() -> {
            List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, BARREL, 2.0);
            helper.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(Items.OAK_LOG)
                    && drop.getItem().getCount() == 3), "barrel contents were lost");
        });
    }

    /** A recipe naming a missing item is dropped at load, so this catches stale Create ids. */
    @GameTest(template = "empty")
    public static void machinesAreCraftable(GameTestHelper helper) {
        for (String path : List.of("kettle", "distillery", "barrel", "spout")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(CreateBrewery.id(path)).isPresent(),
                    "no crafting recipe for " + path);
        }
        helper.succeed();
    }

    // ---- brewer mark -----------------------------------------------------------------

    /** Whoever places the kettle signs what it brews, with their name and the day. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "brewer_sign")
    public static void kettlePlacedByPlayerSignsBrew(GameTestHelper helper) {
        helper.getLevel().setDayTime(5 * BrewerMark.TICKS_PER_DAY + 100);
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        Player player = helper.makeMockPlayer();
        ModBlocks.KETTLE.get().setPlacedBy(helper.getLevel(), helper.absolutePos(KETTLE),
                kettle.getBlockState(), player, new ItemStack(ModBlocks.KETTLE.get()));
        helper.assertTrue(player.getUUID().equals(kettle.getOwnerId()), "owner " + kettle.getOwnerId());

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    ItemStack bottle = kettle.bottleWort();
                    BrewerMark mark = BrewerMark.get(bottle);
                    helper.assertTrue(mark != null, "bottle is not signed");
                    helper.assertTrue(player.getGameProfile().getName().equals(mark.name()), "signed by " + mark.name());
                    long day = BrewerMark.dayOf(helper.getLevel().getDayTime());
                    helper.assertTrue(mark.day() == day, "signed on day " + mark.day() + ", today is " + day);
                    helper.assertTrue(kettle.getWortMark() == null, "empty kettle kept the mark");
                })
                .thenSucceed();
    }

    /** A kettle nobody placed (as by a schematic) brews unsigned drinks. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void ownerlessKettleLeavesNoMark(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        helper.assertTrue(kettle.getOwnerId() == null, "kettle has an owner");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    ItemStack bottle = kettle.bottleWort();
                    helper.assertFalse(bottle.isEmpty(), "no bottle");
                    helper.assertFalse(BrewerMark.get(bottle) != null, "ownerless bottle is signed");
                })
                .thenSucceed();
    }

    /** A second batch topping up the tank keeps the first batch's signature and day. */
    @GameTest(template = "empty", timeoutTicks = 800, batch = "brewer_second")
    public static void secondBrewKeepsFirstMark(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        kettle.setOwner(UUID.randomUUID(), "Kostya");
        long[] firstDay = new long[1];

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "first brew not done"))
                .thenExecute(() -> {
                    BrewerMark mark = kettle.getWortMark();
                    helper.assertTrue(mark != null && "Kostya".equals(mark.name()), "first brew mark " + mark);
                    firstDay[0] = mark.day();
                    helper.getLevel().setDayTime(helper.getLevel().getDayTime() + BrewerMark.TICKS_PER_DAY);
                    items(helper, KETTLE).insertItem(0, new ItemStack(Items.WHEAT, 2), false);
                    fluids(helper, KETTLE).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 2000, "second brew not done"))
                .thenExecute(() -> {
                    BrewerMark mark = kettle.getWortMark();
                    helper.assertTrue(mark != null && mark.day() == firstDay[0],
                            "mark " + mark + ", first day " + firstDay[0]);
                })
                .thenSucceed();
    }

    /** The mark goes into the cube with the wort, back out with it, and onto the distillate and its bottle. */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void markSurvivesDistillery(GameTestHelper helper) {
        BrewerMark kostya = new BrewerMark("Kostya", 7);
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        DistilleryBlockEntity still = blockEntity(helper, DISTILLERY);
        IFluidHandler tank = fluids(helper, DISTILLERY);

        tank.fill(KettleBlockEntity.wortStack(600, 5, "potato_mash", kostya), IFluidHandler.FluidAction.EXECUTE);
        tank.fill(KettleBlockEntity.wortStack(100, 5, "potato_mash", new BrewerMark("Other", 9)),
                IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(kostya.equals(BrewerMark.get(tank.getFluidInTank(0))),
                "tank shows " + BrewerMark.get(tank.getFluidInTank(0)));
        FluidStack drained = tank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(kostya.equals(BrewerMark.get(drained)),
                "drained " + BrewerMark.get(drained));

        // Emptied, the cube forgets the mark: unsigned wort stays unsigned.
        tank.fill(KettleBlockEntity.wortStack(100, 5, "potato_mash", null), IFluidHandler.FluidAction.EXECUTE);
        helper.assertFalse(BrewerMark.get(tank.getFluidInTank(0)) != null, "empty cube kept the mark");
        tank.drain(1000, IFluidHandler.FluidAction.EXECUTE);

        tank.fill(KettleBlockEntity.wortStack(1000, 5, "potato_mash", kostya), IFluidHandler.FluidAction.EXECUTE);
        driveStill(helper, DISTILLERY, 96);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(still.getOutput().getStackInSlot(0).isEmpty(),
                        "no distillate; rpm=" + still.getRpm()))
                .thenExecute(() -> {
                    ItemStack distillate = still.getOutput().getStackInSlot(0);
                    helper.assertTrue("vodka".equals(data(distillate).stage()), "made " + data(distillate).stage());
                    helper.assertTrue(kostya.equals(BrewerMark.get(distillate)),
                            "distillate mark " + BrewerMark.get(distillate));
                    ItemStack bottle = still.takeFinishedBottle();
                    helper.assertTrue(kostya.equals(BrewerMark.get(bottle)),
                            "bottle mark " + BrewerMark.get(bottle));
                })
                .thenSucceed();
    }

    /** Aging keeps the mark, and so does over-aging. */
    @GameTest(template = "empty", timeoutTicks = 300, batch = "aging_brewer")
    public static void markSurvivesBarrel(GameTestHelper helper) {
        BrewerMark kostya = new BrewerMark("Kostya", 7);
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = blockEntity(helper, BARREL);
        ItemStack whiskey = new ItemStack(ModItems.DISTILLATE.get());
        DrinkData.set(whiskey, new DrinkData(5, 40, "whiskey"));
        BrewerMark.set(whiskey, kostya);
        barrel.getInput().setStackInSlot(0, whiskey);
        barrel.getInput().setStackInSlot(1, new ItemStack(Items.OAK_LOG));
        barrel.startAging();
        long period = barrel.getRequiredTicks();

        helper.startSequence()
                .thenExecute(() -> helper.getLevel().setDayTime(helper.getLevel().getDayTime() + period))
                .thenWaitUntil(() -> helper.assertFalse(barrel.getOutput().getStackInSlot(0).isEmpty(), "not finished"))
                .thenExecuteAfter(2, () -> {
                    ItemStack aged = barrel.getOutput().getStackInSlot(0);
                    helper.assertTrue(kostya.equals(BrewerMark.get(aged)),
                            "aged mark " + BrewerMark.get(aged));
                    helper.getLevel().setDayTime(helper.getLevel().getDayTime() + 2 * period);
                })
                .thenWaitUntil(() -> {
                    int stars = data(barrel.getOutput().getStackInSlot(0)).stars();
                    helper.assertTrue(stars == 3, "stars after over-aging: " + stars);
                })
                .thenExecute(() -> {
                    ItemStack worse = barrel.getOutput().getStackInSlot(0);
                    helper.assertTrue(kostya.equals(BrewerMark.get(worse)),
                            "over-aged mark " + BrewerMark.get(worse));
                })
                .thenSucceed();
    }

    /** Spouts and anything else filling bottles carry the mark over from the wort. */
    @GameTest(template = "empty")
    public static void spoutBottlingKeepsMark(GameTestHelper helper) {
        BrewerMark kostya = new BrewerMark("Kostya", 7);
        WortBottling.BottleHandler handler = new WortBottling.BottleHandler(new ItemStack(Items.GLASS_BOTTLE));
        int filled = handler.fill(KettleBlockEntity.wortStack(1000, 4, "beer", kostya), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(filled == WortBottling.WORT_PER_BOTTLE, "filled " + filled);
        helper.assertTrue(kostya.equals(BrewerMark.get(handler.getContainer())),
                "bottle mark " + BrewerMark.get(handler.getContainer()));
        helper.succeed();
    }

    /** Create's deployer and other fake players place kettles without owning them. */
    @GameTest(template = "empty")
    public static void fakePlayerPlacingKettleIsNotOwner(GameTestHelper helper) {
        helper.setBlock(KETTLE, ModBlocks.KETTLE.get());
        KettleBlockEntity kettle = blockEntity(helper, KETTLE);
        var fake = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        ModBlocks.KETTLE.get().setPlacedBy(helper.getLevel(), helper.absolutePos(KETTLE),
                kettle.getBlockState(), fake, new ItemStack(ModBlocks.KETTLE.get()));
        helper.assertTrue(kettle.getOwnerId() == null, "fake player owns the kettle: " + kettle.getOwnerId());
        helper.succeed();
    }

    /** A spoiled batch is still the brewer's: the mark rides on the spoiled distillate. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void spoiledDistillateKeepsMark(GameTestHelper helper) {
        BrewerMark kostya = new BrewerMark("Kostya", 7);
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        driveStill(helper, DISTILLERY, 256);
        fluids(helper, DISTILLERY).fill(KettleBlockEntity.wortStack(1000, 5, "potato_mash", kostya),
                IFluidHandler.FluidAction.EXECUTE);
        DistilleryBlockEntity still = blockEntity(helper, DISTILLERY);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(still.getOutput().getStackInSlot(0).isEmpty(),
                        "no output; rpm=" + still.getRpm()))
                .thenExecute(() -> {
                    ItemStack spoiled = still.getOutput().getStackInSlot(0);
                    helper.assertTrue(data(spoiled).isSpoiled(), "made " + data(spoiled).stage());
                    helper.assertTrue(kostya.equals(BrewerMark.get(spoiled)),
                            "spoiled mark " + BrewerMark.get(spoiled));
                })
                .thenSucceed();
    }

    /** Owner and wort mark are saved; a save from before marks existed loads with neither. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void kettleOwnerSurvivesSaveLoad(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        UUID owner = UUID.randomUUID();
        kettle.setOwner(owner, "Kostya");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() >= 1000, "kettle brewed no wort"))
                .thenExecute(() -> {
                    var tag = kettle.saveWithoutMetadata();
                    KettleBlockEntity loaded = new KettleBlockEntity(kettle.getBlockPos(), kettle.getBlockState());
                    loaded.load(tag);
                    helper.assertTrue(owner.equals(loaded.getOwnerId()), "owner " + loaded.getOwnerId());
                    helper.assertTrue(kettle.getWortMark() != null && kettle.getWortMark().equals(loaded.getWortMark()),
                            "mark " + loaded.getWortMark() + ", was " + kettle.getWortMark());

                    tag.remove("OwnerId");
                    tag.remove("OwnerName");
                    tag.remove("WortBrewer");
                    KettleBlockEntity old = new KettleBlockEntity(kettle.getBlockPos(), kettle.getBlockState());
                    old.load(tag);
                    helper.assertTrue(old.getOwnerId() == null && old.getWortMark() == null, "old save got a brewer");
                })
                .thenSucceed();
    }

    private static KettleBlockEntity placeHeatedKettle(GameTestHelper helper) {
        return placeKettle(helper, Items.WHEAT);
    }

    private static KettleBlockEntity placeKettle(GameTestHelper helper, Item ingredient) {
        helper.setBlock(KETTLE.below(), Blocks.CAMPFIRE);
        helper.setBlock(KETTLE, ModBlocks.KETTLE.get());
        items(helper, KETTLE).insertItem(0, new ItemStack(ingredient, 2), false);
        fluids(helper, KETTLE).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        return blockEntity(helper, KETTLE);
    }

    private static BlockState superheater() {
        return AllBlocks.BLAZE_BURNER.get().defaultBlockState()
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SEETHING);
    }

    /** A creative burner keeps its seething blockstate: a fuelled one would cool off on its own tick. */
    private static void setFire(GameTestHelper helper, BlockState fire) {
        helper.setBlock(KETTLE.below(), fire);
        if (helper.getBlockEntity(KETTLE.below()) instanceof BlazeBurnerBlockEntity burner) {
            burner.isCreative = true;
        }
    }

    /** A kettle with water over the given fire; the items fill the input slots, one kind to a slot. */
    private static KettleBlockEntity placeMixKettle(GameTestHelper helper, BlockState fire, Item... mix) {
        setFire(helper, fire);
        helper.setBlock(KETTLE, ModBlocks.KETTLE.get());
        int slot = -1;
        Item last = null;
        for (Item item : mix) {
            if (item != last) {
                slot++;
                last = item;
            }
            items(helper, KETTLE).insertItem(slot, new ItemStack(item), false);
        }
        fluids(helper, KETTLE).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        return blockEntity(helper, KETTLE);
    }

    // ---- keg ----------------------------------------------------------------------

    private static final BlockPos KEG = new BlockPos(1, 2, 1);

    private static KegBlockEntity placeKeg(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.KEG.get());
        return blockEntity(helper, pos);
    }

    private static ItemStack drinkBottle(int count, DrinkData data, BrewerMark mark) {
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get(), count);
        DrinkData.set(bottle, data);
        if (mark != null) {
            BrewerMark.set(bottle, mark);
        }
        return bottle;
    }

    /** Uses what the player holds on the keg, as a right-click does. */
    private static InteractionResult useOnKeg(GameTestHelper helper, Player player, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        return helper.getLevel().getBlockState(abs).use(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
    }

    private static int countInInventory(Player player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Three bottles poured by hand make three portions, and their glass comes back. */
    @GameTest(template = "empty")
    public static void kegTakesBottlesByHand(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        ServerPlayer player = freshPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, drinkBottle(3, new DrinkData(4, 20, "beer"), null));
        for (int i = 0; i < 3; i++) {
            useOnKeg(helper, player, KEG);
        }
        helper.assertTrue(keg.getPortions() == 3, "portions " + keg.getPortions());
        helper.assertTrue(keg.getEmpties() == 0, "hand filling kept glass in the keg");
        helper.assertTrue(countInInventory(player, ModItems.DRINK_BOTTLE.get()) == 0, "bottles not spent");
        helper.assertTrue(countInInventory(player, Items.GLASS_BOTTLE) == 3,
                "glass bottles back " + countInInventory(player, Items.GLASS_BOTTLE));
        helper.assertTrue(helper.getBlockState(KEG).getValue(KegBlock.LEVEL) == KegRules.windowLevel(3),
                "window shows " + helper.getBlockState(KEG).getValue(KegBlock.LEVEL));
        helper.assertTrue(data(keg.getDrink()).equals(new DrinkData(4, 20, "beer")), "keg holds " + data(keg.getDrink()));
        helper.succeed();
    }

    /** Stars and signatures never blend: only the very same drink goes into a filled keg. */
    @GameTest(template = "empty")
    public static void kegRefusesDifferentStarsOrSignature(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        BrewerMark kostya = new BrewerMark("Kostya", 7);
        helper.assertTrue(keg.pourIn(drinkBottle(1, new DrinkData(4, 20, "beer"), kostya), false), "empty keg refused beer");
        helper.assertFalse(keg.pourIn(drinkBottle(1, new DrinkData(3, 20, "beer"), kostya), false), "took fewer stars");
        helper.assertFalse(keg.pourIn(drinkBottle(1, new DrinkData(4, 20, "beer"), null), false), "took an unsigned beer");
        helper.assertFalse(keg.pourIn(drinkBottle(1, new DrinkData(4, 20, "beer"), new BrewerMark("Other", 7)), false),
                "took another brewer's beer");
        helper.assertFalse(keg.pourIn(drinkBottle(1, new DrinkData(4, 20, "cider"), kostya), false), "took cider");
        helper.assertTrue(keg.getItemHandler(Direction.UP)
                .insertItem(0, drinkBottle(1, new DrinkData(3, 20, "beer"), kostya), false).getCount() == 1,
                "automation slipped in fewer stars");
        helper.assertTrue(keg.pourIn(drinkBottle(1, new DrinkData(4, 20, "beer"), kostya), false), "refused the same beer");
        helper.assertTrue(keg.getPortions() == 2, "portions " + keg.getPortions());
        helper.succeed();
    }

    /** A distillate is not a drink yet: neither hand nor funnel puts one in the keg. */
    @GameTest(template = "empty")
    public static void kegRefusesDistillate(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        ItemStack distillate = new ItemStack(ModItems.DISTILLATE.get());
        DrinkData.set(distillate, new DrinkData(5, 40, "whiskey"));
        helper.assertFalse(keg.pourIn(distillate, false), "keg took a distillate");
        helper.assertTrue(keg.getItemHandler(Direction.NORTH).insertItem(0, distillate.copy(), false).getCount() == 1,
                "funnel put a distillate in");
        ServerPlayer player = freshPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, distillate.copy());
        useOnKeg(helper, player, KEG);
        helper.assertTrue(player.getMainHandItem().is(ModItems.DISTILLATE.get()), "distillate taken from the hand");
        helper.assertTrue(keg.getPortions() == 0, "portions " + keg.getPortions());
        helper.succeed();
    }

    /** A funnel's bottle leaves its glass inside, to be taken from below; a full glass store stops filling. */
    @GameTest(template = "empty")
    public static void kegFunnelInsertStoresEmpties(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        BlockPos abs = helper.absolutePos(KEG);
        IItemHandler side = capability(helper, abs, ForgeCapabilities.ITEM_HANDLER, Direction.NORTH);
        IItemHandler below = capability(helper, abs, ForgeCapabilities.ITEM_HANDLER, Direction.DOWN);
        helper.assertTrue(side != null && below != null, "keg has no item handler");
        DrinkData beer = new DrinkData(5, 20, "beer");

        helper.assertTrue(side.insertItem(0, drinkBottle(1, beer, null), false).isEmpty(), "funnel bottle refused");
        helper.assertTrue(keg.getPortions() == 1 && keg.getEmpties() == 1,
                "portions " + keg.getPortions() + " empties " + keg.getEmpties());
        helper.assertTrue(below.insertItem(0, drinkBottle(1, beer, null), false).getCount() == 1, "bottom took a drink");
        helper.assertTrue(side.extractItem(0, 1, false).isEmpty(), "side gave something out");
        ItemStack glass = below.extractItem(0, 1, false);
        helper.assertTrue(glass.is(Items.GLASS_BOTTLE) && glass.getCount() == 1, "below gave " + glass);
        helper.assertTrue(keg.getEmpties() == 0, "empties " + keg.getEmpties());

        ItemStack rest = side.insertItem(0, drinkBottle(10, beer, null), false);
        helper.assertTrue(rest.getCount() == 3 && keg.getPortions() == KegRules.CAPACITY,
                "left " + rest.getCount() + " portions " + keg.getPortions());
        keg.pourOut();
        helper.assertTrue(keg.getEmpties() == KegRules.EMPTIES - 1, "empties " + keg.getEmpties());
        helper.assertTrue(side.insertItem(0, drinkBottle(1, beer, null), false).isEmpty(), "room left refused");
        keg.pourOut();
        helper.assertTrue(side.insertItem(0, drinkBottle(1, beer, null), false).getCount() == 1,
                "took a bottle with the glass store full");
        helper.succeed();
    }

    /** The drink, its portions and the kept glass survive a save and load. */
    @GameTest(template = "empty")
    public static void kegSurvivesSaveLoad(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        BrewerMark kostya = new BrewerMark("Kostya", 7, Optional.of(UUID.randomUUID()));
        DrinkData whiskey = new DrinkData(4, 40, "aged_whiskey");
        keg.pourIn(drinkBottle(1, whiskey, kostya), false);
        keg.getItemHandler(Direction.EAST).insertItem(0, drinkBottle(2, whiskey, kostya), false);

        KegBlockEntity copy = placeKeg(helper, KEG.east(2));
        copy.load(keg.saveWithFullMetadata());
        helper.assertTrue(copy.getPortions() == 3 && copy.getEmpties() == 2,
                "portions " + copy.getPortions() + " empties " + copy.getEmpties());
        ItemStack drink = copy.getDrink();
        helper.assertTrue(drink.getCount() == 1 && data(drink).equals(whiskey), "drink " + data(drink));
        helper.assertTrue(kostya.equals(BrewerMark.get(drink)), "signature lost");
        helper.assertTrue(copy.pourIn(drinkBottle(1, whiskey, kostya), false), "loaded keg refused its own drink");
        helper.succeed();
    }

    /** An empty mug takes one portion and keeps the drink's quality and signature. */
    @GameTest(template = "empty")
    public static void mugPoursFromKeg(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        BrewerMark kostya = new BrewerMark("Kostya", 7);
        DrinkData whiskey = new DrinkData(4, 40, "aged_whiskey");
        keg.pourIn(drinkBottle(2, whiskey, kostya), false);
        helper.assertTrue(keg.getPortions() == 1, "portions " + keg.getPortions());
        keg.pourIn(drinkBottle(1, whiskey, kostya), false);
        ServerPlayer player = freshPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MUG.get(), 3));
        useOnKeg(helper, player, KEG);
        helper.assertTrue(keg.getPortions() == 1, "portions " + keg.getPortions());
        helper.assertTrue(player.getMainHandItem().is(ModItems.MUG.get()) && player.getMainHandItem().getCount() == 2,
                "mugs left " + player.getMainHandItem());
        ItemStack full = ItemStack.EMPTY;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.DRINK_MUG.get())) {
                full = stack;
            }
        }
        helper.assertFalse(full.isEmpty(), "no mug of drink");
        helper.assertTrue(whiskey.equals(DrinkData.get(full)), "data " + data(full));
        helper.assertTrue(kostya.equals(BrewerMark.get(full)), "signature lost");
        helper.succeed();
    }

    /** A bottle the keg refuses stays in hand and the click falls through, so it can still be drunk. */
    @GameTest(template = "empty")
    public static void refusedBottleIsNotConsumed(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        keg.pourIn(drinkBottle(1, new DrinkData(4, 20, "beer"), null), false);
        ServerPlayer player = freshPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, drinkBottle(1, new DrinkData(3, 20, "beer"), null));
        InteractionResult result = useOnKeg(helper, player, KEG);
        helper.assertTrue(result == InteractionResult.PASS,
                "result " + result);
        helper.assertTrue(keg.getPortions() == 1, "portions " + keg.getPortions());
        helper.assertTrue(player.getMainHandItem().is(ModItems.DRINK_BOTTLE.get()) && player.getMainHandItem().getCount() == 1,
                "hand " + player.getMainHandItem());
        helper.succeed();
    }

    /** A creative player pouring a mug that matches one already carried still gets the new mug. */
    @GameTest(template = "empty")
    public static void creativeMugPourGivesMug(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        DrinkData beer = new DrinkData(4, 20, "beer");
        for (int i = 0; i < 3; i++) {
            keg.pourIn(drinkBottle(1, beer, null), false);
        }
        ServerPlayer player = freshPlayer(helper);
        player.setGameMode(GameType.CREATIVE);
        ItemStack carried = new ItemStack(ModItems.DRINK_MUG.get());
        DrinkData.set(carried, beer);
        player.getInventory().setItem(5, carried);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MUG.get()));
        useOnKeg(helper, player, KEG);
        helper.assertTrue(keg.getPortions() == 2, "portions " + keg.getPortions());
        helper.assertTrue(countInInventory(player, ModItems.DRINK_MUG.get()) == 2,
                "mugs " + countInInventory(player, ModItems.DRINK_MUG.get()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyKegPoursNothing(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        ServerPlayer player = freshPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MUG.get(), 2));
        useOnKeg(helper, player, KEG);
        helper.assertTrue(player.getMainHandItem().getCount() == 2 && player.getMainHandItem().is(ModItems.MUG.get()),
                "mugs " + player.getMainHandItem());
        helper.assertTrue(countInInventory(player, ModItems.DRINK_MUG.get()) == 0, "got a mug of nothing");
        helper.assertTrue(keg.getPortions() == 0, "portions " + keg.getPortions());
        helper.succeed();
    }

    /** A broken keg spills its portions as bottles and the glass it kept. */
    @GameTest(template = "empty")
    public static void brokenKegDropsPortionsAndEmpties(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        DrinkData mead = new DrinkData(3, 20, "mead");
        keg.pourIn(drinkBottle(1, mead, null), false);
        keg.getItemHandler(Direction.UP).insertItem(0, drinkBottle(2, mead, null), false);
        helper.setBlock(KEG, Blocks.AIR);

        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(KEG)).inflate(2));
        int drinks = 0;
        int glass = 0;
        for (ItemEntity item : dropped) {
            if (item.getItem().is(ModItems.DRINK_BOTTLE.get()) && data(item.getItem()).equals(mead)) {
                drinks += item.getItem().getCount();
            } else if (item.getItem().is(Items.GLASS_BOTTLE)) {
                glass += item.getItem().getCount();
            }
        }
        helper.assertTrue(drinks == 3 && glass == 2, "dropped " + drinks + " drinks and " + glass + " glass");
        helper.succeed();
    }

    /**
     * Create lifts machines into a contraption by removing the block entity first and then setting air
     * with drops suppressed (Contraption.removeBlocksFromWorld); the contents travel inside the
     * contraption, so nothing may also fall out on the ground.
     *
     * <p>1.20.1 lays a batch's tests out closer together than 1.21.1, so the eight-block search around this
     * one reached a neighbour's drops (a broken barrel's logs); a batch of its own keeps it alone.
     */
    @GameTest(template = "empty", batch = "contraption")
    public static void contraptionAssemblyDropsNothing(GameTestHelper helper) {
        placeKettle(helper, Items.WHEAT);
        BarrelBlockEntity barrel = placeBarrels(helper, BARREL);
        ItemStack rum = new ItemStack(ModItems.DISTILLATE.get(), 4);
        DrinkData.set(rum, new DrinkData(5, 45, "rum"));
        barrel.getInput().insertItem(0, rum, false);
        BlockPos kegPos = new BlockPos(3, 2, 3);
        KegBlockEntity keg = placeKeg(helper, kegPos);
        keg.pourIn(drinkBottle(1, new DrinkData(3, 20, "mead"), null), false);

        int flags = net.minecraft.world.level.block.Block.UPDATE_MOVE_BY_PISTON
                | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS
                | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE
                | net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                | net.minecraft.world.level.block.Block.UPDATE_IMMEDIATE;
        for (BlockPos pos : List.of(KETTLE, BARREL, kegPos)) {
            BlockPos absolute = helper.absolutePos(pos);
            helper.getLevel().removeBlockEntity(absolute);
            helper.getLevel().setBlock(absolute, Blocks.AIR.defaultBlockState(), flags);
        }

        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8));
        helper.assertTrue(dropped.isEmpty(), "contraption assembly dropped " + dropped.size() + " stacks");
        helper.succeed();
    }

    /** A creative player's bottle stays in the hand, as with vanilla bottles. */
    @GameTest(template = "empty")
    public static void creativePourKeepsBottle(GameTestHelper helper) {
        KegBlockEntity keg = placeKeg(helper, KEG);
        ServerPlayer player = freshPlayer(helper);
        player.getAbilities().instabuild = true;
        player.setItemInHand(InteractionHand.MAIN_HAND, drinkBottle(1, new DrinkData(5, 25, "wine"), null));
        useOnKeg(helper, player, KEG);
        helper.assertTrue(keg.getPortions() == 1, "portions " + keg.getPortions());
        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.is(ModItems.DRINK_BOTTLE.get()) && held.getCount() == 1, "hand holds " + held);
        helper.succeed();
    }

    private static IFluidHandler fluids(GameTestHelper helper, BlockPos pos) {
        IFluidHandler handler = capability(helper, helper.absolutePos(pos), ForgeCapabilities.FLUID_HANDLER, null);
        helper.assertTrue(handler != null, "no fluid capability at " + pos);
        return handler;
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos) {
        IItemHandler handler = capability(helper, helper.absolutePos(pos), ForgeCapabilities.ITEM_HANDLER, null);
        helper.assertTrue(handler != null, "no item capability at " + pos);
        return handler;
    }

    /** 1.20.1's {@code GameTestHelper#getBlockEntity} is not generic, as 1.21.1's is. */
    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> T blockEntity(GameTestHelper helper, BlockPos pos) {
        return (T) helper.getBlockEntity(pos);
    }

    /** What a pipe or funnel at {@code pos} facing {@code side} gets: 1.20.1 asks the block entity. */
    @Nullable
    private static <T> T capability(GameTestHelper helper, BlockPos pos, Capability<T> cap, @Nullable Direction side) {
        BlockEntity entity = helper.getLevel().getBlockEntity(pos);
        return entity == null ? null : entity.getCapability(cap, side).resolve().orElse(null);
    }

    /**
     * A server-side player with its own advancement book, so no test depends on another. Forge's fake
     * players never earn advancements, so this is a plain {@link ServerPlayer} that is never added to the world.
     */
    private static ServerPlayer freshPlayer(GameTestHelper helper) {
        return freshPlayerWithId(helper, UUID.randomUUID());
    }

    private static ServerPlayer freshPlayerWithId(GameTestHelper helper, UUID id) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(id, "taster");
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile);
        // Granting an advancement and syncing the drunk level send packets, so the player needs a connection
        // that swallows them.
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(server, connection, player) {
            @Override
            public void send(Packet<?> packet) {
            }

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            }
        };
        return player;
    }

    private static boolean advancementDone(GameTestHelper helper, ServerPlayer player, String id) {
        Advancement holder = helper.getLevel().getServer().getAdvancements().getAdvancement(CreateBrewery.id(id));
        helper.assertTrue(holder != null, "advancement " + id + " is not loaded");
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Sets a config value for the length of {@code body}, then puts the default back whatever happens. */
    private static <T> void withConfig(ForgeConfigSpec.ConfigValue<T> value, T override, Runnable body) {
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

    private static DrinkData data(ItemStack stack) {
        return DrinkData.getOrDefault(stack, new DrinkData(DrinkData.MIN_STARS, 0, "none"));
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

    private static void drink(GameTestHelper helper, ServerPlayer player, DrinkData data) {
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkData.set(bottle, data);
        bottle.finishUsingItem(helper.getLevel(), player);
    }

    private static void drink(GameTestHelper helper, Player player, int stars, int alcohol, String stage) {
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkData.set(bottle, new DrinkData(stars, alcohol, stage));
        bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);
    }
}
