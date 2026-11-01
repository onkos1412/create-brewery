package com.kostya.createbrewery.client;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.block.KettleBlock;
import com.kostya.createbrewery.block.ModBlocks;
import com.kostya.createbrewery.registry.ModItems;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Create-style animated guides. The scene text lives in the language files under
 * {@code brewery.ponder.<scene>}; the English passed here is only Ponder's placeholder.
 */
public final class BreweryPonder implements PonderPlugin {
    private static final int TEXT_TICKS = 80;

    @Override
    public String getModId() {
        return CreateBrewery.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(ModBlocks.KETTLE.getId(), "kettle", BreweryPonder::kettle);
        helper.addStoryBoard(ModBlocks.DISTILLERY.getId(), "distillery", BreweryPonder::distillery);
        helper.forComponents(ModBlocks.BARREL.getId(), ModBlocks.SPOUT.getId())
                .addStoryBoard("barrel", BreweryPonder::barrel);
    }

    private static void start(SceneBuilder scene, String id, String title) {
        scene.title(id, title);
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
    }

    private static void say(SceneBuilder scene, SceneBuildingUtil util, BlockPos target, Direction side, String text) {
        scene.overlay().showText(TEXT_TICKS)
                .text(text)
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(target, side));
        scene.idle(TEXT_TICKS + 10);
    }

    private static void show(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, Direction from) {
        scene.world().showSection(util.select().position(pos), from);
        scene.idle(10);
    }

    private static void kettle(SceneBuilder scene, SceneBuildingUtil util) {
        BlockPos fire = new BlockPos(2, 1, 2);
        BlockPos kettle = fire.above();
        BlockPos spout = kettle.east();
        start(scene, "kettle", "Brewing wort");

        show(scene, util, fire, Direction.DOWN);
        show(scene, util, kettle, Direction.DOWN);
        say(scene, util, fire, Direction.WEST, "The kettle needs heat beneath it. The exact heat of the recipe keeps full quality");

        scene.overlay().showControls(util.vector().topOf(kettle), Pointing.DOWN, 40)
                .rightClick().withItem(new ItemStack(Items.WATER_BUCKET));
        scene.idle(50);
        say(scene, util, kettle, Direction.NORTH, "Fill it with water, by bucket or by pipe");

        scene.overlay().showControls(util.vector().topOf(kettle), Pointing.DOWN, 40)
                .withItem(new ItemStack(Items.WHEAT));
        scene.idle(50);
        say(scene, util, kettle, Direction.WEST, "Add ingredients in its menu or by funnel. The kettle takes one batch at a time");

        scene.world().cycleBlockProperty(kettle, KettleBlock.BREWING);
        say(scene, util, kettle, Direction.UP, "After a while the water has become wort, ready for the next machine");

        show(scene, util, spout, Direction.WEST);
        scene.overlay().showControls(util.vector().centerOf(spout), Pointing.DOWN, 40)
                .rightClick().withItem(new ItemStack(Items.GLASS_BOTTLE));
        scene.idle(50);
        say(scene, util, spout, Direction.NORTH, "A spout on the kettle bottles it straight away: beer, cider or young wine");
        scene.markAsFinished();
    }

    private static void distillery(SceneBuilder scene, SceneBuildingUtil util) {
        CreateSceneBuilder create = new CreateSceneBuilder(scene);
        BlockPos still = new BlockPos(2, 2, 2);
        BlockPos motor = still.north();
        BlockPos pump = still.west();
        BlockPos kettle = pump.west();
        start(scene, "distillery", "Distilling");

        show(scene, util, still.below(), Direction.DOWN);
        show(scene, util, still, Direction.DOWN);
        show(scene, util, still.above(), Direction.DOWN);
        say(scene, util, still.below(), Direction.WEST, "The distillery turns wort into distillate. It also needs heat beneath it");

        show(scene, util, kettle.below(), Direction.DOWN);
        show(scene, util, kettle, Direction.DOWN);
        show(scene, util, pump, Direction.DOWN);
        show(scene, util, pump.above(), Direction.DOWN);
        show(scene, util, kettle.above(), Direction.DOWN);
        create.world().setKineticSpeed(util.select().fromTo(kettle.above(), pump), 16);
        say(scene, util, pump, Direction.NORTH, "Pipe in wort from a kettle. A kettle right next to it also works, with the Load button");

        show(scene, util, motor, Direction.SOUTH);
        create.world().setKineticSpeed(util.select().position(motor), 32);
        create.world().setKineticSpeed(util.select().position(still), 32);
        say(scene, util, motor, Direction.NORTH, "Drive the shaft in its back. Each drink has its own ideal speed, shown in the menu");

        scene.overlay().showText(TEXT_TICKS)
                .text("Close to the ideal speed keeps the stars. Half of it costs most of them, and double spoils the batch")
                .colored(net.createmod.ponder.api.PonderPalette.RED)
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(motor, Direction.WEST));
        scene.idle(TEXT_TICKS + 10);

        scene.overlay().showControls(util.vector().topOf(still), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.DISTILLATE.get()));
        scene.idle(50);
        say(scene, util, still, Direction.SOUTH, "The distillate goes to a barrel. Vodka needs none: pour it with a spout or from your hand");
        scene.markAsFinished();
    }

    private static void barrel(SceneBuilder scene, SceneBuildingUtil util) {
        BlockPos barrel = new BlockPos(1, 1, 1);
        BlockPos funnel = barrel.above(2);
        BlockPos spout = barrel.east(2);
        start(scene, "barrel", "Aging and bottling");

        scene.world().showSection(util.select().fromTo(barrel, barrel.offset(1, 1, 1)), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showControls(util.vector().topOf(barrel.above()), Pointing.DOWN, 40)
                .withItem(new ItemStack(ModItems.DISTILLATE.get()));
        scene.idle(50);
        say(scene, util, barrel, Direction.WEST, "The big barrel ages up to four stacks of one drink at once: distillate, or bottles of young wine or mead");

        scene.overlay().showControls(util.vector().topOf(barrel.above()), Pointing.DOWN, 40)
                .withItem(new ItemStack(Items.DARK_OAK_LOG));
        scene.idle(50);
        say(scene, util, barrel, Direction.NORTH, "Wood goes in too and is not used up. Each drink ages best in its own wood");

        say(scene, util, barrel, Direction.UP, "Press Start and wait the drink's days. Sleeping counts, so a bed speeds it up");

        show(scene, util, funnel, Direction.DOWN);
        say(scene, util, funnel, Direction.WEST, "Fed by a funnel, it starts on its own and keeps going");

        show(scene, util, spout, Direction.WEST);
        scene.overlay().showControls(util.vector().centerOf(spout), Pointing.DOWN, 40)
                .rightClick().withItem(new ItemStack(Items.GLASS_BOTTLE));
        scene.idle(50);
        say(scene, util, spout, Direction.NORTH, "A spout bottles the drink, from your hand or from empty bottles fed in by funnel");

        scene.overlay().showText(TEXT_TICKS)
                .text("Collect it on time: a finished drink left in the barrel slowly loses its stars")
                .colored(net.createmod.ponder.api.PonderPalette.RED)
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(barrel, Direction.SOUTH));
        scene.idle(TEXT_TICKS + 10);
        scene.markAsFinished();
    }
}
