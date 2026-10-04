package com.wowuwo233.simple_recipe_change.kubejs;

/**
 * 机械动力（Create）的处理类机器。
 *
 * <p>这些类型不是逐个手写的——Create 的 KubeJS 适配（kubejs-create）在
 * {@code KubeJSCreatePlugin.registerRecipeSchemas} 里遍历 {@code AllRecipeTypes}，
 * 凡是 serializer 是 {@code ProcessingRecipeSerializer} 的类型全自动注册，
 * 然后按类型挑下面这几种 schema 之一。所以它们<b>共用同一套参数结构</b>，
 * 界面上没必要一个类型配一套界面。
 *
 * <p>三种 schema 的差别（来自 kubejs-create 的反编译结果）：
 * <ul>
 *   <li>{@link Kind#DEFAULT} —— {@code processingTime} 可省略，默认 100 tick</li>
 *   <li>{@link Kind#WITH_TIME} —— {@code processingTime} 必须写出来（原版这几个都要求）</li>
 *   <li>{@link Kind#UNWRAPPED} —— {@code ingredients} 用「不包裹」的写法</li>
 *   <li>{@link Kind#ITEM_APPLICATION} —— 多一个 {@code keepHeldItem}</li>
 * </ul>
 *
 * <p>调用路径和农夫乐事同构，是命名空间对象下的函数：
 * {@code event.recipes.create.crushing(...)}，不是 {@code event.crushing(...)}。
 *
 * <p>这个类刻意不依赖 Minecraft，方便脱机测试生成结果。
 */
public enum CreateMachine {

    CRUSHING("crushing", "粉碎轮", "粉碎", Kind.WITH_TIME, true),
    MILLING("milling", "石磨", "研磨", Kind.WITH_TIME, true),
    CUTTING("cutting", "动力锯", "切割", Kind.WITH_TIME, false),
    BASIN("basin", "工作盆", "处理", Kind.DEFAULT, false),
    PRESSING("pressing", "动力冲压机", "冲压", Kind.DEFAULT, false),
    MIXING("mixing", "动力搅拌器", "混合", Kind.UNWRAPPED, false),
    COMPACTING("compacting", "动力搅拌器", "压缩", Kind.UNWRAPPED, false),
    SPLASHING("splashing", "鼓风机", "洗涤", Kind.DEFAULT, true),
    HAUNTING("haunting", "鼓风机", "缠魂", Kind.DEFAULT, true),
    SANDPAPER_POLISHING("sandpaper_polishing", "砂纸", "打磨", Kind.DEFAULT, false),
    FILLING("filling", "注液器", "注液", Kind.DEFAULT, false),
    EMPTYING("emptying", "工作盆", "抽液", Kind.DEFAULT, false),
    DEPLOYING("deploying", "机械手", "部署", Kind.ITEM_APPLICATION, false),
    ITEM_APPLICATION("item_application", "机械手", "物品应用", Kind.ITEM_APPLICATION, false);

    /** schema 变体，决定参数怎么写 */
    public enum Kind {
        /** processingTime 可省略 */
        DEFAULT,
        /** processingTime 必须写 */
        WITH_TIME,
        /** ingredients 用不包裹的写法 */
        UNWRAPPED,
        /** 多一个 keepHeldItem */
        ITEM_APPLICATION
    }

    private final String id;
    /** 机器方块的中文名，取自 Create 自己的 zh_cn.json，不是自己翻的 */
    private final String machineName;
    /** 这个机器对材料做的动作 */
    private final String action;
    private final Kind kind;
    private final boolean byproducts;

    CreateMachine(String id, String machineName, String action, Kind kind, boolean byproducts) {
        this.id = id;
        this.machineName = machineName;
        this.action = action;
        this.kind = kind;
        this.byproducts = byproducts;
    }

    /**
     * 这台机器会不会产出<b>副产物</b>（带概率的额外产物）。
     *
     * <p>不是猜的——把 Create 6.0.8 自带的 1766 个官方配方全解析统计了一遍：
     * <pre>
     *   粉碎 crushing   192 个配方中 179 个多产物、186 个带概率（最多 5 个产物）
     *   研磨 milling    222 个中 159 / 163（最多 3 个）
     *   洗涤 splashing   38 个中   9 /  10（最多 2 个）
     *   缠魂 haunting    21 个中   1 /   1（最多 2 个）
     *   其余机器（挤压/切割/注液/抽液/混合/压缩/部署/物品应用/砂纸打磨）
     *                   全部只有单个产物
     * </pre>
     * 所以副产物栏位只对这四台机器显示，别的机器显示出来纯属误导。
     */
    public boolean hasByproducts() {
        return byproducts;
    }

    /** Create 的配方类型路径，比如 {@code crushing} */
    public String id() {
        return id;
    }

    /**
     * 界面上显示的名字，格式是「机器名：动作」——
     * 比如 {@code 粉碎轮：粉碎}、{@code 动力冲压机：冲压}。
     *
     * <p>机器名用的是 Create 官方中文译名（从它的 {@code zh_cn.json} 里取的），
     * 动作则是这台机器在做什么，两者都要，光看"粉碎"不知道是哪个方块在干活。
     */
    public String label() {
        return machineName + "：" + action;
    }

    /** 机器方块的中文名，比如「粉碎轮」 */
    public String machineName() {
        return machineName;
    }

    /** 动作名，比如「粉碎」 */
    public String action() {
        return action;
    }

    public Kind kind() {
        return kind;
    }

    /** 生成的脚本里的事件路径 */
    public String eventPath() {
        return "event.recipes.create." + id;
    }

    /** processingTime 是不是必须写出来 */
    public boolean requiresTime() {
        return kind == Kind.WITH_TIME;
    }

    /** 要不要写 keepHeldItem 这个额外参数 */
    public boolean hasKeepHeldItem() {
        return kind == Kind.ITEM_APPLICATION;
    }

    /** 下一个机器，用于按钮循环切换 */
    public CreateMachine next() {
        CreateMachine[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static CreateMachine byId(String id) {
        for (CreateMachine m : values()) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return CRUSHING;
    }

    public static CreateMachine byOrdinal(int ordinal) {
        CreateMachine[] all = values();
        return (ordinal >= 0 && ordinal < all.length) ? all[ordinal] : CRUSHING;
    }
}
