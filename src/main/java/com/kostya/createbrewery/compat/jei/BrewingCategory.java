package com.kostya.createbrewery.compat.jei;

import com.kostya.createbrewery.CreateBrewery;
import com.kostya.createbrewery.DrinkData;
import com.kostya.createbrewery.block.ModBlocks;
import com.kostya.createbrewery.recipe.BrewingRecipe;
import com.kostya.createbrewery.recipe.DrinkProfile;
import com.kostya.createbrewery.registry.ModDataComponents;
import com.kostya.createbrewery.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shows a brewing recipe with the whole road its drink can travel. */
final class BrewingCategory implements IRecipeCategory<BrewingRecipe> {
    static final RecipeType<BrewingRecipe> TYPE = RecipeType.create(CreateBrewery.MOD_ID, "brewing", BrewingRecipe.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 58;
    private static final int TEXT_COLOR = 0x404040;

    private final IDrawable icon;

    BrewingCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.KETTLE_ITEM.get()));
    }

    @Override
    public RecipeType<BrewingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.brewery.category");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BrewingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
                .addIngredients(recipe.getIngredients().getFirst())
                .setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.INPUT, 19, 1)
                .addIngredients(recipe.getIngredients().getLast())
                .setStandardSlotBackground();

        DrinkProfile drink = recipe.drink();
        builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 37, 1)
                .addItemStack(bottle(drink.id(), drink.alcohol()))
                .setStandardSlotBackground();
        String last = drink.canAge() ? drink.aged() : drink.canDistill() ? drink.distilled() : "";
        if (!last.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 19, 1)
                    .addItemStack(bottle(last, drink.distilledAlcohol()))
                    .setStandardSlotBackground();
        }
    }

    private static ItemStack bottle(String stage, int alcohol) {
        ItemStack stack = new ItemStack(ModItems.DRINK_BOTTLE.get());
        stack.set(ModDataComponents.DRINK_DATA.get(), new DrinkData(DrinkData.MAX_STARS, alcohol, stage));
        return stack;
    }

    @Override
    public void draw(BrewingRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        DrinkProfile drink = recipe.drink();
        var font = Minecraft.getInstance().font;
        int y = 22;
        graphics.drawString(font, Component.translatable("jei.brewery.brew", name(drink.id())), 0, y, TEXT_COLOR, false);
        if (drink.canDistill()) {
            y += 11;
            graphics.drawString(font, Component.translatable("jei.brewery.distill", name(drink.distilled()),
                    drink.idealRpm()), 0, y, TEXT_COLOR, false);
        }
        if (drink.canAge()) {
            y += 11;
            graphics.drawString(font, Component.translatable("jei.brewery.age", drink.agingDays(),
                    Component.translatable("block.minecraft." + drink.wood() + "_planks")), 0, y, TEXT_COLOR, false);
        }
    }

    private static Component name(String stage) {
        return Component.translatable("drink.brewery." + stage);
    }
}
