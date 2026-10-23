package com.wowuwo233.simple_recipe_change.menu.canvas;

import static com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu.*;

/** 机械动力：处理配方（3×3 + 副产物）与序列组装（输入 / 过渡 / 6 步 / 失败产物） */
public final class CreateCanvas implements RecipeCanvas {

    @Override
    public void layout(CanvasContext ctx, SlotPlacer placer) {
        if (ctx.type().isSequenced()) {
            layoutSequenced(ctx, placer);
            return;
        }
        for (int i = 0; i < CREATE_MATERIAL_SLOTS; i++) {
            placer.input(i, CREATE_GRID_X + (i % 3) * SLOT_SIZE,
                    CREATE_GRID_Y + (i / 3) * SLOT_SIZE);
        }
        placer.output(CREATE_OUTPUT_X, CREATE_GRID_Y + SLOT_SIZE);
        for (int i = 0; i < ctx.extraOutputs(); i++) {
            placer.extra(i, EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP, CREATE_EXTRA_ROW_Y);
        }
    }

    /**
     * 序列组装：输入 → 过渡物品 一行，下面 6 个步骤槽。
     *
     * <p>产物槽必须排在<b>所有输入槽之后</b>：{@code outputMenuIndex()} 按输入槽数量推算下标，
     * 插在中间会让产物标签画到步骤槽上。
     */
    private void layoutSequenced(CanvasContext ctx, SlotPlacer placer) {
        placer.input(0, SEQ_INPUT_X, SEQ_ROW_Y);
        placer.input(1, SEQ_TRANSITIONAL_X, SEQ_ROW_Y);
        for (int i = 0; i < SEQ_STEP_COUNT; i++) {
            placer.input(2 + i, SEQ_BUTTON_X0 + i * SEQ_BUTTON_STEP + SEQ_SLOT_INSET, SEQ_SLOT_Y);
        }
        placer.output(SEQ_OUTPUT_X, SEQ_ROW_Y);
        placer.failure(SEQ_FAIL_X, SEQ_ROW_Y);
    }
}
