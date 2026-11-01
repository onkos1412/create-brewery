package com.kostya.createbrewery.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Keeps every model at the game's own pixel size, so no face shows a squashed or stretched texture. */
class ModelTexelTest {
    private static final Path MODELS = Path.of("src/main/resources/assets/brewery/models/block");
    private static final double EPSILON = 0.001;

    @Test
    void facesKeepOnePixelPerUnit() throws IOException {
        List<String> stretched = new ArrayList<>();
        try (Stream<Path> files = Files.list(MODELS)) {
            for (Path file : files.toList()) {
                JsonObject model = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (model.has("elements")) {
                    model.getAsJsonArray("elements").forEach(element -> check(file, element.getAsJsonObject(), stretched));
                }
            }
        }
        assertEquals(List.of(), stretched);
    }

    @Test
    void modelsReferOnlyToTexturesTheyDefine() throws IOException {
        try (Stream<Path> files = Files.list(MODELS)) {
            for (Path file : files.toList()) {
                JsonObject model = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!model.has("elements")) {
                    continue;
                }
                JsonObject textures = model.getAsJsonObject("textures");
                model.getAsJsonArray("elements").forEach(element -> element.getAsJsonObject().getAsJsonObject("faces")
                        .asMap().values().forEach(face -> {
                            String name = face.getAsJsonObject().get("texture").getAsString().substring(1);
                            assertTrue(textures.has(name), file.getFileName() + " has no texture " + name);
                        }));
            }
        }
    }

    /** A uv worked out from a position outside the block would run off the texture. */
    @Test
    void facesWithoutAUvStayInsideTheBlock() throws IOException {
        try (Stream<Path> files = Files.list(MODELS)) {
            for (Path file : files.toList()) {
                JsonObject model = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!model.has("elements")) {
                    continue;
                }
                model.getAsJsonArray("elements").forEach(item -> {
                    JsonObject element = item.getAsJsonObject();
                    boolean inside = true;
                    for (var bound : List.of(element.getAsJsonArray("from"), element.getAsJsonArray("to"))) {
                        for (var value : bound) {
                            inside &= value.getAsDouble() >= 0 && value.getAsDouble() <= 16;
                        }
                    }
                    if (!inside) {
                        // The small tap on a barrel's head pokes out by a pixel, on axes its faces do not show.
                        element.getAsJsonObject("faces").asMap().forEach((name, face) ->
                                assertTrue(face.getAsJsonObject().has("uv") || isTap(element),
                                        file.getFileName() + " " + name + " at " + element.get("from")));
                    }
                });
            }
        }
    }

    private static boolean isTap(JsonObject element) {
        return element.getAsJsonArray("from").get(2).getAsDouble() == -1;
    }

    private static void check(Path file, JsonObject element, List<String> stretched) {
        var from = element.getAsJsonArray("from");
        var to = element.getAsJsonArray("to");
        double x = to.get(0).getAsDouble() - from.get(0).getAsDouble();
        double y = to.get(1).getAsDouble() - from.get(1).getAsDouble();
        double z = to.get(2).getAsDouble() - from.get(2).getAsDouble();
        element.getAsJsonObject("faces").asMap().forEach((name, value) -> {
            JsonObject face = value.getAsJsonObject();
            if (!face.has("uv")) {
                return;
            }
            var uv = face.getAsJsonArray("uv");
            double width = Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble());
            double height = Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble());
            double faceWidth = name.equals("east") || name.equals("west") ? z : x;
            double faceHeight = name.equals("up") || name.equals("down") ? z : y;
            if (Math.abs(width - faceWidth) > EPSILON || Math.abs(height - faceHeight) > EPSILON) {
                stretched.add(file.getFileName() + " " + name + " " + faceWidth + "x" + faceHeight
                        + " shows " + width + "x" + height);
            }
        });
    }
}
