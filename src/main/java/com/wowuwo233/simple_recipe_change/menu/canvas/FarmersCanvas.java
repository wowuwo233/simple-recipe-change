package com.wowuwo233.simple_recipe_change.menu.canvas;

import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import net.minecraft.client.gui.GuiGraphics;

import static com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu.*;

/** 农夫乐事：烹饪锅（3×2 材料 + 容器）与切菜板（材料 + 工具） */
public final class FarmersCanvas implements RecipeCanvas {

    @Override
    public boolean drawSlotFrames() {
        return false;
    }

    @Override
    public void layout(CanvasContext ctx, SlotPlacer placer) {
        if (ctx.type() == RecipeType.FARMERS_COOKING) {
            for (int i = 0; i < 6; i++) {
                placer.input(i, COOK_GRID_X + (i % 3) * SLOT_SIZE,
                        COOK_GRID_Y + (i / 3) * SLOT_SIZE);
            }
            placer.input(6, COOK_CONTAINER_X, COOK_CONTAINER_Y);
        } else {
            placer.input(0, CUT_INPUT_X, CUT_ROW_Y);
            placer.input(1, CUT_TOOL_X, CUT_ROW_Y);
        }
        placer.output(FARMERS_OUTPUT_X, FARMERS_OUTPUT_Y);
        for (int i = 0; i < ctx.extraOutputs(); i++) {
            placer.extra(i, EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP, EXTRA_ROW_Y);
        }
    }

    @Override
    public void background(GuiGraphics graphics, int leftPos, int topPos, CanvasContext ctx) {
        if (ctx.type() == RecipeType.FARMERS_COOKING) {
            // 3×2 材料网格
            for (int i = 0; i < 6; i++) {
                CanvasPainter.recessAtSlot(graphics,
                        leftPos + COOK_GRID_X + (i % 3) * SLOT_SIZE,
                        topPos + COOK_GRID_Y + (i / 3) * SLOT_SIZE);
            }
            // 容器格单独一行，用暗一点的凹槽表示「可留空」
            CanvasPainter.recessAtSlot(graphics, leftPos + COOK_CONTAINER_X,
                    topPos + COOK_CONTAINER_Y);
        } else {
            CanvasPainter.recessAtSlot(graphics, leftPos + CUT_INPUT_X, topPos + CUT_ROW_Y);
            CanvasPainter.recessAtSlot(graphics, leftPos + CUT_TOOL_X, topPos + CUT_ROW_Y);
        }
        CanvasPainter.recessAtSlot(graphics, leftPos + FARMERS_OUTPUT_X,
                topPos + FARMERS_OUTPUT_Y);

        // 副产物：松散的一排，给个外框把它们圈在一起
        if (ctx.extraOutputs() > 0) {
            for (int i = 0; i < ctx.extraOutputs(); i++) {
                CanvasPainter.recessAtSlot(graphics,
                        leftPos + EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP,
                        topPos + EXTRA_ROW_Y);
            }
        }
    }
}
