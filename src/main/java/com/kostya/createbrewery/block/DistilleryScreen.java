package com.kostya.createbrewery.block;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class DistilleryScreen extends AbstractContainerScreen<DistilleryMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("brewery", "textures/gui/distillery.png");

    public DistilleryScreen(DistilleryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int progress = Mth.clamp(menu.getProgress(), 0, 200);
        int height = progress * 24 / 200;
        graphics.fill(leftPos + 145, topPos + 48 - height, leftPos + 152, topPos + 48, 0xFFC97A1E);
        graphics.fill(leftPos + 8, topPos + 56, leftPos + 72, topPos + 70, 0xFF806040);
        graphics.drawString(font, Component.translatable("gui.brewery.distillery_load"),
                leftPos + 11, topPos + 59, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.brewery.distillery_stats",
                        menu.getHeat(), menu.getRpm(), progress),
                leftPos + 82, topPos + 59, 0x404040, false);
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
