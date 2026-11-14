package com.kostya.createbrewery.gametest;

import com.google.gson.JsonObject;
import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.FluidUnits;
import com.kostya.createbrewery.config.BreweryConfig;
import com.kostya.createbrewery.recipe.BrewingRecipeSerializer;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import com.kostya.createbrewery.registry.ModEffects;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.mojang.authlib.GameProfile;
import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.Advancement;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-world checks of what unit tests cannot reach: real block entities, the Transfer API and
 * Create's kinetics. Run with {@code gradlew runGametest}; registered through the {@code fabric-gametest}
 * entrypoint. Every test uses the {@code brewery:empty} structure, so the game-test run keeps it (see
 * {@link GameTestNamespaces}).
 *
 * <p>The tests come back slice by slice from {@code port/todo/.../gametest/BreweryGameTests.java} with their
 * names and assertions unchanged.
 */
public class BreweryGameTests implements FabricGameTest {

    /**
     * Old-style data folders fail silently when misnamed, so every shipped brewing recipe must load, as must every
     * shipped advancement and every other recipe.
     */
    @GameTest(template = "brewery:empty")
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
        return new ResourceLocation(file.getNamespace(), path.substring(folder.length(), path.length() - ".json".length()));
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
    @GameTest(template = "brewery:empty")
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

        // Fabric fluids carry NBT in their immutable variant, so the fluid accessors hand back a new stack.
        FluidStack wort = new FluidStack(ModFluids.WORT.get(), FluidUnits.toDroplets(1000));
        FluidStack plain = new FluidStack(ModFluids.WORT.get(), FluidUnits.toDroplets(1000));
        wort = DrinkData.with(wort, vodka);
        wort = BrewerMark.with(wort, mark);
        helper.assertTrue(DrinkData.get(plain) == null, "plain wort holds a drink");
        helper.assertFalse(wort.isFluidEqual(plain), "different wort mixes");
        helper.assertTrue(wort.isFluidEqual(wort.copy()), "a copy of wort does not mix with it");
        FluidStack loadedWort = FluidStack.loadFluidStackFromNBT(wort.writeToNBT(new CompoundTag()));
        helper.assertTrue(vodka.equals(DrinkData.get(loadedWort)) && mark.equals(BrewerMark.get(loadedWort)),
                "saved wort " + DrinkData.get(loadedWort) + " " + BrewerMark.get(loadedWort));
        wort = DrinkData.with(wort, null);
        wort = BrewerMark.with(wort, null);
        helper.assertTrue(wort.isFluidEqual(plain), "wort with its data removed still differs");
        helper.succeed();
    }

    /** Vodka taken out of the still by hand can still reach a bottle. */
    @GameTest(template = "brewery:empty")
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
    @GameTest(template = "brewery:empty")
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
    @GameTest(template = "brewery:empty")
    public static void muddyGivesNoEffects(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkData.set(bottle, new DrinkData(5, 2, "muddy"));
        bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);
        helper.assertTrue(player.getActiveEffects().isEmpty(), "muddy gave " + player.getActiveEffects());
        helper.succeed();
    }

    /** Drinking the muddy brew from a mixed-up kettle earns "Something's Off". */
    @GameTest(template = "brewery:empty")
    public static void drinkingMuddyAwardsAdvancement(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        helper.assertFalse(advancementDone(helper, player, "muddy"), "muddy done before drinking");
        drink(helper, player, new DrinkData(3, 2, "muddy"));
        helper.assertTrue(advancementDone(helper, player, "muddy"), "muddy brew gave no advancement");
        helper.assertFalse(advancementDone(helper, player, "master_brewer"), "muddy brew made a master");
        helper.succeed();
    }

    /** A five-star drink makes a master brewer, a four-star one does not. */
    @GameTest(template = "brewery:empty")
    public static void fiveStarDrinkAwardsMasterBrewer(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        drink(helper, player, new DrinkData(4, 20, "beer"));
        helper.assertFalse(advancementDone(helper, player, "master_brewer"), "four stars made a master");
        drink(helper, player, new DrinkData(5, 20, "beer"));
        helper.assertTrue(advancementDone(helper, player, "master_brewer"), "five stars gave no advancement");
        helper.succeed();
    }

    /** An aged drink earns "Patience"; the young drink of the same recipe does not. */
    @GameTest(template = "brewery:empty")
    public static void agedDrinkAwardsPatience(GameTestHelper helper) {
        ServerPlayer player = freshPlayer(helper);
        drink(helper, player, new DrinkData(3, 40, "whiskey"));
        helper.assertFalse(advancementDone(helper, player, "aged"), "plain whiskey counted as aged");
        drink(helper, player, new DrinkData(3, 40, "aged_whiskey"));
        helper.assertTrue(advancementDone(helper, player, "aged"), "aged whiskey gave no advancement");
        helper.succeed();
    }

    /** "Taster" needs all thirteen drinks: twelve do not finish it, the thirteenth does. */
    @GameTest(template = "brewery:empty")
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

    @GameTest(template = "brewery:empty")
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
    @GameTest(template = "brewery:empty")
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
    @GameTest(template = "brewery:empty")
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
    @GameTest(template = "brewery:empty")
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
    @GameTest(template = "brewery:empty")
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
    @GameTest(template = "brewery:empty")
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
    @GameTest(template = "brewery:empty", batch = "toast_bonus_off")
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

    /**
     * A server-side player with its own advancement book, so no test depends on another. Fake
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
