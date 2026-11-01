package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.joml.Vector3f;

/**
 * Registry of the mod's fluids.
 *
 * <p>Currently only {@code brewery:wort} — the unfermented brewing liquid that the
 * kettle and the spout move around. No bucket item is provided.
 */
public final class ModFluids {
    /** Resource id of the wort fluid type, source fluid and flowing fluid. */
    public static final ResourceLocation WORT_ID =
            ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "wort");

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, CreateBrewery.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, CreateBrewery.MOD_ID);

    /** Colour of the wort: a warm amber, matching the eventual beer tint. */
    private static final Vector3f WORT_TINT = new Vector3f(0.85f, 0.62f, 0.20f);

    public static final DeferredHolder<FluidType, FluidType> WORT_TYPE = FLUID_TYPES.register("wort",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.brewery.wort")
                    .density(1100)
                    .viscosity(1400)
                    .rarity(Rarity.COMMON)));

    public static final DeferredHolder<Fluid, FlowingFluid> WORT =
            FLUIDS.register("wort", WortFluid.Source::new);
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_WORT =
            FLUIDS.register("flowing_wort", WortFluid.Flowing::new);

    private ModFluids() {
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    /** Wires the wort textures and tint into the client. Must only be called on the client. */
    @EventBusSubscriber(modid = CreateBrewery.MOD_ID, value = Dist.CLIENT)
    public static final class ClientHandler {
        private ClientHandler() {
        }

        @SubscribeEvent
        public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "block/wort_still");
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, "block/wort_flow");
                }

                /** Without a stack to go on, wort is its plain beer colour. */
                @Override
                public int getTintColor() {
                    return 0xFF000000 | DrinkProfile.BEER.color();
                }

                /** Each wort carries its drink, and so its colour, through Create's pipes and tanks. */
                @Override
                public int getTintColor(FluidStack stack) {
                    DrinkData data = stack.get(ModDataComponents.DRINK_DATA.get());
                    ClientLevel level = Minecraft.getInstance().level;
                    if (data == null || level == null) {
                        return getTintColor();
                    }
                    return 0xFF000000 | BrewingRecipe.profileOf(level, data.stage()).color();
                }

                @Override
                public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level,
                                               int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
                    return WORT_TINT;
                }
            }, WORT_TYPE.get());
        }
    }

    /** The wort fluid: one source and one flowing variant, sharing a single fluid type. */
    public abstract static class WortFluid extends FlowingFluid {
        @Override
        public Fluid getFlowing() {
            return FLOWING_WORT.get();
        }

        @Override
        public Fluid getSource() {
            return WORT.get();
        }

        @Override
        public FluidType getFluidType() {
            return WORT_TYPE.get();
        }

        @Override
        public boolean isSame(Fluid fluid) {
            return fluid == WORT.get() || fluid == FLOWING_WORT.get();
        }

        /** Wort does not create new source blocks in the world (no infinite wort). */
        @Override
        protected boolean canConvertToSource(Level level) {
            return false;
        }

        @Override
        protected void beforeDestroyingBlock(LevelAccessor level, BlockPos pos, BlockState state) {
            Block.dropResources(state, level, pos, state.hasBlockEntity() ? level.getBlockEntity(pos) : null);
        }

        @Override
        protected int getSlopeFindDistance(LevelReader level) {
            return 4;
        }

        @Override
        protected int getDropOff(LevelReader level) {
            return 1;
        }

        /** No block form for wort; it is only ever a fluid. */
        @Override
        public BlockState createLegacyBlock(FluidState state) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public float getExplosionResistance() {
            return 100.0f;
        }

        /** Wort is thick: it spreads more slowly than water. */
        @Override
        public int getTickDelay(LevelReader level) {
            return 10;
        }

        @Override
        public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluidIn, Direction direction) {
            return direction == Direction.DOWN && !isSame(fluidIn);
        }

        /** No bucket form for wort (no bucket item is registered). */
        @Override
        public Item getBucket() {
            return Items.AIR;
        }

        public static class Source extends WortFluid {
            @Override
            public boolean isSource(FluidState state) {
                return true;
            }

            @Override
            public int getAmount(FluidState state) {
                return 8;
            }
        }

        public static class Flowing extends WortFluid {
            @Override
            public boolean isSource(FluidState state) {
                return false;
            }

            @Override
            public int getAmount(FluidState state) {
                return state.getValue(LEVEL);
            }
        }
    }
}
