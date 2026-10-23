package com.wowuwo233.simple_recipe_change.menu.canvas;

import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;

/**
 * 配方类型 → 画布。
 *
 * <p>加新类型时，如果它的槽位形状和同分类的已有类型一样，这里不用改；
 * 形状不同的才需要新写一个画布并加一行。
 */
public final class CanvasRegistry {

    private static final RecipeCanvas CRAFTING = new CraftingCanvas();
    private static final RecipeCanvas COOKING = new CookingCanvas();
    private static final RecipeCanvas SMITHING = new SmithingCanvas();
    private static final RecipeCanvas FARMERS = new FarmersCanvas();
    private static final RecipeCanvas CREATE = new CreateCanvas();
    private static final RecipeCanvas THERMAL = new ThermalCanvas();

    public static RecipeCanvas of(RecipeType type) {
        return switch (type.category()) {
            case CRAFTING -> CRAFTING;
            case COOKING -> COOKING;
            case SMITHING -> SMITHING;
            case FARMERS -> FARMERS;
            case CREATE -> CREATE;
            case THERMAL -> THERMAL;
        };
    }

    private CanvasRegistry() {
    }
}
