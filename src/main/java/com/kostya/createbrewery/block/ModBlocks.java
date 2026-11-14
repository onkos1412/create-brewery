package com.kostya.createbrewery.block;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registry of the mod's blocks and block entities.
 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CreateBrewery.MOD_ID);
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CreateBrewery.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CreateBrewery.MOD_ID);

    /**
     * The kettle: holds water and ingredients above a heat source and brews wort.
     */
    public static final RegistryObject<KettleBlock> KETTLE = BLOCKS.register("kettle",
            () -> new KettleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<BlockItem> KETTLE_ITEM = BLOCK_ITEMS.register("kettle",
            () -> new BlockItem(KETTLE.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<KettleBlockEntity>> KETTLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("kettle",
                    () -> BlockEntityType.Builder.of(KettleBlockEntity::new, KETTLE.get()).build(null));

    /**
     * The bottling spout: pours a kettle's wort into an empty bottle.
     */
    public static final RegistryObject<SpoutBlock> SPOUT = BLOCKS.register("spout",
            () -> new SpoutBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 4.0f)
                    .noOcclusion()));

    public static final RegistryObject<BlockItem> SPOUT_ITEM = BLOCK_ITEMS.register("spout",
            () -> new BlockItem(SPOUT.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<SpoutBlockEntity>> SPOUT_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("spout",
                    () -> BlockEntityType.Builder.of(SpoutBlockEntity::new, SPOUT.get()).build(null));

    public static final RegistryObject<DistilleryBlock> DISTILLERY = BLOCKS.register("distillery",
            () -> new DistilleryBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                    .noOcclusion()));

    /** The upper half of the distillery. It has no item: placing the distillery places it. */
    public static final RegistryObject<DistilleryTopBlock> DISTILLERY_TOP = BLOCKS.register("distillery_top",
            () -> new DistilleryTopBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f)
                    .noOcclusion().pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<BlockItem> DISTILLERY_ITEM = BLOCK_ITEMS.register("distillery",
            () -> new BlockItem(DISTILLERY.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<DistilleryBlockEntity>> DISTILLERY_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("distillery",
                    () -> BlockEntityType.Builder.of(DistilleryBlockEntity::new, DISTILLERY.get()).build(null));

    /**
     * 1.20.1 has no block capabilities without a block entity, so the column gets one that only hands pipes and
     * funnels on to the machine below it (1.21.1 registered a block capability on the column instead).
     */
    public static final RegistryObject<BlockEntityType<DistilleryTopBlockEntity>> DISTILLERY_TOP_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("distillery_top",
                    () -> BlockEntityType.Builder.of(DistilleryTopBlockEntity::new, DISTILLERY_TOP.get()).build(null));

    public static final RegistryObject<BarrelBlock> BARREL = BLOCKS.register("barrel",
            () -> new BarrelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .noOcclusion()));

    public static final RegistryObject<BlockItem> BARREL_ITEM = BLOCK_ITEMS.register("barrel",
            () -> new BlockItem(BARREL.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<BarrelBlockEntity>> BARREL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("barrel",
                    () -> BlockEntityType.Builder.of(BarrelBlockEntity::new, BARREL.get()).build(null));

    /** A small keg on the bar: eight portions of one drink, poured into mugs. */
    public static final RegistryObject<KegBlock> KEG = BLOCKS.register("keg",
            () -> new KegBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0f, 3.0f)
                    .noOcclusion()));

    public static final RegistryObject<BlockItem> KEG_ITEM = BLOCK_ITEMS.register("keg",
            () -> new BlockItem(KEG.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<KegBlockEntity>> KEG_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("keg",
                    () -> BlockEntityType.Builder.of(KegBlockEntity::new, KEG.get()).build(null));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
