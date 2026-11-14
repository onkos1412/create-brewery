package com.kostya.createbrewery.registry;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.quality.StarLine;
import io.github.fabricators_of_create.porting_lib.util.LazyRegistrar;
import io.github.fabricators_of_create.porting_lib.util.RegistryObject;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Registry of the mod's fluids.
 *
 * <p>Currently only {@code brewery:wort} — the unfermented brewing liquid that the
 * kettle and the spout move around. No bucket item is provided.
 *
 * <p>Fabric has no Forge {@code FluidType}: the name tanks and goggles show comes from a Transfer API
 * {@link FluidVariantAttributeHandler} (common code), and the textures, tint and fog colour from client fluid
 * render handlers (PORT(Task 6): {@code FluidRenderHandlerRegistry} in the client entrypoint, from the Forge
 * {@code ClientHandler}: still {@code brewery:block/wort_still}, flowing {@code brewery:block/wort_flow}, tint the
 * drink's colour, plain beer colour without one, fog {@code 0.85, 0.62, 0.20}).
 */
public final class ModFluids {
    /** Resource id of the wort source fluid (the flowing one is {@code brewery:flowing_wort}). */
    public static final ResourceLocation WORT_ID = CreateBrewery.id("wort");

    public static final LazyRegistrar<Fluid> FLUIDS = LazyRegistrar.create(Registries.FLUID, CreateBrewery.MOD_ID);

    public static final RegistryObject<FlowingFluid> WORT =
            FLUIDS.register("wort", WortFluid.Source::new);
    public static final RegistryObject<FlowingFluid> FLOWING_WORT =
            FLUIDS.register("flowing_wort", WortFluid.Flowing::new);

    private ModFluids() {
    }

    public static void register() {
        FLUIDS.register();
        FluidVariantAttributes.register(WORT.get(), new WortAttributes());
        FluidVariantAttributes.register(FLOWING_WORT.get(), new WortAttributes());
    }

    /** What Forge's wort {@code FluidType} told tanks and goggles. */
    private static final class WortAttributes implements FluidVariantAttributeHandler {
        /** Tanks and goggles name a wort by its drink and quality, which keep it apart from other wort. */
        @Override
        public Component getName(FluidVariant variant) {
            DrinkData data = DrinkData.get(variant);
            if (data == null) {
                return Component.translatable("fluid_type.brewery.wort");
            }
            return Component.translatable("fluid_type.brewery.wort_of",
                    Component.translatable("drink.brewery." + data.stage()), StarLine.of(data.stars()));
        }
    }

    /** The wort fluid: one source and one flowing variant. */
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
