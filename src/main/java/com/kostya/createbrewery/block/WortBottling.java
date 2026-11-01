package com.kostya.createbrewery.block;

import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Turns wort into a bottled drink, whichever machine does the pouring. */
public final class WortBottling {
    /** One bottle takes a whole brew, so a spout yields exactly what the kettle's own tap does. */
    public static final int WORT_PER_BOTTLE = KettleBlockEntity.WORT_PER_BREW;

    private WortBottling() {
    }

    public static ItemStack bottle(Level level, int stars, String drink) {
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkProfile profile = BrewingRecipe.profileOf(level, drink);
        bottle.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(stars, profile.alcohol(), drink));
        return bottle;
    }

    /** Lets Create's spout, and anything else that fills containers, pour wort into an empty bottle. */
    public static final class BottleHandler implements IFluidHandlerItem {
        private ItemStack container;

        public BottleHandler(ItemStack container) {
            this.container = container;
        }

        @Override
        public ItemStack getContainer() {
            return container;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return WORT_PER_BOTTLE;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return stack.is(ModFluids.WORT.get());
        }

        /**
         * A simulation names the full bottle's price even when less is offered: a spout whose tank
         * is still filling must hold the bottle back rather than let it ride past empty.
         */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource) || !container.is(Items.GLASS_BOTTLE)) {
                return 0;
            }
            if (action.simulate()) {
                return WORT_PER_BOTTLE;
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || container.getCount() != 1 || resource.getAmount() < WORT_PER_BOTTLE) {
                return 0;
            }
            DrinkData data = resource.get(ModDataComponents.DRINK_DATA.get());
            container = data == null
                    ? bottle(server.overworld(), DrinkData.MAX_STARS, DrinkProfile.BEER.id())
                    : bottle(server.overworld(), data.stars(), data.stage());
            return WORT_PER_BOTTLE;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
