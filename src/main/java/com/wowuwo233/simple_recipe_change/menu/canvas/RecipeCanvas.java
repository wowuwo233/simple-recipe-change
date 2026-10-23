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

    /**
     * 画机器底图（凹槽、箭头、产物框之类）。
     *
     * <p>默认什么都不画，走通用的「每格一个方框」。
     * 关掉方框（{@link #drawSlotFrames()} 返回 false）的画布应该在这里把槽位背景画好，
     * 否则物品会浮在空地上。
     *
     * @param leftPos 窗口左上角（屏幕坐标）
     * @param topPos  窗口上边
     */
    default void background(net.minecraft.client.gui.GuiGraphics graphics,
                            int leftPos, int topPos, CanvasContext ctx) {
    }

    /** 要不要给每个槽画方框。返回 false 时由 {@link #background} 自己画 */
    default boolean drawSlotFrames() {
        return true;
    }
}
