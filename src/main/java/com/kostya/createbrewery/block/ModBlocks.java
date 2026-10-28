package com.kostya.createbrewery.block;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry of the mod's blocks and block entities.
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreateBrewery.MOD_ID);
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.createItems(CreateBrewery.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateBrewery.MOD_ID);

    /**
     * The kettle: holds water and ingredients above a heat source and brews wort.
     */
    public static final DeferredBlock<KettleBlock> KETTLE = BLOCKS.registerBlock("kettle",
            KettleBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops());

    public static final DeferredHolder<Item, BlockItem> KETTLE_ITEM = BLOCK_ITEMS.register("kettle",
            () -> new BlockItem(KETTLE.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KettleBlockEntity>> KETTLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("kettle",
                    () -> BlockEntityType.Builder.of(KettleBlockEntity::new, KETTLE.get()).build(null));

    /**
     * The bottling spout: pours a kettle's wort into an empty bottle.
     */
    public static final DeferredBlock<SpoutBlock> SPOUT = BLOCKS.registerBlock("spout",
            SpoutBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 4.0f)
                    .noOcclusion());

    public static final DeferredHolder<Item, BlockItem> SPOUT_ITEM = BLOCK_ITEMS.register("spout",
            () -> new BlockItem(SPOUT.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpoutBlockEntity>> SPOUT_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("spout",
                    () -> BlockEntityType.Builder.of(SpoutBlockEntity::new, SPOUT.get()).build(null));

    public static final DeferredBlock<DistilleryBlock> DISTILLERY = BLOCKS.registerBlock("distillery",
            DistilleryBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f));

    public static final DeferredHolder<Item, BlockItem> DISTILLERY_ITEM = BLOCK_ITEMS.register("distillery",
            () -> new BlockItem(DISTILLERY.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DistilleryBlockEntity>> DISTILLERY_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("distillery",
                    () -> BlockEntityType.Builder.of(DistilleryBlockEntity::new, DISTILLERY.get()).build(null));

    public static final DeferredBlock<BarrelBlock> BARREL = BLOCKS.registerBlock("barrel",
            BarrelBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f));

    public static final DeferredHolder<Item, BlockItem> BARREL_ITEM = BLOCK_ITEMS.register("barrel",
            () -> new BlockItem(BARREL.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BarrelBlockEntity>> BARREL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("barrel",
                    () -> BlockEntityType.Builder.of(BarrelBlockEntity::new, BARREL.get()).build(null));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
