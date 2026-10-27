package com.kostya.createbrewery.item;

import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.drunk.DrunkManager;
import com.kostya.createbrewery.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A bottle of a finished drink. Drinking it raises the player's blood alcohol level.
 *
 * <p>The quality of the drink is stored on the stack as a {@link DrinkData} component,
 * defaulting to five stars when a stack is created without one.
 */
public class DrinkItem extends Item {
    /** How many ticks the drinking animation takes. */
    public static final int DRINK_DURATION_TICKS = 32;

    public DrinkItem(Properties properties) {
        super(properties);
    }

    /**
     * Every new stack of this item starts with {@link DrinkData#MAX_STARS} stars. The
     * brewing pipeline replaces the component with the real quality.
     */
    @Override
    public DataComponentMap components() {
        return DataComponentMap.builder()
                .addAll(super.components())
                .set(ModDataComponents.DRINK_DATA.get(), new DrinkData(DrinkData.MAX_STARS))
                .build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.has(ModDataComponents.DRINK_DATA.get())) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player) {
            DrinkData data = stack.getOrDefault(ModDataComponents.DRINK_DATA.get(), new DrinkData(DrinkData.MAX_STARS));
            if (!level.isClientSide) {
                // A better drink gets you drunker; the amount is the star rating.
                DrunkManager.addDrunk(player, data.stars());
            }
            player.playSound(SoundEvents.PLAYER_BURP, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
            stack.consume(1, player);
        }
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return DRINK_DURATION_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        DrinkData data = stack.get(ModDataComponents.DRINK_DATA.get());
        if (data != null) {
            tooltip.add(Component.translatable("tooltip.brewery.drink_data", data.stars())
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
