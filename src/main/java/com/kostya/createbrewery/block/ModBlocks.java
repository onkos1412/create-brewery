package com.kostya.createbrewery.block;

import com.kostya.createbrewery.CreateBrewery;
import io.github.fabricators_of_create.porting_lib.util.LazyRegistrar;
import io.github.fabricators_of_create.porting_lib.util.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Registry of the mod's blocks and block entities.
 *
 * <p>PORT(Task 3): the ids, properties and holder names are final, but the blocks are still plain {@link Block}s
 * and the block entity types are missing; the machines slice swaps in {@code KettleBlock}, {@code SpoutBlock} …
 * and their block entities from the parked Forge copy ({@code port/todo/.../block/ModBlocks.java}). Registering
 * the ids now lets the shipped recipes, advancements, loot tables and the creative tab load.
 */
public final class ModBlocks {

    public static final LazyRegistrar<Block> BLOCKS = LazyRegistrar.create(Registries.BLOCK, CreateBrewery.MOD_ID);
    public static final LazyRegistrar<Item> BLOCK_ITEMS = LazyRegistrar.create(Registries.ITEM, CreateBrewery.MOD_ID);

    /**
     * The kettle: holds water and ingredients above a heat source and brews wort.
     */
    public static final RegistryObject<Block> KETTLE = BLOCKS.register("kettle",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<BlockItem> KETTLE_ITEM = BLOCK_ITEMS.register("kettle",
            () -> new BlockItem(KETTLE.get(), new Item.Properties()));

    /**
     * The bottling spout: pours a kettle's wort into an empty bottle.
     */
    public static final RegistryObject<Block> SPOUT = BLOCKS.register("spout",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 4.0f)
                    .noOcclusion()));

    public static final RegistryObject<BlockItem> SPOUT_ITEM = BLOCK_ITEMS.register("spout",
            () -> new BlockItem(SPOUT.get(), new Item.Properties()));

    public static final RegistryObject<Block> DISTILLERY = BLOCKS.register("distillery",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                    .noOcclusion()));

    /** The upper half of the distillery. It has no item: placing the distillery places it. */
    public static final RegistryObject<Block> DISTILLERY_TOP = BLOCKS.register("distillery_top",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                    .noOcclusion().pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<BlockItem> DISTILLERY_ITEM = BLOCK_ITEMS.register("distillery",
            () -> new BlockItem(DISTILLERY.get(), new Item.Properties()));

    public static final RegistryObject<Block> BARREL = BLOCKS.register("barrel",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .noOcclusion()));

    public static final RegistryObject<BlockItem> BARREL_ITEM = BLOCK_ITEMS.register("barrel",
            () -> new BlockItem(BARREL.get(), new Item.Properties()));

    /** A small keg on the bar: eight portions of one drink, poured into mugs. */
    public static final RegistryObject<Block> KEG = BLOCKS.register("keg",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .noOcclusion()));

    public static final RegistryObject<BlockItem> KEG_ITEM = BLOCK_ITEMS.register("keg",
            () -> new BlockItem(KEG.get(), new Item.Properties()));

    private ModBlocks() {
    }

    /** Blocks first: the block items look their blocks up when they are built. */
    public static void register() {
        BLOCKS.register();
        BLOCK_ITEMS.register();
    }
}
