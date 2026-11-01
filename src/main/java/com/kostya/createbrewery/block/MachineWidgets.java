package com.kostya.createbrewery.block;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Pieces shared by the machine screens, drawn from {@code textures/gui/machine_parts.png}. */
final class MachineWidgets {
    private static final ResourceLocation PARTS =
            ResourceLocation.fromNamespaceAndPath("brewery", "textures/gui/machine_parts.png");

    static final int TANK_WIDTH = 10;
    static final int BUTTON_WIDTH = 64;
    static final int BUTTON_HEIGHT = 14;

    static final int[] WATER_COLORS = {0xFF2F6FD0, 0xFF4A8CE8, 0xFF8FC0F8};

    private MachineWidgets() {
    }

    /** A tank's edge, body and meniscus colours for a liquid's colour. */
    static int[] shades(int rgb) {
        return new int[] {scaled(rgb, 0.8f), 0xFF000000 | rgb, scaled(rgb, 1.3f)};
    }

    private static int scaled(int rgb, float factor) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((rgb & 0xFF) * factor));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Fills a tank frame from the bottom: a shaded column with a bright meniscus. */
    static void tank(GuiGraphics graphics, int x, int y, int height, int amount, int capacity, int[] colors) {
        int level = Mth.clamp(amount, 0, capacity) * height / capacity;
        if (amount > 0) {
            level = Math.max(1, level);
        }
        if (level <= 0) {
            return;
        }
        int top = y + height - level;
        graphics.fill(x, top, x + 2, y + height, colors[0]);
        graphics.fill(x + 2, top, x + TANK_WIDTH - 2, y + height, colors[1]);
        graphics.fill(x + TANK_WIDTH - 2, top, x + TANK_WIDTH, y + height, colors[0]);
        graphics.fill(x, top, x + TANK_WIDTH, top + 1, colors[2]);
    }

    /** Lights a 22x15 arrow from the left over the frame's dim one. */
    static void arrow(GuiGraphics graphics, int x, int y, int progress, int total) {
        arrow(graphics, x, y, progress, total, 0);
    }

    /** The same arrow without its first {@code skip} columns, for a tail hidden under a slot frame. */
    static void arrow(GuiGraphics graphics, int x, int y, int progress, int total, int skip) {
        int width = Mth.clamp(progress, 0, total) * (22 - skip) / total;
        if (width > 0) {
            graphics.blit(PARTS, x, y, skip, 0, width, 15);
        }
    }

    static void flame(GuiGraphics graphics, int x, int y, boolean lit) {
        graphics.blit(PARTS, x, y, 70, lit ? 0 : 16, 13, 13);
    }

    static void button(GuiGraphics graphics, Font font, int x, int y, Component label, boolean hovered) {
        graphics.blit(PARTS, x, y, 0, hovered ? 30 : 16, BUTTON_WIDTH, BUTTON_HEIGHT);
        graphics.drawCenteredString(font, label, x + BUTTON_WIDTH / 2, y + 3, 0xFFFFFF);
    }
}
