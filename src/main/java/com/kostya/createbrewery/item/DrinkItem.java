package com.kostya.createbrewery.item;

import com.kostya.createbrewery.BrewerMark;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.advancement.BreweryAdvancements;
import com.kostya.createbrewery.config.BreweryConfig;
import com.kostya.createbrewery.drunk.HangoverRules;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkEffect;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.quality.QualityRules;
import com.kostya.createbrewery.quality.StarLine;
import com.kostya.createbrewery.registry.ModEffects;
import com.kostya.createbrewery.social.ToastRules;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * A bottle of a finished drink. Drinking it raises the player's blood alcohol level.
 *
 * <p>The quality of the drink is stored on the stack as {@link DrinkData} (in NBT on 1.20.1),
 * defaulting to five stars when a stack is created without one.
 */
public class DrinkItem extends Item implements DrinkData.Carrier {
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
    public DrinkData defaultDrinkData() {
        return new DrinkData(DrinkData.MAX_STARS);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (DrinkData.has(stack)) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player) {
            DrinkData data = DrinkData.getOrDefault(stack, new DrinkData(DrinkData.MAX_STARS));
            if (!level.isClientSide) {
                boolean spoiledOrMuddy = data.isSpoiled() || data.stage().equals(DrinkProfile.MUDDY.id());
                // The toast marker is spent by this drink whatever it is; only a proper drink gets the bonus.
                boolean toasted = player.hasEffect(ModEffects.TOAST.get());
                if (toasted) {
                    player.removeEffect(ModEffects.TOAST.get());
                }
                boolean toastBonus = toasted && !spoiledOrMuddy && BreweryConfig.toastBonus();
                if (player instanceof ServerPlayer serverPlayer) {
                    awardAdvancements(serverPlayer, level, data);
                    BrewerMark mark = BrewerMark.get(stack);
                    if (!spoiledOrMuddy && mark != null && mark.owner().isPresent() && !(serverPlayer instanceof FakePlayer)) {
                        // PORT: Task 5 - hospitality comes back with social/**:
                        // HospitalityEvents.treat(serverPlayer.server, serverPlayer, mark.owner().get());
                    }
                    if (toasted && !spoiledOrMuddy) {
                        BreweryAdvancements.award(serverPlayer, "toast", "toasted");
                    }
                }
                // Alcohol is stored on the product, while quality remains a separate value.
                int drunk = data.alcohol() / 5;
                // PORT: Task 4 - the drunk level and hangover debt come back with drunk/** (player data):
                // DrunkManager.addDrunk(player, drunk);
                // A drink too weak to raise the drunk level builds no debt either.
                if (drunk > 0) {
                    int debt = ToastRules.hangoverDebt(
                            HangoverRules.debtFor(data.alcohol(), data.stars(), spoiledOrMuddy), toastBonus);
                    // PORT: Task 4 - DrunkManager.addHangoverDebt(player, debt);
                }
                if (data.isSpoiled()) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 400));
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, 300));
                    player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 1));
                    player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100));
                } else if (QualityRules.nauseaTicks(data.stars()) > 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, QualityRules.nauseaTicks(data.stars())));
                }
                if (!data.isSpoiled() && QualityRules.poisonTicks(data.stars()) > 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.POISON, QualityRules.poisonTicks(data.stars())));
                }
                if (!spoiledOrMuddy) {
                    applyDrinkEffects(player, level, data, toastBonus);
                }
            }
            player.playSound(SoundEvents.PLAYER_BURP, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
            // Like a vanilla potion, the empty bottle comes back: production spends one for every drink.
            return ItemUtils.createFilledResult(stack, player, emptyContainer());
        }
        return stack;
    }

    /** What comes back once the drink is gone: the glass bottle, or the mug in {@link DrinkMugItem}. */
    protected ItemStack emptyContainer() {
        return new ItemStack(Items.GLASS_BOTTLE);
    }

    /** Advancements for what was drunk: a muddy brew, an aged drink, a five-star one, and the taster's list. */
    private static void awardAdvancements(ServerPlayer player, Level level, DrinkData data) {
        String stage = data.stage();
        boolean muddy = stage.equals(DrinkProfile.MUDDY.id());
        if (muddy) {
            BreweryAdvancements.award(player, "muddy", "drank_muddy");
        }
        if (!muddy && !data.isSpoiled() && data.stars() == DrinkData.MAX_STARS) {
            BreweryAdvancements.award(player, "master_brewer", "drank_five_stars");
        }
        String aged = BrewingRecipe.profileWithStage(level, stage).aged();
        if (!aged.isBlank() && stage.equals(aged)) {
            BreweryAdvancements.award(player, "aged", "drank_aged");
        }
        if (BreweryAdvancements.TASTER_DRINKS.contains(stage)) {
            BreweryAdvancements.award(player, "taster", stage);
        }
    }

    /**
     * The drink's own effects, as long as its quality earns: a five-star drink keeps the recipe's full duration,
     * and a toast makes them last half as long again.
     */
    private static void applyDrinkEffects(Player player, Level level, DrinkData data, boolean toasted) {
        float factor = QualityRules.effectDurationFactor(data.stars());
        for (DrinkEffect drinkEffect : BrewingRecipe.profileWithStage(level, data.stage()).effects()
                .getOrDefault(data.stage(), List.of())) {
            int ticks = ToastRules.effectTicks(
                    Math.round(drinkEffect.seconds() * 20 * factor * (float) BreweryConfig.effectMultiplier()), toasted);
            if (ticks <= 0) {
                continue;
            }
            // The recipe codec already refuses unknown effects; an absent one here is simply skipped.
            Optional<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getOptional(drinkEffect.effect());
            effect.ifPresent(mobEffect ->
                    player.addEffect(new MobEffectInstance(mobEffect, ticks, drinkEffect.amplifier())));
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return DRINK_DURATION_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        DrinkData data = DrinkData.get(stack);
        if (data != null) {
            tooltip.add(Component.translatable("drink.brewery." + data.stage()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.brewery.drink_data", StarLine.of(data.stars()))
                    .withStyle(ChatFormatting.GRAY));
        }
        BrewerMark mark = BrewerMark.get(stack);
        if (mark != null) {
            tooltip.add(mark.tooltip());
        }
    }
}
