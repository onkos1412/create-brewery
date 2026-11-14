package com.kostya.createbrewery.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
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

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            // PORT: Task 5 - signing the book and sending its pages come back with journal/** and network/**:
            // the 1.21.1 payloadFor(ServerPlayer, ItemStack) (signs an unsigned book to its first real reader, then
            // builds OpenBrewerBookPayload from the journal and the brewing recipes) and
            // PacketDistributor.sendToPlayer(serverPlayer, payloadFor(serverPlayer, stack)).
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BookOwner owner = BookOwner.get(stack);
        Component line = owner == null
                ? Component.translatable("tooltip.brewery.book_unbound")
                : Component.translatable("tooltip.brewery.book_owner", owner.name());
        tooltip.add(line.copy().withStyle(ChatFormatting.GRAY));
    }
}
