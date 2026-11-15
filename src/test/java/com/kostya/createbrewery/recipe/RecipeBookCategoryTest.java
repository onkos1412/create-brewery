package com.kostya.createbrewery.recipe;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeBookCategoryTest {
    /** Forge's registerRecipeCategoryFinder is a client mixin on Fabric, which has no event for it. */
    @Test
    void customBrewingTypeHasClientRecipeBookCategoryLookup() throws Exception {
        String mixin = Files.readString(
                Path.of("src/main/java/com/kostya/createbrewery/mixin/client/ClientRecipeBookMixin.java"));
        int lookup = mixin.indexOf("recipe.getType() == ModRecipeTypes.BREWING.get()");

        assertTrue(mixin.contains("@Mixin(ClientRecipeBook.class)"));
        assertTrue(lookup >= 0);
        assertTrue(mixin.indexOf("cir.setReturnValue(RecipeBookCategories.UNKNOWN)", lookup) > lookup);
        assertTrue(Files.readString(Path.of("src/main/resources/brewery.mixins.json"))
                .contains("\"client.ClientRecipeBookMixin\""));
    }
}
