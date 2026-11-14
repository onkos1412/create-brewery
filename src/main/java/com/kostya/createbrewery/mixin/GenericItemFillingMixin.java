package com.kostya.createbrewery.mixin;

import com.kostya.createbrewery.block.WortBottling;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A glass bottle takes a whole bottle of wort or none. Create's spout asks how much an item needs by offering its
 * whole tank in a simulation; on Forge the bottle answered the full price even when offered less, so a spout still
 * filling held the bottle back instead of letting it ride past empty. A Transfer API storage may not answer more than
 * it is offered, and neither Fabric API nor Porting Lib has a hook here, hence this one targeted injection.
 */
@Mixin(value = GenericItemFilling.class, remap = false)
public abstract class GenericItemFillingMixin {

    @Inject(method = "getRequiredAmountForItem", at = @At("HEAD"), cancellable = true)
    private static void brewery$wholeBottleOfWort(Level world, ItemStack stack, FluidStack availableFluid,
                                                  CallbackInfoReturnable<Long> cir) {
        if (WortBottling.fillsWithWort(stack, availableFluid.getType())) {
            cir.setReturnValue(WortBottling.WORT_PER_BOTTLE_DROPLETS);
        }
    }
}
