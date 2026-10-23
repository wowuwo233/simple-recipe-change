package com.wowuwo233.simple_recipe_change.kubejs;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每种配方类型的「元数据表」。
 *
 * <p>以前这些信息散在 {@link RecipeType} 的 6 个 switch 里（槽位标签、KubeJS 方法名、
 * 事件路径、默认时间、要不要经验框、要不要时间框、时间框标签）。加一个类型要改 4~5 处，
 * 很容易漏。
 *
 * <p>现在收敛成「分类默认值 + 个别类型覆盖」：加类型通常只需要在 {@link RecipeType}
 * 加一行枚举，默认值自动生效；形状特殊的再在 {@link #override} 里补一条。
 *
 * <p>注意：这里只放**元数据**，不放生成逻辑。KubeJS 文本仍然由 {@link KubeJsWriter}
 * 生成，产物依然是能独立运行的 .js 脚本（卸载本模组也生效）。
 */
public record RecipeTypeSpec(
        String[] inputLabels,
        String kubeJsMethod,
        String eventPath,
        int defaultTime,
        boolean cookingSettings,
        boolean timeField,
        String timeLabel
) {

    private static final Map<RecipeType, RecipeTypeSpec> CACHE = new ConcurrentHashMap<>();

    public static RecipeTypeSpec of(RecipeType type) {
        return CACHE.computeIfAbsent(type, RecipeTypeSpec::build);
    }

    private static RecipeTypeSpec build(RecipeType type) {
        RecipeTypeSpec spec = defaults(type);
        return override(type, spec);
    }

    /** 分类默认值：绝大多数类型直接命中，不用单独写 */
    private static RecipeTypeSpec defaults(RecipeType type) {
        return switch (type.category()) {
            case CRAFTING -> new RecipeTypeSpec(
                    type == RecipeType.INVENTORY
                            ? new String[]{"合成格（2×2）"} : new String[]{"合成格（3×3）"},
                    "shaped", "event.shaped", 200, false, false, "");
            case COOKING -> new RecipeTypeSpec(
                    new String[]{"原料"},
                    cookingMethod(type), "event." + cookingMethod(type),
                    type == RecipeType.CAMPFIRE ? 600 : 200,
                    true, true, "");
            case SMITHING -> new RecipeTypeSpec(
                    new String[]{"模板", "基础物品", "升级物品"},
                    "smithing", "event.smithing", 200, false, false, "");
            case FARMERS -> new RecipeTypeSpec(
                    type == RecipeType.FARMERS_COOKING
                            ? new String[]{"材料", "材料", "材料", "材料", "材料", "材料", "容器"}
                            : new String[]{"材料", "工具"},
                    type == RecipeType.FARMERS_COOKING ? "cooking" : "cutting",
                    "event.recipes.farmersdelight."
                            + (type == RecipeType.FARMERS_COOKING ? "cooking" : "cutting"),
                    200, type == RecipeType.FARMERS_COOKING, true, "");
            case CREATE -> new RecipeTypeSpec(
                    type == RecipeType.CREATE_SEQUENCED
                            ? new String[]{"输入物品", "过渡物品", "步骤 1", "步骤 2",
                                    "步骤 3", "步骤 4", "步骤 5", "步骤 6"}
                            : new String[]{"材料"},
                    type == RecipeType.CREATE_SEQUENCED ? "sequenced_assembly" : "processing",
                    "event.recipes.create."
                            + (type == RecipeType.CREATE_SEQUENCED ? "sequenced_assembly" : "processing"),
                    type == RecipeType.CREATE_SEQUENCED ? 4 : 100,
                    false, true, "");
            case THERMAL -> new RecipeTypeSpec(
                    new String[]{"材料"},
                    thermalMethod(type), "event.recipes.thermal." + thermalMethod(type),
                    0, true, true, "");
        };
    }

    /** 个别类型的覆盖：形状或文案跟同分类不一样的，只在这里补一条 */
    private static RecipeTypeSpec override(RecipeType type, RecipeTypeSpec base) {
        // 时间框标签：随类型变，玩家要能看懂这个数字是什么意思
        String timeLabel = switch (type) {
            case CREATE_SEQUENCED -> "循环次数";
            case CREATE_PROCESSING -> "处理时间（tick，默认 100）";
            default -> {
                if (type.isThermal()) {
                    yield type.thermalParamCount() > 0
                            ? "参数（逗号分隔，顺序见提示）" : "能耗（RF，默认 0）";
                }
                yield "烧制时间（tick，默认 " + base.defaultTime() + "）";
            }
        };
        return new RecipeTypeSpec(base.inputLabels(), base.kubeJsMethod(), base.eventPath(),
                base.defaultTime(), base.cookingSettings(), base.timeField(), timeLabel);
    }

    /** 原版四种炉子的 KubeJS 方法名 */
    private static String cookingMethod(RecipeType type) {
        return switch (type) {
            case BLAST_FURNACE -> "blasting";
            case SMOKER -> "smoking";
            case CAMPFIRE -> "campfireCooking";
            default -> "smelting";
        };
    }

    /** 热力膨胀：方法名就是配方类型路径（thermal_pulverizer -> pulverizer） */
    private static String thermalMethod(RecipeType type) {
        String id = type.id();
        return id.startsWith("thermal_") ? id.substring("thermal_".length()) : id;
    }
}
