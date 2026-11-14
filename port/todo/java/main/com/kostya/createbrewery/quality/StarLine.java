package com.kostya.createbrewery.quality;

import com.kostya.createbrewery.DrinkData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Quality as players read it everywhere: gold stars earned, grey stars missed. */
public final class StarLine {
    private StarLine() {
    }

    public static Component of(int stars) {
        return Component.literal("★".repeat(stars)).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("★".repeat(DrinkData.MAX_STARS - stars)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
