package com.kostya.createbrewery.gametest;

import com.kostya.createbrewery.CreateBrewery;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The GameTest server crashes when no game tests are registered, so this one proves the mod
 * boots until the ported game tests take over.
 */
@GameTestHolder(CreateBrewery.MOD_ID)
@PrefixGameTestTemplate(false)
public class BootGameTests {

    @GameTest(template = "empty")
    public static void buildBoots(GameTestHelper helper) {
        helper.succeed();
    }
}
