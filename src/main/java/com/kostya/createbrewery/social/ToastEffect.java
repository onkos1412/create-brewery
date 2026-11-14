package com.kostya.createbrewery.social;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The toast marker: the player has just clinked drinks. It does nothing by itself; the next drink reads it,
 * gets the toast bonus and removes it.
 */
public class ToastEffect extends MobEffect {

    /** Warm amber, like a raised mug of beer. */
    public static final int COLOR = 0xE8B04A;

    public ToastEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
