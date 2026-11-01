package com.kostya.createbrewery.block;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineGuiLayoutTest {
    private static final Path JAVA = Path.of("src/main/java/com/kostya/createbrewery/block");

    @Test
    void barrelMenuKeepsMachineAndPlayerSlotsAligned() throws Exception {
        String menu = Files.readString(JAVA.resolve("BarrelMenu.java"));
        String screen = Files.readString(JAVA.resolve("BarrelScreen.java"));

        assertTrue(menu.contains("handler, 0, 44, 35"));
        assertTrue(menu.contains("handler, 1, 80, 35"));
        assertTrue(menu.contains("getOutput(), 0, 116, 35"));
        assertTrue(menu.contains("84 + row * 18"));
        assertTrue(menu.contains("142"));
        assertTrue(screen.contains("inventoryLabelY = 74"));
        assertTrue(screen.contains("menu.isReady() ? \"gui.brewery.barrel_ready\""));
        assertTrue(screen.contains("leftPos + 84, topPos + 57"));
        assertTrue(screen.contains("leftPos + 84, topPos + 73"));
        assertTrue(!screen.contains("gui.brewery.barrel_status"));
    }

    @Test
    void localizedButtonAndProgressLabelsStayCompact() throws Exception {
        String english = Files.readString(Path.of("src/main/resources/assets/brewery/lang/en_us.json"));
        String russian = Files.readString(Path.of("src/main/resources/assets/brewery/lang/ru_ru.json"));

        assertTrue(english.contains("\"gui.brewery.barrel_days\": \"Days: %s\""));
        assertTrue(russian.contains("\"gui.brewery.barrel_days\": \"Дни: %s\""));
        assertTrue(russian.contains("\"gui.brewery.distillery_load\": \"Загрузить\""));
    }

    @Test
    void distilleryMenuAndScreenUseCompactAlignedLayout() throws Exception {
        String menu = Files.readString(JAVA.resolve("DistilleryMenu.java"));
        String screen = Files.readString(JAVA.resolve("DistilleryScreen.java"));

        assertTrue(menu.contains("80, 35"));
        assertTrue(menu.contains("84 + row * SLOT_SIZE"));
        assertTrue(menu.contains("142"));
        assertTrue(screen.contains("inventoryLabelY = 74"));
        assertTrue(screen.contains("gui.brewery.distillery_status"));
        assertTrue(screen.contains("gui.brewery.distillery_stats"));
    }
}
