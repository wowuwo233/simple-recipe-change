package com.wowuwo233.simple_recipe_change.kubejs;

/**
 * 配方类型。
 *
 * <p>分三大类，界面布局与生成方式都按类走：
 * <ul>
 *   <li>{@link Category#CRAFTING} 合成：工作台 3×3 / 物品栏 2×2，用 shaped 或 shapeless</li>
 *   <li>{@link Category#COOKING} 烧炼：熔炉 / 高炉 / 烟熏炉，单个原料 + 经验 + 烧制时间</li>
 *   <li>{@link Category#SMITHING} 锻造：模板 + 基础物品 + 升级物品 → 产物，三者必填</li>
 * </ul>
 *
 * <p>关于「物品栏合成」和「工作台合成」：原版里这两者<b>不是两种配方类型</b>，
 * 都用 {@code minecraft:crafting_shaped}，区别只在合成格尺寸。详见 README。
 *
 * <p>刻意不依赖 Minecraft，方便单独测试生成结果。
 */
public enum RecipeType {

    CRAFTING_TABLE("crafting_table", "工作台合成（3×3）", "工作台", Category.CRAFTING, 3, 3),
    INVENTORY("inventory", "物品栏合成（2×2）", "物品栏", Category.CRAFTING, 2, 2),
    FURNACE("furnace", "熔炉烧炼", "熔炉", Category.COOKING, 1, 1),
    BLAST_FURNACE("blast_furnace", "高炉烧炼", "高炉", Category.COOKING, 1, 1),
    SMOKER("smoker", "烟熏炉", "烟熏炉", Category.COOKING, 1, 1),
    SMITHING("smithing", "锻造台", "锻造台", Category.SMITHING, 3, 1),
    /** 农夫乐事 · 烹饪锅：6 个材料槽 + 1 个容器槽 */
    FARMERS_COOKING("farmersdelight_cooking", "农夫乐事 · 烹饪锅", "烹饪锅", Category.FARMERS, 7, 1),
    /** 农夫乐事 · 切菜板：1 个材料槽 + 1 个工具槽，产物可以多个且带概率 */
    FARMERS_CUTTING("farmersdelight_cutting", "农夫乐事 · 切菜板", "切菜板", Category.FARMERS, 2, 1);

    /** 配方大类，决定界面布局与生成方式 */
    public enum Category {
        CRAFTING, COOKING, SMITHING, FARMERS
    }

    /**
     * 类型来源分组。
     *
     * <p>界面里「类型」按钮只在同一分组内循环，原版和农夫乐事分开选，
     * 免得切一次类型要按七八下。
     */
    public enum Group {
        VANILLA("原版"),
        FARMERS_DELIGHT("农夫乐事");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public Group next() {
            Group[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private final String id;
    private final String label;
    private final String shortLabel;
    private final Category category;
    private final int gridWidth;
    private final int gridHeight;

    RecipeType(String id, String label, String shortLabel, Category category, int gridWidth, int gridHeight) {
        this.id = id;
        this.label = label;
        this.shortLabel = shortLabel;
        this.category = category;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
    }

    public String id() {
        return id;
    }

    /** 中文显示名 */
    public String label() {
        return label;
    }

    /** 按钮上用的短名 */
    public String shortLabel() {
        return shortLabel;
    }

    public Category category() {
        return category;
    }

    public int gridWidth() {
        return gridWidth;
    }

    public int gridHeight() {
        return gridHeight;
    }

    public boolean isCrafting() {
        return category == Category.CRAFTING;
    }

    /** 界面里要显示的输入槽数量 */
    public int inputSlotCount() {
        return switch (category) {
            case CRAFTING -> gridWidth * gridHeight;
            case COOKING -> 1;
            case SMITHING -> 3;
            case FARMERS -> gridWidth;
        };
    }

    /** 输入槽的中文名，下标与 {@code cells} 的前几项一一对应 */
    public String[] inputLabels() {
        return switch (this) {
            case CRAFTING_TABLE -> new String[]{"合成格（3×3）"};
            case INVENTORY -> new String[]{"合成格（2×2）"};
            case FURNACE, BLAST_FURNACE, SMOKER -> new String[]{"原料"};
            case SMITHING -> new String[]{"模板", "基础物品", "升级物品"};
            case FARMERS_COOKING -> new String[]{"材料", "材料", "材料", "材料", "材料", "材料", "容器"};
            case FARMERS_CUTTING -> new String[]{"材料", "工具"};
        };
    }

    public String outputLabel() {
        return "产物";
    }

    /** 是否需要经验与烧制时间两个输入框（原版烧炼 + 农夫乐事烹饪锅） */
    public boolean hasCookingSettings() {
        return category == Category.COOKING || this == FARMERS_COOKING;
    }

    /** 是否是农夫乐事类型（由 KubeJSDelight 提供 schema） */
    public boolean isFarmers() {
        return category == Category.FARMERS;
    }

    /** 切菜板专用：产物可以是多个，且每个可以带概率 */
    public boolean hasMultipleOutputs() {
        return this == FARMERS_CUTTING;
    }

    /** 切菜板专用：工具槽是否必填 */
    public boolean hasToolSlot() {
        return this == FARMERS_CUTTING;
    }

    /** 烹饪锅专用：容器槽是否必填（可选，留空则不写这一项） */
    public boolean hasOptionalContainer() {
        return this == FARMERS_COOKING;
    }

    /**
     * KubeJS 的方法名（不含 {@code event.}）。
     *
     * <p>合成类由「有序/无序」开关决定，这里返回 shaped 作为默认。
     */
    public String kubeJsMethod() {
        return switch (this) {
            case CRAFTING_TABLE, INVENTORY -> "shaped";
            case FURNACE -> "smelting";
            case BLAST_FURNACE -> "blasting";
            case SMOKER -> "smoking";
            case SMITHING -> "smithing";
            case FARMERS_COOKING -> "cooking";
            case FARMERS_CUTTING -> "cutting";
        };
    }

    /**
     * 生成脚本里用的完整事件路径（含 {@code event.}）。
     *
     * <p>原版九种类型在 KubeJS 里是写死在 {@code RecipesEventJS} 上的字段，
     * 可以直接 {@code event.smelting(...)}；但 mod 提供的类型挂在命名空间对象下，
     * 必须写成 {@code event.recipes.farmersdelight.cooking(...)}——写成
     * {@code event.cooking(...)} 在游戏里会报 undefined。
     */
    public String eventPath() {
        return isFarmers()
                ? "event.recipes.farmersdelight." + kubeJsMethod()
                : "event." + kubeJsMethod();
    }

    /** 下一个类型，只在同一分组内循环 */
    public RecipeType next() {
        RecipeType[] all = values();
        for (int i = 1; i <= all.length; i++) {
            RecipeType t = all[(ordinal() + i) % all.length];
            if (t.group() == this.group()) {
                return t;
            }
        }
        return this;
    }

    /** 这个类型属于哪个来源分组 */
    public RecipeType.Group group() {
        return isFarmers() ? Group.FARMERS_DELIGHT : Group.VANILLA;
    }

    /** 某个分组里的第一个类型 */
    public static RecipeType firstOf(Group group) {
        for (RecipeType t : values()) {
            if (t.group() == group) {
                return t;
            }
        }
        return CRAFTING_TABLE;
    }

    /** 最前面的合成类型，切换类型时用来回退 */
    public static RecipeType byId(String id) {
        for (RecipeType t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return CRAFTING_TABLE;
    }

    public static RecipeType byOrdinal(int ordinal) {
        RecipeType[] all = values();
        return (ordinal >= 0 && ordinal < all.length) ? all[ordinal] : CRAFTING_TABLE;
    }
}
