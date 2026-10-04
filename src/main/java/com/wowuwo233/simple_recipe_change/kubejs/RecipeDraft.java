package com.wowuwo233.simple_recipe_change.kubejs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一份待写入 KubeJS 的配方草稿。
 *
 * <p>{@code cells} 固定 9 项，含义随 {@link RecipeType#category()} 变化：
 * <ul>
 *   <li>合成类：按 3×3 行优先排布（物品栏 2×2 时由 {@link #cropToGrid()} 裁成 2×2）</li>
 *   <li>烧炼类：只用到第 0 项，即原料</li>
 *   <li>锻造类：第 0/1/2 项依次是模板、基础物品、升级物品</li>
 * </ul>
 * 空格子为 {@code null}。
 *
 * <p>{@code sourceRecipeId} 只在「修改配方」时有意义：它是被替换掉的原配方 ID，
 * 生成时会先写一条 {@code event.remove({ id: sourceRecipeId })}。
 *
 * <p>刻意不依赖 Minecraft。
 */
public record RecipeDraft(
        RecipeType type,
        boolean shapeless,
        boolean mirrored,
        String recipeId,
        List<String> cells,
        String outputItem,
        int outputCount,
        Operation operation,
        RemoveBy removeBy,
        String sourceRecipeId,
        double xp,
        int cookingTime,
        List<ExtraOutput> extraOutputs,
        String sound,
        CreateMachine machine,
        String heat,
        boolean keepHeldItem,
        String note
) {
    /** 原版烧炼配方的默认烧制时间（tick） */
    public static final int DEFAULT_COOKING_TIME = 200;

    /**
     * 切菜板的额外产物。
     *
     * @param item   物品 ID
     * @param count  数量
     * @param chance 概率，0–1；{@code >= 1} 时写成普通产物，{@code < 1} 时写成
     *               {@code ChanceResult.of(...)}
     */
    public record ExtraOutput(String item, int count, double chance) {
        public ExtraOutput {
            if (count < 1) {
                count = 1;
            }
            // 概率是 0–1 的闭区间：0 表示「必然拿不到」，1 表示「必定拿到」。
            // 注意不能把 0 当成「没填」——那会把「必然失败」错写成「必定成功」。
            if (chance < 0D) {
                chance = 0D;
            }
            if (chance > 1D) {
                chance = 1D;
            }
        }
    }

    public RecipeDraft {
        if (type == null) {
            type = RecipeType.CRAFTING_TABLE;
        }
        if (operation == null) {
            operation = Operation.ADD;
        }
        if (removeBy == null) {
            removeBy = RemoveBy.ID;
        }
        if (sourceRecipeId == null) {
            sourceRecipeId = "";
        }
        if (cookingTime <= 0) {
            // 原版烧炼是 200 tick，机械动力的处理配方是 100——默认值随类型走
            cookingTime = type.defaultProcessingTime();
        }
        if (sound == null) {
            sound = "";
        }
        if (machine == null) {
            machine = CreateMachine.CRUSHING;
        }
        if (heat == null) {
            heat = "";
        }
        if (note == null) {
            note = "";
        }
        // 空格子是 null，所以不能用 List.copyOf（它会拒绝 null 元素）。
        cells = cells == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(cells));
        extraOutputs = extraOutputs == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(extraOutputs));
    }

    /** 便捷构造：添加配方 */
    public RecipeDraft(RecipeType type, boolean shapeless, boolean mirrored, String recipeId,
                       List<String> cells, String outputItem, int outputCount) {
        this(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                Operation.ADD, RemoveBy.ID, "", 0D, 0, List.of(), "", CreateMachine.CRUSHING, "", false, "");
    }

    /** 便捷构造：指定操作，无原配方 */
    public RecipeDraft(RecipeType type, boolean shapeless, boolean mirrored, String recipeId,
                       List<String> cells, String outputItem, int outputCount,
                       Operation operation, RemoveBy removeBy) {
        this(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, "", 0D, 0, List.of(), "", CreateMachine.CRUSHING, "", false, "");
    }

    /** 便捷构造：带原配方 ID 与烧炼参数（切菜板的额外产物与音效留空） */
    public RecipeDraft(RecipeType type, boolean shapeless, boolean mirrored, String recipeId,
                       List<String> cells, String outputItem, int outputCount,
                       Operation operation, RemoveBy removeBy, String sourceRecipeId,
                       double xp, int cookingTime) {
        this(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, List.of(), "", CreateMachine.CRUSHING, "", false, "");
    }

    /** 切菜板的额外产物（每个可带概率） */
    public RecipeDraft withExtraOutputs(List<ExtraOutput> value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, value, sound,
                machine, heat, keepHeldItem, note);
    }

    /** 切菜板的音效（留空则用农夫乐事的默认值） */
    public RecipeDraft withSound(String value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, value,
                machine, heat, keepHeldItem, note);
    }

    /** 机械动力的具体机器 */
    public RecipeDraft withMachine(CreateMachine value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound,
                value, heat, keepHeldItem, note);
    }

    /** 机械动力的热度要求：空 / heated / superheated */
    public RecipeDraft withHeat(String value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound,
                machine, value, keepHeldItem, note);
    }

    /** 物品应用配方专用：部署器手里那件物品是否保留 */
    public RecipeDraft withKeepHeldItem(boolean value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound,
                machine, heat, value, note);
    }

    /** 备注——保存时写成脚本里的行注释（{@code //}） */
    public RecipeDraft withNote(String value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound,
                machine, heat, keepHeldItem, value);
    }

    public static RecipeDraft empty(RecipeType type) {
        return new RecipeDraft(type, false, true, "", blankCells(), null, 0);
    }

    /** 生成 3×3 容器的空格子数组 */
    public static List<String> blankCells() {
        List<String> cells = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            cells.add(null);
        }
        return cells;
    }

    private RecipeDraft copy(RecipeType type, boolean shapeless, boolean mirrored, String recipeId,
                             List<String> cells, String outputItem, int outputCount,
                             Operation operation, RemoveBy removeBy, String sourceRecipeId,
                             double xp, int cookingTime,
                             List<ExtraOutput> extraOutputs, String sound,
                             CreateMachine machine, String heat, boolean keepHeldItem,
                             String note) {
        return new RecipeDraft(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withType(RecipeType newType) {
        return copy(newType, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withShapeless(boolean value) {
        return copy(type, value, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withMirrored(boolean value) {
        return copy(type, shapeless, value, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withRecipeId(String value) {
        return copy(type, shapeless, mirrored, value, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withOperation(Operation value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                value, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withRemoveBy(RemoveBy value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, value, sourceRecipeId, xp, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withSourceRecipeId(String value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, value, xp, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withCells(List<String> newCells) {
        return copy(type, shapeless, mirrored, recipeId, newCells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withOutput(String item, int count) {
        return copy(type, shapeless, mirrored, recipeId, cells, item, count,
                operation, removeBy, sourceRecipeId, xp, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withXp(double value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, value, cookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    public RecipeDraft withCookingTime(int value) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, xp, value, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    /** 「我的配方」载入时整体套用 */
    public RecipeDraft withCookingSettings(double newXp, int newCookingTime) {
        return copy(type, shapeless, mirrored, recipeId, cells, outputItem, outputCount,
                operation, removeBy, sourceRecipeId, newXp, newCookingTime, extraOutputs, sound, machine, heat, keepHeldItem, note);
    }

    /** 只保留本品类网格范围内的格子（例如 2×2 模式下忽略第 3 行/列） */
    public RecipeDraft cropToGrid() {
        if (!type.isCrafting()) {
            return this;
        }
        List<String> cropped = new ArrayList<>(type.inputSlotCount());
        for (int y = 0; y < type.gridHeight(); y++) {
            for (int x = 0; x < type.gridWidth(); x++) {
                int src = y * 3 + x; // 合成格容器始终是 3×3
                cropped.add(src < cells.size() ? cells.get(src) : null);
            }
        }
        return withCells(cropped);
    }

    /** 当前品类实际用到的输入槽内容 */
    public List<String> inputCells() {
        int n = Math.min(type.inputSlotCount(), cells.size());
        return cells.subList(0, n);
    }

    public String cell(int x, int y) {
        int i = y * type.gridWidth() + x;
        return (i >= 0 && i < cells.size()) ? cells.get(i) : null;
    }

    /** 按顺序列出所有非空材料（无序配方与烧炼/锻造都按这个顺序） */
    public List<String> ingredients() {
        List<String> out = new ArrayList<>();
        for (String c : inputCells()) {
            if (c != null && !c.isBlank()) {
                out.add(c);
            }
        }
        return out;
    }

    /** 非空材料的去重列表，保持首次出现顺序 */
    public List<String> distinctIngredients() {
        List<String> out = new ArrayList<>();
        for (String c : ingredients()) {
            if (!out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * 裁掉四周的空行空列，返回紧凑的图案行（用 'X' 占位）。仅合成类有意义。
     *
     * <p>语义安全：原版 shaped 配方本来就允许图案在合成格里任意平移。
     */
    public List<String> trimmedPattern() {
        int[] box = contentBox();
        List<String> rows = new ArrayList<>();
        if (box == null) {
            return rows;
        }
        for (int y = box[1]; y <= box[3]; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = box[0]; x <= box[2]; x++) {
                String c = cell(x, y);
                sb.append(c == null || c.isBlank() ? ' ' : 'X');
            }
            rows.add(sb.toString());
        }
        return rows;
    }

    /** 非空区域的包围盒 {minX, minY, maxX, maxY}，全空时返回 null */
    public int[] contentBox() {
        int w = type.gridWidth();
        int h = type.gridHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                String c = cell(x, y);
                if (c != null && !c.isBlank()) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        return maxX < 0 ? null : new int[]{minX, minY, maxX, maxY};
    }
}
