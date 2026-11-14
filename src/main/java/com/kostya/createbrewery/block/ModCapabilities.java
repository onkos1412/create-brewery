package com.kostya.createbrewery.block;

import com.kostya.createbrewery.CreateBrewery;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes the machines to Create's pipes and funnels, and to any other automation.
 *
 * <p>On 1.20.1 the block entities answer {@code getCapability} themselves (see each machine); this class adds the
 * glass bottle's fluid handler, which 1.21.1 registered through {@code RegisterCapabilitiesEvent}.
 */
@Mod.EventBusSubscriber(modid = CreateBrewery.MOD_ID)
public final class ModCapabilities {
    private static final double STILL_STRESS = 4.0;
    private static final net.minecraft.resources.ResourceLocation BOTTLE_WORT = CreateBrewery.id("bottle_wort");

    private ModCapabilities() {
    }

    /** Lets Create's spout, and anything else that fills containers, pour wort into an empty glass bottle. */
    @SubscribeEvent
    public static void attachBottleHandler(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (stack.is(Items.GLASS_BOTTLE)) {
            event.addCapability(BOTTLE_WORT, new ICapabilityProvider() {
                @Override
                public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
                    // A fresh handler each time: filling swaps the container it reports.
                    return ForgeCapabilities.FLUID_HANDLER_ITEM.orEmpty(cap,
                            LazyOptional.of(() -> new WortBottling.BottleHandler(stack)));
                }
            });
        }
    }

    /** Mod-bus events. */
    @Mod.EventBusSubscriber(modid = CreateBrewery.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        /** The distillery loads a network as much as a millstone does. */
        @SubscribeEvent
        public static void registerStress(FMLCommonSetupEvent event) {
            BlockStressValues.IMPACTS.register(ModBlocks.DISTILLERY.get(), () -> STILL_STRESS);
        }
    }

    /** Spills a machine's inventories into the world when its block is removed. */
    static void dropContents(Level level, BlockPos pos, IItemHandler... inventories) {
        for (IItemHandler inventory : inventories) {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        inventory.getStackInSlot(slot).copy());
            }
        }
    }
}
