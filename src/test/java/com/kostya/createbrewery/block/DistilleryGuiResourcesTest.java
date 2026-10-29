package com.kostya.createbrewery.block;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DistilleryGuiResourcesTest {
    @Test
    void distilleryUsesItsOwnGuiTextureAndLoadLabel() throws Exception {
        Path distilleryTexture = Path.of("src/main/resources/assets/brewery/textures/gui/distillery.png");
        Path kettleTexture = Path.of("src/main/resources/assets/brewery/textures/gui/kettle.png");
        String screen = Files.readString(Path.of("src/main/java/com/kostya/createbrewery/block/DistilleryScreen.java"));

        assertTrue(Files.exists(distilleryTexture));
        var image = ImageIO.read(distilleryTexture.toFile());
        assertTrue(image.getWidth() == 176 && image.getHeight() == 166);
        assertTrue(Files.mismatch(distilleryTexture, kettleTexture) != -1,
                "distillery GUI must not be a copy of the kettle GUI");
        assertTrue(screen.contains("gui.brewery.distillery_load"));
        assertTrue(screen.contains("gui.brewery.distillery_stats"));
    }
}
