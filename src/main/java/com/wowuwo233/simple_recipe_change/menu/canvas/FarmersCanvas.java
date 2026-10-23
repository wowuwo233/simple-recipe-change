package com.wowuwo233.simple_recipe_change.menu.canvas;

import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;

import static com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu.*;

/** 农夫乐事：烹饪锅（3×2 材料 + 容器）与切菜板（材料 + 工具） */
public final class FarmersCanvas implements RecipeCanvas {

    @Override
    public void layout(CanvasContext ctx, SlotPlacer placer) {
        if (ctx.type() == RecipeType.FARMERS_COOKING) {
            // 3×2 材料网格，下标 0..5，行优先
            for (int i = 0; i < 6; i++) {
                placer.input(i, COOK_GRID_X + (i % 3) * SLOT_SIZE,
                        COOK_GRID_Y + (i / 3) * SLOT_SIZE);
            }
            // 容器单独一行（下标 6，可留空）
            placer.input(6, COOK_CONTAINER_X, COOK_CONTAINER_Y);
        } else {
            placer.input(0, CUT_INPUT_X, CUT_ROW_Y);
            placer.input(1, CUT_TOOL_X, CUT_ROW_Y);
        }
        placer.output(FARMERS_OUTPUT_X, FARMERS_OUTPUT_Y);

        // 额外产物只有切菜板有
        for (int i = 0; i < ctx.extraOutputs(); i++) {
            placer.extra(i, EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP, EXTRA_ROW_Y);
        }
    }
}
