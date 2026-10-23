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
    /**
     * 营火烹饪：和熔炉一样是「单原料 → 产物」，但原版默认要 <b>600 tick</b>（熔炉是 200），
     * 所以时间框的默认值随类型走。
     */
    CAMPFIRE("campfire", "营火烹饪", "营火", Category.COOKING, 1, 1),
    SMITHING("smithing", "锻造台", "锻造台", Category.SMITHING, 3, 1),
    /** 农夫乐事 · 烹饪锅：6 个材料槽 + 1 个容器槽 */
    FARMERS_COOKING("farmersdelight_cooking", "农夫乐事 · 烹饪锅", "烹饪锅", Category.FARMERS, 7, 1),
    /** 农夫乐事 · 切菜板：1 个材料槽 + 1 个工具槽，产物可以多个且带概率 */
    FARMERS_CUTTING("farmersdelight_cutting", "农夫乐事 · 切菜板", "切菜板", Category.FARMERS, 2, 1),
    /**
     * 机械动力 · 处理配方。
     *
     * <p>Create 有十几个处理机器，但它们<b>共用同一套参数结构</b>（产物数组 + 材料数组 +
     * 处理时间 + 热度），所以界面上只做一个类型，具体机器由 {@link CreateMachine} 切换，
     * 新增机器不用改界面代码。
     */
    CREATE_PROCESSING("create_processing", "机械动力 · 处理配方", "处理", Category.CREATE, 9, 1),
    /**
     * 机械动力 · 序列组装。
     *
     * <p>和其它类型不同，它是一串<b>子配方</b>：输入物品先变成「过渡物品」，再按顺序经过
     * 部署 / 压印 / 切割若干步，循环若干次后产出结果。所以 {@code cells} 的含义是：
     * <ul>
     *   <li>0 = 输入物品，1 = 过渡物品</li>
     *   <li>2..5 = 四步各自要额外消耗的物品（只有部署步需要；压印/切割只用过渡物品本身）</li>
     * </ul>
     *
     * <p>步骤类型不另开字段，复用 {@code heat} 这个字符串（本类型不用热度），
     * 按逗号分隔存 {@code deploying,pressing,...}；循环次数复用 {@code cookingTime}。
     * 这样索引、网络包都不用改结构，代价是字段语义要在这里写清楚。
     */
    CREATE_SEQUENCED("create_sequenced", "机械动力 · 序列组装", "序列组装", Category.CREATE, 8, 1),

    // ---- 热力膨胀（kubejs-thermal）：基础处理机器，材料/产物都允许流体 ----
    THERMAL_FURNACE("thermal_furnace", "红石炉：熔炼", "红石炉", Category.THERMAL, 9, 1),
    THERMAL_SAWMILL("thermal_sawmill", "锯木机：锯木", "锯木", Category.THERMAL, 9, 1),
    THERMAL_PULVERIZER("thermal_pulverizer", "粉碎机：粉碎", "粉碎", Category.THERMAL, 9, 1),
    THERMAL_SMELTER("thermal_smelter", "感应炉：熔炼", "熔炼", Category.THERMAL, 9, 1),
    THERMAL_CENTRIFUGE("thermal_centrifuge", "离心机：离心", "离心", Category.THERMAL, 9, 1),
    THERMAL_PRESS("thermal_press", "多驱冲压机：冲压", "冲压", Category.THERMAL, 9, 1),
    THERMAL_CRUCIBLE("thermal_crucible", "熔岩炉：熔化", "熔化", Category.THERMAL, 9, 1),
    THERMAL_CHILLER("thermal_chiller", "低温机：低温", "低温", Category.THERMAL, 9, 1),
    THERMAL_REFINERY("thermal_refinery", "精炼机：精炼", "精炼", Category.THERMAL, 9, 1),
    THERMAL_PYROLYZER("thermal_pyrolyzer", "热解炉：热解", "热解", Category.THERMAL, 9, 1),
    THERMAL_BOTTLER("thermal_bottler", "灌装机：灌装", "灌装", Category.THERMAL, 9, 1),
    THERMAL_BREWER("thermal_brewer", "酿造机：酿造", "酿造", Category.THERMAL, 9, 1),
    THERMAL_CRYSTALLIZER("thermal_crystallizer", "结晶机：结晶", "结晶", Category.THERMAL, 9, 1),
    THERMAL_INSOLATOR("thermal_insolator", "光照栽培机：栽培", "栽培", Category.THERMAL, 9, 1),
    THERMAL_PULVERIZER_RECYCLE("thermal_pulverizer_recycle", "粉碎机：回收", "粉碎回收", Category.THERMAL, 9, 1),
    THERMAL_SMELTER_RECYCLE("thermal_smelter_recycle", "感应炉：回收", "熔炼回收", Category.THERMAL, 9, 1),

    // 燃料：只吃材料 + 能量，没有产物
    THERMAL_STIRLING_FUEL("thermal_stirling_fuel", "斯特林发电机：燃烧", "斯特林", Category.THERMAL, 9, 1),
    THERMAL_NUMISMATIC_FUEL("thermal_numismatic_fuel", "数币发电机：燃烧", "数币", Category.THERMAL, 9, 1),
    THERMAL_LAPIDARY_FUEL("thermal_lapidary_fuel", "宝石发电机：燃烧", "宝石", Category.THERMAL, 9, 1),
    THERMAL_DISENCHANTMENT_FUEL("thermal_disenchantment_fuel", "祛魔发电机：燃烧", "祛魔", Category.THERMAL, 9, 1),
    THERMAL_GOURMAND_FUEL("thermal_gourmand_fuel", "美食发电机：燃烧", "美食", Category.THERMAL, 9, 1),
    THERMAL_COMPRESSION_FUEL("thermal_compression_fuel", "压缩发电机：燃烧", "压缩", Category.THERMAL, 9, 1),
    THERMAL_MAGMATIC_FUEL("thermal_magmatic_fuel", "岩浆发电机：燃烧", "岩浆", Category.THERMAL, 9, 1),

    // 映射 / 增益：槽位形状各不相同，生成时按类型单独拼参数
    THERMAL_HIVE_EXTRACTOR("thermal_hive_extractor", "蜂箱提取器：提取", "蜂箱", Category.THERMAL, 9, 1),
    THERMAL_TREE_EXTRACTOR("thermal_tree_extractor", "树木提取器：提取", "树木", Category.THERMAL, 9, 1),
    THERMAL_ROCK_GEN("thermal_rock_gen", "造石机：造石", "造石", Category.THERMAL, 9, 1),
    THERMAL_TREE_BOOST("thermal_tree_extractor_boost", "树木提取器：增益", "树木增益", Category.THERMAL, 9, 1),
    THERMAL_FISHER_BOOST("thermal_fisher_boost", "自动钓鱼器：增益", "钓鱼增益", Category.THERMAL, 9, 1),
    THERMAL_PULVERIZER_CATALYST("thermal_pulverizer_catalyst", "粉碎机：催化剂", "粉碎催化", Category.THERMAL, 9, 1),
    THERMAL_SMELTER_CATALYST("thermal_smelter_catalyst", "感应炉：催化剂", "熔炼催化", Category.THERMAL, 9, 1),
    THERMAL_INSOLATOR_CATALYST("thermal_insolator_catalyst", "光照栽培机：催化剂", "栽培催化", Category.THERMAL, 9, 1),
    THERMAL_POTION_BOOST("thermal_potion_diffuser_boost", "药水扩散器：增益", "药水增益", Category.THERMAL, 9, 1);

    /** 配方大类，决定界面布局与生成方式 */
    public enum Category {
        CRAFTING, COOKING, SMITHING, FARMERS, CREATE, THERMAL
    }

    /**
     * 类型来源分组。
     *
     * <p>界面里「类型」按钮只在同一分组内循环，原版和农夫乐事分开选，
     * 免得切一次类型要按七八下。
     */
    public enum Group {
        VANILLA("原版"),
        FARMERS_DELIGHT("农夫乐事"),
        CREATE("机械动力"),
        THERMAL("热力膨胀");

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

    private RecipeTypeSpec specCache;

    /** 元数据表（槽位标签 / 方法名 / 路径 / 默认值），见 {@link RecipeTypeSpec} */
    public RecipeTypeSpec spec() {
        return RecipeTypeSpec.of(this);
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
            case FARMERS, CREATE, THERMAL -> gridWidth;
        };
    }

    /** 输入槽的中文名，下标与 {@code cells} 的前几项一一对应 */
    public String[] inputLabels() {
        return spec().inputLabels();
    }

    public String outputLabel() {
        return "产物";
    }

    /**
     * 需不需要「经验」这一个输入框。
     *
     * <p>只有原版烧炼和农夫乐事烹饪锅有；机械动力的处理配方没有经验。
     */
    public boolean hasCookingSettings() {
        return spec().cookingSettings();
    }

    /**
     * 需不需要那个数字输入框（烧制时间 / 处理时间 / 循环次数）。
     *
     * <p>机械动力也要，但含义不同——所以字段名必须随类型变，见 {@link #timeFieldLabel()}。
     */
    public boolean hasTimeField() {
        return spec().timeField();
    }

    /** 时间输入框上方那行说明 */
    public String timeFieldLabel() {
        return spec().timeLabel();
    }

    /** 产物是否带概率（只有切菜板有） */
    public boolean hasChanceResults() {
        return this == FARMERS_CUTTING;
    }

    /** 是否是农夫乐事类型（由 KubeJSDelight 提供 schema） */
    public boolean isFarmers() {
        return category == Category.FARMERS;
    }

    /** 是否是机械动力类型（由 kubejs-create 提供 schema） */
    public boolean isCreate() {
        return category == Category.CREATE;
    }

    /**
     * 热力膨胀里要「一串数值」的类型：催化剂 4 个、药水扩散 3 个。
     *
     * <p>数值借 {@code heat} 字段存（逗号分隔），界面上就是那个数字框，
     * 标签改成「参数」并给出顺序说明。
     */
    public int thermalParamCount() {
        return switch (this) {
            case THERMAL_PULVERIZER_CATALYST, THERMAL_SMELTER_CATALYST,
                 THERMAL_INSOLATOR_CATALYST -> 4;
            case THERMAL_POTION_BOOST -> 3;
            default -> 0;
        };
    }

    /** 热力膨胀里形状特殊的几种：映射 / 增益 */
    public boolean isThermalSpecial() {
        return this == THERMAL_HIVE_EXTRACTOR || this == THERMAL_TREE_EXTRACTOR
                || this == THERMAL_ROCK_GEN || this == THERMAL_TREE_BOOST
                || this == THERMAL_FISHER_BOOST || thermalParamCount() > 0;
    }

    /** 热力膨胀的燃料类（只要材料 + 能量，没有产物） */
    public boolean isThermalFuel() {
        return isThermal() && id.endsWith("_fuel");
    }

    /** 是否是热力膨胀类型（由 kubejs-thermal 提供 schema） */
    public boolean isThermal() {
        return category == Category.THERMAL;
    }

    /** 是不是「多个产物」的类型（切菜板 + Create 处理配方）；序列组装只做单产物 */
    public boolean hasMultipleOutputs() {
        return this == FARMERS_CUTTING || (isCreate() && !isSequenced()) || isThermal();
    }

    /** 机械动力 · 序列组装（步骤是一串子配方，界面和生成方式都特殊） */
    public boolean isSequenced() {
        return this == CREATE_SEQUENCED;
    }

    /** 处理时间默认值：Create 100 tick、营火 600 tick、其余（熔炉等）200 tick；序列组装这里是循环次数默认 4 */
    public int defaultProcessingTime() {
        return spec().defaultTime();
    }

    /** 需不需要「热度」这个参数（只有 Create 的处理配方有；序列组装没有） */
    public boolean hasHeatRequirement() {
        return isCreate() && !isSequenced();
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
        return spec().kubeJsMethod();
    }

    /**
     * 生成脚本里用的完整事件路径（含 {@code event.}）。
     *
     * <p>原版九种类型在 KubeJS 里是写死在 {@code RecipesEventJS} 上的字段，
     * 可以直接 {@code event.smelting(...)}；但 mod 提供的类型挂在命名空间对象下，
     * 必须写成 {@code event.recipes.farmersdelight.cooking(...)}——写成
     * {@code event.cooking(...)} 在游戏里会报 undefined。
     *
     * <p>Create 处理配方的机器名不固定，这个返回值对它是占位符，
     * 真正用的是 {@link CreateMachine#eventPath()}。
     */
    public String eventPath() {
        return spec().eventPath();
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
        if (isFarmers()) {
            return Group.FARMERS_DELIGHT;
        }
        if (isCreate()) {
            return Group.CREATE;
        }
        return isThermal() ? Group.THERMAL : Group.VANILLA;
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

    public static RecipeType byOrdinal(int ordinal) {
        RecipeType[] all = values();
        return (ordinal >= 0 && ordinal < all.length) ? all[ordinal] : CRAFTING_TABLE;
    }
}
