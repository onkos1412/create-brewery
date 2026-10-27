package com.kostya.createbrewery.block;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the kettle: ingredient slots, the output slot and a small progress
 * bar showing how far the current brew has come.
 */
public class KettleScreen extends AbstractContainerScreen<KettleMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("brewery", "textures/gui/kettle.png");

    public KettleScreen(KettleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = KettleMenu.Layout.WIDTH;
        this.imageHeight = KettleMenu.Layout.HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int progressHeight = (int) (28 * (Math.min(menu.getBrewProgress(), 200) / 200f));
        if (progressHeight > 0) {
            graphics.blit(TEXTURE, leftPos + 152, topPos + 20 + 28 - progressHeight,
                    176, 28 - progressHeight, 8, progressHeight);
        }
    }
}
