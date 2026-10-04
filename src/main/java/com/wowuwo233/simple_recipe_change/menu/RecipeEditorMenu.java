package com.wowuwo233.simple_recipe_change.menu;

import com.wowuwo233.simple_recipe_change.kubejs.Operation;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndexEntry;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import com.wowuwo233.simple_recipe_change.network.ModNetwork;
import com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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

    public static final int IMAGE_WIDTH = 340;
    public static final int IMAGE_HEIGHT = 346;
    public static final int SLOT_SIZE = 18;

    /** 合成类：3×3 网格的行位置 */
    public static final int CRAFT_GRID_Y = 74;
    /** 烧炼 / 锻造的槽位行位置 */
    public static final int ROW_Y = 92;

    /** 添加模式：合成格 → 产物（与普通合成台一致） */
    public static final int ADD_GRID_X = 16;
    public static final int ADD_OUTPUT_X = 98;
    /** 修改 / 删除模式：产物在前，合成格在后 */
    public static final int MOD_GRID_X = 60;
    public static final int MOD_OUTPUT_X = 16;

    /** 烧炼：原料 → 产物 */
    public static final int COOK_INPUT_X = 16;
    public static final int COOK_OUTPUT_X = 74;

    /** 锻造：模板、基础物品、升级物品 → 产物（三者都必填） */
    public static final int SMITH_TEMPLATE_X = 16;    public static final int SMITH_BASE_X = 60;
    public static final int SMITH_ADDITION_X = 104;
    public static final int SMITH_OUTPUT_X = 176;

    /**
     * 农夫乐事 · 烹饪锅：照原版界面摆成 3×2 材料网格，容器放在网格右侧的第二行。
     *
     * <p>这样材料和产物都在左边，不会跑到右边去跟「获得经验 / 烧制时间」两个输入框打架。
     */
    public static final int COOK_GRID_X = 16;
    public static final int COOK_GRID_Y = 76;
    public static final int COOK_CONTAINER_X = 88;
    public static final int COOK_CONTAINER_Y = 94;

    /** 农夫乐事 · 切菜板：材料与工具分开摆，别挤在一起 */
    public static final int CUT_INPUT_X = 16;
    public static final int CUT_TOOL_X = 76;
    public static final int CUT_ROW_Y = 94;

    public static final int FARMERS_OUTPUT_X = 120;
    public static final int FARMERS_OUTPUT_Y = 94;

    /**
     * 农夫乐事所有槽位标注共用的 y。
     *
     * <p>统一画在槽位<b>下方</b>，和原版那些类型保持一致——之前有上有下，
     * 而且画在槽上方时会顶到搜索框（搜索框占 46..64）。
     */
    public static final int FARMERS_LABEL_Y = 115;

    /** 切菜板的额外产物：4 个槽 + 4 个概率输入框，全部排在标注行下面 */
    public static final int EXTRA_OUTPUT_COUNT = 4;
    /** 额外产物借用容器里空着的下标（切菜板材料只用 0、1） */
    public static final int EXTRA_OUTPUT_CONTAINER_BASE = 2;
    public static final int EXTRA_ROW_Y = 127;
    public static final int EXTRA_SLOT_X0 = 12;
    public static final int EXTRA_SLOT_STEP = 80;

    /** 「物品用标签」开关的位置（在下面那排按钮的最右边） */
    public static final int TAG_BUTTON_X = 268;
    public static final int TAG_BUTTON_Y = 218;
    public static final int TAG_BUTTON_W = 60;

    /** 玩家背包 */
    public static final int PLAYER_X = 89;
    public static final int PLAYER_Y = 262;
    public static final int HOTBAR_Y = 320;

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
    private final Container output = new SimpleContainer(1);
    private final Player owner;
    private final Inventory playerInventory;

    private Operation operation = Operation.ADD;
    /** 当前配方类型；服务端也要知道，因为不同类型要去不同的配方分类里反查 */
    private RecipeType recipeType = RecipeType.CRAFTING_TABLE;
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
     * <p>注意：重建时不能再用 {@code addSlot()}——它还会往私有的
     * {@code lastSlots}/{@code remoteSlots} 里追加。首次构建走 {@code addSlot()}，
     * 之后只替换 {@code slots} 本身（槽位数只会减少，不会超过首次的长度）。
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
                        PLAYER_X + col * SLOT_SIZE, PLAYER_Y + row * SLOT_SIZE), firstTime);
            }
        }
        for (int col = 0; col < 9; col++) {
            place(new EditorSlot(this.playerInventory, col, PLAYER_X + col * SLOT_SIZE, HOTBAR_Y), firstTime);
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

    private void place(Slot slot, boolean firstTime) {
        if (firstTime) {
            this.addSlot(slot);
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
            case FARMERS -> recipeType.inputSlotCount();
        };
    }

    /** 产物格在 slots 列表里的下标（紧跟输入槽之后） */
    public int outputMenuIndex() {
        return inputMenuCount();
    }

    /** 额外产物槽的数量（只有切菜板有） */
    public int extraOutputCount() {
        return recipeType.hasMultipleOutputs() ? EXTRA_OUTPUT_COUNT : 0;
    }

    /** 玩家背包槽在 slots 列表里的起点（输入槽 → 产物格 → 额外产物 → 背包） */
    public int playerMenuStart() {
        return outputMenuIndex() + 1 + extraOutputCount();
    }

    // ------------------------------------------------------------------ 模式

    /** 客户端切换操作或配方类型时调用（服务端执行） */
    public void setMode(ServerPlayer player, Operation newOperation, RecipeType newType) {
        this.operation = newOperation;
        this.recipeType = newType;
        layoutSlots(false);

        if (!newOperation.needsLookup()) {
            this.lastFound = null;
            this.sourceRecipeId = "";
            // 添加模式服务端没有材料数据，绝不能覆盖客户端已放好的原料
            ModNetwork.sendEditorState(player, this, List.of(), false, List.of());
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
                replace ? this.lastFound.cells() : List.of(), replace, List.of());
    }

    // ------------------------------------------------------------------ 载入已有配方

    /** 把「我的配方」里的一条记录发回客户端回填 */
    public void applyEntry(ServerPlayer player, RecipeIndexEntry entry) {
        this.operation = entry.operation();
        this.recipeType = entry.type();
        this.sourceRecipeId = entry.sourceRecipeId();
        this.lastFound = null;
        layoutSlots(false);

        this.output.setItem(0, stackOf(entry.outputItem()));
        if (!entry.outputItem().isBlank()) {
            ItemStack out = this.output.getItem(0);
            if (!out.isEmpty()) {
                out.setCount(Math.max(1, Math.min(64, entry.outputCount())));
            }
        }
        this.lastOutput = this.output.getItem(0).copy();

        ModNetwork.sendLoadedState(player, this, entry);
    }

    /** 清空编辑区（产物格里的东西还给玩家） */
    public void clearEditor(Player player) {
        returnItems(player, this.output);
        this.lastOutput = ItemStack.EMPTY;
        this.lastFound = null;
        this.sourceRecipeId = "";
        if (player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendEditorState(serverPlayer, this, List.of(), true, List.of());
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
