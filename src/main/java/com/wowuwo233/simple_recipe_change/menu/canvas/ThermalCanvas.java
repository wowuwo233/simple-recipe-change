package com.wowuwo233.simple_recipe_change.menu.canvas;

import net.minecraft.client.gui.GuiGraphics;

import static com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu.*;

/** 热力膨胀：3×3 材料 + 产物 + 副产物行（吃流体的机器多一个储罐格） */
public final class ThermalCanvas implements RecipeCanvas {

    @Override
    public boolean drawSlotFrames() {
        return false;
    }

    @Override
    public void layout(CanvasContext ctx, SlotPlacer placer) {
        for (int i = 0; i < CREATE_MATERIAL_SLOTS; i++) {
            placer.input(i, CREATE_GRID_X + (i % 3) * SLOT_SIZE,
                    CREATE_GRID_Y + (i / 3) * SLOT_SIZE);
        }
        if (ctx.type().hasFluidSlot()) {
            placer.fluid(FLUID_CELL, FLUID_SLOT_X, FLUID_SLOT_Y);
        }
        placer.output(CREATE_OUTPUT_X, CREATE_GRID_Y + SLOT_SIZE);
        for (int i = 0; i < ctx.extraOutputs(); i++) {
            placer.extra(i, EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP, CREATE_EXTRA_ROW_Y);
        }
    }

    @Override
    public void background(GuiGraphics graphics, int leftPos, int topPos, CanvasContext ctx) {
        for (int i = 0; i < CREATE_MATERIAL_SLOTS; i++) {
            CanvasPainter.recessAtSlot(graphics,
                    leftPos + CREATE_GRID_X + (i % 3) * SLOT_SIZE,
                    topPos + CREATE_GRID_Y + (i / 3) * SLOT_SIZE);
        }
        CanvasPainter.recessAtSlot(graphics, leftPos + CREATE_OUTPUT_X,
                topPos + CREATE_GRID_Y + SLOT_SIZE);
        CanvasPainter.arrowRight(graphics,
                leftPos + (ctx.type().hasFluidSlot()
                        ? FLUID_SLOT_X + SLOT_SIZE : CREATE_GRID_X + 3 * SLOT_SIZE) + 4,
                leftPos + CREATE_OUTPUT_X - 4,
                topPos + CREATE_GRID_Y + SLOT_SIZE + 9);

        for (int i = 0; i < ctx.extraOutputs(); i++) {
            CanvasPainter.recessAtSlot(graphics,
                    leftPos + EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP,
                    topPos + CREATE_EXTRA_ROW_Y);
        }
    }
}
