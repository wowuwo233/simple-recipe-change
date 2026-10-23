package com.wowuwo233.simple_recipe_change.client;

import com.wowuwo233.simple_recipe_change.ModCompat;
import com.wowuwo233.simple_recipe_change.kubejs.CreateMachine;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 配方类型选择界面（分组可折叠）。
 *
 * <p>以前「分组」「类型」是两个循环按钮，热力膨胀 32 个类型要按三十多下。
 * 现在点开这个界面，按分组折叠展示：点分组标题展开/收起，点具体类型切换。
 *
 * <p><b>默认全部折叠</b>，只展开当前类型所在的分组——这样打开就能看到自己在哪，
 * 又不至于一屏全是类型。
 */
@OnlyIn(Dist.CLIENT)
public class TypePickerScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 260;
    private static final int HEADER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 16;
    private static final int LIST_TOP = 30;

    private static final int COLOR_PANEL = 0xFF2B2B2B;
    private static final int COLOR_TITLE_BAR = 0xFF3C3C3C;
    private static final int COLOR_BORDER = 0xFF8B8B8B;
    private static final int COLOR_HEADER = 0xFF3A3A3A;
    private static final int COLOR_HEADER_HOVER = 0xFF4A4A4A;
    private static final int COLOR_ROW = 0xFF1B1B1B;
    private static final int COLOR_ROW_HOVER = 0xFF3D5A7A;
    private static final int COLOR_ROW_CURRENT = 0xFF2F5F3F;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_TEXT_DIM = 0xFF9A9A9A;
    private static final int COLOR_ACCENT = 0xFFA8C0D8;

    /**
     * 一行三种：
     * <ul>
     *   <li>分组标题：type 与 machine 都为 null</li>
     *   <li>具体类型：machine 为 null</li>
     *   <li>机械动力的机器：type 是处理配方，machine 具体机器</li>
     * </ul>
     */
    private record Row(RecipeType.Group group, RecipeType type, CreateMachine machine,
                       boolean subheader) {
    }

    private final RecipeEditorScreen parent;
    private final Set<RecipeType.Group> expanded = EnumSet.noneOf(RecipeType.Group.class);
    private int scroll;
    /** 「处理配方」下面的 14 台机器是否展开（和分组折叠一样，默认按当前类型决定） */
    private boolean machinesOpen;

    public TypePickerScreen(RecipeEditorScreen parent) {
        super(Component.literal("选择配方类型"));
        this.parent = parent;
        // 默认全折叠，但把当前类型所在的分组展开
        RecipeType current = parent.currentType();
        if (current != null && ModCompat.available(current.group())) {
            expanded.add(current.group());
        }
        // 当前就在用某台机器时，进来就能看到是哪台
        this.machinesOpen = current == RecipeType.CREATE_PROCESSING;
    }

    /** 按当前展开状态算出要显示的行 */
    private List<Row> visibleRows() {
        List<Row> rows = new ArrayList<>();
        for (RecipeType.Group group : RecipeType.Group.values()) {
            if (!ModCompat.available(group)) {
                continue;
            }
            rows.add(new Row(group, null, null, false));
            if (!expanded.contains(group)) {
                continue;
            }
            for (RecipeType type : RecipeType.values()) {
                if (type.group() != group) {
                    continue;
                }
                if (type == RecipeType.CREATE_PROCESSING) {
                    // 处理配方本身是个二级标题：点它展开/收起机器，点机器才算选中
                    rows.add(new Row(group, type, null, true));
                    if (machinesOpen) {
                        for (CreateMachine machine : CreateMachine.values()) {
                            rows.add(new Row(group, type, machine, false));
                        }
                    }
                } else {
                    rows.add(new Row(group, type, null, false));
                }
            }
        }
        return rows;
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

    private int maxScroll(List<Row> rows) {
        int content = 0;
        for (Row row : rows) {
            content += row.type() == null ? HEADER_HEIGHT : ROW_HEIGHT;
        }
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
        graphics.drawString(this.font, "点分组展开 · 滚轮翻动 · Esc 返回",
                left + PANEL_WIDTH - 168, top + 7, COLOR_TEXT_DIM, false);

        List<Row> rows = visibleRows();
        int x = left + 6;
        int width = PANEL_WIDTH - 12;
        int y = top + LIST_TOP - scroll;

        graphics.enableScissor(left + 2, top + LIST_TOP, left + PANEL_WIDTH - 2, listBottom());
        for (Row row : rows) {
            int height = row.type() == null ? HEADER_HEIGHT : ROW_HEIGHT;
            if (y + height >= top + LIST_TOP && y <= listBottom()) {
                boolean hover = mouseX >= x && mouseX < x + width
                        && mouseY >= y && mouseY < y + height - 1;
                if (row.type() == null) {
                    boolean open = expanded.contains(row.group());
                    graphics.fill(x, y, x + width, y + height - 1,
                            hover ? COLOR_HEADER_HOVER : COLOR_HEADER);
                    // ▾ 展开 / ▸ 折叠
                    graphics.drawString(this.font, open ? "▾ " + row.group().label()
                            : "▸ " + row.group().label(), x + 4, y + 5, COLOR_ACCENT, false);
                } else if (row.subheader()) {
                    graphics.fill(x, y, x + width, y + height - 1,
                            hover ? COLOR_HEADER_HOVER : COLOR_ROW);
                    graphics.drawString(this.font,
                            machinesOpen ? "  ▾ " + row.type().label() : "  ▸ " + row.type().label(),
                            x + 4, y + 4, COLOR_ACCENT, false);
                } else {
                    boolean machineRow = row.machine() != null;
                    boolean current = machineRow
                            ? this.parent.currentType() == row.type()
                                    && this.parent.currentMachine() == row.machine()
                            : row.type() == this.parent.currentType()
                                    && !this.parent.currentType().isCreate();
                    int color = current ? COLOR_ROW_CURRENT
                            : hover ? COLOR_ROW_HOVER : COLOR_ROW;
                    graphics.fill(x, y, x + width, y + height - 1, color);
                    String label = machineRow ? row.machine().label() : row.type().label();
                    graphics.drawString(this.font, (machineRow ? "        " : "    ") + label,
                            x + 4, y + 4, current ? COLOR_TEXT : COLOR_TEXT_DIM, false);
                    if (current) {
                        graphics.drawString(this.font, "当前", x + width - 26, y + 4,
                                COLOR_TEXT, false);
                    }
                }
            }
            y += height;
        }
        graphics.disableScissor();

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        List<Row> rows = visibleRows();
        int x = left() + 6;
        int width = PANEL_WIDTH - 12;
        int y = top() + LIST_TOP - scroll;
        for (Row row : rows) {
            int height = row.type() == null ? HEADER_HEIGHT : ROW_HEIGHT;
            boolean inside = mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height - 1;
            if (inside && row.type() == null) {
                // 点分组标题：展开 / 收起
                if (!expanded.remove(row.group())) {
                    expanded.add(row.group());
                }
                this.scroll = Math.min(this.scroll, maxScroll(visibleRows()));
                return true;
            }
            if (inside && row.subheader()) {
                // 点「处理配方」：展开 / 收起下面的机器
                this.machinesOpen = !this.machinesOpen;
                this.scroll = Math.min(this.scroll, maxScroll(visibleRows()));
                return true;
            }
            if (inside && row.type() != null) {
                this.parent.applyType(row.type(), row.machine());
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
        this.scroll = Math.max(0,
                Math.min(maxScroll(visibleRows()), this.scroll - (int) (delta * ROW_HEIGHT)));
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
