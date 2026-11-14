package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.quality.StarLine;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

import java.util.function.Consumer;

/**
 * Registry of the mod's fluids.
 *
 * <p>Currently only {@code brewery:wort} — the unfermented brewing liquid that the
 * kettle and the spout move around. No bucket item is provided.
 */
public final class ModFluids {
    /** Resource id of the wort fluid type, source fluid and flowing fluid. */
    public static final ResourceLocation WORT_ID = CreateBrewery.id("wort");

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, CreateBrewery.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, CreateBrewery.MOD_ID);

    /** Colour of the wort: a warm amber, matching the eventual beer tint. */
    private static final Vector3f WORT_TINT = new Vector3f(0.85f, 0.62f, 0.20f);

    public static final RegistryObject<FluidType> WORT_TYPE = FLUID_TYPES.register("wort",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid_type.brewery.wort")
                    .density(1100)
                    .viscosity(1400)
                    .rarity(Rarity.COMMON)) {
                /** Tanks and goggles name a wort by its drink and quality, which keep it apart from other wort. */
                @Override
                public Component getDescription(FluidStack stack) {
                    DrinkData data = DrinkData.get(stack);
                    if (data == null) {
                        return super.getDescription(stack);
                    }
                    return Component.translatable("fluid_type.brewery.wort_of",
                            Component.translatable("drink.brewery." + data.stage()), StarLine.of(data.stars()));
                }

                /** Called by Forge on the physical client only, so the client classes stay off the server. */
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(ClientHandler.extensions());
                }
            });

    public static final RegistryObject<FlowingFluid> WORT =
            FLUIDS.register("wort", WortFluid.Source::new);
    public static final RegistryObject<FlowingFluid> FLOWING_WORT =
            FLUIDS.register("flowing_wort", WortFluid.Flowing::new);

    private ModFluids() {
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    /**
     * The wort textures and tint on the client. Only {@link FluidType#initializeClient} reaches this class, and Forge
     * calls that on the physical client alone (1.21.1: {@code RegisterClientExtensionsEvent}).
     */
    public static final class ClientHandler {
        private ClientHandler() {
        }

        static IClientFluidTypeExtensions extensions() {
            return new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return CreateBrewery.id("block/wort_still");
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return CreateBrewery.id("block/wort_flow");
                }

                /** Without a stack to go on, wort is its plain beer colour. */
                @Override
                public int getTintColor() {
                    return 0xFF000000 | DrinkProfile.BEER.color();
                }

                /** Each wort carries its drink, and so its colour, through Create's pipes and tanks. */
                @Override
                public int getTintColor(FluidStack stack) {
                    DrinkData data = DrinkData.get(stack);
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
            };
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
