package com.wowuwo233.simple_recipe_change.menu.canvas;

import static com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu.*;

/** 工作台 / 物品栏合成：3×3（或 2×2）材料 + 产物 */
public final class CraftingCanvas implements RecipeCanvas {

    private static final int CELL = 18;
    private static final int INSET = 2;      // 凹槽内缩，让边缘有厚度
    private static final int RECESS = 0xFF2B2B2B;
    private static final int EDGE_DARK = 0xFF141414;
    private static final int EDGE_LIGHT = 0xFF5E5E5E;
    private static final int ARROW = 0xFF7A7A7A;

    /** 工作台不用逐格方框，整块画成一张合成台底图 */
    @Override
    public boolean drawSlotFrames() {
        return false;
    }

    @Override
    public void background(net.minecraft.client.gui.GuiGraphics graphics,
                           int leftPos, int topPos, CanvasContext ctx) {
        int gridX = leftPos + (ctx.adding() ? ADD_GRID_X : MOD_GRID_X);
        int outX = leftPos + (ctx.adding() ? ADD_OUTPUT_X : MOD_OUTPUT_X);
        int rowY = topPos + ROW_Y;

        // 3×3 凹槽
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int x = gridX + col * CELL;
                int y = topPos + CRAFT_GRID_Y + row * CELL;
                CanvasPainter.recessAtSlot(graphics, x, y);
            }
        }
        // 产物凹槽
        CanvasPainter.recessAtSlot(graphics, outX, rowY);
    }

    /** 一个凹槽：内部深色 + 左上暗边 + 右下亮边，看起来是「凹进去」的 */
    private static void drawRecess(net.minecraft.client.gui.GuiGraphics graphics,
                                   int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, RECESS);
        graphics.fill(x, y, x + size, y + 1, EDGE_DARK);
        graphics.fill(x, y, x + 1, y + size, EDGE_DARK);
        graphics.fill(x, y + size - 1, x + size, y + size, EDGE_LIGHT);
        graphics.fill(x + size - 1, y, x + size, y + size, EDGE_LIGHT);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, RECESS);
    }

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
