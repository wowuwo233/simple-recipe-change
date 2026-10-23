package com.wowuwo233.simple_recipe_change.menu.canvas;

/**
 * 画布往菜单里摆槽位的出口。
 *
 * <p>画布只管「第几个槽放在哪个坐标」，怎么建 Slot、怎么维护同步下标由菜单负责——
 * 这样画布不需要知道 {@code place()} 那些坑（见 RecipeEditorMenu#place 的注释）。
 */
public interface SlotPlacer {

    /** 输入展示槽（下标对应 cells 的前几项） */
    void input(int index, int x, int y);

    /** 产物槽 */
    void output(int x, int y);

    /** 额外产物格（切菜板 / 机械动力副产物 / 热力副产物） */
    void extra(int index, int x, int y);

    /** 序列组装的失败产物格（单独一个容器，避免和步骤槽撞下标） */
    void failure(int x, int y);

    /** 专用流体储罐格：只能放流体，物品拖进来会被拒绝 */
    void fluid(int index, int x, int y);
}
