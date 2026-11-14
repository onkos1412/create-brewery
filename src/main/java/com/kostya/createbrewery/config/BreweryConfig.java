package com.kostya.createbrewery.config;

import com.kostya.createbrewery.drunk.DrunkLevel;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server settings (file {@code brewery-server.toml}). The pure rule classes never read these: the
 * drunk manager, the drunk events and the drink item gate and scale with them.
 *
 * <p>Server config values only exist while a world is running, so every getter falls back to the
 * default when the spec is not loaded yet.
 */
public final class BreweryConfig {

    public static final boolean DEFAULT_STAGGER = true;
    public static final boolean DEFAULT_VOMIT = true;
    public static final boolean DEFAULT_HANGOVER = true;
    public static final int DEFAULT_PASS_OUT_AT = DrunkLevel.PASS_OUT_AT;
    public static final int DEFAULT_SECONDS_PER_SOBER_STEP = 1;
    public static final double DEFAULT_EFFECT_MULTIPLIER = 1.0;
    public static final boolean DEFAULT_TOAST_BONUS = true;
    public static final int DEFAULT_TOAST_SECONDS = 10;

    public static final ForgeConfigSpec.BooleanValue STAGGER;
    public static final ForgeConfigSpec.BooleanValue VOMIT;
    public static final ForgeConfigSpec.BooleanValue HANGOVER;
    public static final ForgeConfigSpec.IntValue PASS_OUT_AT;
    public static final ForgeConfigSpec.IntValue SECONDS_PER_SOBER_STEP;
    public static final ForgeConfigSpec.DoubleValue EFFECT_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue TOAST_BONUS;
    public static final ForgeConfigSpec.IntValue TOAST_SECONDS;

    public static final ForgeConfigSpec SPEC;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Drunk mechanics").translation("brewery.configuration.drunk").push("drunk");
        STAGGER = builder
                .comment("Drunk players are shoved sideways while walking")
                .translation("brewery.configuration.stagger")
                .define("stagger", DEFAULT_STAGGER);
        VOMIT = builder
                .comment("Heavy drinkers can throw up")
                .translation("brewery.configuration.vomit")
                .define("vomit", DEFAULT_VOMIT);
        HANGOVER = builder
                .comment("Bad drinks leave a hangover once the player is sober. When off, no debt builds up")
                .translation("brewery.configuration.hangover")
                .define("hangover", DEFAULT_HANGOVER);
        PASS_OUT_AT = builder
                .comment("Drunk level at which a player passes out (100 is practically never). After a blackout the level "
                        + "drops to 60, or to one below this value if that is lower")
                .translation("brewery.configuration.passOutAt")
                .defineInRange("passOutAt", DEFAULT_PASS_OUT_AT, 50, 100);
        SECONDS_PER_SOBER_STEP = builder
                .comment("Every this many seconds the drunk level drops by one")
                .translation("brewery.configuration.secondsPerSoberStep")
                .defineInRange("secondsPerSoberStep", DEFAULT_SECONDS_PER_SOBER_STEP, 1, 60);
        builder.pop();
        builder.comment("Drinks").translation("brewery.configuration.drinks").push("drinks");
        EFFECT_MULTIPLIER = builder
                .comment("Multiplier for the duration of the effects drinks give (0 turns them off)")
                .translation("brewery.configuration.effectMultiplier")
                .defineInRange("effectMultiplier", DEFAULT_EFFECT_MULTIPLIER, 0.0, 4.0);
        TOAST_BONUS = builder
                .comment("Drinking right after clinking drinks makes the drink's effects last 50% longer and halves its "
                        + "hangover debt. When off, clinking still works and still earns the advancement")
                .translation("brewery.configuration.toastBonus")
                .define("toastBonus", DEFAULT_TOAST_BONUS);
        TOAST_SECONDS = builder
                .comment("How many seconds after clinking the next drink still counts as a toast")
                .translation("brewery.configuration.toastSeconds")
                .defineInRange("toastSeconds", DEFAULT_TOAST_SECONDS, 3, 60);
        builder.pop();
        SPEC = builder.build();
    }

    private BreweryConfig() {
    }

    public static boolean stagger() {
        return SPEC.isLoaded() ? STAGGER.get() : DEFAULT_STAGGER;
    }

    public static boolean vomit() {
        return SPEC.isLoaded() ? VOMIT.get() : DEFAULT_VOMIT;
    }

    public static boolean hangover() {
        return SPEC.isLoaded() ? HANGOVER.get() : DEFAULT_HANGOVER;
    }

    public static int passOutAt() {
        return SPEC.isLoaded() ? PASS_OUT_AT.get() : DEFAULT_PASS_OUT_AT;
    }

    public static int secondsPerSoberStep() {
        return SPEC.isLoaded() ? SECONDS_PER_SOBER_STEP.get() : DEFAULT_SECONDS_PER_SOBER_STEP;
    }

    public static double effectMultiplier() {
        return SPEC.isLoaded() ? EFFECT_MULTIPLIER.get() : DEFAULT_EFFECT_MULTIPLIER;
    }

    public static boolean toastBonus() {
        return SPEC.isLoaded() ? TOAST_BONUS.get() : DEFAULT_TOAST_BONUS;
    }

    public static int toastSeconds() {
        return SPEC.isLoaded() ? TOAST_SECONDS.get() : DEFAULT_TOAST_SECONDS;
    }
}
