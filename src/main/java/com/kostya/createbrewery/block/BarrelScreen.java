package com.kostya.createbrewery.block;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class BarrelScreen extends AbstractContainerScreen<BarrelMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("brewery", "textures/gui/barrel.png");

    private static final int BUTTON_X = 8;
    private static final int BUTTON_Y = 56;
    private static final int LOCKED_SLOT = 0xFF2E2C26;
    private static final int DISPLAY_X = 80;
    private static final int DISPLAY_Y = 58;
    private static final int DISPLAY_WIDTH = 85;
    private static final int MACHINE_SLOTS = 10;

    public BarrelScreen(BarrelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 74;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // Stacks this barrel is too small for: more barrels beside it open them up.
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, LOCKED_SLOT);
            }
        }
        MachineWidgets.arrow(graphics, leftPos + 97, topPos + 36, menu.isReady() ? 100 : menu.getPercent(), 100, 5);
        MachineWidgets.button(graphics, font, leftPos + BUTTON_X, topPos + BUTTON_Y,
                Component.translatable("gui.brewery.barrel_start"), overButton(mouseX, mouseY));
        String status = menu.isReady() ? "gui.brewery.barrel_ready"
                : menu.isAging() ? "gui.brewery.barrel_waiting" : "gui.brewery.barrel_idle";
        int statusColor = menu.isReady() ? MachineWidgets.GOOD : menu.isAging() ? MachineWidgets.WARN : MachineWidgets.IDLE;
        Component days = Component.translatable("gui.brewery.barrel_days", menu.getDays());
        int daysWidth = font.width(days);
        graphics.drawString(font, days, leftPos + DISPLAY_X + DISPLAY_WIDTH - daysWidth, topPos + DISPLAY_Y, MachineWidgets.IDLE, false);
        MachineWidgets.text(graphics, font, Component.translatable(status), leftPos + DISPLAY_X, topPos + DISPLAY_Y,
                DISPLAY_WIDTH - daysWidth - 4, statusColor);
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
        renderHints(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** An empty slot says what it is for, and a closed one how to open it. */
    private void renderHints(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        if (menu.isAging() && MachineWidgets.inside(mouseX, mouseY, leftPos + 92, topPos + 36, 22, 15)) {
            graphics.renderTooltip(font, Component.translatable("gui.brewery.barrel_progress", menu.getPercent()), mouseX, mouseY);
        }
        for (int index = 0; index < MACHINE_SLOTS; index++) {
            Slot slot = menu.slots.get(index);
            if (!slot.hasItem() && MachineWidgets.inside(mouseX, mouseY, leftPos + slot.x, topPos + slot.y, 16, 16)) {
                graphics.renderTooltip(font, Component.translatable(hintKey(index, slot)), mouseX, mouseY);
            }
        }
    }

    private static String hintKey(int index, Slot slot) {
        if (!slot.isActive()) {
            return "gui.brewery.barrel_slot_locked";
        }
        return switch (index) {
            case 1 -> "gui.brewery.barrel_slot_wood";
            case 3 -> "gui.brewery.barrel_slot_bottles";
            case 2, 7, 8, 9 -> "gui.brewery.barrel_slot_drink";
            default -> "gui.brewery.barrel_slot_batch";
        };
    }
}
