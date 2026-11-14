package com.kostya.createbrewery.block;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.FluidUnits;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.registry.ModFluids;
import com.kostya.createbrewery.registry.ModItems;
import io.github.fabricators_of_create.porting_lib.util.ServerLifecycleHooks;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Turns wort into a bottled drink, whichever machine does the pouring. */
public final class WortBottling {
    /** One bottle takes a whole brew, so a spout yields exactly what the kettle's own tap does. */
    public static final int WORT_PER_BOTTLE = KettleBlockEntity.WORT_PER_BREW;
    /** The same bottle in droplets: 81000, never Fabric's usual third of a bucket. */
    public static final long WORT_PER_BOTTLE_DROPLETS = FluidUnits.toDroplets(WORT_PER_BOTTLE);

    private WortBottling() {
    }

    public static ItemStack bottle(Level level, int stars, String drink, @Nullable BrewerMark mark) {
        ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
        DrinkProfile profile = BrewingRecipe.profileOf(level, drink);
        DrinkData.set(bottle, new DrinkData(stars, profile.alcohol(), drink));
        if (mark != null) {
            BrewerMark.set(bottle, mark);
        }
        return bottle;
    }

    /** Whether {@code stack} is a glass bottle and {@code fluid} the wort it can be filled with. */
    public static boolean fillsWithWort(ItemStack stack, FluidVariant fluid) {
        return stack.is(Items.GLASS_BOTTLE) && fluid.getFluid().isSame(ModFluids.WORT.get());
    }

    /**
     * Lets Create's spout, and anything else that fills containers, pour wort into an empty glass bottle: a whole
     * bottle's worth or nothing. Registered with Fabric's combined provider for the glass bottle, next to Fabric's
     * own water bottle storage (see {@link ModCapabilities}).
     *
     * <p>Forge's handler named the full bottle's price even when a simulation offered less, so that Create's spout
     * held a bottle back until its tank had enough. A Transfer API storage may never report more than it was
     * offered, so that part lives in {@code GenericItemFillingMixin}.
     */
    public static final class BottleStorage implements InsertionOnlyStorage<FluidVariant> {
        private final ContainerItemContext context;

        public BottleStorage(ContainerItemContext context) {
            this.context = context;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            // As on Forge: one bottle at a time, and only a whole bottle's worth.
            if (!resource.getFluid().isSame(ModFluids.WORT.get()) || maxAmount < WORT_PER_BOTTLE_DROPLETS
                    || !context.getItemVariant().isOf(Items.GLASS_BOTTLE) || context.getAmount() != 1) {
                return 0;
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return 0;
            }
            DrinkData data = DrinkData.get(resource);
            BrewerMark mark = BrewerMark.get(resource);
            ItemStack drink = data == null
                    ? bottle(server.overworld(), DrinkData.MAX_STARS, DrinkProfile.BEER.id(), mark)
                    : bottle(server.overworld(), data.stars(), data.stage(), mark);
            return context.exchange(ItemVariant.of(drink), 1, transaction) == 1 ? WORT_PER_BOTTLE_DROPLETS : 0;
        }
    }
}
