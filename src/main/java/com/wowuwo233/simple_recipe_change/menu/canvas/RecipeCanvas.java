package com.wowuwo233.simple_recipe_change.menu.canvas;

/**
 * 一种配方类型的「画布」。
 *
 * <p>思路参考 ViScriptRecipe：每种配方类型有自己的可视化布局类，而不是把
 * 所有类型的坐标堆在一个 switch 里。区别是我们只用原版 {@code GuiGraphics}
 * 画，不引入任何 UI 框架。
 *
 * <p>加新类型 = 写一个画布类 + 在 {@link CanvasRegistry} 加一行。
 */
public interface RecipeCanvas {

    /** 把这个类型的槽位摆出来 */
    void layout(CanvasContext ctx, SlotPlacer placer);
}
