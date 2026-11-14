package com.kostya.createbrewery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The brewer's book: signed by its first reader, it shows only them their brewing journal. */
public class BrewerBookItem extends Item {

    public BrewerBookItem(Properties properties) {
        super(properties);
    }

    // PORT(Task 5): use() and payloadFor(ServerPlayer, ItemStack) — signing to the first real reader and sending the
    // journal pages over the network — come back with journal/** and network/** (Forge branch, BrewerBookItem).

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BookOwner owner = BookOwner.get(stack);
        Component line = owner == null
                ? Component.translatable("tooltip.brewery.book_unbound")
                : Component.translatable("tooltip.brewery.book_owner", owner.name());
        tooltip.add(line.copy().withStyle(ChatFormatting.GRAY));
    }
}
