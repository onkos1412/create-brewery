package com.kostya.createbrewery.block;

import com.kostya.createbrewery.quality.StarLine;
import com.kostya.createbrewery.recipe.MixRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the kettle: ingredient slots, a brew progress bar, the heat source
 * and the water and wort tanks.
 */
public class KettleScreen extends AbstractContainerScreen<KettleMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("brewery", "textures/gui/kettle.png");

    private static final int BAR_X = 26;
    private static final int BAR_Y = 44;
    private static final int BAR_WIDTH = 72;
    private static final int BAR_HEIGHT = 6;

    private static final int TANK_Y = 18;
    private static final int TANK_HEIGHT = 46;
    private static final int WATER_X = 130;
    private static final int WORT_X = 150;

    private static final int DISPLAY_X = 12;
    private static final int DISPLAY_Y = 55;
    private static final int DISPLAY_WIDTH = 107;

    private static final int TRACK_COLOR = 0xFF1C211E;
    private static final int FILL_COLOR = 0xFFC97A1E;
    private static final int FILL_HIGHLIGHT = 0xFFF0B04A;

    public KettleScreen(KettleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = KettleMenu.Layout.WIDTH;
        this.imageHeight = KettleMenu.Layout.HEIGHT;
        this.inventoryLabelY = this.imageHeight - 92;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderGaugeTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int barX = leftPos + BAR_X;
        int barY = topPos + BAR_Y;
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, TRACK_COLOR);
        int fill = Mth.clamp(menu.getBrewProgress(), 0, menu.getBrewTime()) * (BAR_WIDTH - 2) / menu.getBrewTime();
        if (fill > 0) {
            graphics.fill(barX + 1, barY + 1, barX + 1 + fill, barY + BAR_HEIGHT - 1, FILL_COLOR);
            graphics.fill(barX + 1, barY + 1, barX + 1 + fill, barY + 2, FILL_HIGHLIGHT);
        }

        boolean heated = menu.getHeat() > KettleLogic.HEAT_NONE;
        MachineWidgets.flame(graphics, leftPos + 108, topPos + 22, heated);
        MachineWidgets.tank(graphics, leftPos + WATER_X, topPos + TANK_Y, TANK_HEIGHT,
                menu.getWaterAmount(), KettleBlockEntity.TANK_CAPACITY, MachineWidgets.WATER_COLORS);
        MachineWidgets.tank(graphics, leftPos + WORT_X, topPos + TANK_Y, TANK_HEIGHT,
                menu.getWortAmount(), KettleBlockEntity.TANK_CAPACITY, MachineWidgets.shades(menu.getWortColor()));

        // While brewing the display hints how close the mix is, never which drink it will be.
        int aroma = Mth.clamp(menu.getAroma(), MixRules.AROMA_WEAK, MixRules.AROMA_EXCELLENT);
        String status = !heated ? "gui.brewery.kettle_status_no_heat"
                : menu.getBrewProgress() > 0 ? "gui.brewery.kettle_aroma_" + aroma
                : "gui.brewery.kettle_status_idle";
        int statusColor = !heated ? MachineWidgets.BAD
                : menu.getBrewProgress() > 0 ? MachineWidgets.GOOD : MachineWidgets.IDLE;
        int textWidth = DISPLAY_WIDTH;
        if (menu.getWortAmount() > 0) {
            Component stars = StarLine.of(menu.getWortStars());
            int starsWidth = font.width(stars);
            graphics.drawString(font, stars, leftPos + DISPLAY_X + DISPLAY_WIDTH - starsWidth, topPos + DISPLAY_Y, 0xFFFFFF, false);
            textWidth -= starsWidth + 4;
        }
        MachineWidgets.text(graphics, font, Component.translatable(status), leftPos + DISPLAY_X, topPos + DISPLAY_Y,
                textWidth, statusColor);
    }

    /** Hovering a gauge shows its exact value. */
    private void renderGaugeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Component line = null;
        if (MachineWidgets.inside(mouseX, mouseY, leftPos + BAR_X, topPos + BAR_Y, BAR_WIDTH, BAR_HEIGHT)) {
            int percent = Mth.clamp(menu.getBrewProgress(), 0, menu.getBrewTime()) * 100 / menu.getBrewTime();
            line = Component.translatable("tooltip.brewery.kettle_progress", percent);
        } else if (MachineWidgets.inside(mouseX, mouseY, leftPos + WATER_X, topPos + TANK_Y,
                MachineWidgets.TANK_WIDTH, TANK_HEIGHT)) {
            line = Component.translatable("tooltip.brewery.kettle_water", menu.getWaterAmount());
        } else if (MachineWidgets.inside(mouseX, mouseY, leftPos + WORT_X, topPos + TANK_Y,
                MachineWidgets.TANK_WIDTH, TANK_HEIGHT)) {
            line = menu.getWortAmount() > 0
                    ? Component.translatable("tooltip.brewery.kettle_wort", menu.getWortAmount())
                            .append(" ").append(StarLine.of(menu.getWortStars()))
                    : Component.translatable("tooltip.brewery.kettle_wort", 0);
        }
        if (line != null) {
            graphics.renderTooltip(font, line, mouseX, mouseY);
        }
    }
}
