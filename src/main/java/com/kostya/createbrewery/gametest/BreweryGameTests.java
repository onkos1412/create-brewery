package com.kostya.createbrewery.gametest;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.barrel.AgingRules;
import com.kostya.createbrewery.block.BarrelBlockEntity;
import com.kostya.createbrewery.block.BarrelMenu;
import com.kostya.createbrewery.block.DistilleryBlockEntity;
import com.kostya.createbrewery.block.KettleBlockEntity;
import com.kostya.createbrewery.block.ModBlocks;
import com.kostya.createbrewery.drunk.DrunkLevel;
import com.kostya.createbrewery.drunk.DrunkManager;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

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
        helper.setBlock(DISTILLERY.above(), AllBlocks.CREATIVE_MOTOR.get());
        BlockEntityBehaviour.get(helper.getBlockEntity(DISTILLERY.above()), ScrollValueBehaviour.TYPE)
                .setValue(32);
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
        helper.setBlock(DISTILLERY.above(), AllBlocks.CREATIVE_MOTOR.get());
        BlockEntityBehaviour.get(helper.getBlockEntity(DISTILLERY.above()), ScrollValueBehaviour.TYPE)
                .setValue(16);
        DistilleryBlockEntity distillery = helper.getBlockEntity(DISTILLERY);
        fluids(helper, DISTILLERY).fill(new FluidStack(ModFluids.WORT.get(), 1000), IFluidHandler.FluidAction.EXECUTE);

        helper.succeedWhen(() -> {
            ItemStack output = distillery.getOutput().getStackInSlot(0);
            helper.assertFalse(output.isEmpty(), "no distillate at 16 RPM; rpm=" + distillery.getRpm());
            helper.assertTrue(data(output).stars() == 2, "stars at 16 RPM: " + data(output).stars());
        });
    }

    /** A setting that is completely off makes the worst drink there is, but still makes one. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void wildRpmMakesOneStar(GameTestHelper helper) {
        helper.setBlock(DISTILLERY.below(), Blocks.CAMPFIRE);
        helper.setBlock(DISTILLERY, ModBlocks.DISTILLERY.get());
        helper.setBlock(DISTILLERY.above(), AllBlocks.CREATIVE_MOTOR.get());
        BlockEntityBehaviour.get(helper.getBlockEntity(DISTILLERY.above()), ScrollValueBehaviour.TYPE)
                .setValue(256);
        DistilleryBlockEntity distillery = helper.getBlockEntity(DISTILLERY);
        fluids(helper, DISTILLERY).fill(new FluidStack(ModFluids.WORT.get(), 1000), IFluidHandler.FluidAction.EXECUTE);

        helper.succeedWhen(() -> {
            ItemStack output = distillery.getOutput().getStackInSlot(0);
            helper.assertFalse(output.isEmpty(), "no distillate at 256 RPM; rpm=" + distillery.getRpm());
            helper.assertTrue(data(output).stars() == 1, "stars at 256 RPM: " + data(output).stars());
        });
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
        helper.setBlock(DISTILLERY.above(), AllBlocks.CREATIVE_MOTOR.get());
        BlockEntityBehaviour.get(helper.getBlockEntity(DISTILLERY.above()), ScrollValueBehaviour.TYPE)
                .setValue(32);
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
                })
                .thenExecute(() -> kettle.drainWort(1000))
                .thenWaitUntil(() -> helper.assertTrue(kettle.getWortAmount() == 1000, "apples never brewed"))
                .thenExecute(() -> helper.assertTrue("cider".equals(kettle.getWortDrink()),
                        "tank holds " + kettle.getWortDrink()))
                .thenSucceed();
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
        helper.setBlock(DISTILLERY.above(), AllBlocks.CREATIVE_MOTOR.get());
        BlockEntityBehaviour.get(helper.getBlockEntity(DISTILLERY.above()), ScrollValueBehaviour.TYPE).setValue(rpm);
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

    /** A funnel fills the slot well past one batch; only water should limit the brewing. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void surplusWheatBrewsOneBatchAtATime(GameTestHelper helper) {
        KettleBlockEntity kettle = placeHeatedKettle(helper);
        items(helper, KETTLE).insertItem(0, new ItemStack(Items.WHEAT, 14), false);

        helper.succeedWhen(() -> {
            helper.assertTrue(kettle.getWortAmount() == 1000, "wort: " + kettle.getWortAmount());
            int wheat = kettle.getInput().getStackInSlot(0).getCount();
            helper.assertTrue(wheat == 14, "wheat left: " + wheat);
        });
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
