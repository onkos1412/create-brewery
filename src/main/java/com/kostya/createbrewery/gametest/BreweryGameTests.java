package com.kostya.createbrewery.gametest;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.barrel.AgingRules;
import com.kostya.createbrewery.block.BarrelBlock;
import com.kostya.createbrewery.block.BarrelBlockEntity;
import com.kostya.createbrewery.block.BarrelMenu;
import com.kostya.createbrewery.block.DistilleryBlockEntity;
import com.kostya.createbrewery.block.KettleBlockEntity;
import com.kostya.createbrewery.block.KettleLogic;
import com.kostya.createbrewery.block.ModBlocks;
import com.kostya.createbrewery.drunk.DrunkLevel;
import com.kostya.createbrewery.drunk.DrunkManager;
import com.kostya.createbrewery.drunk.ModAttachments;
import com.kostya.createbrewery.drunk.VomitRules;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.BrewingRecipeSerializer;
import com.kostya.createbrewery.recipe.MixRules;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
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
import java.util.UUID;

/**
 * In-world checks of what unit tests cannot reach: real block entities, capabilities and
 * Create's kinetics. Run with {@code gradlew runGameTestServer}.
 */
@GameTestHolder(CreateBrewery.MOD_ID)
@PrefixGameTestTemplate(false)
public class BreweryGameTests {
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
        DistilleryBlockEntity distillery = helper.getBlockEntity(DISTILLERY);

        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);
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
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
        DistilleryBlockEntity distillery = helper.getBlockEntity(DISTILLERY);
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

                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
        BlockEntityBehaviour.get(helper.getBlockEntity(motor), ScrollValueBehaviour.TYPE).setValue(rpm);
    }

    private static DistilleryBlockEntity distilleryAt(GameTestHelper helper, int rpm) {
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        driveStill(helper, DISTILLERY, rpm);
        fluids(helper, DISTILLERY).fill(new FluidStack(ModFluids.WORT.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        return helper.getBlockEntity(DISTILLERY);
    }

    /** Wine goes kettle, bottle, barrel: the still refuses it, and the bottle comes back empty. */
    @GameTest(template = "empty", timeoutTicks = 800, batch = "aging_wine")
    public static void berriesAgeIntoWineInTheBottle(GameTestHelper helper) {
        KettleBlockEntity kettle = placeKettle(helper, Items.SWEET_BERRIES);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);

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
            HopperBlockEntity hopper = helper.getBlockEntity(spout.below());
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
            BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);
            ItemStack rum = new ItemStack(ModItems.DRINK_BOTTLE.get());
            rum.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 45, "aged_rum"));
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
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);
        ItemStack whiskey = new ItemStack(ModItems.DISTILLATE.get());
        whiskey.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 40, "whiskey"));
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
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);
        ItemStack whiskey = new ItemStack(ModItems.DISTILLATE.get());
        whiskey.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 40, "whiskey"));
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
        DistilleryBlockEntity still = helper.getBlockEntity(DISTILLERY);
        FluidStack cider = new FluidStack(ModFluids.WORT.get(), 600);
        cider.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(4, 0, "cider"));
        IFluidHandler tank = fluids(helper, DISTILLERY);
        tank.fill(cider, IFluidHandler.FluidAction.EXECUTE);

        FluidStack beer = new FluidStack(ModFluids.WORT.get(), 1000);
        beer.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 0, "beer"));
        helper.assertTrue(tank.fill(beer, IFluidHandler.FluidAction.SIMULATE) == 0, "worts mixed");

        FluidStack drained = tank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(drained.getAmount() == 600, "drained " + drained.getAmount());
        helper.assertTrue("cider".equals(drained.get(ModDataComponents.DRINK_DATA.get()).stage()), "lost its drink");
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

        DistilleryBlockEntity still = helper.getBlockEntity(DISTILLERY);
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
        topState.getBlock().playerWillDestroy(helper.getLevel(), topAbs, topState, helper.makeMockPlayer(GameType.SURVIVAL));

        helper.assertBlockNotPresent(ModBlocks.DISTILLERY.get(), DISTILLERY);
        helper.assertBlockNotPresent(ModBlocks.DISTILLERY_TOP.get(), DISTILLERY.above());
        helper.succeed();
    }

    private static BarrelBlockEntity placeBarrels(GameTestHelper helper, BlockPos... positions) {
        for (BlockPos pos : positions) {
            helper.setBlock(pos, ModBlocks.BARREL.get());
        }
        return helper.getBlockEntity(positions[0]);
    }

    /** A lone barrel holds one stack; each barrel beside it opens another, and then deepens them. */
    @GameTest(template = "empty")
    public static void barrelsBesideEachOtherGrowTogether(GameTestHelper helper) {
        BarrelBlockEntity first = placeBarrels(helper, BARREL);
        ItemStack rum = new ItemStack(ModItems.DISTILLATE.get(), 16);
        rum.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 45, "rum"));
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
        BarrelBlockEntity other = helper.getBlockEntity(BARREL.east());
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
        BarrelBlockEntity survivor = helper.getBlockEntity(otherPos);
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
        rum5.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 45, "rum"));
        ItemStack rum3 = new ItemStack(ModItems.DISTILLATE.get(), 7);
        rum3.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(3, 45, "rum"));
        ItemStack whiskey = new ItemStack(ModItems.DISTILLATE.get());
        whiskey.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 40, "whiskey"));

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
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);
        var registries = helper.getLevel().registryAccess();
        var tag = barrel.saveWithFullMetadata(registries);
        var oldInput = new net.neoforged.neoforge.items.ItemStackHandler(2);
        oldInput.setStackInSlot(1, new ItemStack(Items.OAK_LOG, 2));
        tag.put("Input", oldInput.serializeNBT(registries));
        tag.put("Output", new net.neoforged.neoforge.items.ItemStackHandler(1).serializeNBT(registries));

        barrel.loadWithComponents(tag, registries);
        helper.assertTrue(barrel.getInput().getSlots() == 5 && barrel.getOutput().getSlots() == 4, "barrel stayed small");
        helper.assertTrue(barrel.getInput().getStackInSlot(1).getCount() == 2, "old wood lost");
        helper.succeed();
    }

    /** The surface shows what is inside: nothing, water, then the drink's own wort. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void kettleLiquidTakesTheDrinksColor(GameTestHelper helper) {
        helper.setBlock(KETTLE.below(), Blocks.CAMPFIRE);
        helper.setBlock(KETTLE, ModBlocks.KETTLE.get());
        KettleBlockEntity kettle = helper.getBlockEntity(KETTLE);
        helper.assertTrue(kettle.getLiquidColor() == KettleBlockEntity.EMPTY_COLOR, "empty kettle colour");
        fluids(helper, KETTLE).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        int water = kettle.getLiquidColor();
        helper.assertTrue(water != KettleBlockEntity.EMPTY_COLOR, "water shows no colour");
        items(helper, KETTLE).insertItem(0, new ItemStack(Items.SWEET_BERRIES, 2), false);

        helper.succeedWhen(() -> {
            helper.assertTrue(kettle.getWortAmount() == 1000, "no wort yet");
            helper.assertTrue(kettle.getLiquidColor() == 0x8E1F3A, "wine wort colour " + kettle.getLiquidColor());
            // A client joining later must be told the drink as well as the amount.
            var tag = kettle.getUpdateTag(helper.getLevel().registryAccess());
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
        tank.fill(KettleBlockEntity.wortStack(500, 3, "young_wine"), IFluidHandler.FluidAction.EXECUTE);
        items(helper, depot).insertItem(0, new ItemStack(Items.GLASS_BOTTLE), false);
        ItemStack loose = new ItemStack(Items.GLASS_BOTTLE);
        var bottle = loose.getCapability(Capabilities.FluidHandler.ITEM);
        helper.assertTrue(bottle.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE) == 0,
                "bottle handler took water");

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(items(helper, depot).getStackInSlot(0).is(Items.GLASS_BOTTLE), "filled from half a bottle's wort");
            tank.fill(KettleBlockEntity.wortStack(500, 3, "young_wine"), IFluidHandler.FluidAction.EXECUTE);
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
        var registries = helper.getLevel().registryAccess();
        KettleBlockEntity kettle = placeKettle(helper, Items.APPLE);
        kettle.getInput().setStackInSlot(1, new ItemStack(Items.APPLE, 5));
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);
        barrel.getBottles().setStackInSlot(0, new ItemStack(Items.GLASS_BOTTLE, 7));
        barrel.getInput().setStackInSlot(1, new ItemStack(Items.BIRCH_LOG, 3));
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        DistilleryBlockEntity still = helper.getBlockEntity(DISTILLERY);
        FluidStack wort = new FluidStack(ModFluids.WORT.get(), 600);
        wort.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(3, 0, "cider"));
        still.getFluidHandler().fill(wort, IFluidHandler.FluidAction.EXECUTE);

        // Saved part-way through a brew, which must carry on rather than start over.
        helper.runAfterDelay(20, () -> {
            KettleBlockEntity kettle2 = new KettleBlockEntity(kettle.getBlockPos(), kettle.getBlockState());
            kettle2.loadWithComponents(kettle.saveWithFullMetadata(registries), registries);
            helper.assertTrue(kettle2.getWaterAmount() == 1000, "water " + kettle2.getWaterAmount());
            helper.assertTrue(kettle2.getInput().getStackInSlot(1).getCount() == 5, "kettle items lost");
            int progress = kettle.getBrewProgressTicks();
            helper.assertTrue(progress > 0, "kettle never started brewing");
            kettle2.setLevel(helper.getLevel());
            kettle2.serverTick();
            helper.assertTrue(kettle2.getBrewProgressTicks() == progress + 1,
                    "brew progress after loading: " + kettle2.getBrewProgressTicks() + ", before: " + progress);

            BarrelBlockEntity barrel2 = new BarrelBlockEntity(barrel.getBlockPos(), barrel.getBlockState());
            barrel2.loadWithComponents(barrel.saveWithFullMetadata(registries), registries);
            helper.assertTrue(barrel2.getBottles().getStackInSlot(0).getCount() == 7, "bottles lost");
            helper.assertTrue(barrel2.getInput().getStackInSlot(1).is(Items.BIRCH_LOG), "wood lost");

            DistilleryBlockEntity still2 = new DistilleryBlockEntity(still.getBlockPos(), still.getBlockState());
            still2.loadWithComponents(still.saveWithFullMetadata(registries), registries);
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
        BarrelBlockEntity barrel = helper.getBlockEntity(barrelPos);
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
        DistilleryBlockEntity still = helper.getBlockEntity(DISTILLERY);
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
        DistilleryBlockEntity still = helper.getBlockEntity(DISTILLERY);
        fluids(helper, DISTILLERY).fill(new FluidStack(ModFluids.WORT.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        BlockPos shaft = DISTILLERY.south();
        helper.setBlock(shaft, AllBlocks.SHAFT.get().defaultBlockState()
                .setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z));
        KineticBlockEntity kinetic = helper.getBlockEntity(shaft);
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
        BarrelBlockEntity barrel = helper.getBlockEntity(barrelPos);
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
            bottle.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 20, stage));
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
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
        DistilleryBlockEntity distillery = helper.getBlockEntity(DISTILLERY);
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

                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
        KettleBlockEntity dry = helper.getBlockEntity(KETTLE);
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
        KettleBlockEntity kettle = helper.getBlockEntity(KETTLE);
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
        DistilleryBlockEntity distillery = helper.getBlockEntity(DISTILLERY);
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);

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
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
        DistilleryBlockEntity distillery = helper.getBlockEntity(DISTILLERY);
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

                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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
        KettleBlockEntity kettle = helper.getBlockEntity(KETTLE);
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);
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
        DistilleryBlockEntity distillery = helper.getBlockEntity(still);
        helper.setBlock(pipe, AllBlocks.FLUID_PIPE.get().updateBlockState(AllBlocks.FLUID_PIPE.getDefaultState(),
                Direction.EAST, null, helper.getLevel(), helper.absolutePos(pipe)));

        helper.succeedWhen(() -> helper.assertTrue(distillery.getWortAmount() == 1000,
                "wort at the far end: " + distillery.getWortAmount()));
    }

    /** Every way of taking a drink from the barrel costs an empty bottle. */
    @GameTest(template = "empty")
    public static void takingADrinkCostsABottle(GameTestHelper helper) {
        helper.setBlock(BARREL, ModBlocks.BARREL.get());
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);
        IItemHandler funnelView = items(helper, BARREL);
        barrel.getOutput().setStackInSlot(0, new ItemStack(ModItems.DRINK_BOTTLE.get()));

        helper.assertTrue(funnelView.extractItem(2, 1, false).isEmpty(), "funnel took a drink for free");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
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

    /** Vodka taken out of the still by hand can still reach a bottle. */
    @GameTest(template = "empty")
    public static void vodkaPoursFromTheHand(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack vodka = new ItemStack(ModItems.DISTILLATE.get());
        vodka.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(4, 40, "vodka"));
        player.setItemInHand(InteractionHand.MAIN_HAND, vodka);

        vodka.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertFalse(player.getMainHandItem().isEmpty(), "poured with no bottle to pour into");

        player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        vodka.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        DrinkData drink = data(findBottle(helper, player));
        helper.assertTrue("vodka".equals(drink.stage()) && drink.stars() == 4, "poured " + drink);
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 0, "bottle not spent");

        ItemStack whiskey = new ItemStack(ModItems.DISTILLATE.get());
        whiskey.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 40, "whiskey"));
        player.setItemInHand(InteractionHand.MAIN_HAND, whiskey);
        player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        whiskey.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "whiskey skipped the barrel");
        helper.succeed();
    }

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

    /** A drink gives its own effect, shorter for a poorer star count and none for a bad one. */
    @GameTest(template = "empty")
    public static void drinkingTequilaGivesSpeed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        bottle.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 40, "tequila"));
        bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);
        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(speed != null && Math.abs(speed.getDuration() - 1200) <= 20,
                "5 stars speed " + (speed == null ? "none" : speed.getDuration()));

        Player poor = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack weak = new ItemStack(ModItems.DRINK_BOTTLE.get());
        weak.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(2, 40, "tequila"));
        weak.getItem().finishUsingItem(weak, helper.getLevel(), poor);
        helper.assertTrue(!poor.hasEffect(MobEffects.MOVEMENT_SPEED), "2 stars gave speed");
        helper.succeed();
    }

    /** Muddy drinks never give the effect of any stage. */
    @GameTest(template = "empty")
    public static void muddyGivesNoEffects(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        bottle.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(5, 2, "muddy"));
        bottle.getItem().finishUsingItem(bottle, helper.getLevel(), player);
        helper.assertTrue(player.getActiveEffects().isEmpty(), "muddy gave " + player.getActiveEffects());
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
                    helper.assertTrue("muddy".equals(muddy.get(ModDataComponents.DRINK_DATA.get()).stage()),
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

    /**
     * A server-side player with its own advancement book, so no test depends on another. NeoForge's fake
     * players never earn advancements, so this is a plain {@link ServerPlayer} that is never added to the world.
     */
    private static ServerPlayer freshPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "taster");
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
        helper.assertTrue(BrewingRecipeSerializer.CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, good).result().isPresent(),
                "a known effect was refused");
        helper.assertTrue(BrewingRecipeSerializer.CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, bad).isError(),
                "an unknown effect was accepted");
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
        DistilleryBlockEntity distillery = helper.getBlockEntity(DISTILLERY);

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
        BarrelBlockEntity barrel = helper.getBlockEntity(BARREL);
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

private static KettleBlockEntity placeHeatedKettle(GameTestHelper helper) {
        return placeKettle(helper, Items.WHEAT);
    }

    private static KettleBlockEntity placeKettle(GameTestHelper helper, Item ingredient) {
        helper.setBlock(KETTLE.below(), Blocks.CAMPFIRE);
        helper.setBlock(KETTLE, ModBlocks.KETTLE.get());
        items(helper, KETTLE).insertItem(0, new ItemStack(ingredient, 2), false);
        fluids(helper, KETTLE).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        return helper.getBlockEntity(KETTLE);
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
        return helper.getBlockEntity(KETTLE);
    }

    private static IFluidHandler fluids(GameTestHelper helper, BlockPos pos) {
        IFluidHandler handler = helper.getLevel()
                .getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(pos), null);
        helper.assertTrue(handler != null, "no fluid capability at " + pos);
        return handler;
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos) {
        IItemHandler handler = helper.getLevel()
                .getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), null);
        helper.assertTrue(handler != null, "no item capability at " + pos);
        return handler;
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
}
