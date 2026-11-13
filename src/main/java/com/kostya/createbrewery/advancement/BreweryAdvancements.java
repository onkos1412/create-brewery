package com.kostya.createbrewery.advancement;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Hands out the mod's advancements whose conditions vanilla triggers cannot express: those criteria are
 * {@code minecraft:impossible} in the JSON and are awarded from here.
 */
public final class BreweryAdvancements {

    /** The finished drinks "Taster" asks for, one criterion each. */
    public static final Set<String> TASTER_DRINKS = Set.of("beer", "cider", "kvass", "wine", "mead", "stout",
            "aged_whiskey", "aged_calvados", "aged_rum", "aged_tequila", "vodka", "absinthe", "firefly_liqueur");

    private BreweryAdvancements() {
    }

    /** Awards one criterion of {@code brewery:<advancement>}; a missing advancement or a repeat does nothing. */
    public static void award(ServerPlayer player, String advancement, String criterion) {
        AdvancementHolder holder = player.server.getAdvancements()
                .get(ResourceLocation.fromNamespaceAndPath(CreateBrewery.MOD_ID, advancement));
        if (holder != null) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
