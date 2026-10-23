package com.wowuwo233.simple_recipe_change.menu.canvas;

import static com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu.*;

/** 热力膨胀：3×3 材料 + 产物 + 副产物行（和机械动力处理配方同一套摆法） */
public final class ThermalCanvas implements RecipeCanvas {

    @Override
    public void layout(CanvasContext ctx, SlotPlacer placer) {
        for (int i = 0; i < CREATE_MATERIAL_SLOTS; i++) {
            placer.input(i, CREATE_GRID_X + (i % 3) * SLOT_SIZE,
                    CREATE_GRID_Y + (i / 3) * SLOT_SIZE);
        }
        placer.output(CREATE_OUTPUT_X, CREATE_GRID_Y + SLOT_SIZE);
        for (int i = 0; i < ctx.extraOutputs(); i++) {
            placer.extra(i, EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP, CREATE_EXTRA_ROW_Y);
        }
    }
}
