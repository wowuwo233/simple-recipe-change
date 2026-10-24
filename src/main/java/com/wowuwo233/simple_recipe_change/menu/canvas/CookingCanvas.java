package com.wowuwo233.simple_recipe_change.menu.canvas;

import net.minecraft.client.gui.GuiGraphics;

import static com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu.*;

/** 熔炉 / 高炉 / 烟熏炉 / 营火：原料 → 产物 */
public final class CookingCanvas implements RecipeCanvas {

    @Override
    public boolean drawSlotFrames() {
        return false;
    }

    @Override
    public void layout(CanvasContext ctx, SlotPlacer placer) {
        placer.input(0, COOK_INPUT_X, ROW_Y);
        placer.output(COOK_OUTPUT_X, ROW_Y);
    }

    @Override
    public void background(GuiGraphics graphics, int leftPos, int topPos, CanvasContext ctx) {
        int inX = leftPos + COOK_INPUT_X;
        int outX = leftPos + COOK_OUTPUT_X;
        int y = topPos + ROW_Y;
        CanvasPainter.recessAtSlot(graphics, inX, y);
        CanvasPainter.recessAtSlot(graphics, outX, y);
    }
}
