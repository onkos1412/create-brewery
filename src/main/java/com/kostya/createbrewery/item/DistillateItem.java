package com.kostya.createbrewery.item;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.quality.StarLine;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.registry.ModItems;
import net.minecraft.ChatFormatting;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A product of the distillery, waiting for a barrel or, like vodka, for a bottle. */
public class DistillateItem extends Item implements DrinkData.Carrier {

    public DistillateItem(Properties properties) {
        super(properties);
    }

    @Override
    public DrinkData defaultDrinkData() {
        return new DrinkData(DrinkData.MAX_STARS, 40, "whiskey");
    }

    /** A distillate that skips the barrel, like vodka, can be poured by hand into an empty bottle. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        DrinkData data = DrinkData.get(stack);
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
            DrinkData.set(bottle, data);
            BrewerMark mark = BrewerMark.get(stack);
            if (mark != null) {
                BrewerMark.set(bottle, mark);
            }
            stack.shrink(1);
            if (!player.getInventory().add(bottle)) {
                player.drop(bottle, false);
            }
            player.playSound(SoundEvents.BOTTLE_FILL, 1.0f, 1.0f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        DrinkData data = DrinkData.get(stack);
        if (data != null) {
            tooltip.add(Component.translatable("drink.brewery." + data.stage()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.brewery.distillate_data", StarLine.of(data.stars()))
                    .withStyle(ChatFormatting.GRAY));
        }
        BrewerMark mark = BrewerMark.get(stack);
        if (mark != null) {
            tooltip.add(mark.tooltip());
        }
    }
}
