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
    SMITHING("smithing", "锻造台", "锻造台", Category.SMITHING, 3, 1);

    /** 配方大类，决定界面布局与生成方式 */
    public enum Category {
        CRAFTING, COOKING, SMITHING
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
        };
    }

    /** 输入槽的中文名，下标与 {@code cells} 的前几项一一对应 */
    public String[] inputLabels() {
        return switch (this) {
            case CRAFTING_TABLE -> new String[]{"合成格（3×3）"};
            case INVENTORY -> new String[]{"合成格（2×2）"};
            case FURNACE, BLAST_FURNACE, SMOKER -> new String[]{"原料"};
            case SMITHING -> new String[]{"模板", "基础物品", "升级物品"};
        };
    }

    public String outputLabel() {
        return "产物";
    }

    /** 是否是烧炼类，需要经验与烧制时间 */
    public boolean hasCookingSettings() {
        return category == Category.COOKING;
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
        };
    }

    /** 下一个类型，用于按钮循环切换 */
    public RecipeType next() {
        RecipeType[] all = values();
        return all[(ordinal() + 1) % all.length];
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
