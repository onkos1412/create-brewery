package com.kostya.createbrewery.mixin.client;

import com.kostya.createbrewery.recipe.ModRecipeTypes;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.RecipeBookCategories;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge's {@code RegisterRecipeBookCategoriesEvent} finder: brewing recipes have no recipe-book tab, so they file
 * under {@link RecipeBookCategories#UNKNOWN}. Vanilla ends there too, but logs an "Unknown recipe category" warning
 * for every brewing recipe each time the recipes reach the client; Forge's finder kept the log quiet, and so does this.
 */
@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin {
    @Inject(method = "getCategory", at = @At("HEAD"), cancellable = true)
    private static void brewery$brewingHasNoCategory(Recipe<?> recipe, CallbackInfoReturnable<RecipeBookCategories> cir) {
        if (recipe.getType() == ModRecipeTypes.BREWING.get()) {
            cir.setReturnValue(RecipeBookCategories.UNKNOWN);
        }
    }
}
