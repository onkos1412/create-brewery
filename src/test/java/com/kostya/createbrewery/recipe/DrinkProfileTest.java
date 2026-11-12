package com.kostya.createbrewery.recipe;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrinkProfileTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/brewery/recipe");

    private static DrinkProfile read(String recipe) throws Exception {
        var json = JsonParser.parseString(Files.readString(RECIPES.resolve(recipe + ".json"))).getAsJsonObject();
        return DrinkProfile.CODEC.parse(JsonOps.INSTANCE, json.get("drink")).getOrThrow();
    }

    @Test
    void shippedRecipesDescribeTheirRoads() throws Exception {
        assertEquals(DrinkProfile.BEER, read("beer"));
        assertTrue(read("cider").canAge());
        assertTrue(read("cane_mash").canAge());

        DrinkProfile mash = read("potato_mash");
        assertEquals("vodka", mash.distilled());
        assertFalse(mash.canAge(), "vodka is bottled straight from the still");

        assertTrue(read("wine").agesInTheBottle());
        assertTrue(read("mead").agesInTheBottle());
        assertFalse(read("beer").agesInTheBottle());
    }

    @Test
    void everyDrinkHasANameInBothLanguages() throws Exception {
        for (String language : List.of("en_us", "ru_ru")) {
            String lang = Files.readString(Path.of("src/main/resources/assets/brewery/lang/" + language + ".json"));
            for (String recipe : List.of("beer", "cider", "potato_mash", "cane_mash", "wine", "mead")) {
                DrinkProfile drink = read(recipe);
                for (String id : List.of(drink.id(), drink.distilled(), drink.aged())) {
                    assertTrue(id.isEmpty() || lang.contains("\"drink.brewery." + id + "\""),
                            language + " has no name for " + id);
                }
            }
        }
    }

    @Test
    void settingsTheMachinesDivideByMustBePositive() {
        for (String field : List.of("agingDays", "distillTicks", "idealRpm")) {
            var json = JsonParser.parseString("{\"id\": \"kvass\", \"alcohol\": 5, \"" + field + "\": 0}");
            assertTrue(DrinkProfile.CODEC.parse(JsonOps.INSTANCE, json).isError(), field + " 0 was accepted");
        }
    }

    @Test
    void colorsReadFromRecipesAndDifferBetweenDrinks() throws Exception {
        assertEquals(DrinkProfile.BEER.color(), read("beer").color());
        var seen = new java.util.HashSet<Integer>();
        for (String recipe : List.of("beer", "cider", "potato_mash", "cane_mash", "wine", "mead")) {
            assertTrue(seen.add(read(recipe).color()), recipe + " shares a colour");
        }
        var bad = JsonParser.parseString("{\"id\": \"kvass\", \"alcohol\": 5, \"color\": \"red\"}");
        assertTrue(DrinkProfile.CODEC.parse(JsonOps.INSTANCE, bad).isError());
    }

    @Test
    void muddyCannotGoFurther() {
        assertFalse(DrinkProfile.MUDDY.canDistill());
        assertFalse(DrinkProfile.MUDDY.canAge());
        assertEquals(0x5B4A32, DrinkProfile.MUDDY.color());
    }

    @Test
    void omittedStagesMeanTheRoadEnds() {
        var json = JsonParser.parseString("{\"id\": \"kvass\", \"alcohol\": 5}");
        DrinkProfile kvass = DrinkProfile.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertFalse(kvass.canDistill());
        assertFalse(kvass.canAge());
    }

    @Test
    void shippedRecipesCarryTheirEffects() throws Exception {
        assertEquals(List.of(new DrinkEffect(ResourceLocation.parse("minecraft:speed"), 60, 0)),
                read("pulque").effects().get("tequila"));
        assertEquals(List.of(new DrinkEffect(ResourceLocation.parse("minecraft:speed"), 120, 1)),
                read("pulque").effects().get("aged_tequila"));
        assertEquals(2, read("wormwood").effects().get("absinthe").size());
        assertEquals(2, read("glow").effects().get("firefly_liqueur").size());
        assertTrue(read("beer").effects().isEmpty());
    }

    @Test
    void effectsOfAnotherStageAreRejected() {
        var json = JsonParser.parseString("{\"id\": \"kvass\", \"alcohol\": 5, "
                + "\"effects\": {\"tequila\": [{\"effect\": \"minecraft:speed\", \"seconds\": 60}]}}");
        assertTrue(DrinkProfile.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }

    @Test
    void effectsLastAtLeastASecond() {
        var json = JsonParser.parseString("{\"id\": \"kvass\", \"alcohol\": 5, "
                + "\"effects\": {\"kvass\": [{\"effect\": \"minecraft:speed\", \"seconds\": 0}]}}");
        assertTrue(DrinkProfile.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }
}
