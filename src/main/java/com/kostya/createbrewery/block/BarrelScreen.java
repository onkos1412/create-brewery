package com.kostya.createbrewery.block;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class BarrelScreen extends AbstractContainerScreen<BarrelMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("brewery", "textures/gui/barrel.png");

    private static final int BUTTON_X = 8;
    private static final int BUTTON_Y = 56;

    public BarrelScreen(BarrelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 74;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        MachineWidgets.arrow(graphics, leftPos + 98, topPos + 36, menu.isReady() ? 100 : menu.getPercent(), 100);
        MachineWidgets.button(graphics, font, leftPos + BUTTON_X, topPos + BUTTON_Y,
                Component.translatable("gui.brewery.barrel_start"), overButton(mouseX, mouseY));
        graphics.drawString(font, Component.translatable("gui.brewery.barrel_days", menu.getDays()),
                leftPos + 84, topPos + 73, 0x404040, false);
        String status = menu.isReady() ? "gui.brewery.barrel_ready"
                : menu.isAging() ? "gui.brewery.barrel_waiting" : "gui.brewery.barrel_idle";
        graphics.drawString(font, Component.translatable(status),
                leftPos + 84, topPos + 57, 0x404040, false);
    }

    private boolean overButton(double mouseX, double mouseY) {
        return MachineWidgets.inside(mouseX, mouseY, leftPos + BUTTON_X, topPos + BUTTON_Y,
                MachineWidgets.BUTTON_WIDTH, MachineWidgets.BUTTON_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overButton(mouseX, mouseY)) {
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
