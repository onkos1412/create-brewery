package com.kostya.createbrewery.mixin;

import com.kostya.createbrewery.drunk.DrunkEvents;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forge's {@code LivingEntityUseItemEvent.Finish}, for {@link DrunkEvents#onFinishUsingItem}: the hook needs the
 * item as it was before use (a water bottle), which neither Fabric API offers nor Porting Lib's
 * {@code LIVING_USE_ITEM_FINISH} keeps (it copies the stack after use, by then an empty stack for the last potion).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @WrapOperation(method = "completeUsingItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack brewery$finishUsingItem(ItemStack stack, Level level, LivingEntity entity, Operation<ItemStack> original) {
        // As Forge: a copy taken before the item is used, handed over once it has been.
        ItemStack before = stack.copy();
        ItemStack result = original.call(stack, level, entity);
        DrunkEvents.onFinishUsingItem((LivingEntity) (Object) this, before);
        return result;
    }
}
