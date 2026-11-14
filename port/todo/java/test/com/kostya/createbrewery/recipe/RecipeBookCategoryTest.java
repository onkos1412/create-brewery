package com.kostya.createbrewery.recipe;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeBookCategoryTest {
    @Test
    void customBrewingTypeHasClientRecipeBookCategoryLookup() throws Exception {
        String setup = Files.readString(Path.of("src/main/java/com/kostya/createbrewery/client/ModClientSetup.java"));
        int registration = setup.indexOf("registerRecipeCategoryFinder(ModRecipeTypes.BREWING.get()");

        assertTrue(registration >= 0);
        assertTrue(setup.indexOf("recipe -> RecipeBookCategories.UNKNOWN", registration) > registration);
    }
}
