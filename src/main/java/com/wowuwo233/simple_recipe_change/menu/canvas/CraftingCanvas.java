package com.wowuwo233.simple_recipe_change.menu.canvas;

import static com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu.*;

/** 工作台 / 物品栏合成：3×3（或 2×2）材料 + 产物 */
public final class CraftingCanvas implements RecipeCanvas {

    @Override
    public void layout(CanvasContext ctx, SlotPlacer placer) {
        int gridX = ctx.adding() ? ADD_GRID_X : MOD_GRID_X;
        int outX = ctx.adding() ? ADD_OUTPUT_X : MOD_OUTPUT_X;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                placer.input(row * 3 + col, gridX + col * SLOT_SIZE, CRAFT_GRID_Y + row * SLOT_SIZE);
            }
        }
        placer.output(outX, ROW_Y);
    }
}
