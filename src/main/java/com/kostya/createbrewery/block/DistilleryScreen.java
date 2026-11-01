package com.kostya.createbrewery.block;

import com.kostya.createbrewery.distillery.DistilleryLogic;
import com.kostya.createbrewery.distillery.DistillingRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class DistilleryScreen extends AbstractContainerScreen<DistilleryMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("brewery", "textures/gui/distillery.png");

    private static final int TANK_X = 20;
    private static final int TANK_Y = 18;
    private static final int TANK_HEIGHT = 34;
    private static final int BUTTON_X = 8;
    private static final int BUTTON_Y = 56;

    public DistilleryScreen(DistilleryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 74;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int progress = Mth.clamp(menu.getProgress(), 0, DistilleryLogic.IDEAL_TICKS);
        MachineWidgets.tank(graphics, leftPos + TANK_X, topPos + TANK_Y, TANK_HEIGHT,
                menu.getWort(), DistilleryLogic.WORT_REQUIRED, MachineWidgets.WORT_COLORS);
        MachineWidgets.arrow(graphics, leftPos + 50, topPos + 36, progress, DistilleryLogic.IDEAL_TICKS);
        MachineWidgets.flame(graphics, leftPos + 36, topPos + 37, menu.getHeat() >= DistillingRules.MIN_HEAT);
        MachineWidgets.button(graphics, font, leftPos + BUTTON_X, topPos + BUTTON_Y,
                Component.translatable("gui.brewery.distillery_load"), overButton(mouseX, mouseY));
        graphics.drawString(font, Component.translatable(statusKey()),
                leftPos + 82, topPos + 57, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.brewery.distillery_stats",
                        menu.getHeat(), menu.getRpm(), progress),
                leftPos + 82, topPos + 73, 0x404040, false);
    }

    private String statusKey() {
        if (menu.getSlot(0).hasItem()) {
            return "gui.brewery.distillery_status_done";
        }
        if (menu.getWort() < DistilleryLogic.WORT_REQUIRED) {
            return "gui.brewery.distillery_status_no_wort";
        }
        if (menu.getHeat() < DistillingRules.MIN_HEAT) {
            return "gui.brewery.distillery_status_no_heat";
        }
        return menu.getRpm() < DistillingRules.MIN_RPM
                ? "gui.brewery.distillery_status_no_rotation"
                : "gui.brewery.distillery_status_running";
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
        if (MachineWidgets.inside(mouseX, mouseY, leftPos + TANK_X, topPos + TANK_Y,
                MachineWidgets.TANK_WIDTH, TANK_HEIGHT)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.brewery.kettle_wort", menu.getWort()),
                    mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }
}
