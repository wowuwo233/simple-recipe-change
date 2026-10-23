package com.wowuwo233.simple_recipe_change.menu.canvas;

import static com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu.*;

/** 熔炉 / 高炉 / 烟熏炉 / 营火：原料 → 产物 */
public final class CookingCanvas implements RecipeCanvas {

    @Override
    public void layout(CanvasContext ctx, SlotPlacer placer) {
        placer.input(0, COOK_INPUT_X, ROW_Y);
        placer.output(COOK_OUTPUT_X, ROW_Y);
    }
}
