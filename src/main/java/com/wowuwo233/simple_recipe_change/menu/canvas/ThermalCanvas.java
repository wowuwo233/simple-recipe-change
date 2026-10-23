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
        // 吃流体的机器多一个竖长储罐格。
        // 必须排在产物之前：产物下标是按「输入格数量」推算的，插在中间会让产物认错槽。
        if (ctx.type().hasFluidSlot()) {
            placer.fluid(FLUID_CELL, FLUID_SLOT_X, FLUID_SLOT_Y);
        }
        placer.output(CREATE_OUTPUT_X, CREATE_GRID_Y + SLOT_SIZE);
        for (int i = 0; i < ctx.extraOutputs(); i++) {
            placer.extra(i, EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP, CREATE_EXTRA_ROW_Y);
        }
    }
}
