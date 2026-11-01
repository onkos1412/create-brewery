package com.kostya.createbrewery.item;

import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** A product of the distillery, waiting for a barrel or, like vodka, for a bottle. */
public class DistillateItem extends Item {

    public DistillateItem(Properties properties) {
        super(properties);
    }

    @Override
    public DataComponentMap components() {
        return DataComponentMap.builder()
                .addAll(super.components())
                .set(ModDataComponents.DRINK_DATA.get(), new DrinkData(DrinkData.MAX_STARS, 40, "whiskey"))
                .build();
    }

    /** A distillate that skips the barrel, like vodka, can be poured by hand into an empty bottle. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        DrinkData data = stack.get(ModDataComponents.DRINK_DATA.get());
        if (data == null || !data.isSpoiled() && BrewingRecipe.profileDistilledTo(level, data.stage()).canAge()) {
            return InteractionResultHolder.pass(stack);
        }
        int glass = player.getInventory().findSlotMatchingItem(new ItemStack(Items.GLASS_BOTTLE));
        if (glass < 0) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide()) {
            player.getInventory().removeItem(glass, 1);
            ItemStack bottle = new ItemStack(ModItems.DRINK_BOTTLE.get());
            bottle.set(ModDataComponents.DRINK_DATA.get(), data);
            stack.shrink(1);
            if (!player.getInventory().add(bottle)) {
                player.drop(bottle, false);
            }
            player.playSound(SoundEvents.BOTTLE_FILL, 1.0f, 1.0f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DrinkData data = stack.get(ModDataComponents.DRINK_DATA.get());
        if (data != null) {
            tooltip.add(Component.translatable("drink.brewery." + data.stage()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.brewery.distillate_data", data.stars())
                    .withStyle(ChatFormatting.GOLD));
            if (context.level() != null) {
                DrinkProfile drink = BrewingRecipe.profileDistilledTo(context.level(), data.stage());
                if (!data.isSpoiled() && drink.canAge()) {
                    tooltip.add(Component.translatable("tooltip.brewery.distillate_aging", drink.agingDays(),
                                    Component.translatable("block.minecraft." + drink.wood() + "_planks"))
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    tooltip.add(Component.translatable("tooltip.brewery.distillate_pour")
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }
}
