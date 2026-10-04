package com.wowuwo233.simple_recipe_change.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wowuwo233.simple_recipe_change.ModCompat;
import com.wowuwo233.simple_recipe_change.kubejs.KubeJsWriter;
import com.wowuwo233.simple_recipe_change.kubejs.Operation;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeDraft;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import com.wowuwo233.simple_recipe_change.kubejs.RemoveBy;
import com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu;
import com.wowuwo233.simple_recipe_change.network.ModNetwork;
import com.wowuwo233.simple_recipe_change.search.ItemSearch;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方编辑器界面（全中文）。
 *
 * <p>顶部：添加配方 / 修改配方 / 删除配方 / 我的配方 / 重载。
 * 布局随配方类型变化，并且每个栏位下面都标了名字。
 *
 * <p><b>输入槽是展示槽</b>：里面没有真实物品，格子内容以物品 ID 字符串保存在客户端，
 * 由本界面自己绘制。点一下槽位即可用光标上的物品设置它，空手点则清空。
 * 这样既不可能刷物品，也能原样保留 {@code #标签} 写法。
 */
@OnlyIn(Dist.CLIENT)
public class RecipeEditorScreen extends AbstractContainerScreen<RecipeEditorMenu> {

    private static final int COLOR_PANEL = 0xFF2B2B2B;
    private static final int COLOR_TITLE_BAR = 0xFF3C3C3C;
    private static final int COLOR_BORDER = 0xFF8B8B8B;
    private static final int COLOR_SLOT_BG = 0xFF1A1A1A;
    private static final int COLOR_SLOT_BORDER = 0xFF5A5A5A;
    private static final int COLOR_SLOT_DISABLED = 0xFF6B3030;
    private static final int COLOR_DISABLED_TINT = 0x66FF0000;
    private static final int COLOR_OVERLAY_BG = 0xFF1E1E1E;
    private static final int COLOR_CELL = 0xFF141414;
    private static final int COLOR_CELL_HOVER = 0xFF4A5A6A;
    private static final int COLOR_BUTTON_BG = 0xFF3A3A3A;
    private static final int COLOR_BUTTON_HOVER = 0xFF4E4E4E;
    private static final int COLOR_BUTTON_OFF = 0xFF262626;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_TEXT_DIM = 0xFF9A9A9A;
    private static final int COLOR_LABEL = 0xFFCFCFCF;
    private static final int COLOR_SLOT_LABEL = 0xFFA8C0D8;
    private static final int COLOR_OK = 0xFF55FF55;
    private static final int COLOR_FAIL = 0xFFFF5555;

    private static final int MAX_STATUS_LINES = 2;

    // 版面纵坐标
    private static final int MODE_Y = 22;
    private static final int SEARCH_Y = 46;
    private static final int FOUND_Y = 142;
    private static final int CONTROLS_Y = 158;
    private static final int LABEL_Y = 184;
    private static final int FIELD_Y = 194;
    private static final int BUTTONS_Y = 218;
    private static final int STATUS_Y = 242;

    // 搜索浮层：从搜索框下方一直铺到窗口底部，搜索时把下半部分（含玩家背包）整块盖住。
    // 盖不住的槽位靠控件隐藏兜底，避免出现「透出来叠字」。
    private static final int OVERLAY_X = 10;
    private static final int OVERLAY_Y = 68;
    private static final int OVERLAY_W = 320;
    private static final int OVERLAY_H = 270;
    private static final int OVERLAY_COLS = 9;
    private static final int OVERLAY_ROWS = 8;
    private static final int CELL = 18;
    private static final int PER_PAGE = OVERLAY_COLS * OVERLAY_ROWS;

    private Operation operation = Operation.ADD;
    private RecipeType recipeType = RecipeType.CRAFTING_TABLE;
    private RemoveBy removeBy = RemoveBy.OUTPUT;
    private boolean shapeless = false;
    private boolean mirrored = true;

    private boolean found = false;
    private String foundSummary = "";

    /** 输入槽内容（物品 ID，空格为 null）——由客户端维护，服务端只负责回填 */
    private List<String> inputItems = blankInputs();

    private EditBox idField;
    private EditBox fileField;
    private EditBox searchField;
    private EditBox xpField;
    private EditBox timeField;

    private final Button[] modeButtons = new Button[3];
    private Button secondaryButton;
    /** 「原版 / 农夫乐事」分组切换；类型按钮只在当前分组内循环 */
    private Button groupButton;
    /**
     * 所有类型通用：勾上之后，输入槽里的物品会写成<b>标签</b>而不是具体物品。
     *
     * <p>比如放石斧就生成 {@code #minecraft:axes}，这样钻石斧也同样能满足这条配方。
     */
    private Button tagButton;
    private boolean useItemTags = false;
    private Button shapelessButton;
    private Button mirrorButton;
    private Button saveButton;
    private Button reloadButton;
    private Button clearButton;

    private Boolean statusOk = null;
    private String statusMessage = "";

    private List<ItemSearch.Entry> searchResults = List.of();
    private int searchPage = 0;
    private boolean inventorySource = false;
    private ItemSearch.Entry hoveredResult;

    /** 切菜板的额外产物（客户端维护的展示数据，最多 4 个） */
    private List<String> extraItems = blankExtras();
    /** 每个额外产物右边的概率输入框（0–1，留空按 1 处理） */
    private final List<EditBox> extraChanceFields = new ArrayList<>();

    public RecipeEditorScreen(RecipeEditorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = RecipeEditorMenu.IMAGE_WIDTH;
        this.imageHeight = RecipeEditorMenu.IMAGE_HEIGHT;
        this.recipeType = menu.getRecipeType();
        // 兜底：万一类型停在没装的分组上（比如整合包中途去掉了农夫乐事），退回原版
        if (!ModCompat.available(this.recipeType.group())) {
            this.recipeType = RecipeType.firstOf(RecipeType.Group.VANILLA);
        }
        this.operation = menu.getOperation();
    }

    private static List<String> blankExtras() {
        List<String> list = new ArrayList<>(RecipeEditorMenu.EXTRA_OUTPUT_COUNT);
        for (int i = 0; i < RecipeEditorMenu.EXTRA_OUTPUT_COUNT; i++) {
            list.add(null);
        }
        return list;
    }

    private void setExtra(int index, String itemId) {
        while (this.extraItems.size() <= index) {
            this.extraItems.add(null);
        }
        this.extraItems.set(index, itemId);
    }

    /** 把额外产物格 + 概率框收成列表；概率留空或写坏了都按 1 处理 */
    private List<RecipeDraft.ExtraOutput> collectExtraOutputs() {
        List<RecipeDraft.ExtraOutput> out = new ArrayList<>();
        if (!this.recipeType.hasMultipleOutputs()) {
            return out;
        }
        for (int i = 0; i < this.extraItems.size(); i++) {
            String item = this.extraItems.get(i);
            if (item == null || item.isBlank()) {
                continue;
            }
            double chance = 1D;
            if (i < this.extraChanceFields.size()) {
                String text = this.extraChanceFields.get(i).getValue().trim();
                if (!text.isEmpty()) {
                    try {
                        chance = Double.parseDouble(text);
                    } catch (NumberFormatException ignored) {
                        chance = 1D;
                    }
                }
            }
            out.add(new RecipeDraft.ExtraOutput(item, 1, chance));
        }
        return out;
    }

    private static List<String> blankInputs() {
        List<String> list = new ArrayList<>(RecipeEditorMenu.GRID_SIZE);
        for (int i = 0; i < RecipeEditorMenu.GRID_SIZE; i++) {
            list.add(null);
        }
        return list;
    }

    // ------------------------------------------------------------------ 初始化

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        Operation[] modes = {Operation.ADD, Operation.MODIFY, Operation.REMOVE};
        int[] modeX = {10, 74, 138};
        for (int i = 0; i < modes.length; i++) {
            final Operation mode = modes[i];
            modeButtons[i] = this.addRenderableWidget(Button.builder(
                            Component.literal(mode.label()), b -> switchMode(mode))
                    .bounds(this.leftPos + modeX[i], this.topPos + MODE_Y, 60, 20)
                    .build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("我的配方"), b -> {
                    ModNetwork.requestIndex();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new RecipeListScreen(this));
                    }
                })
                .bounds(this.leftPos + 202, this.topPos + MODE_Y, 66, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("重载"), b -> {
                    ModNetwork.requestReload();
                    setStatus(true, "已请求重载，KubeJS 会重新读取脚本");
                })
                .bounds(this.leftPos + 272, this.topPos + MODE_Y, 58, 20)
                .build());

        String previous = this.searchField == null ? "" : this.searchField.getValue();
        this.searchField = new EditBox(this.font, this.leftPos + 12, this.topPos + SEARCH_Y,
                RecipeEditorMenu.IMAGE_WIDTH - 24, 18, Component.literal("搜索物品"));
        this.searchField.setMaxLength(64);
        this.searchField.setHint(Component.literal("搜索物品：中文名 / 拼音 / @模组名"));
        this.searchField.setValue(previous);
        this.searchField.setResponder(text -> {
            this.searchResults = ItemSearch.filter(text, candidates());
            this.searchPage = 0;
            updateWidgetVisibility();
        });
        this.addRenderableWidget(this.searchField);

        this.secondaryButton = this.addRenderableWidget(Button.builder(
                        Component.literal(secondaryLabel()), b -> cycleSecondary())
                .bounds(this.leftPos + 12, this.topPos + CONTROLS_Y, 80, 20)
                .build());

        this.groupButton = this.addRenderableWidget(Button.builder(
                        Component.literal(groupLabel()), b -> cycleGroup())
                .bounds(this.leftPos + 96, this.topPos + CONTROLS_Y, 80, 20)
                .build());

        this.shapelessButton = this.addRenderableWidget(Button.builder(
                        Component.literal(shapelessLabel()), b -> {
                            this.shapeless = !this.shapeless;
                            refreshButtonStates();
                        })
                .bounds(this.leftPos + 180, this.topPos + CONTROLS_Y, 74, 20)
                .build());

        this.mirrorButton = this.addRenderableWidget(Button.builder(
                        Component.literal(mirrorLabel()), b -> {
                            this.mirrored = !this.mirrored;
                            refreshButtonStates();
                        })
                .bounds(this.leftPos + 258, this.topPos + CONTROLS_Y, 70, 20)
                .build());

        // 烧炼专用的经验与时间
        String xpText = this.xpField == null ? "0" : this.xpField.getValue();
        String timeText = this.timeField == null ? String.valueOf(RecipeDraft.DEFAULT_COOKING_TIME)
                : this.timeField.getValue();
        this.xpField = new EditBox(this.font, this.leftPos + 150, this.topPos + 92, 178, 18,
                Component.literal("经验"));
        this.xpField.setMaxLength(10);
        this.xpField.setValue(xpText);
        this.addRenderableWidget(this.xpField);

        this.timeField = new EditBox(this.font, this.leftPos + 150, this.topPos + 120, 178, 18,
                Component.literal("烧制时间"));
        this.timeField.setMaxLength(10);
        this.timeField.setValue(timeText);
        this.addRenderableWidget(this.timeField);

        this.idField = new EditBox(this.font, this.leftPos + 12, this.topPos + FIELD_Y, 152, 18,
                Component.literal("配方名"));
        this.idField.setMaxLength(200);
        this.idField.setHint(Component.literal("留空自动生成"));
        this.addRenderableWidget(this.idField);

        this.fileField = new EditBox(this.font, this.leftPos + 176, this.topPos + FIELD_Y, 152, 18,
                Component.literal("文件名"));
        this.fileField.setMaxLength(120);
        this.fileField.setValue(KubeJsWriter.DEFAULT_FILE_NAME);
        this.addRenderableWidget(this.fileField);

        // 切菜板的额外产物：每个槽右边一个概率框，非切菜板类型整体隐藏
        this.extraChanceFields.clear();
        for (int i = 0; i < RecipeEditorMenu.EXTRA_OUTPUT_COUNT; i++) {
            EditBox box = new EditBox(this.font,
                    this.leftPos + RecipeEditorMenu.EXTRA_SLOT_X0
                            + i * RecipeEditorMenu.EXTRA_SLOT_STEP + 20,
                    this.topPos + RecipeEditorMenu.EXTRA_ROW_Y, 56, 18,
                    Component.literal("概率"));
            box.setMaxLength(6);
            box.setValue("1");
            this.extraChanceFields.add(this.addRenderableWidget(box));
        }

        // 「物品用标签」开关：所有类型都有，可以把输入槽物品换成同标签的任意物品
        this.tagButton = this.addRenderableWidget(Button.builder(
                        Component.literal(tagButtonLabel()), b -> {
                            this.useItemTags = !this.useItemTags;
                            refreshButtonStates();
                        })
                .bounds(this.leftPos + RecipeEditorMenu.TAG_BUTTON_X,
                        this.topPos + RecipeEditorMenu.TAG_BUTTON_Y,
                        RecipeEditorMenu.TAG_BUTTON_W, 20)
                .build());

        this.saveButton = this.addRenderableWidget(Button.builder(Component.literal("保存并写入"), b -> save())
                .bounds(this.leftPos + 12, this.topPos + BUTTONS_Y, 84, 20)
                .build());

        this.reloadButton = this.addRenderableWidget(Button.builder(Component.literal("重载脚本"), b -> {
                    ModNetwork.requestReload();
                    setStatus(true, "已请求重载，KubeJS 会重新读取脚本");
                })
                .bounds(this.leftPos + 100, this.topPos + BUTTONS_Y, 84, 20)
                .build());

        this.clearButton = this.addRenderableWidget(Button.builder(Component.literal("清空编辑区"), b -> {
                    ModNetwork.requestClearEditor();
                    this.inputItems = blankInputs();
                    setStatus(true, "已清空编辑区");
                })
                .bounds(this.leftPos + 188, this.topPos + BUTTONS_Y, 76, 20)
                .build());

        applySlotLayout();
        refreshButtonStates();
        updateWidgetVisibility();
    }

    private void switchMode(Operation mode) {
        if (this.operation == mode) {
            return;
        }
        this.operation = mode;
        // 槽位布局等服务端确认后再重建，避免两端槽位数短暂不一致
        ModNetwork.requestSetMode(this.operation, this.recipeType);
        refreshButtonStates();
    }

    private void cycleSecondary() {
        if (this.operation == Operation.REMOVE) {
            this.removeBy = this.removeBy.next();
            refreshButtonStates();
            return;
        }
        this.recipeType = this.recipeType.next();
        this.inputItems = blankInputs();
        this.extraItems = blankExtras();
        ModNetwork.requestSetMode(this.operation, this.recipeType);
        applySlotLayout();
        refreshButtonStates();
        updateWidgetVisibility();
    }

    private void applySlotLayout() {
        // 注意：不能用 menu.getRecipeType() 反写回本地类型——客户端菜单对象更新得比这里晚，
        // 那样会把刚切换的类型直接抹掉，表现为「类型点不动」。
        this.menu.applyLayout(this.operation, this.recipeType);
    }

    private void refreshButtonStates() {
        Operation[] modes = {Operation.ADD, Operation.MODIFY, Operation.REMOVE};
        for (int i = 0; i < modeButtons.length; i++) {
            if (modeButtons[i] != null) {
                modeButtons[i].active = this.operation != modes[i];
            }
        }
        if (this.secondaryButton != null) {
            this.secondaryButton.setMessage(Component.literal(secondaryLabel()));
        }
        if (this.groupButton != null) {
            // 没装农夫乐事 / KubeJSDelight 时只有一个分组，按钮置灰
            this.groupButton.active = ModCompat.hasMultipleGroups();
            this.groupButton.setMessage(Component.literal(groupLabel()));
        }
        if (this.tagButton != null) {
            this.tagButton.setMessage(Component.literal(tagButtonLabel()));
        }
        boolean adding = this.operation == Operation.ADD;
        boolean crafting = this.recipeType.isCrafting();
        if (this.shapelessButton != null) {
            this.shapelessButton.active = adding && crafting;
            this.shapelessButton.setMessage(Component.literal(shapelessLabel()));
        }
        if (this.mirrorButton != null) {
            this.mirrorButton.active = adding && crafting && !this.shapeless;
            this.mirrorButton.setMessage(Component.literal(mirrorLabel()));
        }
    }

    private String secondaryLabel() {
        if (this.operation == Operation.REMOVE) {
            return "依据：" + this.removeBy.label();
        }
        return "类型：" + this.recipeType.shortLabel();
    }

    /**
     * 切换来源分组：原版 ←→ 农夫乐事。
     *
     * <p>切过去之后停在目标分组的第一个类型上；「类型」按钮只在组内循环，
     * 不用从工作台一路按到烹饪锅。
     */
    private void cycleGroup() {
        // 只切到「装了」的分组；一个分组都没得选（只有原版）时按钮是灰的，这里再兜一层
        RecipeType.Group from = this.recipeType.group();
        RecipeType.Group target = from;
        for (int i = 0; i < RecipeType.Group.values().length; i++) {
            target = target.next();
            if (ModCompat.available(target)) {
                break;
            }
        }
        if (target == from) {
            return;
        }
        this.recipeType = RecipeType.firstOf(target);
        this.inputItems = blankInputs();
        this.extraItems = blankExtras();
        ModNetwork.requestSetMode(this.operation, this.recipeType);
        applySlotLayout();
        refreshButtonStates();
        updateWidgetVisibility();
    }

    private String groupLabel() {
        return "分组：" + this.recipeType.group().label();
    }

    private String tagButtonLabel() {
        return "标签：" + (this.useItemTags ? "是" : "否");
    }

    /**
     * 把一个物品换成「同标签的任意物品」写法。
     *
     * <p>生成的脚本里会写成 {@code #minecraft:axes} 这类标签，于是钻石斧也能满足
     * 用石斧写的配方。标签按「越常用越优先」排：原版 {@code minecraft:*} →
     * Forge 子分类 {@code forge:*\/*} → Forge 顶层 → 其它模组。
     *
     * <p>一个标签都没有时返回 {@code null}，保持具体物品不变。
     */
    private String tagForItem(String itemId) {
        if (itemId == null || itemId.isBlank() || itemId.startsWith("#")) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null) {
            return null;
        }
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(key);
        if (item == null) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        new ItemStack(item).getTags().forEach(t -> tags.add(t.location().toString()));
        if (tags.isEmpty()) {
            return null;
        }
        tags.sort(java.util.Comparator
                .comparingInt(RecipeEditorScreen::tagPriority)
                .thenComparing(java.util.Comparator.naturalOrder()));
        return "#" + tags.get(0);
    }

    /** 标签优先级：数字越小越优先。原版那批「any X」标签最通用，所以排最前 */
    private static int tagPriority(String tag) {
        int colon = tag.indexOf(':');
        if (colon <= 0) {
            return 5;
        }
        String ns = tag.substring(0, colon);
        boolean sub = tag.indexOf('/', colon + 1) > colon;
        if ("minecraft".equals(ns) && !sub) {
            return 0;
        }
        if ("forge".equals(ns) && sub) {
            return 1;
        }
        if ("forge".equals(ns)) {
            return 2;
        }
        if (!sub) {
            return 3;
        }
        return 4;
    }

    private String secondaryCaption() {
        return this.operation == Operation.REMOVE ? "删除依据" : "配方类型";
    }

    private String shapelessLabel() {
        return "无序：" + (shapeless ? "是" : "否");
    }

    private String mirrorLabel() {
        return "镜像：" + (mirrored ? "是" : "否");
    }

    // ------------------------------------------------------------------ 可见性

    /** 搜索时隐藏被浮层盖住的控件；非烧炼类型隐藏经验/时间字段 */
    private void updateWidgetVisibility() {
        boolean visible = !searching();

        // 槽位也一起关掉：背包物品是用另一套渲染类型画的，光靠浮层去盖并不可靠
        this.menu.setSlotsHidden(!visible);

        for (Button button : modeButtons) {
            if (button != null) {
                button.visible = visible;
            }
        }
        for (Button button : new Button[]{secondaryButton, groupButton, shapelessButton, mirrorButton,
                saveButton, reloadButton, clearButton}) {
            if (button != null) {
                button.visible = visible;
            }
        }
        if (idField != null) {
            idField.visible = visible;
            if (!visible) {
                idField.setFocused(false);
            }
        }
        if (fileField != null) {
            fileField.visible = visible;
            if (!visible) {
                fileField.setFocused(false);
            }
        }

        boolean cooking = this.recipeType.hasCookingSettings();
        if (xpField != null) {
            xpField.visible = visible && cooking;
            if (!xpField.visible) {
                xpField.setFocused(false);
            }
        }
        if (timeField != null) {
            timeField.visible = visible && cooking;
            if (!timeField.visible) {
                timeField.setFocused(false);
            }
        }

        // 额外产物只属于切菜板
        boolean extra = visible && this.recipeType.hasMultipleOutputs();
        for (EditBox box : this.extraChanceFields) {
            box.visible = extra;
            if (!extra) {
                box.setFocused(false);
            }
        }

        // 「物品用标签」开关所有类型都有
        if (this.tagButton != null) {
            this.tagButton.visible = visible;
        }

        if (visible && this.searchField != null) {
            this.setFocused(this.searchField);
            this.searchField.setFocused(true);
        }
    }

    // ------------------------------------------------------------------ 服务端状态

    public void applyServerState(ModNetwork.EditorStatePacket msg) {
        Operation newOperation = Operation.byOrdinal(msg.operationOrdinal());
        RecipeType newType = RecipeType.byOrdinal(msg.typeOrdinal());

        // 操作也要比：合成类里「添加」和「修改」的槽位摆法是不同的（产物在前/在后）
        boolean layoutChanged = newType != this.recipeType || newOperation != this.operation;
        this.operation = newOperation;
        this.recipeType = newType;

        if (layoutChanged) {
            applySlotLayout();
        }

        if (msg.applyText()) {
            this.shapeless = msg.shapeless();
            this.mirrored = msg.mirrored();
            if (this.idField != null) {
                this.idField.setValue(msg.recipeId());
            }
            if (this.fileField != null && !msg.fileName().isBlank()) {
                this.fileField.setValue(msg.fileName());
            }
        }

        this.found = msg.found();
        this.foundSummary = msg.foundSummary();

        // 只有服务端明确要求覆盖时才动输入槽；否则会清掉玩家刚放好的原料
        if (msg.replaceInputs()) {
            List<String> cells = ModNetwork.decodeCells(msg.inputCells());
            this.inputItems = blankInputs();
            for (int i = 0; i < Math.min(9, cells.size()); i++) {
                this.inputItems.set(i, cells.get(i));
            }
            // 额外产物跟着一起回填（切菜板专用）
            this.extraItems = blankExtras();
            List<RecipeDraft.ExtraOutput> extras =
                    ModNetwork.decodeExtraOutputs(msg.extraOutputs());
            for (int i = 0; i < extras.size() && i < this.extraItems.size(); i++) {
                RecipeDraft.ExtraOutput extra = extras.get(i);
                this.extraItems.set(i, extra.item());
                if (i < this.extraChanceFields.size()) {
                    this.extraChanceFields.get(i)
                            .setValue(KubeJsWriter.formatNumber(extra.chance()));
                }
            }
        }

        // 只有真的查到原版配方时才回填经验/时间，否则会把用户刚填的值冲掉
        if (this.found && msg.cookingTime() > 0) {
            if (this.timeField != null) {
                this.timeField.setValue(String.valueOf(msg.cookingTime()));
            }
            if (this.xpField != null) {
                this.xpField.setValue(KubeJsWriter.formatNumber(msg.xp()));
            }
        }

        // 删除模式按产物删时，把查到的配方 ID 顺手填进配方名
        if (this.found && this.operation == Operation.REMOVE && this.idField != null
                && this.idField.getValue().isBlank() && msg.sourceRecipeId() != null
                && !msg.sourceRecipeId().isBlank()) {
            this.idField.setValue(msg.sourceRecipeId());
        }

        refreshButtonStates();
        updateWidgetVisibility();
    }

    public void setStatus(boolean ok, String message) {
        this.statusOk = ok;
        this.statusMessage = message == null ? "" : message;
    }

    // ------------------------------------------------------------------ 搜索

    private boolean searching() {
        return this.searchField != null && !this.searchField.getValue().trim().isEmpty();
    }

    private List<ItemSearch.Entry> candidates() {
        return this.inventorySource ? ItemSearchCache.inventoryItems() : ItemSearchCache.allItems();
    }

    private int maxSearchPage() {
        return Math.max(0, (this.searchResults.size() - 1) / PER_PAGE);
    }

    private int overlayLeft() {
        return this.leftPos + OVERLAY_X;
    }

    private int overlayTop() {
        return this.topPos + OVERLAY_Y;
    }

    /** 搜索结果的落点：添加模式进第一个空输入槽，修改/删除模式进产物格 */
    private boolean searchGoesToOutput() {
        return this.operation != Operation.ADD;
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, COLOR_PANEL);
        graphics.fill(x, y, x + this.imageWidth, y + 20, COLOR_TITLE_BAR);
        drawBorder(graphics, x, y, this.imageWidth, this.imageHeight, COLOR_BORDER);

        int inputs = this.menu.inputMenuCount();
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            boolean enabled = !(i < inputs && isDisabledInput(slot.getContainerSlot()));
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, COLOR_SLOT_BG);
            drawBorder(graphics, sx - 1, sy - 1, 18, 18, enabled ? COLOR_SLOT_BORDER : COLOR_SLOT_DISABLED);
        }
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, "配方编辑器  ·  " + this.operation.label() + "  ·  "
                + this.recipeType.label(), 10, 6, COLOR_TEXT, false);

        if (searching()) {
            return;
        }

        drawSlotLabels(graphics);
        graphics.drawString(this.font, secondaryCaption(), 12, CONTROLS_Y - 10, COLOR_LABEL, false);
        graphics.drawString(this.font, "配方名（可留空）", 12, LABEL_Y, COLOR_LABEL, false);
        graphics.drawString(this.font, "文件名", 176, LABEL_Y, COLOR_LABEL, false);

        if (this.recipeType.hasCookingSettings()) {
            graphics.drawString(this.font, "获得经验", 150, 82, COLOR_LABEL, false);
            graphics.drawString(this.font, "烧制时间（tick，默认 200）", 150, 110, COLOR_LABEL, false);
        }

        renderFoundInfo(graphics);

        if (statusOk != null) {
            int color = statusOk ? COLOR_OK : COLOR_FAIL;
            int sy = STATUS_Y;
            int used = 0;
            for (String line : wrapText(statusMessage, 316)) {
                if (used++ >= MAX_STATUS_LINES) {
                    break;
                }
                graphics.drawString(this.font, line, 12, sy, color, false);
                sy += 9;
            }
        }
    }

    /** 在每个输入槽与产物格下面标明是什么 */
    private void drawSlotLabels(GuiGraphics graphics) {
        int inputs = this.recipeType.inputSlotCount();
        String[] labels = this.recipeType.inputLabels();

        if (this.recipeType.isCrafting()) {
            Slot first = this.menu.slots.get(0);
            graphics.drawString(this.font, labels[0], first.x,
                    RecipeEditorMenu.CRAFT_GRID_Y + 3 * RecipeEditorMenu.SLOT_SIZE + 3,
                    COLOR_SLOT_LABEL, false);
        } else if (this.recipeType.isFarmers()) {
            drawFarmersLabels(graphics);
        } else {
            // 用 slot.y 而不是固定的 ROW_Y：农夫乐事之外的类型也可能把行挪走
            for (int i = 0; i < inputs && i < labels.length; i++) {
                Slot slot = this.menu.slots.get(i);
                graphics.drawString(this.font, labels[i], slot.x, slot.y + 22,
                        COLOR_SLOT_LABEL, false);
            }
        }

        Slot output = this.menu.slots.get(this.menu.outputMenuIndex());
        graphics.drawString(this.font, this.recipeType.outputLabel(), output.x,
                output.y + 22, COLOR_SLOT_LABEL, false);
    }

    /**
     * 农夫乐事的槽位标注。
     *
     * <p>烹饪锅的材料是 3×2 网格、容器另起一行，没法像其它类型那样「每个槽下面一行字」——
     * 那样标注会撒得到处都是，还会和右边的经验/时间输入框挤在一起。
     * 改成每组只标一次，标在组的上方。
     */
    private void drawFarmersLabels(GuiGraphics graphics) {
        int ly = RecipeEditorMenu.FARMERS_LABEL_Y;
        if (this.recipeType == RecipeType.FARMERS_COOKING) {
            graphics.drawString(this.font, "材料", RecipeEditorMenu.COOK_GRID_X, ly,
                    COLOR_SLOT_LABEL, false);
            // 容器槽在 x=88，但「容器(可空)」有 46px 宽，按槽位对齐会压到右边的「产物」（x=120）
            graphics.drawString(this.font, "容器(可空)", RecipeEditorMenu.COOK_CONTAINER_X - 18, ly,
                    COLOR_SLOT_LABEL, false);
        } else {
            graphics.drawString(this.font, "材料", RecipeEditorMenu.CUT_INPUT_X, ly,
                    COLOR_SLOT_LABEL, false);
            graphics.drawString(this.font, "工具", RecipeEditorMenu.CUT_TOOL_X, ly,
                    COLOR_SLOT_LABEL, false);
            // 额外产物在下面一行，概率填在每格右边——提示跟在同行，不另起一行
            graphics.drawString(this.font, "额外产物 ↓ 右侧填概率", 170, ly, COLOR_TEXT_DIM, false);
        }
    }

    /** 原版配方信息：材料已经回填在输入槽里，这里只给一行类型说明 */
    private void renderFoundInfo(GuiGraphics graphics) {
        if (this.operation == Operation.ADD) {
            return;
        }
        if (!this.found) {
            String hint = outputHasItem() ? "未找到该物品的原版配方" : "把要处理的物品放进产物格";
            graphics.drawString(this.font, hint, 12, FOUND_Y, COLOR_TEXT_DIM, false);
            return;
        }
        graphics.drawString(this.font, "原版配方：" + this.foundSummary, 12, FOUND_Y,
                COLOR_LABEL, false);
    }

    private boolean outputHasItem() {
        return !this.menu.getOutputContainer().getItem(0).isEmpty();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        int x = this.leftPos;
        int y = this.topPos;
        int inputs = this.menu.inputMenuCount();

        // 输入槽里没有真实物品，ghost 内容由这里画出来（搜索时槽位整体隐藏，不画）
        if (!searching()) {
            for (int i = 0; i < inputs && i < this.menu.slots.size(); i++) {
                Slot slot = this.menu.slots.get(i);
                int cell = slot.getContainerSlot();
                if (isDisabledInput(cell)) {
                    graphics.fill(x + slot.x - 1, y + slot.y - 1,
                            x + slot.x + 17, y + slot.y + 17, COLOR_DISABLED_TINT);
                    continue;
                }
                ItemStack ghost = RecipeEditorMenu.stackOf(cell < this.inputItems.size()
                        ? this.inputItems.get(cell) : null);
                if (!ghost.isEmpty()) {
                    graphics.renderItem(ghost, x + slot.x, y + slot.y);
                }
            }

            // 切菜板的额外产物：同样由客户端画，右边那个框填概率
            // （提示文字统一在 drawFarmersLabels 里画在标注那一行，不另起一行挤位置）
            if (this.recipeType.hasMultipleOutputs()) {
                int base = this.menu.outputMenuIndex() + 1;
                for (int i = 0; i < RecipeEditorMenu.EXTRA_OUTPUT_COUNT; i++) {
                    int slotIndex = base + i;
                    if (slotIndex >= this.menu.slots.size()) {
                        break;
                    }
                    Slot slot = this.menu.slots.get(slotIndex);
                    ItemStack ghost = RecipeEditorMenu.stackOf(i < this.extraItems.size()
                            ? this.extraItems.get(i) : null);
                    if (!ghost.isEmpty()) {
                        graphics.renderItem(ghost, x + slot.x, y + slot.y);
                    }
                }
            }
        }

        if (searching()) {
            renderSearchOverlay(graphics, mouseX, mouseY);
        }
    }

    /**
     * 搜索结果浮层。
     *
     * <p>搜索时下半部分（合成区、控件、玩家背包）整块隐藏，只留这一层面板，
     * 所以不会出现任何「文字从底下透出来」的重叠。
     */
    private void renderSearchOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = overlayLeft();
        int top = overlayTop();

        graphics.fill(left, top, left + OVERLAY_W, top + OVERLAY_H, COLOR_OVERLAY_BG);
        drawBorder(graphics, left, top, OVERLAY_W, OVERLAY_H, COLOR_BORDER);

        this.hoveredResult = null;
        int gridX = left + (OVERLAY_W - OVERLAY_COLS * CELL) / 2;
        int gridY = top + 30;
        int start = this.searchPage * PER_PAGE;

        for (int i = 0; i < PER_PAGE; i++) {
            int cx = gridX + (i % OVERLAY_COLS) * CELL;
            int cy = gridY + (i / OVERLAY_COLS) * CELL;
            boolean isHover = inRect(mouseX, mouseY, cx, cy, CELL, CELL);
            graphics.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, isHover ? COLOR_CELL_HOVER : COLOR_CELL);

            int index = start + i;
            if (index < this.searchResults.size()) {
                ItemSearch.Entry entry = this.searchResults.get(index);
                ItemStack stack = RecipeEditorMenu.stackOf(entry.itemId());
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, cx + 1, cy + 1);
                }
                if (isHover) {
                    this.hoveredResult = entry;
                }
            }
        }

        // 顶部：结果数与目标格
        graphics.drawString(this.font, "搜索结果 " + this.searchResults.size() + " 个 · 点击放入："
                        + (searchGoesToOutput() ? "产物格" : "输入槽"),
                left + 8, top + 8, COLOR_TEXT, false);

        // 中部：悬停物品 / 搜索语法提示
        if (this.hoveredResult != null) {
            graphics.drawString(this.font, this.hoveredResult.displayName() + "   "
                            + this.hoveredResult.itemId(),
                    left + 8, top + 188, COLOR_TEXT, false);
        } else {
            graphics.drawString(this.font, "支持：中文名 / 物品 ID / 拼音全拼 / 拼音首字母 / @模组名",
                    left + 8, top + 188, COLOR_TEXT_DIM, false);
        }
        graphics.drawString(this.font, "右键搜索框清空 · Esc 关闭搜索 · 滚轮翻页",
                left + 8, top + 202, COLOR_TEXT_DIM, false);

        // 底部：翻页 + 页码 + 来源切换
        int rowY = top + OVERLAY_H - 26;
        boolean hasPrev = this.searchPage > 0;
        boolean prevHover = inRect(mouseX, mouseY, left + 8, rowY, 56, 16) && hasPrev;
        graphics.fill(left + 8, rowY, left + 64, rowY + 16,
                hasPrev ? (prevHover ? COLOR_BUTTON_HOVER : COLOR_BUTTON_BG) : COLOR_BUTTON_OFF);
        graphics.drawString(this.font, "< 上一页", left + 11, rowY + 4,
                hasPrev ? COLOR_TEXT : COLOR_TEXT_DIM, false);

        boolean hasNext = this.searchPage < maxSearchPage();
        boolean nextHover = inRect(mouseX, mouseY, left + 68, rowY, 56, 16) && hasNext;
        graphics.fill(left + 68, rowY, left + 124, rowY + 16,
                hasNext ? (nextHover ? COLOR_BUTTON_HOVER : COLOR_BUTTON_BG) : COLOR_BUTTON_OFF);
        graphics.drawString(this.font, "下一页 >", left + 71, rowY + 4,
                hasNext ? COLOR_TEXT : COLOR_TEXT_DIM, false);

        graphics.drawString(this.font, "第 " + (this.searchPage + 1) + " / " + (maxSearchPage() + 1) + " 页",
                left + 132, rowY + 4, COLOR_TEXT_DIM, false);

        int srcX = left + OVERLAY_W - 104;
        boolean srcHover = inRect(mouseX, mouseY, srcX, rowY, 96, 16);
        graphics.fill(srcX, rowY, srcX + 96, rowY + 16, srcHover ? COLOR_BUTTON_HOVER : COLOR_BUTTON_BG);
        drawBorder(graphics, srcX, rowY, 96, 16, COLOR_SLOT_BORDER);
        graphics.drawString(this.font, "来源：" + (this.inventorySource ? "我的背包" : "全部物品"),
                srcX + 4, rowY + 4, COLOR_TEXT, false);
    }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        for (String raw : text.split("\n", -1)) {
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (current.length() > 0 && this.font.width(current.toString() + c) > maxWidth) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                current.append(c);
            }
            out.add(current.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------ 交互

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.minecraft != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && searching()) {
                this.searchField.setValue("");
                this.searchResults = List.of();
                return true;
            }
            InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
            if (this.minecraft.options.keyInventory.isActiveAndMatches(key)) {
                if (this.getFocused() != null) {
                    this.getFocused().keyPressed(keyCode, scanCode, modifiers);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchField != null && this.searchField.visible
                && this.searchField.isMouseOver(mouseX, mouseY) && button == 1) {
            this.searchField.setValue("");
            this.searchResults = List.of();
            this.searchPage = 0;
            return true;
        }

        if (searching()) {
            handleSearchClick(mouseX, mouseY);
            return true;
        }

        // 输入槽是展示槽：用光标上的物品设置它，空手点或右键则清空
        int inputs = this.menu.inputMenuCount();
        for (int i = 0; i < inputs && i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            int cell = slot.getContainerSlot();
            if (isDisabledInput(cell)) {
                continue;
            }
            if (!isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                continue;
            }
            if (button == 1) {
                setInput(cell, null);
            } else {
                ItemStack carried = this.menu.getCarried();
                setInput(cell, carried.isEmpty() ? null : itemId(carried));
            }
            return true;
        }

        // 切菜板的额外产物格：同一个交互——拿东西点一下放，空手点或右键清空
        if (this.recipeType.hasMultipleOutputs()) {
            int base = this.menu.outputMenuIndex() + 1;
            for (int i = 0; i < RecipeEditorMenu.EXTRA_OUTPUT_COUNT; i++) {
                int slotIndex = base + i;
                if (slotIndex >= this.menu.slots.size()) {
                    break;
                }
                Slot slot = this.menu.slots.get(slotIndex);
                if (!isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                    continue;
                }
                if (button == 1) {
                    setExtra(i, null);
                } else {
                    ItemStack carried = this.menu.getCarried();
                    setExtra(i, carried.isEmpty() ? null : itemId(carried));
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void setInput(int index, String itemId) {
        if (index >= 0 && index < this.inputItems.size()) {
            this.inputItems.set(index, itemId);
        }
    }

    private boolean handleSearchClick(double mouseX, double mouseY) {
        int left = overlayLeft();
        int top = overlayTop();
        int rowY = top + OVERLAY_H - 26;

        if (inRect(mouseX, mouseY, left + OVERLAY_W - 104, rowY, 96, 16)) {
            this.inventorySource = !this.inventorySource;
            this.searchResults = ItemSearch.filter(this.searchField.getValue(), candidates());
            this.searchPage = 0;
            return true;
        }

        int gridX = left + (OVERLAY_W - OVERLAY_COLS * CELL) / 2;
        int gridY = top + 30;

        if (inRect(mouseX, mouseY, left + 8, rowY, 56, 16)) {
            if (this.searchPage > 0) {
                this.searchPage--;
            }
            return true;
        }
        if (inRect(mouseX, mouseY, left + 68, rowY, 56, 16)) {
            if (this.searchPage < maxSearchPage()) {
                this.searchPage++;
            }
            return true;
        }

        if (mouseX >= gridX && mouseY >= gridY) {
            int col = (int) ((mouseX - gridX) / CELL);
            int row = (int) ((mouseY - gridY) / CELL);
            if (col >= 0 && col < OVERLAY_COLS && row >= 0 && row < OVERLAY_ROWS) {
                int index = this.searchPage * PER_PAGE + row * OVERLAY_COLS + col;
                if (index < this.searchResults.size()) {
                    applySearchResult(this.searchResults.get(index).itemId());
                    this.searchField.setValue("");
                    this.searchResults = List.of();
                }
                return true;
            }
        }
        return true;
    }

    private void applySearchResult(String itemId) {
        if (searchGoesToOutput()) {
            ModNetwork.requestPickItem(itemId);
            return;
        }        // 找一个还空着的、且当前类型用得到的格子
        int inputs = this.menu.inputMenuCount();
        for (int i = 0; i < inputs && i < this.menu.slots.size(); i++) {
            int cell = this.menu.slots.get(i).getContainerSlot();
            if (isDisabledInput(cell)) {
                continue;
            }
            if (cell >= this.inputItems.size() || this.inputItems.get(cell) == null) {
                setInput(cell, itemId);
                return;
            }
        }
        setInput(this.menu.slots.get(0).getContainerSlot(), itemId);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (searching()) {
            if (delta > 0 && this.searchPage > 0) {
                this.searchPage--;
            } else if (delta < 0 && this.searchPage < maxSearchPage()) {
                this.searchPage++;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** 删除模式下输入槽只作展示；网格小于容器时多出来的行/列也不用 */
    private boolean isDisabledInput(int index) {
        if (this.operation == Operation.REMOVE) {
            return true;
        }
        if (!this.recipeType.isCrafting()) {
            return false;
        }
        // 合成格容器恒为 3 列，所以按 3 取模换算行列；用 gridWidth() 会让 2×2 的格子错位
        int col = index % 3;
        int row = index / 3;
        return col >= this.recipeType.gridWidth() || row >= this.recipeType.gridHeight();
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? null : key.toString();
    }

    // ------------------------------------------------------------------ 保存

    private void save() {
        // 勾了「物品用标签」就把所有输入槽物品换成标签，让同类物品都能用
        List<String> cells = new ArrayList<>(this.inputItems);
        if (this.useItemTags) {
            for (int i = 0; i < cells.size(); i++) {
                String tag = tagForItem(cells.get(i));
                if (tag != null) {
                    cells.set(i, tag);
                }
            }
        }
        ModNetwork.requestSaveRecipe(
                this.fileField.getValue(),
                this.idField.getValue(),
                this.shapeless,
                this.mirrored,
                this.recipeType.ordinal(),
                this.removeBy.ordinal(),
                parseXp(),
                parseCookingTime(),
                cells,
                collectExtraOutputs());
        setStatus(true, "正在写入…");
    }

    private double parseXp() {
        if (this.xpField == null) {
            return 0D;
        }
        try {
            return Math.max(0D, Double.parseDouble(this.xpField.getValue().trim()));
        } catch (NumberFormatException e) {
            return 0D;
        }
    }

    private int parseCookingTime() {
        if (this.timeField == null) {
            return RecipeDraft.DEFAULT_COOKING_TIME;
        }
        try {
            return Math.max(1, Integer.parseInt(this.timeField.getValue().trim()));
        } catch (NumberFormatException e) {
            return RecipeDraft.DEFAULT_COOKING_TIME;
        }
    }
}
