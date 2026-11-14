package com.kostya.createbrewery.item;

import com.kostya.createbrewery.journal.BreweryJournalData;
import com.kostya.createbrewery.journal.DrinkJournal;
import com.kostya.createbrewery.network.ModNetwork;
import com.kostya.createbrewery.network.OpenBrewerBookPayload;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.recipe.ModRecipeTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The brewer's book: signed by its first reader, it shows only them their brewing journal. */
public class BrewerBookItem extends Item {

    public BrewerBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendTo(serverPlayer, payloadFor(serverPlayer, stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * What {@code reader} sees on opening {@code book}. An unsigned book is signed to its first real reader; the
     * owner gets one page per drink of the brewing recipes, in recipe-id order, and anyone else an unreadable book.
     */
    public static OpenBrewerBookPayload payloadFor(ServerPlayer reader, ItemStack book) {
        BookOwner owner = BookOwner.get(book);
        if (owner == null && !(reader instanceof FakePlayer)) {
            owner = new BookOwner(reader.getUUID(), reader.getGameProfile().getName());
            BookOwner.set(book, owner);
        }
        if (owner == null || !owner.id().equals(reader.getUUID())) {
            return new OpenBrewerBookPayload(false, "", List.of());
        }
        ServerLevel level = reader.serverLevel();
        Map<String, DrinkJournal> journal = BreweryJournalData.get(level.getServer()).journalOf(owner.id());
        List<OpenBrewerBookPayload.Page> pages = level.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.BREWING.get()).stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(recipe -> recipe.drink().id())
                .distinct()
                .filter(drink -> !drink.equals(DrinkProfile.MUDDY.id()))
                .map(drink -> {
                    DrinkProfile profile = BrewingRecipe.profileOf(level, drink);
                    return new OpenBrewerBookPayload.Page(drink, profile.canDistill(), profile.canAge(),
                            Optional.ofNullable(journal.get(drink)));
                })
                .toList();
        return new OpenBrewerBookPayload(true, owner.name(), pages);
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
