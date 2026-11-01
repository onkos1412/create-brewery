package com.kostya.createbrewery.gametest;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.barrel.AgingRules;
import com.kostya.createbrewery.block.BarrelBlockEntity;
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
    @GameTest(template = "empty", timeoutTicks = 1200)
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
            helper.assertTrue(data(output).stars() == 4, "stars at 16 RPM: " + data(output).stars());
        });
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
        helper.setBlock(KETTLE.below(), Blocks.CAMPFIRE);
        helper.setBlock(KETTLE, ModBlocks.KETTLE.get());
        items(helper, KETTLE).insertItem(0, new ItemStack(Items.WHEAT, 2), false);
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
