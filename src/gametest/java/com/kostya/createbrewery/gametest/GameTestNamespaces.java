package com.kostya.createbrewery.gametest;

import com.kostya.createbrewery.CreateBrewery;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fabric's counterpart of Forge's {@code forge.enabledGameTestNamespaces}: Create Fabric registers its own
 * game tests through the same {@code fabric-gametest} entrypoint, so the game-test run keeps only the tests
 * whose structure lies in one of the namespaces named by the {@value #PROPERTY} system property.
 * Without the property (every normal launch) nothing is touched.
 */
public final class GameTestNamespaces implements DedicatedServerModInitializer {
    public static final String PROPERTY = "brewery.gametest.namespaces";

    /**
     * Drops the other mods' game tests. A {@code server} entrypoint, so it runs after every {@code main} one,
     * including Fabric API's game-test initializer that registers the test classes. Game tests only run on a
     * dedicated server.
     */
    @Override
    public void onInitializeServer() {
        String value = System.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            return;
        }
        Set<String> namespaces = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        int before = GameTestRegistry.getAllTestFunctions().size();
        GameTestRegistry.getAllTestFunctions().removeIf(test -> !namespaces.contains(namespace(test)));
        CreateBrewery.LOGGER.info("Game tests limited to namespaces {}: {} of {} kept", namespaces,
                GameTestRegistry.getAllTestFunctions().size(), before);
    }

    private static String namespace(TestFunction test) {
        String structure = test.getStructureName();
        int colon = structure.indexOf(':');
        return colon < 0 ? "minecraft" : structure.substring(0, colon);
    }
}
