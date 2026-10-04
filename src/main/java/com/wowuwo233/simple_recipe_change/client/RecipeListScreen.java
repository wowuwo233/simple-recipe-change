package com.wowuwo233.simple_recipe_change.client;

import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndexEntry;
import com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu;
import com.wowuwo233.simple_recipe_change.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * 「我的配方」列表：查看本模组写过的配方，可载回编辑器改，也可以直接删除。
 *
 * <p>数据来自服务端的索引文件（{@code kubejs/server_scripts/simple_recipe_change.index.json}），
 * 删除时会从 .js 文件里整块移除对应内容，手写脚本不受影响。
 */
@OnlyIn(Dist.CLIENT)
public class RecipeListScreen extends Screen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 240;
    private static final int PER_PAGE = 6;
    private static final int ROW_HEIGHT = 24;

    private static final int COLOR_PANEL = 0xFF2B2B2B;
    private static final int COLOR_TITLE_BAR = 0xFF3C3C3C;
    private static final int COLOR_BORDER = 0xFF8B8B8B;
    private static final int COLOR_ROW = 0xFF1B1B1B;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_TEXT_DIM = 0xFF9A9A9A;

    private final RecipeEditorScreen parent;
    private List<RecipeIndexEntry> entries;
    private int page = 0;

    public RecipeListScreen(RecipeEditorScreen parent) {
        super(Component.literal("我的配方"));
        this.parent = parent;
        this.entries = ClientScreenHooks.cachedIndex();
    }

    /** 收到新的索引后由 ClientScreenHooks 调用 */
    public void refresh() {
        this.entries = ClientScreenHooks.cachedIndex();
        this.rebuild();
    }

    private int left() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (this.height - PANEL_HEIGHT) / 2;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        this.clearWidgets();

        int left = left();
        int top = top();

        int maxPage = Math.max(0, (this.entries.size() - 1) / PER_PAGE);
        if (this.page > maxPage) {
            this.page = maxPage;
        }
        if (this.page < 0) {
            this.page = 0;
        }

        int start = this.page * PER_PAGE;
        int end = Math.min(this.entries.size(), start + PER_PAGE);

        for (int i = start; i < end; i++) {
            RecipeIndexEntry entry = this.entries.get(i);
            int rowY = top + 30 + (i - start) * ROW_HEIGHT;

            this.addRenderableWidget(Button.builder(Component.literal("载入"), b -> {
                        ModNetwork.requestLoad(entry.blockId());
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(this.parent);
                        }
                    })
                    .bounds(left + PANEL_WIDTH - 118, rowY + 3, 50, 18)
                    .build());

            this.addRenderableWidget(Button.builder(Component.literal("删除"), b ->
                            ModNetwork.requestDelete(entry.blockId()))
                    .bounds(left + PANEL_WIDTH - 64, rowY + 3, 50, 18)
                    .build());
        }

        Button prev = this.addRenderableWidget(Button.builder(Component.literal("< 上一页"), b -> {
            this.page--;
            this.rebuild();
        }).bounds(left + 8, top + PANEL_HEIGHT - 26, 64, 18).build());
        prev.active = this.page > 0;

        Button next = this.addRenderableWidget(Button.builder(Component.literal("下一页 >"), b -> {
            this.page++;
            this.rebuild();
        }).bounds(left + 78, top + PANEL_HEIGHT - 26, 64, 18).build());
        next.active = this.page < maxPage;

        this.addRenderableWidget(Button.builder(Component.literal("返回编辑器"), b -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parent);
                    }
                })
                .bounds(left + PANEL_WIDTH - 92, top + PANEL_HEIGHT - 26, 84, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int left = left();
        int top = top();

        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOR_PANEL);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 20, COLOR_TITLE_BAR);
        graphics.fill(left, top, left + PANEL_WIDTH, top + 1, COLOR_BORDER);
        graphics.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOR_BORDER);
        graphics.fill(left, top, left + 1, top + PANEL_HEIGHT, COLOR_BORDER);
        graphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, COLOR_BORDER);

        String total = this.entries.isEmpty() ? "" : "（共 " + this.entries.size() + " 条）";
        graphics.drawString(this.font, "我的配方  " + total, left + 8, top + 6, COLOR_TEXT, false);

        if (this.entries.isEmpty()) {
            graphics.drawString(this.font, "还没有写过配方。", left + 12, top + 40, COLOR_TEXT_DIM, false);
            graphics.drawString(this.font, "回到编辑器保存一次，这里就会出现记录。",
                    left + 12, top + 52, COLOR_TEXT_DIM, false);
        } else {
            int start = this.page * PER_PAGE;
            int end = Math.min(this.entries.size(), start + PER_PAGE);
            for (int i = start; i < end; i++) {
                RecipeIndexEntry entry = this.entries.get(i);
                int rowY = top + 30 + (i - start) * ROW_HEIGHT;

                graphics.fill(left + 6, rowY, left + PANEL_WIDTH - 6, rowY + ROW_HEIGHT - 2, COLOR_ROW);

                ItemStack stack = RecipeEditorMenu.stackOf(entry.outputItem());
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, left + 10, rowY + 3);
                }

                String title = stack.isEmpty() ? "（无产物）" : stack.getHoverName().getString();
                if (entry.outputCount() > 1) {
                    title += " ×" + entry.outputCount();
                }
                graphics.drawString(this.font, title, left + 30, rowY + 3, COLOR_TEXT, false);

                String sub = entry.operation().label() + " · " + entry.fileName();
                graphics.drawString(this.font, trim(sub, 150), left + 30, rowY + 13,
                        COLOR_TEXT_DIM, false);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String trim(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (this.font.width(sb.toString() + text.charAt(i)) > maxWidth - 6) {
                break;
            }
            sb.append(text.charAt(i));
        }
        return sb + "…";
    }
}
