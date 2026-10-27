package com.kostya.createbrewery.block;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the kettle: ingredient slots, the output slot and a small progress
 * bar showing how far the current brew has come.
 */
public class KettleScreen extends AbstractContainerScreen<KettleMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("brewery", "textures/gui/kettle.png");

    /** Progress bar frame inside the GUI texture. */
    private static final int BAR_X = 152;
    private static final int BAR_Y = 20;
    private static final int BAR_WIDTH = 8;
    private static final int BAR_HEIGHT = 28;
    /** Drawable interior of the frame, one pixel in from each side. */
    private static final int BAR_INNER_HEIGHT = BAR_HEIGHT - 2;

    /** Brew time of the reference recipe; the bar fills against this. */
    private static final int FULL_BREW_TICKS = 200;

    private static final int TRACK_COLOR = 0xFF302C26;
    private static final int FILL_COLOR = 0xFFC97A1E;
    private static final int FILL_HIGHLIGHT = 0xFFF0B04A;

    public KettleScreen(KettleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = KettleMenu.Layout.WIDTH;
        this.imageHeight = KettleMenu.Layout.HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderProgressTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int innerX = leftPos + BAR_X + 1;
        int innerY = topPos + BAR_Y + 1;
        int innerRight = innerX + BAR_WIDTH - 2;

        // Repaint the track so it stays readable whatever the frame art looks like.
        graphics.fill(innerX, innerY, innerRight, innerY + BAR_INNER_HEIGHT, TRACK_COLOR);

        int progress = Mth.clamp(menu.getBrewProgress(), 0, FULL_BREW_TICKS);
        int fillHeight = progress * BAR_INNER_HEIGHT / FULL_BREW_TICKS;
        if (fillHeight > 0) {
            int fillTop = innerY + BAR_INNER_HEIGHT - fillHeight;
            graphics.fill(innerX, fillTop, innerRight, innerY + BAR_INNER_HEIGHT, FILL_COLOR);
            // One bright row on top sells the fill as a liquid column.
            graphics.fill(innerX, fillTop, innerRight, fillTop + 1, FILL_HIGHLIGHT);
        }
    }

    /** Hovering the bar shows the exact progress and what is inside the kettle. */
    private void renderProgressTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int barLeft = leftPos + BAR_X - 1;
        int barTop = topPos + BAR_Y - 1;
        int barRight = barLeft + BAR_WIDTH + 2;
        int barBottom = barTop + BAR_HEIGHT + 2;
        if (mouseX < barLeft || mouseX >= barRight || mouseY < barTop || mouseY >= barBottom) {
            return;
        }

        int percent = Mth.clamp(menu.getBrewProgress(), 0, FULL_BREW_TICKS) * 100 / FULL_BREW_TICKS;
        graphics.renderComponentTooltip(font,
                java.util.List.of(
                        Component.translatable("tooltip.brewery.kettle_progress", percent),
                        Component.translatable("tooltip.brewery.kettle_water", menu.getWaterAmount()),
                        Component.translatable("tooltip.brewery.kettle_wort", menu.getWortAmount())),
                mouseX, mouseY);
    }
}
