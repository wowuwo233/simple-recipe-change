package com.wowuwo233.simple_recipe_change.menu.canvas;

import net.minecraft.client.gui.GuiGraphics;

import static com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu.*;

/** 锻造台：模板 / 基础物品 / 升级物品 → 产物 */
public final class SmithingCanvas implements RecipeCanvas {

    @Override
    public boolean drawSlotFrames() {
        return false;
    }

    @Override
    public void layout(CanvasContext ctx, SlotPlacer placer) {
        placer.input(0, SMITH_TEMPLATE_X, ROW_Y);
        placer.input(1, SMITH_BASE_X, ROW_Y);
        placer.input(2, SMITH_ADDITION_X, ROW_Y);
        placer.output(SMITH_OUTPUT_X, ROW_Y);
    }

    @Override
    public void background(GuiGraphics graphics, int leftPos, int topPos, CanvasContext ctx) {
        int y = topPos + ROW_Y;
        int[] xs = {SMITH_TEMPLATE_X, SMITH_BASE_X, SMITH_ADDITION_X};
        for (int x : xs) {
            CanvasPainter.recessAtSlot(graphics, leftPos + x, y);
        }
        CanvasPainter.recessAtSlot(graphics, leftPos + SMITH_OUTPUT_X, y);
    }
}
