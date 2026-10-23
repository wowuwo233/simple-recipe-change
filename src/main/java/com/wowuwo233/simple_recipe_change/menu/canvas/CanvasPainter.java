package com.wowuwo233.simple_recipe_change.menu.canvas;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 画布共用的绘制工具：凹槽、箭头、分隔线。
 *
 * <p>所有类型都用同一套视觉语言——「凹槽 + 箭头」，这样切换类型时不会一会儿方框一会儿机器图。
 */
public final class CanvasPainter {

    /** 和 RecipeEditorMenu.SLOT_SIZE 一致：槽位是 18×18，物品画在里面是 16×16 */
    public static final int CELL = 18;
    /** 物品相对槽位的偏移：物品从 slot.x 开始画 16×16，所以凹槽要往左上各让 1px 才居中 */
    public static final int SLOT_INSET = 1;

    /** 按「槽位坐标」画凹槽：内部对齐 16×16 的物品 */
    public static void recessAtSlot(GuiGraphics graphics, int slotX, int slotY) {
        recess(graphics, slotX - SLOT_INSET, slotY - SLOT_INSET, CELL);
    }

    private static final int RECESS = 0xFF2B2B2B;
    private static final int EDGE_DARK = 0xFF141414;
    private static final int EDGE_LIGHT = 0xFF5E5E5E;
    private static final int ARROW = 0xFF8A8A8A;
    private static final int ARROW_DIM = 0xFF5A5A5A;

    /** 一个凹槽：内部深色 + 左上暗边 + 右下亮边 */
    public static void recess(GuiGraphics graphics, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, RECESS);
        graphics.fill(x, y, x + size, y + 1, EDGE_DARK);
        graphics.fill(x, y, x + 1, y + size, EDGE_DARK);
        graphics.fill(x, y + size - 1, x + size, y + size, EDGE_LIGHT);
        graphics.fill(x + size - 1, y, x + size, y + size, EDGE_LIGHT);
    }

    /** 禁用/占位的凹槽：整体压暗，和可用的区分开 */
    public static void recessDisabled(GuiGraphics graphics, int x, int y, int size) {
        recess(graphics, x, y, size);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x60000000);
    }

    /** 向右的箭头：从 x1 到 x2，纵向中心 y */
    public static void arrowRight(GuiGraphics graphics, int x1, int x2, int y) {
        if (x2 <= x1) {
            return;
        }
        graphics.fill(x1, y - 1, x2 - 4, y + 1, ARROW);
        for (int i = 0; i < 4; i++) {
            graphics.fill(x2 - 4 + i, y - 1 - i, x2 - 3 + i, y + 2 + i, ARROW);
        }
    }

    /** 两格之间的小箭头（烹饪锅那种短距离） */
    public static void arrowRightShort(GuiGraphics graphics, int x1, int x2, int y) {
        graphics.fill(x1, y - 1, x2, y + 1, ARROW_DIM);
        for (int i = 0; i < 3; i++) {
            graphics.fill(x2 - i, y - i, x2 + 1 - i, y + 1 + i, ARROW_DIM);
        }
    }

    /** 一组槽的外框（用于「副产物」这种松散摆放的格子） */
    public static void groupFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + 1, EDGE_DARK);
        graphics.fill(x, y + height - 1, x + width, y + height, EDGE_DARK);
        graphics.fill(x, y, x + 1, y + height, EDGE_DARK);
        graphics.fill(x + width - 1, y, x + width, y + height, EDGE_DARK);
    }

    private CanvasPainter() {
    }
}
