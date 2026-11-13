package com.kostya.createbrewery.config;

import net.neoforged.neoforge.common.ModConfigSpec;

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
    public static final int DEFAULT_PASS_OUT_AT = 90;
    public static final int DEFAULT_SECONDS_PER_SOBER_STEP = 1;
    public static final double DEFAULT_EFFECT_MULTIPLIER = 1.0;

    public static final ModConfigSpec.BooleanValue STAGGER;
    public static final ModConfigSpec.BooleanValue VOMIT;
    public static final ModConfigSpec.BooleanValue HANGOVER;
    public static final ModConfigSpec.IntValue PASS_OUT_AT;
    public static final ModConfigSpec.IntValue SECONDS_PER_SOBER_STEP;
    public static final ModConfigSpec.DoubleValue EFFECT_MULTIPLIER;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Drunk mechanics").push("drunk");
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
        builder.comment("Drinks").push("drinks");
        EFFECT_MULTIPLIER = builder
                .comment("Multiplier for the duration of the effects drinks give (0 turns them off)")
                .translation("brewery.configuration.effectMultiplier")
                .defineInRange("effectMultiplier", DEFAULT_EFFECT_MULTIPLIER, 0.0, 4.0);
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
}
