package com.wowuwo233.simple_recipe_change.client;

import com.wowuwo233.simple_recipe_change.ModCompat;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方类型选择界面。
 *
 * <p>以前「分组」「类型」是两个循环按钮，装的热力膨胀有 32 个类型，切一次要按三十多下。
 * 现在点一下按钮打开这个界面，按分组列出来，点哪个用哪个。
 *
 * <p>只列当前整合包装了的分组（见 {@link ModCompat#available}），没装的模组不会出现。
 */
@OnlyIn(Dist.CLIENT)
public class TypePickerScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 260;
    private static final int HEADER_HEIGHT = 16;
    private static final int ROW_HEIGHT = 16;
    private static final int LIST_TOP = 30;

    private static final int COLOR_PANEL = 0xFF2B2B2B;
    private static final int COLOR_TITLE_BAR = 0xFF3C3C3C;
    private static final int COLOR_BORDER = 0xFF8B8B8B;
    private static final int COLOR_HEADER = 0xFF3A3A3A;
    private static final int COLOR_ROW = 0xFF1B1B1B;
    private static final int COLOR_ROW_HOVER = 0xFF3D5A7A;
    private static final int COLOR_ROW_CURRENT = 0xFF2F5F3F;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_TEXT_DIM = 0xFF9A9A9A;
    private static final int COLOR_ACCENT = 0xFFA8C0D8;

    /** 一行：分组标题（type 为 null）或具体类型 */
    private record Row(RecipeType.Group group, RecipeType type) {
    }

    private final RecipeEditorScreen parent;
    private final List<Row> rows = new ArrayList<>();
    private int scroll;

    public TypePickerScreen(RecipeEditorScreen parent) {
        super(Component.literal("选择配方类型"));
        this.parent = parent;
        for (RecipeType.Group group : RecipeType.Group.values()) {
            if (!ModCompat.available(group)) {
                continue;
            }
            rows.add(new Row(group, null));
            for (RecipeType type : RecipeType.values()) {
                if (type.group() == group) {
                    rows.add(new Row(group, type));
                }
            }
        }
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (this.height - PANEL_HEIGHT) / 2;
    }

    private int listBottom() {
        return top() + PANEL_HEIGHT - 8;
    }

    private int maxScroll() {
        int content = rows.size() * ROW_HEIGHT;
        int visible = listBottom() - (top() + LIST_TOP);
        return Math.max(0, content - visible);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int left = left();
        int top = top();

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOR_PANEL);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 22, COLOR_TITLE_BAR);
        drawBorder(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT, COLOR_BORDER);

        graphics.drawString(this.font, "选择配方类型", left + 8, top + 7, COLOR_TEXT, false);
        graphics.drawString(this.font, "滚轮翻动 · Esc 返回", left + PANEL_WIDTH - 130, top + 7,
                COLOR_TEXT_DIM, false);

        int x = left + 6;
        int width = PANEL_WIDTH - 12;
        int y = top + LIST_TOP - scroll;

        graphics.enableScissor(left + 2, top + LIST_TOP, left + PANEL_WIDTH - 2, listBottom());
        for (Row row : rows) {
            if (y + ROW_HEIGHT >= top + LIST_TOP && y <= listBottom()) {
                if (row.type() == null) {
                    graphics.fill(x, y, x + width, y + HEADER_HEIGHT - 2, COLOR_HEADER);
                    graphics.drawString(this.font, row.group().label(), x + 4, y + 3,
                            COLOR_ACCENT, false);
                } else {
                    boolean hover = mouseX >= x && mouseX < x + width
                            && mouseY >= y && mouseY < y + ROW_HEIGHT - 1;
                    boolean current = row.type() == this.parent.currentType();
                    int color = current ? COLOR_ROW_CURRENT
                            : hover ? COLOR_ROW_HOVER : COLOR_ROW;
                    graphics.fill(x, y, x + width, y + ROW_HEIGHT - 1, color);
                    graphics.drawString(this.font, "  " + row.type().label(), x + 4, y + 4,
                            current ? COLOR_TEXT : COLOR_TEXT_DIM, false);
                    if (current) {
                        graphics.drawString(this.font, "当前", x + width - 26, y + 4,
                                COLOR_TEXT, false);
                    }
                }
            }
            y += row.type() == null ? HEADER_HEIGHT : ROW_HEIGHT;
        }
        graphics.disableScissor();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int x = left() + 6;
        int width = PANEL_WIDTH - 12;
        int y = top() + LIST_TOP - scroll;
        for (Row row : rows) {
            int height = row.type() == null ? HEADER_HEIGHT : ROW_HEIGHT;
            if (row.type() != null && mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height - 1) {
                this.parent.applyType(row.type());
                if (this.minecraft != null) {
                    this.minecraft.setScreen(this.parent);
                }
                return true;
            }
            y += height;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll - (int) (delta * ROW_HEIGHT)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height,
                                   int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
