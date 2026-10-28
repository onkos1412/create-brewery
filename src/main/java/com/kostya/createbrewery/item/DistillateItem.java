package com.kostya.createbrewery.item;

import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Intermediate whiskey distillate produced by the distillery. */
public class DistillateItem extends Item {

    public DistillateItem(Properties properties) {
        super(properties);
    }

    @Override
    public DataComponentMap components() {
        return DataComponentMap.builder()
                .addAll(super.components())
                .set(ModDataComponents.DRINK_DATA.get(), new DrinkData(DrinkData.MAX_STARS, 40, "distillate"))
                .build();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DrinkData data = stack.get(ModDataComponents.DRINK_DATA.get());
        if (data != null) {
            tooltip.add(Component.translatable("tooltip.brewery.distillate_data", data.stars())
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
