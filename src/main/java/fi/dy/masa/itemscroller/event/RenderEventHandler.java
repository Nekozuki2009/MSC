package fi.dy.masa.itemscroller.event;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import fi.dy.masa.malilib.render.InventoryOverlay;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.itemscroller.config.Configs;
import fi.dy.masa.itemscroller.recipes.RecipePattern;
import fi.dy.masa.itemscroller.recipes.RecipeStorage;
import fi.dy.masa.itemscroller.util.AccessorUtils;
import fi.dy.masa.itemscroller.util.InputUtils;
import fi.dy.masa.itemscroller.util.InventoryUtils;

public class RenderEventHandler
{
    private static final RenderEventHandler INSTANCE = new RenderEventHandler();

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private int recipeListX;
    private int recipeListY;
    private int recipesPerColumn;
    private int columnWidth;
    private int columns;
    private int numberTextWidth;
    private int gapColumn;
    private int entryHeight;
    private double scale;

    public static RenderEventHandler instance()
    {
        return INSTANCE;
    }

    public void renderRecipeView(DrawContext drawContext)
    {
        if (GuiUtils.getCurrentScreen() instanceof HandledScreen && InputUtils.isRecipeViewOpen())
        {
            HandledScreen<?> gui = (HandledScreen<?>) GuiUtils.getCurrentScreen();
            RecipeStorage recipes = RecipeStorage.getInstance();
            final int first = recipes.getFirstVisibleRecipeId();
            final int countPerPage = recipes.getRecipeCountPerPage();
            final int lastOnPage = first + countPerPage - 1;

            this.calculateRecipePositions(gui);

            MatrixStack matrixStack = RenderSystem.getModelViewStack();
            matrixStack.push();
            matrixStack.translate(this.recipeListX, this.recipeListY, 0);
            matrixStack.scale((float) this.scale, (float) this.scale, 1);

            String str = StringUtils.translate("itemscroller.gui.label.recipe_page", (first / countPerPage) + 1, recipes.getTotalRecipeCount() / countPerPage);

            drawContext.drawText(this.mc.textRenderer, str, 16, -12, 0xC0C0C0C0, false);

            for (int i = 0, recipeId = first; recipeId <= lastOnPage; ++i, ++recipeId)
            {
                ItemStack stack = recipes.getRecipe(recipeId).getResult();
                boolean selected = recipeId == recipes.getSelection();
                int row = i % this.recipesPerColumn;
                int column = i / this.recipesPerColumn;

                this.renderStoredRecipeStack(stack, recipeId, row, column, gui, selected, drawContext);
            }

            if (Configs.Generic.CRAFTING_RENDER_RECIPE_ITEMS.getBooleanValue())
            {
                final int mouseX = fi.dy.masa.malilib.util.InputUtils.getMouseX();
                final int mouseY = fi.dy.masa.malilib.util.InputUtils.getMouseY();
                final int recipeId = this.getHoveredRecipeId(mouseX, mouseY, recipes, gui);
                RecipePattern recipe = recipeId >= 0 ? recipes.getRecipe(recipeId) : recipes.getSelectedRecipe();

                this.renderRecipeItems(recipe, recipes.getRecipeCountPerPage(), gui, drawContext);
            }

            matrixStack.pop();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableBlend(); // Fixes the crafting book icon rendering
        }
    }

    public void onDrawScreenPost(MinecraftClient mc, DrawContext drawContext)
    {
        this.renderRecipeView(drawContext);

        if (GuiUtils.getCurrentScreen() instanceof HandledScreen)
        {
            HandledScreen<?> gui = (HandledScreen<?>) this.mc.currentScreen;

            if (InputUtils.isRecipeViewOpen() == false)
            {
                return;
            }

            RecipeStorage recipes = RecipeStorage.getInstance();

            final int mouseX = fi.dy.masa.malilib.util.InputUtils.getMouseX();
            final int mouseY = fi.dy.masa.malilib.util.InputUtils.getMouseY();
            final int recipeId = this.getHoveredRecipeId(mouseX, mouseY, recipes, gui);

            float offset = 300f;
            MatrixStack matrixStack = RenderSystem.getModelViewStack();
            matrixStack.push();
            matrixStack.translate(0, 0, offset);

            if (recipeId >= 0)
            {
                RecipePattern recipe = recipes.getRecipe(recipeId);
                this.renderHoverTooltip(mouseX, mouseY, recipe, gui, drawContext);
            }
            else if (Configs.Generic.CRAFTING_RENDER_RECIPE_ITEMS.getBooleanValue())
            {
                RecipePattern recipe = recipes.getSelectedRecipe();
                ItemStack stack = this.getHoveredRecipeIngredient(mouseX, mouseY, recipe, recipes.getRecipeCountPerPage(), gui);

                if (InventoryUtils.isStackEmpty(stack) == false)
                {
                    InventoryOverlay.renderStackToolTip(mouseX, mouseY, stack, this.mc, drawContext);
                }
            }

            matrixStack.pop();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private void calculateRecipePositions(HandledScreen<?> gui)
    {
        RecipeStorage recipes = RecipeStorage.getInstance();
        final int gapHorizontal = 2;
        final int gapVertical = 2;
        final int stackBaseHeight = 16;
        final int guiLeft = AccessorUtils.getGuiLeft(gui);

        this.recipesPerColumn = 9;
        this.columns = (int) Math.ceil((double) recipes.getRecipeCountPerPage() / (double) this.recipesPerColumn);
        this.numberTextWidth = 12;
        this.gapColumn = 4;

        int usableHeight = GuiUtils.getScaledWindowHeight();
        int usableWidth = guiLeft;
        // Scale the maximum stack size by taking into account the relative gap size
        double gapScaleVertical = (1D - (double) gapVertical / (double) (stackBaseHeight + gapVertical));
        // the +1.2 is for the gap and page text height on the top and bottom
        int maxStackDimensionsVertical = (int) ((usableHeight / ((double) this.recipesPerColumn + 1.2)) * gapScaleVertical);
        // assume a maximum of 3x3 recipe size for now... thus columns + 3 stacks rendered horizontally
        double gapScaleHorizontal = (1D - (double) gapHorizontal / (double) (stackBaseHeight + gapHorizontal));
        int maxStackDimensionsHorizontal = (int) (((usableWidth - (this.columns * (this.numberTextWidth + this.gapColumn))) / (this.columns + 3 + 0.8)) * gapScaleHorizontal);
        int stackDimensions = (int) Math.min(maxStackDimensionsVertical, maxStackDimensionsHorizontal);

        this.scale = (double) stackDimensions / (double) stackBaseHeight;
        this.entryHeight = stackBaseHeight + gapVertical;
        this.recipeListX = guiLeft - (int) ((this.columns * (stackBaseHeight + this.numberTextWidth + this.gapColumn) + gapHorizontal) * this.scale);
        this.recipeListY = (int) (this.entryHeight * this.scale);
        this.columnWidth = stackBaseHeight + this.numberTextWidth + this.gapColumn;
    }

    private void renderHoverTooltip(int mouseX, int mouseY, RecipePattern recipe, HandledScreen<?> gui, DrawContext drawContext)
    {
        ItemStack stack = recipe.getResult();

        if (InventoryUtils.isStackEmpty(stack) == false)
        {
            InventoryOverlay.renderStackToolTip(mouseX, mouseY, stack, this.mc, drawContext);
        }
    }

    public int getHoveredRecipeId(int mouseX, int mouseY, RecipeStorage recipes, HandledScreen<?> gui)
    {
        if (InputUtils.isRecipeViewOpen())
        {
            this.calculateRecipePositions(gui);
            final int stackDimensions = (int) (16 * this.scale);

            for (int column = 0; column < this.columns; ++column)
            {
                int startX = this.recipeListX + (int) ((column * this.columnWidth + this.gapColumn + this.numberTextWidth) * this.scale);

                if (mouseX >= startX && mouseX <= startX + stackDimensions)
                {
                    for (int row = 0; row < this.recipesPerColumn; ++row)
                    {
                        int startY = this.recipeListY + (int) (row * this.entryHeight * this.scale);

                        if (mouseY >= startY && mouseY <= startY + stackDimensions)
                        {
                            return recipes.getFirstVisibleRecipeId() + column * this.recipesPerColumn + row;
                        }
                    }
                }
            }
        }

        return -1;
    }

    private void renderStoredRecipeStack(ItemStack stack, int recipeId, int row, int column, HandledScreen<?> gui,
            boolean selected, DrawContext drawContext)
    {
        final TextRenderer font = this.mc.textRenderer;
        final String indexStr = String.valueOf(recipeId + 1);

        int x = column * this.columnWidth + this.gapColumn + this.numberTextWidth;
        int y = row * this.entryHeight;
        this.renderStackAt(stack, x, y, selected, drawContext);

        float scale = 0.75F;
        x = x - (int) (font.getWidth(indexStr) * scale) - 2;
        y = row * this.entryHeight + this.entryHeight / 2 - font.fontHeight / 2;

        MatrixStack matrixStack = drawContext.getMatrices();
        matrixStack.push();
        matrixStack.translate(x, y, 0);
        matrixStack.scale(scale, scale, 1);

        drawContext.drawText(font, indexStr, 0, 0, 0xFFC0C0C0, false);

        matrixStack.pop();
    }

    private void renderRecipeItems(RecipePattern recipe, int recipeCountPerPage, HandledScreen<?> gui, DrawContext drawContext)
    {
        ItemStack[] items = recipe.getRecipeItems();
        int recipelen = recipe.getRecipeLength();
        int x = -3 * 17 + 2;
        int y = 3 * this.entryHeight;
        final double recipeDimensions;
        if ((recipeDimensions = Math.sqrt(recipelen)) % 1 == 0) {
            for (int i = 0, row = 0; row < recipeDimensions; row++) {
                for (int col = 0; col < recipeDimensions; col++, i++) {
                    int xOff = col * 17;
                    int yOff = row * 17;

                    this.renderStackAt(items[i], x + xOff, y + yOff, false, drawContext);
                }
            }
        } else {
            for (int i=0;i<recipelen;i++) {
                int xOff = i * 17;
                int yOff = 17;

                this.renderStackAt(items[i], x + xOff, y + yOff, false, drawContext);
            }
        }
    }

    private ItemStack getHoveredRecipeIngredient(int mouseX, int mouseY, RecipePattern recipe, int recipeCountPerPage, HandledScreen<?> gui)
    {
        final int recipelen = recipe.getRecipeLength();
        final double recipeDimensions = Math.sqrt(recipelen);
        int scaledStackDimensions = (int) (16 * this.scale);
        int scaledGridEntry = (int) (17 * this.scale);
        int x = this.recipeListX - (int) ((3 * 17 - 2) * this.scale);
        int y = this.recipeListY + (int) (3 * this.entryHeight * this.scale);

        if (mouseX >= x && mouseX <= x + recipeDimensions * scaledGridEntry &&
            mouseY >= y && mouseY <= y + recipeDimensions * scaledGridEntry)
        {
            if (recipeDimensions % 1 == 0) {
                for (int i = 0, row = 0; row < recipeDimensions; row++) {
                    for (int col = 0; col < recipeDimensions; col++, i++) {
                        int xOff = col * scaledGridEntry;
                        int yOff = row * scaledGridEntry;
                        int xStart = x + xOff;
                        int yStart = y + yOff;

                        if (mouseX >= xStart && mouseX < xStart + scaledStackDimensions &&
                                mouseY >= yStart && mouseY < yStart + scaledStackDimensions) {
                            return recipe.getRecipeItems()[i];
                        }
                    }
                }
            } else {
                for (int i = 0; i < recipelen; i++) {
                    int yOff = i * scaledGridEntry;
                    int xStart = x + scaledGridEntry;
                    int yStart = y + yOff;
                    if (mouseX >= xStart && mouseX < xStart + scaledStackDimensions &&
                            mouseY >= yStart && mouseY < yStart + scaledStackDimensions) {
                        return recipe.getRecipeItems()[i];
                    }
                }
            }
        }

        return ItemStack.EMPTY;
    }

    private void renderStackAt(ItemStack stack, int x, int y, boolean border, DrawContext drawContext)
    {
        final int w = 16;

        if (border)
        {
            // Draw a light/white border around the stack
            RenderUtils.drawOutline(x - 1, y - 1, w + 2, w + 2, 0xFFFFFFFF);
        }

        RenderUtils.drawRect(x, y, w, w, 0x20FFFFFF); // light background for the item

        if (InventoryUtils.isStackEmpty(stack) == false)
        {
            DiffuseLighting.enableGuiDepthLighting();

            stack = stack.copy();
            InventoryUtils.setStackSize(stack, 1);

            MatrixStack matrixStack = drawContext.getMatrices();
            matrixStack.push();
            matrixStack.translate(0, 0, 100.f);

            drawContext.drawItem(stack, x, y);

            matrixStack.pop();
        }
    }

    /*
    public static void enableGUIStandardItemLighting(float scale)
    {
        RenderSystem.pushMatrix();
        RenderSystem.rotatef(-30.0F, 0.0F, 1.0F, 0.0F);
        RenderSystem.rotatef(165.0F, 1.0F, 0.0F, 0.0F);

        enableStandardItemLighting(scale);

        RenderSystem.popMatrix();
    }

    public static void enableStandardItemLighting(float scale)
    {
        RenderSystem.enableLighting();
        GlStateManager.enableLight(0);
        GlStateManager.enableLight(1);
        RenderSystem.enableColorMaterial();
        RenderSystem.colorMaterial(1032, 5634);

        float lightStrength = 0.3F * scale;
        float ambientLightStrength = 0.4F;

        GlStateManager.light(16384, 4611, singletonBuffer((float) LIGHT0_POS.x, (float) LIGHT0_POS.y, (float) LIGHT0_POS.z, 0.0f));
        GlStateManager.light(16384, 4609, singletonBuffer(lightStrength, lightStrength, lightStrength, 1.0F));
        GlStateManager.light(16384, 4608, singletonBuffer(0.0F, 0.0F, 0.0F, 1.0F));
        GlStateManager.light(16384, 4610, singletonBuffer(0.0F, 0.0F, 0.0F, 1.0F));

        GlStateManager.light(16385, 4611, singletonBuffer((float) LIGHT1_POS.x, (float) LIGHT1_POS.y, (float) LIGHT1_POS.z, 0.0f));
        GlStateManager.light(16385, 4609, singletonBuffer(lightStrength, lightStrength, lightStrength, 1.0F));
        GlStateManager.light(16385, 4608, singletonBuffer(0.0F, 0.0F, 0.0F, 1.0F));
        GlStateManager.light(16385, 4610, singletonBuffer(0.0F, 0.0F, 0.0F, 1.0F));

        RenderSystem.shadeModel(GL11.GL_FLAT);

        GlStateManager.lightModel(2899, singletonBuffer(ambientLightStrength, ambientLightStrength, ambientLightStrength, 1.0F));
    }

    private static FloatBuffer singletonBuffer(float val1, float val2, float val3, float val4)
    {
        FLOAT_BUFFER.clear();
        FLOAT_BUFFER.put(val1).put(val2).put(val3).put(val4);
        FLOAT_BUFFER.flip();

        return FLOAT_BUFFER;
    }
    */
}
