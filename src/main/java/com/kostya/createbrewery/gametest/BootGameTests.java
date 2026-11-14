package com.kostya.createbrewery.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The GameTest server fails when no game tests are registered, so this one proves the mod
 * boots until the ported game tests take over. Registered through the {@code fabric-gametest}
 * entrypoint in fabric.mod.json.
 */
public class BootGameTests implements FabricGameTest {

    @GameTest(template = "brewery:empty")
    public void buildBoots(GameTestHelper helper) {
        helper.succeed();
    }
}
