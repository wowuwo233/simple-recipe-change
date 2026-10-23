package com.wowuwo233.simple_recipe_change.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wowuwo233.simple_recipe_change.ModCompat;
import com.wowuwo233.simple_recipe_change.kubejs.CreateMachine;
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
import net.minecraft.client.gui.components.Tooltip;
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

    // 版面纵坐标
    private static final int MODE_Y = 22;
    private static final int SEARCH_Y = 46;
    private static final int FOUND_Y = 196;
    private static final int CONTROLS_Y = 208;
    private static final int LABEL_Y = 236;
    /** 配方名与文件名并排时各自的宽度：ID 可能很长，给宽一点 */
    private static final int ID_W = 278;
    private static final int FILE_X = 294;
    private static final int FIELD_Y = 246;
    /**
     * 备注输入框。
     *
     * <p>放在内容区下半段的空档里（y 166~194）——所有类型的槽位都止于 164，
     * 而「配方类型」标签在 202，中间正好空着。之前放到底部又开了一行，
     * 窗口涨到 420 就直接超出屏幕可用高度、顶部被裁掉了。
     */
    private static final int NOTE_LABEL_Y = 166;
    private static final int NOTE_Y = 176;
    private static final int BUTTONS_Y = 270;

    /**
     * 经验/成功率、处理时间/循环次数这两个框的 y。
     *
     * <p>它们从内容区挪到了「配方类型」那一行的上面——原来占着内容区右边，
     * 而序列组装的内容区要整个让给 5 个步骤列（机器按钮 + 槽位），挪下来既腾了地方，
     * 也顺带填掉了加高窗口后空出来的那一段。
     */
    /**
     * 经验框 / 时间框的 y。
     *
     * <p>放在内容区<b>右上角</b>：所有类型的槽位都只占左边（最远到 x=138），
     * x=150 往右整片是空的，把框放这儿不会跟任何槽位打架。
     *
     * <p>之前有过两版都错了：一版把它们放到底部，结果压住备注框；
     * 一版放到 y=144/178，又跟备注框重叠。**关键是备注框横跨整个宽度**，
     * 所以这两个框必须待在槽位区右边，别往下挪。
     */
    private static final int XP_FIELD_Y = 76;
    private static final int TIME_FIELD_Y_TWO_ROWS = 106;
    /**
     * 只有时间框一行时它的 y（机械动力处理配方）。
     *
     * <p>放在 106 而不是接着往下排——机械动力的材料是 3×3 网格，额外产物行被挤到了
     * {@code CREATE_EXTRA_ROW_Y}(146)，时间框要是也往下放就会压上去。
     * 挪到右上角（网格只占 x 16..70，右边整片是空的）正好避开。
     */
    private static final int TIME_FIELD_Y = 106;
    /** 右半边输入框的 x 与宽度 */
    private static final int FIELD_X = 260;
    private static final int FIELD_W = 178;

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

    /**
     * 产物是否携带 NBT。
     *
     * <p>产物格里的物品带附魔 / 药水效果 / 自定义名称时，默认把这些 NBT 一起写进脚本；
     * 想「不管放进去的是什么，都产出干净物品」就把这个关掉。
     *
     * <p>注意这跟上面的 {@link #tagButton}（物品用标签）不是一回事：
     * 那个管的是<b>输入材料</b>写成 {@code #标签}，这个管的是<b>产物</b>的 NBT。
     */
    private Button outputNbtButton;
    private boolean carryOutputNbt = true;
    /** 产物格当前是否真的带 NBT——决定按钮显示「无」还是「是 / 否」 */
    private boolean outputHasNbt = false;

    /**
     * 序列组装的 4 个步骤。
     *
     * <p>步骤类型只存在客户端，保存时编码进 {@code heat} 字段（这个类型不用热度），
     * 服务端和索引都不需要为它改结构。
     */
    private final Button[] seqButtons = new Button[RecipeEditorMenu.SEQ_STEP_COUNT];
    private final String[] seqKinds = {"deploying", "deploying", "deploying",
            "deploying", "deploying", "deploying"};
    /** 实际用几步（1..6）；编码进 heat 的项数就是它 */
    private int seqStepCount = 4;
    private Button seqCountButton;

    /** 最近一次从 JEI 拖进来 / 搜索选中的物品；右键空格时用它填充 */
    private String lastPickedItem;

    /** 我们自己创建的控件；别的模组（FTB 等）后加进来的会被清掉 */
    private final java.util.Set<Object> ownWidgets =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /**
     * 机械动力的具体机器与热度要求。
     *
     * <p>这两个控件<b>复用「无序」和「镜像」两个按钮槽</b>——那两个按钮本来就只对合成类
     * 有意义，在 Create 类型下必然是灰的，白占位置；文献上也不冲突。
     */
    private CreateMachine createMachine = CreateMachine.CRUSHING;
    /** 空 / heated / superheated */
    private String createHeat = "";
    /** 热度是三态，用按钮循环比文本框合适 */
    private static final String[] HEAT_CYCLE = {"", "heated", "superheated"};

    private static String heatLabel(String heat) {
        return switch (heat) {
            case "heated" -> "加热";
            case "superheated" -> "超热";
            default -> "无";
        };
    }

    private static String nextHeat(String heat) {
        for (int i = 0; i < HEAT_CYCLE.length; i++) {
            if (HEAT_CYCLE[i].equals(heat)) {
                return HEAT_CYCLE[(i + 1) % HEAT_CYCLE.length];
            }
        }
        return "";
    }
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
        // 切菜板是多产物，序列组装的「失败产物」也算一个额外产物（但只有一格、没有概率框）
        if (!this.recipeType.hasMultipleOutputs() && !this.recipeType.isSequenced()) {
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
        // 靠左贴边：右边整条留给 JEI 的物品列表。居中放的话窗口会压住 JEI，
        // 而 JEI 的面板宽度是它自己定的，我们只能「让出右侧」。
        this.leftPos = 2;
        this.clearWidgets();

        Operation[] modes = {Operation.ADD, Operation.MODIFY, Operation.REMOVE};
        int[] modeX = {10, 112, 214};
        for (int i = 0; i < modes.length; i++) {
            final Operation mode = modes[i];
            modeButtons[i] = this.addRenderableWidget(Button.builder(
                            Component.literal(mode.label()), b -> switchMode(mode))
                    .bounds(this.leftPos + modeX[i], this.topPos + MODE_Y, 92, 20)
                    .build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("我的配方"), b -> {
                    ModNetwork.requestIndex();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new RecipeListScreen(this));
                    }
                })
                .bounds(this.leftPos + 316, this.topPos + MODE_Y, 92, 20)
                .build());

        // 全界面唯一的重载入口。底栏原本还有一个文案与行为完全相同的「重载脚本」，
        // 两个按钮干同一件事只会让人犹豫点哪个，已删除。
        this.reloadButton = this.addRenderableWidget(Button.builder(Component.literal("重载"), b -> {
                    ModNetwork.requestReload();
                    setStatus(true, "已请求重载，KubeJS 会重新读取脚本");
                })
                .bounds(this.leftPos + 418, this.topPos + MODE_Y, 92, 20)
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
                .bounds(this.leftPos + 62, this.topPos + CONTROLS_Y, 96, 20)
                .build());

        this.groupButton = this.addRenderableWidget(Button.builder(
                        Component.literal(groupLabel()), b -> cycleGroup())
                .bounds(this.leftPos + 166, this.topPos + CONTROLS_Y, 96, 20)
                .build());

        this.shapelessButton = this.addRenderableWidget(Button.builder(
                        Component.literal(shapelessLabel()), b -> {
                            if (this.recipeType.isCreate()) {
                                this.createMachine = this.createMachine.next();
                            } else {
                                this.shapeless = !this.shapeless;
                            }
                            refreshButtonStates();
                        })
                .bounds(this.leftPos + 270, this.topPos + CONTROLS_Y, 96, 20)
                .build());

        this.mirrorButton = this.addRenderableWidget(Button.builder(
                        Component.literal(mirrorLabel()), b -> {
                            if (this.recipeType.isCreate()) {
                                this.createHeat = nextHeat(this.createHeat);
                            } else {
                                this.mirrored = !this.mirrored;
                            }
                            refreshButtonStates();
                        })
                .bounds(this.leftPos + 374, this.topPos + CONTROLS_Y, 96, 20)
                .build());

        // 烧炼专用的经验与时间
        String xpText = this.xpField == null ? "0" : this.xpField.getValue();
        String timeText = this.timeField == null
                ? String.valueOf(this.recipeType.defaultProcessingTime())
                : this.timeField.getValue();
        // 位置必须用这两个常量：标签是按它们画的，写死数字就会出现「框在这、字在那」
        this.xpField = new EditBox(this.font, this.leftPos + FIELD_X, this.topPos + XP_FIELD_Y,
                FIELD_W, 18, Component.literal("经验"));
        this.xpField.setMaxLength(10);
        this.xpField.setValue(xpText);
        this.addRenderableWidget(this.xpField);

        this.timeField = new EditBox(this.font, this.leftPos + FIELD_X,
                this.topPos + TIME_FIELD_Y_TWO_ROWS, FIELD_W, 18,
                Component.literal("烧制时间"));
        this.timeField.setMaxLength(10);
        this.timeField.setValue(timeText);
        this.addRenderableWidget(this.timeField);

        // 备注：写进脚本时变成 // 注释，放在配方上方
        String noteText = this.noteField == null ? "" : this.noteField.getValue();
        this.noteField = new EditBox(this.font, this.leftPos + 12, this.topPos + NOTE_Y, RecipeEditorMenu.IMAGE_WIDTH - 24, 18,
                Component.literal("备注"));
        this.noteField.setMaxLength(200);
        this.noteField.setValue(noteText);
        this.noteField.setHint(Component.literal("备注（会写成脚本里的 // 注释，可留空）"));
        this.addRenderableWidget(this.noteField);

        this.idField = new EditBox(this.font, this.leftPos + 12, this.topPos + FIELD_Y, ID_W, 18,
                Component.literal("配方名"));
        this.idField.setMaxLength(200);
        this.idField.setHint(Component.literal("留空自动生成"));
        this.addRenderableWidget(this.idField);

        this.fileField = new EditBox(this.font, this.leftPos + FILE_X, this.topPos + FIELD_Y, RecipeEditorMenu.IMAGE_WIDTH - FILE_X - 12, 18,
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
                    this.topPos + RecipeEditorMenu.EXTRA_ROW_Y, 34, 18,
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

        // 「产物NBT」开关：产物带 NBT 时决定要不要写进脚本。
        // 底栏「清空编辑区」止于 x=184，「标签」从 268 开始，中间这 84px 正好放它。
        this.outputNbtButton = this.addRenderableWidget(Button.builder(
                        Component.literal(outputNbtLabel()), b -> {
                            this.carryOutputNbt = !this.carryOutputNbt;
                            refreshButtonStates();
                        })
                .bounds(this.leftPos + 270, this.topPos + BUTTONS_Y, 96, 20)
                .build());
        this.outputNbtButton.setTooltip(Tooltip.create(Component.literal(
                "产物带 NBT（附魔 / 药水 / 自定义名称）时，是否把这些标签写进脚本。\n"
                        + "产物格里的物品没有 NBT 时本项无效。")));

        // 序列组装的 4 个步骤：按钮选机器，下面一格放这一步额外消耗的物品
        for (int i = 0; i < this.seqButtons.length; i++) {
            final int index = i;
            this.seqButtons[i] = this.addRenderableWidget(Button.builder(
                            Component.literal(seqButtonLabel(i)), b -> {
                                this.seqKinds[index] = nextSeqKind(this.seqKinds[index]);
                                refreshButtonStates();
                                updateWidgetVisibility();
                            })
                    .bounds(this.leftPos + RecipeEditorMenu.SEQ_BUTTON_X0
                                    + i * RecipeEditorMenu.SEQ_BUTTON_STEP,
                            this.topPos + RecipeEditorMenu.SEQ_BUTTON_Y,
                            RecipeEditorMenu.SEQ_BUTTON_W, 18)
                    .build());
        }

        // 「步骤数」按钮：序列组装下占了「机器 / 热度」那两个按钮的位置
        this.seqCountButton = this.addRenderableWidget(Button.builder(
                        Component.literal(seqCountLabel()), b -> {
                            this.seqStepCount = this.seqStepCount
                                    % RecipeEditorMenu.SEQ_STEP_COUNT + 1;
                            applySeqStepCount();
                        })
                .bounds(this.leftPos + 270, this.topPos + CONTROLS_Y, 96, 20)
                .build());

        // 控件都建好之后再统一摆一次位置与可见性：
        // init 前半段调用的那次是在字段创建之前，改不到它们。
        refreshButtonStates();
        updateWidgetVisibility();

        this.saveButton = this.addRenderableWidget(Button.builder(Component.literal("保存并写入"), b -> save())
                .bounds(this.leftPos + 62, this.topPos + BUTTONS_Y, 96, 20)
                .build());

        this.clearButton = this.addRenderableWidget(Button.builder(Component.literal("清空编辑区"), b -> {
                    ModNetwork.requestClearEditor();
                    this.inputItems = blankInputs();
                    setStatus(true, "已清空编辑区");
                })
                .bounds(this.leftPos + 166, this.topPos + BUTTONS_Y, 96, 20)
                .build());

        applySlotLayout();
        refreshButtonStates();
        updateWidgetVisibility();

        // 记下「我们自己建的控件」——FTB Library / Quests 会在 Init.Post 里往任何容器界面
        // 塞按钮，那些控件是 init() 之后才加进来的，渲染前清掉（见 purgeForeignWidgets）。
        this.ownWidgets.clear();
        this.ownWidgets.addAll(this.renderables);
    }

    /**
     * 把别的模组塞进这个界面的按钮摘掉。
     *
     * <p>FTB Library / FTB Quests 这类模组用 {@code ScreenEvent.Init.Post} 给所有
     * {@code AbstractContainerScreen} 加按钮，于是它们会浮在编辑器上面挡住版面。
     * 我们不认识它们的类，所以反过来做：只保留 init() 结束时就已经存在的控件。
     */
    private void purgeForeignWidgets() {
        if (this.ownWidgets.isEmpty()) {
            return;
        }
        this.renderables.removeIf(widget -> !this.ownWidgets.contains(widget));
        this.children().removeIf(widget -> !this.ownWidgets.contains(widget));
    }

    private void switchMode(Operation mode) {
        if (this.operation == mode) {
            return;
        }
        this.operation = mode;
        // 槽位布局等服务端确认后再重建，避免两端槽位数短暂不一致
        ModNetwork.requestSetMode(this.operation, this.recipeType, this.createMachine);
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
        ModNetwork.requestSetMode(this.operation, this.recipeType, this.createMachine);
        applySlotLayout();
        refreshButtonStates();
        updateWidgetVisibility();
    }

    private void applySlotLayout() {
        // 注意：不能用 menu.getRecipeType() 反写回本地类型——客户端菜单对象更新得比这里晚，
        // 那样会把刚切换的类型直接抹掉，表现为「类型点不动」。
        this.menu.setSeqStepCount(this.seqStepCount);
        this.menu.applyLayout(this.operation, this.recipeType, this.createMachine);
        // 处理时间的默认值随类型走：原版烧炼 200 tick，机械动力 100 tick，序列组装是循环次数 4
        if (this.timeField != null) {
            this.timeField.setValue(String.valueOf(this.recipeType.defaultProcessingTime()));
        }
        // 序列组装的成功率默认 100%（经验框被借来当成功率用）
        if (this.xpField != null && this.recipeType.isSequenced()) {
            this.xpField.setValue("100");
        }
        // 类型换了，步骤按钮/循环次数框的可见性与位置也要跟着换
        updateWidgetVisibility();
    }

    /**
     * 产物格的 NBT 状态变了就刷新「产物NBT」按钮。
     *
     * <p>放在 render 里每帧比一次：产物槽是真实槽位，玩家拖进拖出、搜索选中、
     * 从「我的配方」载入都会改它，而这几条路径都没有现成的回调。
     */
    private void updateOutputNbtButton() {
        ItemStack out = this.menu.getOutputContainer().getItem(0);
        boolean has = out != null && !out.isEmpty() && out.hasTag();
        if (has != this.outputHasNbt) {
            this.outputHasNbt = has;
            refreshButtonStates();
        }
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
        if (this.outputNbtButton != null) {
            // 删除模式不写产物；产物格没有 NBT 时也没什么可带
            this.outputNbtButton.active = this.operation != Operation.REMOVE && this.outputHasNbt;
            this.outputNbtButton.setMessage(Component.literal(outputNbtLabel()));
        }
        boolean adding = this.operation == Operation.ADD;
        boolean crafting = this.recipeType.isCrafting();
        boolean create = this.recipeType.isCreate();
        boolean sequenced = this.recipeType.isSequenced();
        if (this.shapelessButton != null) {
            // Create 下「机器」始终可点；序列组装的步骤在下面单独选，这里不用
            this.shapelessButton.active = !sequenced && (create || (adding && crafting));
            this.shapelessButton.setMessage(Component.literal(shapelessLabel()));
        }
        if (this.mirrorButton != null) {
            // Create 下「热度」始终可点；镜像只在有序合成且未勾无序时才有意义
            this.mirrorButton.active = !sequenced && (create || (adding && crafting && !this.shapeless));
            this.mirrorButton.setMessage(Component.literal(mirrorLabel()));
        }
        for (int i = 0; i < this.seqButtons.length; i++) {
            if (this.seqButtons[i] != null) {
                this.seqButtons[i].setMessage(Component.literal(seqButtonLabel(i)));
            }
        }
        if (this.seqCountButton != null) {
            this.seqCountButton.setMessage(Component.literal(seqCountLabel()));
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
        ModNetwork.requestSetMode(this.operation, this.recipeType, this.createMachine);
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
     * 「产物NBT」按钮的文案。
     *
     * <p>产物格没放东西、或者放的东西本来就没 NBT 时显示「无」——这时候开关没有意义，
     * 按钮也会置灰，免得玩家以为点了没反应是 bug。
     */
    private String outputNbtLabel() {
        if (!this.outputHasNbt) {
            return "产物NBT：无";
        }
        return "产物NBT：" + (this.carryOutputNbt ? "是" : "否");
    }

    /** 序列组装可用的步骤类型（Create 里能进序列的机器就这四种） */
    private static final String[] SEQ_KIND_CYCLE = {"deploying", "pressing", "cutting", "filling"};

    private static String nextSeqKind(String kind) {
        for (int i = 0; i < SEQ_KIND_CYCLE.length; i++) {
            if (SEQ_KIND_CYCLE[i].equals(kind)) {
                return SEQ_KIND_CYCLE[(i + 1) % SEQ_KIND_CYCLE.length];
            }
        }
        return SEQ_KIND_CYCLE[0];
    }

    /** 按「机器：行为」显示，和 Create 自己配方书里的叫法一致 */
    private static String seqKindName(String kind) {
        return switch (kind) {
            case "pressing" -> "压床：压印";
            case "cutting" -> "动力锯：切割";
            case "filling" -> "注液器：灌装";
            default -> "机械手：部署";
        };
    }

    private String seqButtonLabel(int index) {
        return (index + 1) + " " + seqKindName(this.seqKinds[index]);
    }

    private String seqCountLabel() {
        return "步骤数：" + this.seqStepCount;
    }

    /**
     * 步骤数变了之后同步三件事：菜单里的槽位显隐、按钮显隐、以及清掉多出来的格子。
     *
     * <p>不清的话，缩到 3 步再把步骤数调回 6，之前塞在第 4~6 步的东西会突然冒出来。
     */
    private void applySeqStepCount() {
        this.menu.setSeqStepCount(this.seqStepCount);
        for (int i = this.seqStepCount; i < RecipeEditorMenu.SEQ_STEP_COUNT; i++) {
            setInput(2 + i, null);
        }
        refreshButtonStates();
        updateWidgetVisibility();
    }

    /** 从索引/网络包里的 heat 字段还原步骤类型与步骤数 */
    private void decodeSeqKinds(String encoded) {
        String[] parts = encoded == null || encoded.isBlank() ? new String[0] : encoded.split(",");
        this.seqStepCount = Math.max(1, Math.min(RecipeEditorMenu.SEQ_STEP_COUNT,
                parts.length == 0 ? 4 : parts.length));
        for (int i = 0; i < this.seqKinds.length; i++) {
            this.seqKinds[i] = i < parts.length && !parts[i].isBlank()
                    ? parts[i].trim() : "deploying";
        }
        this.menu.setSeqStepCount(this.seqStepCount);
        refreshButtonStates();
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
        // Create 类型下这个按钮槽改当「机器」用——直接显示机器名，不加「机器：」前缀
        if (this.recipeType.isCreate()) {
            return this.createMachine.label();
        }
        return "无序：" + (shapeless ? "是" : "否");
    }

    private String mirrorLabel() {
        // Create 类型下这个按钮槽改当「热度」用
        if (this.recipeType.isCreate()) {
            return "热度：" + heatLabel(this.createHeat);
        }
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
        boolean sequenced = this.recipeType.isSequenced();
        boolean time = this.recipeType.hasTimeField();
        // 经验框对机械动力没有意义，但序列组装会把它当「组装成功率」用
        boolean xpRow = cooking || sequenced;
        // 只有「经验/成功率」和「时间/循环」都在时才排两行；否则时间框上移到 y=92，
        // 不然两个框会落在同一个位置叠在一起（序列组装就踩过这个坑）
        boolean twoRows = twoFieldRows();
        if (xpField != null) {
            xpField.visible = visible && xpRow;
            if (!xpField.visible) {
                xpField.setFocused(false);
            } else if (sequenced) {
                // 成功率框和循环次数框并排放在第一行右侧
                this.xpField.setX(this.leftPos + RecipeEditorMenu.SEQ_CHANCE_X);
                this.xpField.setY(this.topPos + RecipeEditorMenu.SEQ_LOOPS_Y);
                this.xpField.setWidth(RecipeEditorMenu.SEQ_CHANCE_W);
            } else {
                this.xpField.setX(this.leftPos + FIELD_X);
                this.xpField.setY(this.topPos + XP_FIELD_Y);
                this.xpField.setWidth(FIELD_W);
            }
        }
        if (timeField != null) {
            timeField.visible = visible && time;
            if (!timeField.visible) {
                timeField.setFocused(false);
            } else if (this.recipeType.isSequenced()) {
                // 序列组装没有经验行，循环次数框挪到第一行右侧，别压住步骤按钮
                this.timeField.setX(this.leftPos + RecipeEditorMenu.SEQ_LOOPS_X);
                this.timeField.setY(this.topPos + RecipeEditorMenu.SEQ_LOOPS_Y);
                this.timeField.setWidth(RecipeEditorMenu.SEQ_LOOPS_W);
            } else {
                this.timeField.setX(this.leftPos + FIELD_X);
                this.timeField.setWidth(FIELD_W);
                // 没有经验那一行时把时间/循环框挪上去，否则会压到额外产物格
                this.timeField.setY(this.topPos + (twoRows ? TIME_FIELD_Y_TWO_ROWS : TIME_FIELD_Y));
            }
        }

        // 步骤按钮只有序列组装才有，而且只显示「步骤数」以内的
        for (int i = 0; i < this.seqButtons.length; i++) {
            if (this.seqButtons[i] != null) {
                this.seqButtons[i].visible = visible && sequenced && i < this.seqStepCount;
            }
        }
        if (this.seqCountButton != null) {
            this.seqCountButton.visible = visible && sequenced;
        }
        // 序列组装下「机器 / 热度」没意义，藏起来把位置让给「步骤数」
        if (this.shapelessButton != null) {
            this.shapelessButton.visible = visible && !sequenced;
        }
        if (this.mirrorButton != null) {
            this.mirrorButton.visible = visible && !sequenced;
        }

        // 概率框只属于切菜板；机械动力的额外产物不带概率，格子显示但框不显示
        // 概率框的 y 要跟服务端摆的槽位一致：机械动力的额外产物行比其它类型低
        int extraY = this.recipeType.isCreate()
                ? RecipeEditorMenu.CREATE_EXTRA_ROW_Y : RecipeEditorMenu.EXTRA_ROW_Y;
        for (EditBox box : this.extraChanceFields) {
            box.setY(this.topPos + extraY);
        }
        boolean extra = visible && this.menu.hasByproductChance();
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
        // 「产物NBT」同理，搜索浮层展开时一起藏起来
        if (this.outputNbtButton != null) {
            this.outputNbtButton.visible = visible;
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
            // 额外产物跟着一起回填（切菜板 / 机械动力专用）
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
            // 机械动力的机器与热度跟着载入
            this.createMachine = CreateMachine.byOrdinal(msg.machineOrdinal());
            this.createHeat = msg.heat() == null ? "" : msg.heat();
            if (newType.isSequenced()) {
                decodeSeqKinds(this.createHeat);
                if (this.xpField != null && msg.xp() > 0) {
                    this.xpField.setValue(KubeJsWriter.formatNumber(msg.xp()));
                }
            }
            if (this.noteField != null) {
                this.noteField.setValue(msg.note() == null ? "" : msg.note());
            }
            refreshButtonStates();
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

    /**
     * 输入的是不是一条配方 ID（{@code namespace:path}）。
     *
     * <p>只要长得像就往服务端问一次，由服务端判断存不存在、类型支不支持——
     * 客户端拿不到完整的配方表（专用服务器上更是如此）。
     */
    private static boolean looksLikeRecipeId(String text) {
        return text != null && text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }

    private List<ItemSearch.Entry> candidates() {
        return this.inventorySource ? ItemSearchCache.inventoryItems() : ItemSearchCache.allItems();
    }

    private int maxSearchPage() {
        return Math.max(0, (this.searchResults.size() - 1) / PER_PAGE);
    }

    private int overlayLeft() {
        return this.leftPos + (RecipeEditorMenu.IMAGE_WIDTH - OVERLAY_W) / 2;
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
            // 序列组装里超出「步骤数」的槽整个不画
            if (!slot.isActive()) {
                continue;
            }
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
        String title = "配方编辑器  ·  " + this.operation.label() + "  ·  " + this.recipeType.label();
        graphics.drawString(this.font, title, 10, 6, COLOR_TEXT, false);

        // 状态提示放标题栏右侧。它原本占着背包上方那 16px——窗口高度因此压不下来，
        // 1080p + GUI 缩放 3（可用高度 360）时最下面的快捷栏会被屏幕裁掉。
        if (statusOk != null) {
            int left = 10 + this.font.width(title) + 8;
            int maxWidth = 328 - left;
            if (maxWidth > 30) {
                String text = fitText(statusMessage, maxWidth);
                graphics.drawString(this.font, text, 328 - this.font.width(text), 6,
                        statusOk ? COLOR_OK : COLOR_FAIL, false);
            }
        }

        if (searching()) {
            return;
        }

        drawSlotLabels(graphics);
        graphics.drawString(this.font, "配方名（可留空）", 12, LABEL_Y, COLOR_LABEL, false);
        // 标签必须短：它从 x=12 起画，太长会撞上右边 x=150 的「烧制时间」标签。
        // 完整说明放在输入框的提示文字里。
        graphics.drawString(this.font, "备注（可留空）", 12, NOTE_LABEL_Y, COLOR_LABEL, false);
        graphics.drawString(this.font, "文件名", FILE_X, LABEL_Y, COLOR_LABEL, false);

        boolean cooking = this.recipeType.hasCookingSettings();
        // 经验框：原版烧炼/烹饪锅是「获得经验」，序列组装借它当「组装成功率」
        if (this.recipeType.isSequenced()) {
            graphics.drawString(this.font, "成功率", RecipeEditorMenu.SEQ_CHANCE_X,
                    RecipeEditorMenu.SEQ_LOOPS_Y - 10, COLOR_LABEL, false);
        } else if (cooking) {
            graphics.drawString(this.font, "获得经验", FIELD_X, XP_FIELD_Y - 10, COLOR_LABEL, false);
        }
        // 时间框：烧制时间 / 处理时间——之前被 hasCookingSettings 一起挡住了，
        // 导致机械动力那个框光秃秃的没有标签
        if (this.recipeType.hasTimeField()) {
            if (this.recipeType.isSequenced()) {
                graphics.drawString(this.font, this.recipeType.timeFieldLabel(),
                        RecipeEditorMenu.SEQ_LOOPS_X, RecipeEditorMenu.SEQ_LOOPS_Y - 10,
                        COLOR_LABEL, false);
            } else {
                int ty = twoFieldRows() ? TIME_FIELD_Y_TWO_ROWS : TIME_FIELD_Y;
                graphics.drawString(this.font, this.recipeType.timeFieldLabel(), FIELD_X, ty - 10,
                        COLOR_LABEL, false);
            }
        }

        renderFoundInfo(graphics);
    }

    /** 在每个输入槽与产物格下面标明是什么 */
    private void drawSlotLabels(GuiGraphics graphics) {
        int inputs = this.recipeType.inputSlotCount();
        String[] labels = this.recipeType.inputLabels();

        if (this.recipeType.isSequenced()) {
            drawSequencedLabels(graphics);
        } else if (this.recipeType.isCrafting()) {
            Slot first = this.menu.slots.get(0);
            graphics.drawString(this.font, labels[0], first.x,
                    RecipeEditorMenu.CRAFT_GRID_Y + 3 * RecipeEditorMenu.SLOT_SIZE + 3,
                    COLOR_SLOT_LABEL, false);
        } else if (this.recipeType.isFarmers()) {
            drawFarmersLabels(graphics);
        } else if (this.recipeType.isCreate() || this.recipeType.isThermal()) {
            drawCreateLabels(graphics);
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

    private EditBox noteField;

    /**
     * 右边这两行是不是都要占。
     *
     * <p>字段位置和标签位置必须用<b>同一个判断</b>：{@code updateWidgetVisibility} 和
     * {@code renderLabels} 一旦用不同的条件，就会出现「框挪走了、标签留在原地」的重叠。
     */
    private boolean twoFieldRows() {
        return this.recipeType.hasCookingSettings() && this.recipeType.hasTimeField();
    }

    /** 序列组装的槽位标注（步骤序号写在按钮上，这里只标第一行） */
    private void drawSequencedLabels(GuiGraphics graphics) {
        graphics.drawString(this.font, "输入物品", RecipeEditorMenu.SEQ_INPUT_X,
                RecipeEditorMenu.SEQ_ROW_Y + 22, COLOR_SLOT_LABEL, false);
        graphics.drawString(this.font, "过渡物品", RecipeEditorMenu.SEQ_TRANSITIONAL_X,
                RecipeEditorMenu.SEQ_ROW_Y + 22, COLOR_SLOT_LABEL, false);
        graphics.drawString(this.font, "失败产物", RecipeEditorMenu.SEQ_FAIL_X,
                RecipeEditorMenu.SEQ_ROW_Y + 22, COLOR_SLOT_LABEL, false);
    }

    /**
     * 机械动力的槽位标注。
     *
     * <p>处理配方的材料是 3×2 网格，所以不能像其它类型那样「每个槽下面一行字」——
     * 那样只有第一个槽会拿到标注，位置还正好落在网格中间。
     */
    private void drawCreateLabels(GuiGraphics graphics) {
        graphics.drawString(this.font, "材料", RecipeEditorMenu.CREATE_GRID_X,
                RecipeEditorMenu.CREATE_LABEL_Y, COLOR_SLOT_LABEL, false);
        graphics.drawString(this.font, "副产物", 200, RecipeEditorMenu.CREATE_LABEL_Y,
                COLOR_TEXT_DIM, false);
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
            // 副产物单独占下面一行，标注写在那一行下面，别跟材料/工具的标签挤在一起
            graphics.drawString(this.font, "副产物（右侧填概率，可留空）",
                    RecipeEditorMenu.EXTRA_SLOT_X0, RecipeEditorMenu.EXTRA_ROW_Y + 21,
                    COLOR_TEXT_DIM, false);
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
        // 先把别的模组（FTB 等）挂上来的按钮摘掉，免得压在版面上
        purgeForeignWidgets();
        // 产物格可能被拖动、被搜索选中、被「我的配方」载入，NBT 状态随时会变
        updateOutputNbtButton();
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
                String cellValue = cell < this.inputItems.size() ? this.inputItems.get(cell) : null;
                if (cellValue != null && cellValue.startsWith("fluid:")) {
                    // 流体：没有物品贴图，画个色块 + 数量，至少能看出「这里放的是流体」
                    graphics.fill(x + slot.x + 1, y + slot.y + 1,
                            x + slot.x + 17, y + slot.y + 17, 0x803B6EA5);
                    graphics.drawString(this.font, "F", x + slot.x + 6, y + slot.y + 5,
                            0xFFFFFFFF, false);
                    continue;
                }
                ItemStack ghost = RecipeEditorMenu.stackOf(cellValue);
                if (!ghost.isEmpty()) {
                    graphics.renderItem(ghost, x + slot.x, y + slot.y);
                }
            }

            // 额外产物（切菜板 / 机械动力副产物 / 序列组装的失败产物）：由客户端画
            int extraCount = this.menu.extraOutputCount();
            if (extraCount > 0) {
                int base = this.menu.outputMenuIndex() + 1;
                for (int i = 0; i < extraCount; i++) {
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

        // 顶部：结果数与目标格；输入的是配方 ID 时改成「回车跳转」
        String query = this.searchField.getValue().trim();
        if (looksLikeRecipeId(query)) {
            graphics.drawString(this.font, "配方 ID：" + query + "  ·  按回车跳转",
                    left + 8, top + 8, COLOR_OK, false);
        } else {
            graphics.drawString(this.font, "搜索结果 " + this.searchResults.size() + " 个 · 点击放入："
                            + (searchGoesToOutput() ? "产物格" : "输入槽"),
                    left + 8, top + 8, COLOR_TEXT, false);
        }

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

    /**
     * 把状态文字压成一行并裁到指定宽度，超出部分用省略号。
     *
     * <p>标题栏右侧空间有限，而保存结果的提示里带换行（「已写入 xxx.js\n标识：xxx」），
     * 直接画会盖到标题上，所以先拍平再裁。完整信息仍然在聊天栏里。
     */
    private String fitText(String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String flat = text.replace('\r', ' ').replace('\n', ' ')
                .replaceAll("\\s+", " ").trim();
        if (this.font.width(flat) <= maxWidth) {
            return flat;
        }
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < flat.length(); i++) {
            if (this.font.width(sb.toString() + flat.charAt(i) + ellipsis) > maxWidth) {
                break;
            }
            sb.append(flat.charAt(i));
        }
        return sb.toString() + ellipsis;
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
            // 搜索框里输 fluid:xxx：回车把它当流体放进第一个空格
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && searching()) {
                String query = this.searchField.getValue().trim();
                if (query.startsWith("fluid:") && query.length() > 6) {
                    fillFirstEmptyCell(query);
                    this.searchField.setValue("");
                    this.searchResults = List.of();
                    this.searchPage = 0;
                    return true;
                }
            }
            // 搜索框里输的是配方 ID：回车直接跳过去
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER && searching()) {
                String query = this.searchField.getValue().trim();
                if (looksLikeRecipeId(query)) {
                    ModNetwork.requestLoadRecipeById(query);
                    this.searchField.setValue("");
                    this.searchResults = List.of();
                    this.searchPage = 0;
                    setStatus(true, "正在查找配方 " + query + " …");
                    return true;
                }
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
                // 右键：空格用「上次拖/选的物品」填上，有内容则清空
                String current = cell < this.inputItems.size() ? this.inputItems.get(cell) : null;
                if ((current == null || current.isBlank()) && this.lastPickedItem != null) {
                    setInput(cell, this.lastPickedItem);
                } else {
                    setInput(cell, null);
                }
            } else {
                ItemStack carried = this.menu.getCarried();
                setInput(cell, carried.isEmpty() ? null : itemId(carried));
            }
            return true;
        }

        // 额外产物格：同一个交互——拿东西点一下放，空手点或右键清空
        int extraCount = this.menu.extraOutputCount();
        if (extraCount > 0) {
            int base = this.menu.outputMenuIndex() + 1;
            for (int i = 0; i < extraCount; i++) {
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
        this.lastPickedItem = itemId;
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
    // ------------------------------------------------------------------ JEI 拖拽

    /**
     * JEI 把物品拖到某个槽位上时，这个槽位收不收。
     *
     * <p>{@code slotIndex} 是菜单槽位下标（不是 containerSlot）：
     * 前 {@code inputMenuCount()} 个是输入展示槽，紧跟其后的是产物格。
     */
    /**
     * 把物品放进第一个空的、可用的输入格；都满了就按当前模式放产物格。
     *
     * <p>快捷键（默认 V）和 JEI 拖拽都走这里，顺便记住这个物品，供右键快速填充用。
     */
    /** 直接把一个字符串（物品 ID 或 fluid:xxx）放进第一个空输入格 */
    public boolean fillFirstEmptyCell(String value) {
        for (int i = 0; i < this.menu.inputMenuCount() && i < this.menu.slots.size(); i++) {
            int cell = this.menu.slots.get(i).getContainerSlot();
            if (isDisabledInput(cell)) {
                continue;
            }
            String current = cell < this.inputItems.size() ? this.inputItems.get(cell) : null;
            if (current == null || current.isBlank()) {
                setInput(cell, value);
                this.lastPickedItem = value;
                return true;
            }
        }
        return false;
    }

    public boolean fillFirstEmptySlot(ItemStack stack) {
        String id = itemId(stack);
        if (id == null) {
            return false;
        }
        this.lastPickedItem = id;
        for (int i = 0; i < this.menu.inputMenuCount() && i < this.menu.slots.size(); i++) {
            int cell = this.menu.slots.get(i).getContainerSlot();
            if (isDisabledInput(cell)) {
                continue;
            }
            String current = cell < this.inputItems.size() ? this.inputItems.get(cell) : null;
            if (current == null || current.isBlank()) {
                setInput(cell, id);
                return true;
            }
        }
        if (searchGoesToOutput()) {
            ModNetwork.requestPickItem(id);
            return true;
        }
        return false;
    }

    public boolean acceptsGhostDrop(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.menu.slots.size()) {
            return false;
        }
        if (slotIndex < this.menu.inputMenuCount()) {
            return !isDisabledInput(this.menu.slots.get(slotIndex).getContainerSlot());
        }
        return slotIndex == this.menu.outputMenuIndex();
    }

    /** JEI 拖拽落下：输入格直接写字符串，产物格让服务端换成这个物品 */
    public void acceptGhostDrop(int slotIndex, ItemStack stack) {
        String id = itemId(stack);
        if (id == null) {
            return;
        }
        this.lastPickedItem = id;
        if (slotIndex < this.menu.inputMenuCount()) {
            setInput(this.menu.slots.get(slotIndex).getContainerSlot(), id);
        } else {
            ModNetwork.requestPickItem(id);
        }
    }

    private boolean isDisabledInput(int index) {
        if (this.operation == Operation.REMOVE) {
            return true;
        }
        // 序列组装：超出「步骤数」的格子、以及压印 / 切割（只用过渡物品本身）的格子都禁用
        if (this.recipeType.isSequenced() && index >= 2) {
            int step = index - 2;
            return step >= this.seqStepCount
                    || step >= this.seqKinds.length
                    || !"deploying".equals(this.seqKinds[step]);
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
            return null;        }
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
        if (this.recipeType.isSequenced()) {
            // 步骤类型编码进 heat（本类型不用热度），项数就是「步骤数」
            this.createHeat = String.join(",", java.util.Arrays.copyOf(this.seqKinds, this.seqStepCount));
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
                collectExtraOutputs(),
                this.createMachine.ordinal(),
                this.createHeat,
                this.noteField == null ? "" : this.noteField.getValue(),
                this.carryOutputNbt);
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

