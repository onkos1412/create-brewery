package com.kostya.createbrewery.block;

import com.kostya.createbrewery.distillery.DistilleryLogic;
import com.kostya.createbrewery.distillery.DistillingRules;
import com.kostya.createbrewery.quality.StarLine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class DistilleryScreen extends AbstractContainerScreen<DistilleryMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("brewery", "textures/gui/distillery.png");

    private static final int TANK_X = 12;
    private static final int TANK_Y = 18;
    private static final int TANK_HEIGHT = 34;
    private static final int BUTTON_X = 8;
    private static final int BUTTON_Y = 56;
    private static final int DISPLAY_X = 92;
    private static final int DISPLAY_WIDTH = 73;
    private static final int GAUGE_X = 76;
    private static final int GAUGE_Y = 58;
    private static final int GAUGE_WIDTH = 92;
    private static final int GAUGE_HEIGHT = 10;
    private static final int MAX_RPM = 256;

    public DistilleryScreen(DistilleryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 74;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int progress = Mth.clamp(menu.getProgress(), 0, menu.getIdealTicks());
        MachineWidgets.tank(graphics, leftPos + TANK_X, topPos + TANK_Y, TANK_HEIGHT,
                menu.getWort(), DistilleryLogic.WORT_REQUIRED, MachineWidgets.shades(menu.getWortColor()));
        MachineWidgets.arrow(graphics, leftPos + 42, topPos + 36, progress, menu.getIdealTicks());
        MachineWidgets.flame(graphics, leftPos + 28, topPos + 37, menu.getHeat() >= DistillingRules.MIN_HEAT);
        MachineWidgets.button(graphics, font, leftPos + BUTTON_X, topPos + BUTTON_Y,
                Component.translatable("gui.brewery.distillery_load"), overButton(mouseX, mouseY));
        String status = statusKey();
        MachineWidgets.text(graphics, font, Component.translatable(status), leftPos + DISPLAY_X, topPos + 20, DISPLAY_WIDTH,
                status.endsWith("running") || status.endsWith("done") ? MachineWidgets.GOOD : MachineWidgets.BAD);
        MachineWidgets.text(graphics, font, Component.translatable("gui.brewery.distillery_stats", menu.getRpm()),
                leftPos + DISPLAY_X, topPos + 31, DISPLAY_WIDTH, MachineWidgets.IDLE);
        if (menu.getWort() > 0) {
            graphics.drawString(font, StarLine.of(menu.getWortStars()), leftPos + DISPLAY_X, topPos + 42, 0xFFFFFF, false);
        }
        MachineWidgets.speedGauge(graphics, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT,
                menu.getRpm(), MAX_RPM, MachineWidgets.IDLE);
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
            Component line = Component.translatable("tooltip.brewery.kettle_wort", menu.getWort());
            if (menu.getWort() > 0) {
                line = line.copy().append(" ").append(StarLine.of(menu.getWortStars()));
            }
            graphics.renderTooltip(font, line, mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }
}
