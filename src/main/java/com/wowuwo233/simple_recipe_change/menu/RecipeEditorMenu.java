package com.wowuwo233.simple_recipe_change.menu;

import com.wowuwo233.simple_recipe_change.kubejs.CreateMachine;
import com.wowuwo233.simple_recipe_change.kubejs.Operation;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndexEntry;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import com.wowuwo233.simple_recipe_change.network.ModNetwork;
import com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 配方编辑菜单。
 *
 * <p><b>输入槽是展示槽</b>：里面从来不放假物品，材料以「物品 ID 字符串」的形式保存在客户端，
 * 由界面自己画出来。这样做有两个好处：
 * <ul>
 *   <li>不可能刷物品——回填的原版材料只是字符串，关界面时没有任何东西可以「归还」给玩家；</li>
 *   <li>标签（{@code #minecraft:planks}）能原样保留，不必再额外记一份标签快照。</li>
 * </ul>
 *
 * <p>只有<b>产物格</b>是真实槽位，因为需要用它的堆叠数量表示产出数量。
 *
 * <p>槽位顺序固定为「输入槽 → 产物格 → 玩家背包」，客户端与服务端两边一致，
 * 因此下标始终对得上。
 */
public class RecipeEditorMenu extends AbstractContainerMenu {

    /** 输入槽容器固定 9 格（合成 3×3 用满，其它类型只用前几个） */
    public static final int GRID_SIZE = 9;

    // ---------------------------------------------------------- 界面布局常量
    // 槽位坐标相对屏幕左上角 (leftPos, topPos)。

    public static final int IMAGE_WIDTH = 470;
    /**
     * 窗口高度。
     *
     * <p>原本是 346（刚好放下搜索框 + 内容 + 控件 + 背包）。序列组装的界面要求
     * 每步摆「机器按钮 + 原料格 + 废品格/概率」三行，346 根本塞不下，所以整体加高。
     *
     * <p>现在定在 <b>370</b>，上限是算出来的：1080p 下 GUI 缩放 3 只有 360 的可用高度
     * （1080/3），窗口一旦超过 360 就会上下各裁掉一截——最直观的现象就是玩家背包的
     * 快捷栏被切掉一半。原来的 388 就踩了这个坑，把底部状态栏那 16px 让出来之后
     * 刚好压到 370，快捷栏能完整露出来。
     *
     * <p>做成<b>所有类型共用</b>而不是按类型变：窗口高度变了就得重建控件
     * （{@code leftPos}/{@code topPos} 是 init 时算的），切换类型时重建会带出
     * 一堆状态重置的坑；共用一套坐标最省事，代价只是别的类型下方多留了点空。
     */
    public static final int IMAGE_HEIGHT = 358;
    public static final int SLOT_SIZE = 18;

    /** 合成类：3×3 网格的行位置 */
    public static final int CRAFT_GRID_Y = 74;
    /** 烧炼 / 锻造的槽位行位置 */
    public static final int ROW_Y = 92;

    /** 添加模式：合成格 → 产物（与普通合成台一致） */
    public static final int ADD_GRID_X = 41;
    public static final int ADD_OUTPUT_X = 123;
    /** 修改 / 删除模式：产物在前，合成格在后 */
    public static final int MOD_GRID_X = 85;
    public static final int MOD_OUTPUT_X = 41;

    /** 烧炼：原料 → 产物 */
    public static final int COOK_INPUT_X = 41;
    public static final int COOK_OUTPUT_X = 99;

    /** 锻造：模板、基础物品、升级物品 → 产物（三者都必填） */
    public static final int SMITH_TEMPLATE_X = 41;    public static final int SMITH_BASE_X = 85;
    public static final int SMITH_ADDITION_X = 129;
    public static final int SMITH_OUTPUT_X = 201;

    /**
     * 农夫乐事 · 烹饪锅：照原版界面摆成 3×2 材料网格，容器放在网格右侧的第二行。
     *
     * <p>这样材料和产物都在左边，不会跑到右边去跟「获得经验 / 烧制时间」两个输入框打架。
     */
    public static final int COOK_GRID_X = 41;
    public static final int COOK_GRID_Y = 76;
    public static final int COOK_CONTAINER_X = 113;
    public static final int COOK_CONTAINER_Y = 94;

    /** 农夫乐事 · 切菜板：材料与工具分开摆，别挤在一起 */
    public static final int CUT_INPUT_X = 41;
    public static final int CUT_TOOL_X = 101;
    public static final int CUT_ROW_Y = 94;

    public static final int FARMERS_OUTPUT_X = 145;
    public static final int FARMERS_OUTPUT_Y = 94;

    /**
     * 农夫乐事所有槽位标注共用的 y。
     *
     * <p>统一画在槽位<b>下方</b>，和原版那些类型保持一致——之前有上有下，
     * 而且画在槽上方时会顶到搜索框（搜索框占 46..64）。
     */
    public static final int FARMERS_LABEL_Y = 115;

    /** 机械动力：材料 3×3 网格（9 格），产物在右侧，额外产物单独排一行 */
    public static final int CREATE_GRID_X = 41;
    public static final int CREATE_GRID_Y = 76;
    public static final int CREATE_OUTPUT_X = 145;
    /** 机械动力槽位标注共用的 y（3×3 网格止于 130，所以标在它下面） */
    public static final int CREATE_LABEL_Y = 133;
    /** 处理配方的材料格数（3×3） */
    public static final int CREATE_MATERIAL_SLOTS = 9;

    /**
     * 机械动力 · 序列组装。
     *
     * <p>第一行是「输入物品 / 过渡物品 / 产物」，下面一排 4 个步骤：
     * 每个步骤上面一个按钮（部署 / 压印 / 切割），下面一个材料格。
     */
    public static final int SEQ_STEP_COUNT = 6;
    public static final int SEQ_ROW_Y = 76;
    public static final int SEQ_INPUT_X = 41;
    public static final int SEQ_TRANSITIONAL_X = 101;
    public static final int SEQ_OUTPUT_X = 161;
    /** 失败产物（产物按权重随机，成功率之外的权重给它） */
    public static final int SEQ_FAIL_X = 221;
    /** 步骤按钮：6 个并排，宽度压到 46 才排得下 */
    public static final int SEQ_BUTTON_X0 = 41;
    public static final int SEQ_BUTTON_STEP = 63;
    public static final int SEQ_BUTTON_Y = 112;
    public static final int SEQ_BUTTON_W = 60;
    public static final int SEQ_SLOT_Y = 136;
    /** 步骤材料格在按钮下方的居中偏移 */
    public static final int SEQ_SLOT_INSET = 14;
    /** 循环次数 / 成功率输入框（第一行右侧，失败产物止于 232） */
    public static final int SEQ_LOOPS_X = 265;
    public static final int SEQ_LOOPS_Y = 76;
    public static final int SEQ_LOOPS_W = 36;
    public static final int SEQ_CHANCE_X = 313;
    public static final int SEQ_CHANCE_W = 36;
    /**
     * 机械动力的额外产物行。
     *
     * <p>比其它类型（{@link #EXTRA_ROW_Y}）低 19px——它的 3×3 材料网格多占一行，
     * 用原来的 127 会压到网格第三行上。
     */
    public static final int CREATE_EXTRA_ROW_Y = 146;

    /** 切菜板的额外产物：4 个槽 + 4 个概率输入框，全部排在标注行下面 */
    public static final int EXTRA_OUTPUT_COUNT = 4;
    /** 额外产物借用容器里空着的下标（切菜板材料只用 0、1） */
    public static final int EXTRA_OUTPUT_CONTAINER_BASE = 2;
    public static final int EXTRA_ROW_Y = 127;
    public static final int EXTRA_SLOT_X0 = 37;
    public static final int EXTRA_SLOT_STEP = 80;

    /** 「物品用标签」开关的位置（在下面那排按钮的最右边） */    public static final int TAG_BUTTON_X = 349;
    public static final int TAG_BUTTON_Y = 258;
    public static final int TAG_BUTTON_W = 96;

    /**
     * 玩家背包。
     *
     * <p>整块上移了 12px：底部状态栏挪到标题栏右侧之后，这里就不用再给状态文字留位置，
     * 背包跟着提上来，窗口高度才收得到 370。
     */
    public static final int PLAYER_X = 154;
    public static final int PLAYER_Y = 284;
    public static final int HOTBAR_Y = 340;

    /** 背包的实际 y（序列组装要往下让） */
    public int playerY() {
        return PLAYER_Y;
    }

    public int hotbarY() {
        return HOTBAR_Y;
    }

    /**
     * 搜索浮层打开时把所有槽位设为不可用。
     *
     * <p>{@code AbstractContainerScreen} 只渲染 {@code slot.isActive()} 为真的槽位，
     * 所以这样能让背包物品<b>完全不绘制</b>，而不是靠浮层去盖——
     * 物品是用另一套渲染类型画的，光靠「后画一层不透明矩形」并不可靠。
     */
    private boolean slotsHidden = false;

    /** 客户端在搜索时调用；只影响渲染与点击，不影响服务端读取容器内容 */
    public void setSlotsHidden(boolean hidden) {
        this.slotsHidden = hidden;
    }

    /** 可整体隐藏的槽位 */
    private class EditorSlot extends Slot {
        EditorSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !slotsHidden;
        }
    }

    /** 纯展示、不可交互的输入槽 */
    private final class DisplaySlot extends EditorSlot {
        DisplaySlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            // 序列组装：超出「步骤数」的步骤槽整个不显示，不然会留下一排没按钮的红框
            if (recipeType.isSequenced()) {
                int cell = getContainerSlot();
                if (cell >= 2 && cell < 2 + SEQ_STEP_COUNT) {
                    return super.isActive() && cell - 2 < seqStepCount;
                }
            }
            return super.isActive();
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isHighlightable() {
            return false;
        }
    }

    /** 只用来给展示槽当载体，永远保持为空 */
    private final Container placeholder = new SimpleContainer(GRID_SIZE);
    /** 序列组装的失败产物单独用一个容器：复用 placeholder 的下标会和步骤槽撞车 */
    private final Container failureSlot = new SimpleContainer(1);
    private final Container output = new SimpleContainer(1);
    private final Player owner;
    private final Inventory playerInventory;

    private Operation operation = Operation.ADD;
    /** 当前配方类型；服务端也要知道，因为不同类型要去不同的配方分类里反查 */
    private RecipeType recipeType = RecipeType.CRAFTING_TABLE;
    /**
     * 序列组装用几步（1~6）。
     *
     * <p>只有客户端知道，服务端不需要——但它决定「多出来的步骤槽显不显示」，
     * 所以由客户端在切步骤数时推过来。
     */
    private int seqStepCount = SEQ_STEP_COUNT;

    public void setSeqStepCount(int count) {
        this.seqStepCount = Math.max(1, Math.min(SEQ_STEP_COUNT, count));
    }
    /** 被替换掉的原配方 ID（仅「修改配方」用） */
    private String sourceRecipeId = "";
    /** 缓存最近一次查到的原版配方 */
    private VanillaRecipeLookup.Found lastFound;

    private ItemStack lastOutput = ItemStack.EMPTY;

    public RecipeEditorMenu(int windowId, Inventory playerInventory) {
        super(ModMenus.RECIPE_EDITOR.get(), windowId);
        this.playerInventory = playerInventory;
        this.owner = playerInventory.player;
        layoutSlots(true);
    }

    /**
     * 按当前类型与操作摆放槽位。
     *
     * <p>{@code Slot.x}/{@code Slot.y} 是 final，构造完就改不了，所以切换类型/模式时
     * 只能重建 {@code slots} 列表。槽位<b>顺序与数量在两端保持一致</b>，网络同步不受影响。
     *
     * <p><b>踩过的坑</b>：重建时不能无脑绕开 {@code addSlot()}。它除了往 {@code slots} 里加，
     * 还会往私有的 {@code lastSlots}/{@code remoteSlots} 里加——而 {@code broadcastChanges()}
     * 会按下标去读那两个列表。曾经以为「槽位数只会减少」，直到机械动力的布局
     * （9 材料 + 1 产物 + 4 额外产物）超过了首次构建的 46 格，直接
     * {@code IndexOutOfBoundsException: Index 46 out of bounds for length 46}。
     * 现在由 {@link #place} 按下标判断：顶到已同步长度就走 {@code addSlot()} 把它撑大。
     */
    public void layoutSlots(boolean firstTime) {
        this.slots.clear();

        switch (recipeType.category()) {
            case CRAFTING -> {
                boolean adding = operation == Operation.ADD;
                int gridX = adding ? ADD_GRID_X : MOD_GRID_X;
                int outX = adding ? ADD_OUTPUT_X : MOD_OUTPUT_X;
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        place(new DisplaySlot(this.placeholder, row * 3 + col,
                                gridX + col * SLOT_SIZE, CRAFT_GRID_Y + row * SLOT_SIZE), firstTime);
                    }
                }
                place(new EditorSlot(this.output, 0, outX, ROW_Y), firstTime);
            }
            case COOKING -> {
                place(new DisplaySlot(this.placeholder, 0, COOK_INPUT_X, ROW_Y), firstTime);
                place(new EditorSlot(this.output, 0, COOK_OUTPUT_X, ROW_Y), firstTime);
            }
            case SMITHING -> {
                place(new DisplaySlot(this.placeholder, 0, SMITH_TEMPLATE_X, ROW_Y), firstTime);
                place(new DisplaySlot(this.placeholder, 1, SMITH_BASE_X, ROW_Y), firstTime);
                place(new DisplaySlot(this.placeholder, 2, SMITH_ADDITION_X, ROW_Y), firstTime);
                place(new EditorSlot(this.output, 0, SMITH_OUTPUT_X, ROW_Y), firstTime);
            }
            case CREATE -> {
                if (recipeType.isSequenced()) {
                    // 输入 → 过渡物品 一行，下面 4 个步骤（槽位由客户端画按钮）。
                    // 产物槽必须排在<b>所有输入槽之后</b>：outputMenuIndex() 按「输入槽数量」
                    // 推算它的下标，插在中间会让产物标签画到步骤槽上去。
                    place(new DisplaySlot(this.placeholder, 0, SEQ_INPUT_X, SEQ_ROW_Y), firstTime);
                    place(new DisplaySlot(this.placeholder, 1, SEQ_TRANSITIONAL_X, SEQ_ROW_Y), firstTime);
                    for (int i = 0; i < SEQ_STEP_COUNT; i++) {
                        place(new DisplaySlot(this.placeholder, 2 + i,
                                SEQ_BUTTON_X0 + i * SEQ_BUTTON_STEP + SEQ_SLOT_INSET,
                                SEQ_SLOT_Y), firstTime);
                    }
                    place(new EditorSlot(this.output, 0, SEQ_OUTPUT_X, SEQ_ROW_Y), firstTime);
                    // 失败产物：排在产物之后的额外产物格（索引 9），内容由客户端维护
                    place(new DisplaySlot(this.failureSlot, 0, SEQ_FAIL_X, SEQ_ROW_Y), firstTime);
                } else {
                    // 处理配方：材料 3×3（9 格），网格下方是标注行，再下面是额外产物行
                    for (int i = 0; i < CREATE_MATERIAL_SLOTS; i++) {
                        place(new DisplaySlot(this.placeholder, i,
                                CREATE_GRID_X + (i % 3) * SLOT_SIZE,
                                CREATE_GRID_Y + (i / 3) * SLOT_SIZE), firstTime);
                    }
                    place(new EditorSlot(this.output, 0, CREATE_OUTPUT_X,
                            CREATE_GRID_Y + SLOT_SIZE), firstTime);

                    int extra = extraOutputCount();
                    for (int i = 0; i < extra; i++) {
                        place(new DisplaySlot(this.placeholder, EXTRA_OUTPUT_CONTAINER_BASE + i,
                                EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP, CREATE_EXTRA_ROW_Y), firstTime);
                    }
                }
            }
            case THERMAL -> {
                // 热力膨胀：和机械动力处理配方同一套摆法（3×3 材料 + 产物 + 副产物行）
                for (int i = 0; i < CREATE_MATERIAL_SLOTS; i++) {
                    place(new DisplaySlot(this.placeholder, i,
                            CREATE_GRID_X + (i % 3) * SLOT_SIZE,
                            CREATE_GRID_Y + (i / 3) * SLOT_SIZE), firstTime);
                }
                place(new EditorSlot(this.output, 0, CREATE_OUTPUT_X,
                        CREATE_GRID_Y + SLOT_SIZE), firstTime);
                int extraThermal = extraOutputCount();
                for (int i = 0; i < extraThermal; i++) {
                    place(new DisplaySlot(this.placeholder, EXTRA_OUTPUT_CONTAINER_BASE + i,
                            EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP, CREATE_EXTRA_ROW_Y), firstTime);
                }
            }
            case FARMERS -> {
                if (recipeType == RecipeType.FARMERS_COOKING) {
                    // 3×2 材料网格（下标 0..5，行优先），照原版烹饪锅的样子
                    for (int i = 0; i < 6; i++) {
                        place(new DisplaySlot(this.placeholder, i,
                                COOK_GRID_X + (i % 3) * SLOT_SIZE,
                                COOK_GRID_Y + (i / 3) * SLOT_SIZE), firstTime);
                    }
                    // 容器单独放网格下面一行（下标 6，可留空）
                    place(new DisplaySlot(this.placeholder, 6,
                            COOK_CONTAINER_X, COOK_CONTAINER_Y), firstTime);
                } else {
                    // 切菜板：材料 0、工具 1，分开摆
                    place(new DisplaySlot(this.placeholder, 0, CUT_INPUT_X, CUT_ROW_Y), firstTime);
                    place(new DisplaySlot(this.placeholder, 1, CUT_TOOL_X, CUT_ROW_Y), firstTime);
                }
                place(new EditorSlot(this.output, 0, FARMERS_OUTPUT_X, FARMERS_OUTPUT_Y), firstTime);

                // 额外产物只有切菜板有，排在下面一行（纯展示槽，内容由客户端维护）
                int extra = extraOutputCount();
                for (int i = 0; i < extra; i++) {
                    place(new DisplaySlot(this.placeholder, EXTRA_OUTPUT_CONTAINER_BASE + i,
                            EXTRA_SLOT_X0 + i * EXTRA_SLOT_STEP, EXTRA_ROW_Y), firstTime);
                }
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                place(new EditorSlot(this.playerInventory, col + row * 9 + 9,
                        PLAYER_X + col * SLOT_SIZE, playerY() + row * SLOT_SIZE), firstTime);
            }
        }
        for (int col = 0; col < 9; col++) {
            place(new EditorSlot(this.playerInventory, col,
                    PLAYER_X + col * SLOT_SIZE, hotbarY()), firstTime);
        }
    }

    /**
     * 客户端按当前选择重建槽位。
     *
     * <p>会把操作与类型同步到菜单对象上——槽位坐标只存在于各自那一侧的 {@code Slot} 里，
     * 客户端必须自己把菜单对象更新成所选类型，否则布局会一直停在旧类型上。
     */
    public void applyLayout(Operation newOperation, RecipeType newType) {
        this.operation = newOperation;
        this.recipeType = newType;
        layoutSlots(false);
    }

    /** 客户端版本的 {@link #applyLayout}：多带上 Create 机器，因为它决定副产物槽摆不摆 */
    public void applyLayout(Operation newOperation, RecipeType newType, CreateMachine machine) {
        setCreateMachine(machine);
        applyLayout(newOperation, newType);
    }

    /**
     * 通过 {@code addSlot()} 追加过的槽位总数——也就是 {@code AbstractContainerMenu} 里
     * {@code lastSlots}/{@code remoteSlots} 的实际长度。
     *
     * <p>那两个列表是私有的，且<b>只有 addSlot 会让它们增长</b>。所以重建槽位时，
     * 凡是下标已经顶到这个长度就必须走 addSlot，否则 {@code broadcastChanges()}
     * 会拿越界下标去取它们，直接抛 IndexOutOfBoundsException。
     */
    private int syncedSlotCount = 0;

    private void place(Slot slot, boolean firstTime) {
        if (firstTime || this.slots.size() >= this.syncedSlotCount) {
            this.addSlot(slot);
            this.syncedSlotCount = this.slots.size();
        } else {
            slot.index = this.slots.size();
            this.slots.add(slot);
        }
    }

    // ------------------------------------------------------------------ 查询

    public Container getOutputContainer() {
        return output;
    }

    public Operation getOperation() {
        return operation;
    }

    public RecipeType getRecipeType() {
        return recipeType;
    }

    public String getSourceRecipeId() {
        return sourceRecipeId;
    }

    public VanillaRecipeLookup.Found getLastFound() {
        return lastFound;
    }

    /**
     * 菜单里输入槽的数量。
     *
     * <p>注意与 {@link RecipeType#inputSlotCount()} 区分：后者是「这个配方逻辑上有几个材料」
     * （物品栏合成是 4 个），而合成类的容器<b>始终是 3×3 的 9 格</b>，
     * 2×2 只是把多余的第 3 行/列禁用掉。两者混用会导致取错槽位。
     */
    public int inputMenuCount() {
        return switch (recipeType.category()) {
            case CRAFTING -> GRID_SIZE;
            case COOKING -> 1;
            case SMITHING -> 3;
            case FARMERS, CREATE, THERMAL -> recipeType.inputSlotCount();
        };
    }

    /** 产物格在 slots 列表里的下标（紧跟输入槽之后） */
    public int outputMenuIndex() {
        return inputMenuCount();
    }

    /**
     * 当前选的 Create 机器。
     *
     * <p>槽位布局是服务端建的，而机器是客户端选的，所以它必须跟着 {@code SetModePacket}
     * 同步过来——否则服务端不知道该不该给这台机器摆副产物槽。
     */
    private CreateMachine createMachine = CreateMachine.CRUSHING;

    public void setCreateMachine(CreateMachine machine) {
        this.createMachine = machine == null ? CreateMachine.CRUSHING : machine;
    }

    public CreateMachine getCreateMachine() {
        return createMachine;
    }

    /**
     * 额外产物槽的数量。
     *
     * <p>机械动力只在<b>真有副产物</b>的机器上显示：统计过 Create 自带的 1766 个官方配方，
     * 只有粉碎（179/192 多产物）、研磨（159/222）、洗涤（9/38）、缠魂（1/21）有，
     * 其余机器全是单产物——给它们摆副产物槽纯属误导。
     */
    public int extraOutputCount() {
        if (recipeType.isSequenced()) {
            // 序列组装只留一格「失败产物」，没有概率框（权重由成功率反推）
            return 1;
        }
        if (recipeType.isCreate()) {
            return createMachine.hasByproducts() ? EXTRA_OUTPUT_COUNT : 0;
        }
        return recipeType.hasMultipleOutputs() ? EXTRA_OUTPUT_COUNT : 0;
    }

    /** 副产物要不要带概率框 */
    public boolean hasByproductChance() {
        if (recipeType.isSequenced()) {
            // 序列组装只做单产物，不摆概率框
            return false;
        }
        return recipeType.isCreate() ? createMachine.hasByproducts() : recipeType.hasChanceResults();
    }

    /** 玩家背包槽在 slots 列表里的起点（输入槽 → 产物格 → 额外产物 → 背包） */
    public int playerMenuStart() {
        return outputMenuIndex() + 1 + extraOutputCount();
    }

    // ------------------------------------------------------------------ 模式

    /** 客户端切换操作或配方类型时调用（服务端执行） */
    public void setMode(ServerPlayer player, Operation newOperation, RecipeType newType,
                        CreateMachine machine) {
        // 机器要在 layoutSlots 之前设好——它决定副产物槽摆几个
        setCreateMachine(machine);
        this.operation = newOperation;
        this.recipeType = newType;
        layoutSlots(false);

        if (!newOperation.needsLookup()) {
            this.lastFound = null;
            this.sourceRecipeId = "";
            // 添加模式服务端没有材料数据，绝不能覆盖客户端已放好的原料
            ModNetwork.sendEditorState(player, this, List.of(), false, List.of(), 0, "", "");
            return;
        }
        handleOutputChanged(player);
    }

    // ------------------------------------------------------------------ 输出变化

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof ServerPlayer serverPlayer) {
            ItemStack current = this.output.getItem(0);
            // 只在产物格真的变了的时候才查配方，避免每 tick 遍历全部配方
            if (!ItemStack.matches(current, this.lastOutput)) {
                handleOutputChanged(serverPlayer);
            }
        }
    }

    private void handleOutputChanged(ServerPlayer player) {
        this.lastOutput = this.output.getItem(0).copy();
        this.lastFound = null;
        this.sourceRecipeId = "";

        if (operation.needsLookup()) {
            this.lastFound = VanillaRecipeLookup.find(
                    player.serverLevel(), this.output.getItem(0), this.recipeType);
        }

        if (this.lastFound != null) {
            this.sourceRecipeId = this.lastFound.recipeId();
        }

        // 只有真的查到原版配方时才把材料发给客户端回填；查不到就保持客户端原样，
        // 否则添加模式下「先放原料、再放产物」会把刚放好的原料清掉
        boolean replace = this.lastFound != null;
        ModNetwork.sendEditorState(player, this,
                replace ? this.lastFound.cells() : List.of(), replace, List.of(), 0, "", "");
    }

    // ------------------------------------------------------------------ 载入已有配方

    /** 把「我的配方」里的一条记录发回客户端回填 */
    public void applyEntry(ServerPlayer player, RecipeIndexEntry entry) {
        this.operation = entry.operation();
        this.recipeType = entry.type();
        this.sourceRecipeId = entry.sourceRecipeId();
        this.lastFound = null;
        layoutSlots(false);

        this.output.setItem(0, stackOf(entry.outputItem(), entry.outputNbt()));
        if (!entry.outputItem().isBlank()) {
            ItemStack out = this.output.getItem(0);
            if (!out.isEmpty()) {
                out.setCount(Math.max(1, Math.min(64, entry.outputCount())));
            }
        }
        this.lastOutput = this.output.getItem(0).copy();

        ModNetwork.sendLoadedState(player, this, entry);
    }

    /**
     * 按配方 ID 查到的配方：切到对应类型、进入「修改配方」、把产物放进产物格，再整包推给客户端。
     *
     * <p>进入 MODIFY 是因为玩家点「跳转」十有八九是想改这条配方；
     * 保存时会先 {@code event.remove({id})} 再写新的。
     */
    /** 最近一次「按 ID 跳转」载入的配方；客户端界面建好后会主动要一次，防止首个状态包被丢 */
    private VanillaRecipeLookup.Found pendingFound;

    public void resendFound(ServerPlayer player) {
        if (this.pendingFound != null) {
            ModNetwork.sendRecipeById(player, this, this.pendingFound);
        }
    }

    public void applyFoundById(ServerPlayer player, VanillaRecipeLookup.Found found) {
        this.pendingFound = found;
        this.operation = Operation.MODIFY;
        this.recipeType = found.suggestedType();
        this.sourceRecipeId = found.recipeId();
        this.lastFound = found;
        setCreateMachine(found.machine());
        layoutSlots(false);

        ItemStack out = stackOf(found.outputItem());
        if (!out.isEmpty()) {
            out.setCount(Math.max(1, Math.min(64, found.outputCount())));
        }
        this.output.setItem(0, out);
        this.lastOutput = out.copy();

        ModNetwork.sendRecipeById(player, this, found);
    }

    /** 清空编辑区（产物格里的东西还给玩家） */
    public void clearEditor(Player player) {
        returnItems(player, this.output);
        this.lastOutput = ItemStack.EMPTY;
        this.lastFound = null;
        this.sourceRecipeId = "";
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendEditorState(serverPlayer, this, List.of(), true, List.of(), 0, "", "");
        }
    }

    /** 把标签或物品 ID 变成一个用于显示的物品（标签取其中第一个物品） */
    public static ItemStack stackOf(String id) {
        if (id == null || id.isBlank()) {
            return ItemStack.EMPTY;
        }
        if (id.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(id.substring(1));
            if (tagId == null) {
                return ItemStack.EMPTY;
            }
            var holders = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId));
            if (holders.isPresent()) {
                for (Holder<Item> holder : holders.get()) {
                    return new ItemStack(holder.value());
                }
            }
            return ItemStack.EMPTY;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * 同上，并把 SNBT 套到物品上。
     *
     * <p>「我的配方」载入时必须走这个重载：产物槽是真实槽位，载入时得把附魔、药水效果、
     * 自定义名称这些 NBT 一起放回去，否则再保存一次就把标签弄丢了。
     */
    public static ItemStack stackOf(String id, String nbt) {
        ItemStack stack = stackOf(id);
        if (stack.isEmpty() || nbt == null || nbt.isBlank()) {
            return stack;
        }
        try {
            CompoundTag tag = TagParser.parseTag(nbt);
            stack.setTag(tag);
        } catch (Exception ignored) {
            // 索引里的 SNBT 坏了就退回白板物品——总比整个界面打不开好
        }
        return stack;
    }

    // ------------------------------------------------------------------ 基础

    @Override
    public boolean stillValid(Player player) {
        // 编辑器不依赖方块位置，始终有效；避免玩家走动时界面被强制关闭。
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // 输入槽不可交互；这里只处理产物格与玩家背包之间的转移
        Slot slot = this.slots.get(index);
        if (!slot.hasItem() || slot instanceof DisplaySlot) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = playerMenuStart();

        if (index == outputMenuIndex()) {
            if (!this.moveItemStackTo(stack, playerStart, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, outputMenuIndex(), outputMenuIndex() + 1, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 只在服务端归还物品，客户端那份是同步副本，动了会导致不同步。
        // 输入槽本来就没有真实物品，不需要处理。
        if (!player.level().isClientSide()) {
            returnItems(player, this.output);
        }
    }

    private void returnItems(Player player, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.removeItemNoUpdate(i);
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }
}
