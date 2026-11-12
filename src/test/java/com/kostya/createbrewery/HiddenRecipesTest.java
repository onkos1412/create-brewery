package com.kostya.createbrewery;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Players find recipes by experimenting, so nothing in the game or the docs may spell them out. */
class HiddenRecipesTest {
    private static final Path MAIN = Path.of("src/main/java/com/kostya/createbrewery");

    @Test
    void noRecipeViewerIsShipped() throws IOException {
        Path jei = MAIN.resolve("compat/jei");
        if (Files.exists(jei)) {
            try (Stream<Path> files = Files.list(jei)) {
                assertTrue(files.findAny().isEmpty(), "JEI would list every recipe");
            }
        }
    }

    @Test
    void readmeDoesNotListTheDrinks() throws IOException {
        String readme = Files.readString(Path.of("README.md"));
        assertFalse(readme.contains("Виски (32)"));
        assertFalse(readme.contains("Выдержанный виски"));
    }

    @Test
    void agingIsNotShownInTooltips() throws IOException {
        String ru = Files.readString(Path.of("src/main/resources/assets/brewery/lang/ru_ru.json"));
        assertFalse(ru.contains("tooltip.brewery.distillate_aging"));
        assertFalse(ru.contains("tooltip.brewery.distillate_pour"));
    }

    @Test
    void theStillDoesNotShowItsIdealSpeed() throws IOException {
        String screen = Files.readString(MAIN.resolve("block/DistilleryScreen.java"));
        assertFalse(screen.contains("getIdealRpm"));
        String menu = Files.readString(MAIN.resolve("block/DistilleryMenu.java"));
        assertFalse(menu.contains("getIdealRpm"));
    }
}
