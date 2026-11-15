package com.kostya.createbrewery.client;

import com.kostya.createbrewery.journal.BarrelRecord;
import com.kostya.createbrewery.journal.DrinkJournal;
import com.kostya.createbrewery.journal.KettleRecord;
import com.kostya.createbrewery.journal.StillRecord;
import com.kostya.createbrewery.network.OpenBrewerBookPayload;
import com.kostya.createbrewery.quality.StarLine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

/**
 * The brewer's book: a title spread, then a two-page spread per drink with the owner's best kettle,
 * still and barrel results. A stranger sees only scribbles.
 */
@Environment(EnvType.CLIENT)
public class BrewerBookScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("brewery", "textures/gui/brewer_book.png");
    private static final Style SCRIBBLE = Style.EMPTY.withFont(new ResourceLocation("alt"));

    static final int TEX_WIDTH = 320;
    static final int TEX_HEIGHT = 200;
    static final int BOOK_WIDTH = 292;
    static final int BOOK_HEIGHT = 180;

    /** Writing area of each page, relative to the book's top-left corner. */
    static final int LEFT_X = 18;
    static final int RIGHT_X = 154;
    static final int PAGE_WIDTH = 120;
    static final int TOP_Y = 16;
    static final int SECTION_Y = 34;
    static final int BOTTOM_Y = 150;
    static final int LINE = 10;
    static final int ICON = 18;
    static final int ICONS_PER_ROW = PAGE_WIDTH / ICON;

    static final int ARROW_WIDTH = 18;
    static final int ARROW_HEIGHT = 10;
    static final int ARROW_Y = 156;
    static final int PREV_X = LEFT_X;
    static final int NEXT_X = RIGHT_X + PAGE_WIDTH - ARROW_WIDTH;
    /** Arrow sprites in the texture at v 184: next at u 0, previous at u 40, each hovered 20 to the right. */
    private static final int ARROW_V = 184;
    private static final int NEXT_U = 0;
    private static final int PREV_U = 40;

    private static final int INK = 0xFF3B2A1A;
    private static final int INK_TITLE = 0xFF5A2A1C;
    private static final int INK_HEAD = 0xFF6B3E1E;
    private static final int INK_FAINT = 0xFF8A7350;
    private static final int INK_RED = 0xFF7A2A1C;

    private final OpenBrewerBookPayload book;
    private final int spreads;
    private int spread;
    private int left;
    private int top;
    /** Lines of scribbles per page, made from a fixed seed so they never flicker. */
    private final List<List<Component>> scribbles = new ArrayList<>();
    private ItemStack hovered = ItemStack.EMPTY;

    public BrewerBookScreen(OpenBrewerBookPayload book) {
        super(book.readable()
                ? Component.translatable("gui.brewery.book.title", book.ownerName())
                : Component.translatable("item.brewery.brewer_book"));
        this.book = book;
        this.spreads = spreadCount(book);
    }

    /** A readable book has a title spread and one spread per drink; a stranger's has three of scribbles. */
    public static int spreadCount(OpenBrewerBookPayload book) {
        return book.readable() ? 1 + book.pages().size() : 3;
    }

    /** Drinks the owner has made at least once. */
    public static int discoveredCount(OpenBrewerBookPayload book) {
        return (int) book.pages().stream().filter(page -> page.journal().isPresent()).count();
    }

    @Override
    protected void init() {
        left = (width - BOOK_WIDTH) / 2;
        top = (height - BOOK_HEIGHT) / 2;
        scribbles.clear();
        if (!book.readable()) {
            for (int page = 0; page < spreads * 2; page++) {
                scribbles.add(scribbleLines(new Random(0xB00CL * 31 + page)));
            }
        }
    }

    private List<Component> scribbleLines(Random random) {
        List<Component> lines = new ArrayList<>();
        for (int line = 0; line < (BOTTOM_Y - TOP_Y) / LINE; line++) {
            // A ragged right edge, and now and then a short line that ends a paragraph.
            int limit = random.nextInt(5) == 0
                    ? PAGE_WIDTH / 2 + random.nextInt(PAGE_WIDTH / 3)
                    : PAGE_WIDTH - random.nextInt(14);
            String text = "";
            while (true) {
                StringBuilder word = new StringBuilder();
                int length = 2 + random.nextInt(6);
                for (int i = 0; i < length; i++) {
                    word.append((char) ('a' + random.nextInt(26)));
                }
                String next = text.isEmpty() ? word.toString() : text + " " + word;
                if (font.width(Component.literal(next).withStyle(SCRIBBLE)) > limit) {
                    break;
                }
                text = next;
            }
            lines.add(Component.literal(text).withStyle(SCRIBBLE));
        }
        return lines;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        super.renderBackground(graphics);
        graphics.blit(TEXTURE, left, top, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.20.1's Screen#render does not draw the background on its own.
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        hovered = ItemStack.EMPTY;
        if (!book.readable()) {
            renderScribbles(graphics);
        } else if (spread == 0) {
            renderTitle(graphics);
        } else {
            renderDrink(graphics, book.pages().get(spread - 1), mouseX, mouseY);
        }
        if (spread > 0) {
            arrow(graphics, PREV_X, PREV_U, mouseX, mouseY);
        }
        if (spread < spreads - 1) {
            arrow(graphics, NEXT_X, NEXT_U, mouseX, mouseY);
        }
        Component number = Component.literal((spread + 1) + " / " + spreads);
        graphics.drawString(font, number, left + RIGHT_X + (PAGE_WIDTH - font.width(number)) / 2,
                top + ARROW_Y + 1, INK_FAINT, false);
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    private void arrow(GuiGraphics graphics, int x, int u, int mouseX, int mouseY) {
        int sprite = u + (overArrow(x, mouseX, mouseY) ? 20 : 0);
        graphics.blit(TEXTURE, left + x, top + ARROW_Y, sprite, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT,
                TEX_WIDTH, TEX_HEIGHT);
    }

    private boolean overArrow(int x, double mouseX, double mouseY) {
        return mouseX >= left + x && mouseX < left + x + ARROW_WIDTH
                && mouseY >= top + ARROW_Y && mouseY < top + ARROW_Y + ARROW_HEIGHT;
    }

    private void renderTitle(GuiGraphics graphics) {
        int y = 60;
        for (FormattedCharSequence line : font.split(title, PAGE_WIDTH)) {
            graphics.drawString(font, line, left + LEFT_X + (PAGE_WIDTH - font.width(line)) / 2, top + y,
                    INK_TITLE, false);
            y += LINE + 2;
        }
        rule(graphics, LEFT_X, y + 2);
        centered(graphics, Component.translatable("gui.brewery.book.discovered",
                discoveredCount(book), book.pages().size()), LEFT_X, y + 10, INK);
    }

    private void renderDrink(GuiGraphics graphics, OpenBrewerBookPayload.Page page, int mouseX, int mouseY) {
        if (page.journal().isEmpty()) {
            centered(graphics, Component.translatable("gui.brewery.book.unknown"), LEFT_X, 85, INK_FAINT);
            return;
        }
        DrinkJournal journal = page.journal().get();
        centered(graphics, Component.translatable("drink.brewery." + page.drinkId()), LEFT_X, TOP_Y, INK_TITLE);
        rule(graphics, LEFT_X, TOP_Y + 11);

        // Left page: the kettle's mix, heat and stars.
        Optional<KettleRecord> kettle = journal.kettle();
        header(graphics, LEFT_X, SECTION_Y, "gui.brewery.book.kettle", kettle.map(KettleRecord::stars));
        if (kettle.isPresent()) {
            int cursor = 0;
            int row = 0;
            for (Map.Entry<String, Integer> entry : new TreeMap<>(kettle.get().mix()).entrySet()) {
                Component count = Component.literal("×" + entry.getValue());
                int cell = 16 + 2 + font.width(count) + 5;
                if (cursor > 0 && cursor + cell - 5 > PAGE_WIDTH) {
                    cursor = 0;
                    row++;
                }
                int x = left + LEFT_X + cursor;
                int y = top + SECTION_Y + 12 + row * ICON;
                ItemStack stack = stack(entry.getKey());
                graphics.renderItem(stack, x, y);
                graphics.drawString(font, count, x + 18, y + 4, INK, false);
                hover(stack, x, y, mouseX, mouseY);
                cursor += cell;
            }
            int rows = row + 1;
            int heat = Mth.clamp(kettle.get().heat(), 0, 2);
            fitted(graphics, Component.translatable("gui.brewery.book.heat." + heat),
                    LEFT_X, SECTION_Y + 14 + rows * ICON, PAGE_WIDTH, INK);
        } else {
            none(graphics, LEFT_X, SECTION_Y + 12);
        }

        // Right page: the still and the barrel, for drinks that go through them.
        int y = SECTION_Y;
        if (page.hasStill()) {
            Optional<StillRecord> still = journal.still();
            header(graphics, RIGHT_X, y, "gui.brewery.book.still", still.map(StillRecord::stars));
            if (still.isPresent()) {
                fitted(graphics, Component.translatable("gui.brewery.book.rpm", still.get().rpm()),
                        RIGHT_X, y + 12, PAGE_WIDTH, INK);
            } else {
                none(graphics, RIGHT_X, y + 12);
            }
            y += 34;
        }
        if (page.hasBarrel()) {
            Optional<BarrelRecord> barrel = journal.barrel();
            header(graphics, RIGHT_X, y, "gui.brewery.book.barrel", barrel.map(BarrelRecord::stars));
            if (barrel.isPresent()) {
                ItemStack wood = stack(barrel.get().wood());
                Component woodName;
                if (wood.isEmpty()) {
                    woodName = Component.literal(barrel.get().wood());
                } else {
                    int iconX = left + RIGHT_X;
                    int iconY = top + y + 11;
                    graphics.renderItem(wood, iconX, iconY);
                    hover(wood, iconX, iconY, mouseX, mouseY);
                    woodName = wood.getHoverName();
                }
                fitted(graphics, woodName, RIGHT_X + ICON + 2, y + 15, PAGE_WIDTH - ICON - 2, INK);
                fitted(graphics, Component.translatable("gui.brewery.book.days", barrel.get().days()),
                        RIGHT_X, y + 31, PAGE_WIDTH, INK);
            } else {
                none(graphics, RIGHT_X, y + 12);
            }
        }
    }

    private void renderScribbles(GuiGraphics graphics) {
        int y = TOP_Y;
        for (FormattedCharSequence line : font.split(Component.translatable("gui.brewery.book.illegible"), PAGE_WIDTH)) {
            graphics.drawString(font, line, left + LEFT_X + (PAGE_WIDTH - font.width(line)) / 2, top + y,
                    INK_RED, false);
            y += LINE;
        }
        rule(graphics, LEFT_X, y + 1);
        drawScribbles(graphics, scribbles.get(spread * 2), LEFT_X, y + 6);
        drawScribbles(graphics, scribbles.get(spread * 2 + 1), RIGHT_X, TOP_Y);
    }

    private void drawScribbles(GuiGraphics graphics, List<Component> lines, int x, int startY) {
        int y = startY;
        for (Component line : lines) {
            if (y + LINE > BOTTOM_Y) {
                break;
            }
            graphics.drawString(font, line, left + x, top + y, INK, false);
            y += LINE;
        }
    }

    /** The item with this id, or an empty stack when there is none. */
    private static ItemStack stack(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(key));
        return stack.is(Items.AIR) ? ItemStack.EMPTY : stack;
    }

    private void hover(ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            hovered = stack;
        }
    }

    /** A section heading with its stars at the right edge of the page. */
    private void header(GuiGraphics graphics, int x, int y, String key, Optional<Integer> stars) {
        int textWidth = PAGE_WIDTH;
        if (stars.isPresent()) {
            Component line = StarLine.of(stars.get());
            int starsWidth = font.width(line);
            graphics.drawString(font, line, left + x + PAGE_WIDTH - starsWidth, top + y, 0xFFFFFFFF, false);
            textWidth -= starsWidth + 4;
        }
        fitted(graphics, Component.translatable(key), x, y, textWidth, INK_HEAD);
    }

    private void none(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, Component.translatable("gui.brewery.book.none"), left + x, top + y, INK_FAINT, false);
    }

    /** A thin inked line under a heading. */
    private void rule(GuiGraphics graphics, int x, int y) {
        graphics.fill(left + x + 10, top + y, left + x + PAGE_WIDTH - 10, top + y + 1, INK_FAINT);
    }

    private void centered(GuiGraphics graphics, Component text, int pageX, int y, int color) {
        int textWidth = Math.min(font.width(text), PAGE_WIDTH);
        fitted(graphics, text, pageX + (PAGE_WIDTH - textWidth) / 2, y, PAGE_WIDTH, color);
    }

    /** Text squeezed to {@code maxWidth} when a long translation would run off the page. */
    private void fitted(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        int textWidth = font.width(text);
        graphics.pose().pushPose();
        graphics.pose().translate(left + x, top + y, 0);
        if (textWidth > maxWidth) {
            float scale = maxWidth / (float) textWidth;
            graphics.pose().scale(scale, scale, 1);
        }
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void turn(int delta) {
        int next = Mth.clamp(spread + delta, 0, spreads - 1);
        if (next != spread) {
            spread = next;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (spread > 0 && overArrow(PREV_X, mouseX, mouseY)) {
                turn(-1);
                return true;
            }
            if (spread < spreads - 1 && overArrow(NEXT_X, mouseX, mouseY)) {
                turn(1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (scrollY != 0) {
            turn(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            turn(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            turn(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
