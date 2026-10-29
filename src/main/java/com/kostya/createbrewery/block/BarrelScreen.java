package com.kostya.createbrewery.block;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class BarrelScreen extends AbstractContainerScreen<BarrelMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("brewery", "textures/gui/barrel.png");

    public BarrelScreen(BarrelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 74;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        graphics.fill(leftPos + 8, topPos + 56, leftPos + 72, topPos + 70, 0xFF806040);
        graphics.drawString(font, Component.translatable("gui.brewery.barrel_start"),
                leftPos + 11, topPos + 59, 0xFFFFFF, false);
        int days = menu.getDays();
        graphics.drawString(font, Component.translatable("gui.brewery.barrel_days", days),
                leftPos + 84, topPos + 73, 0x404040, false);
        graphics.drawString(font, Component.translatable(
                        days >= 3 ? "gui.brewery.barrel_ready" : "gui.brewery.barrel_waiting"),
                leftPos + 84, topPos + 57, 0x404040, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftPos + 8 && mouseX < leftPos + 72
                && mouseY >= topPos + 56 && mouseY < topPos + 70) {
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
