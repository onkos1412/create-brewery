package com.kostya.createbrewery.block;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrelGuiResourcesTest {
    @Test
    void barrelUsesItsOwnGuiTextureAndLabels() throws Exception {
        Path barrelTexture = Path.of("src/main/resources/assets/brewery/textures/gui/barrel.png");
        Path kettleTexture = Path.of("src/main/resources/assets/brewery/textures/gui/kettle.png");
        String screen = Files.readString(Path.of("src/main/java/com/kostya/createbrewery/block/BarrelScreen.java"));

        assertTrue(Files.exists(barrelTexture));
        var image = ImageIO.read(barrelTexture.toFile());
        // GuiGraphics.blit assumes a 256x256 sheet; any other size stretches the background.
        assertTrue(image.getWidth() == 256 && image.getHeight() == 256);
        assertTrue(Files.mismatch(barrelTexture, kettleTexture) != -1,
                "barrel GUI must not be a copy of the kettle GUI");
        assertTrue(screen.contains("gui.brewery.barrel_start"));
        assertTrue(screen.contains("gui.brewery.barrel_days"));
        assertTrue(screen.contains("gui.brewery.barrel_ready"));
    }
}
