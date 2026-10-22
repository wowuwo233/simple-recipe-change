package com.wowuwo233.simple_recipe_change.kubejs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 {@link RecipeDraft} 渲染成符合 KubeJS 书写规格的脚本，并负责把它并入目标 .js 文件。
 *
 * <p>生成格式的依据是 KubeJS 1.20.1（分支 {@code 2001}）的真实源码：
 * <ul>
 *   <li>{@code ShapedRecipeSchema.ShapedRecipeJS} 提供 {@code noMirror()} 与 {@code noShrink()}，
 *       它们写入 {@code kubejs:mirror} / {@code kubejs:shrink}，默认值均为 {@code true}；
 *       KubeJS 只在存在这两个字段时才使用自家的 {@code kubejs:shaped} 类型，否则回退到原版类型。</li>
 *   <li>{@code RecipeJS.id(ResourceLocation)} 用于指定配方 ID。</li>
 * </ul>
 *
 * <p>本类不依赖 Minecraft，可以脱离游戏单独验证输出。
 */
public final class KubeJsWriter {

    /** 托管块的起止标记；同一配方 ID 的旧块会被整体替换 */
    public static final String MARK_BEGIN = "// ==== SIMPLE_RECIPE_CHANGE:BEGIN ";
    public static final String MARK_END = "// ==== SIMPLE_RECIPE_CHANGE:END ";

    /** 用户没填文件名时使用的默认文件名 */
    public static final String DEFAULT_FILE_NAME = "simple_recipe_change_recipes.js";

    private KubeJsWriter() {
    }

    // ---------------------------------------------------------------- 校验与规范化

    /** 把用户输入的配方名规范成 {@code namespace:path}；为空时按输出物品自动生成 */
    public static String normalizeRecipeId(String raw, String fallbackNamespace, String outputItem) {
        String ns = (fallbackNamespace == null || fallbackNamespace.isBlank())
                ? "simple_recipe_change" : sanitize(fallbackNamespace, "simple_recipe_change");

        String value = raw == null ? "" : raw.trim().toLowerCase();
        if (value.isEmpty()) {
            String path = "auto_" + sanitize(outputItem == null ? "recipe" : outputItem.replace(':', '_'), "recipe");
            return ns + ":" + path;
        }

        int colon = value.indexOf(':');
        if (colon < 0) {
            return ns + ":" + sanitize(value, "recipe");
        }
        return sanitize(value.substring(0, colon), ns) + ":" + sanitize(value.substring(colon + 1), "recipe");
    }

    /** 文件名规范化：去掉路径分隔符与非法字符，保证以 .js 结尾 */
    public static String normalizeFileName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return DEFAULT_FILE_NAME;
        }
        value = value.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        value = value.replaceAll("[^A-Za-z0-9_\\-.]", "_");
        // 折叠并剪掉首尾的下划线/连字符，避免中文输入产生一堆无意义的下划线
        value = value.replaceAll("_+", "_").replaceAll("^[-_]+", "").replaceAll("[-_]+$", "");
        if (value.isBlank() || value.equals(".js") || value.equals(".")) {
            return DEFAULT_FILE_NAME;
        }
        if (!value.toLowerCase().endsWith(".js")) {
            value = value + ".js";
        }
        return value;
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String cleaned = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9_\\-./]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[_.\\-/]+", "")
                .replaceAll("[_.\\-/]+$", "");
        return cleaned.isBlank() ? fallback : cleaned;
    }

    // ---------------------------------------------------------------- 渲染

    /**
     * 渲染单个配方，返回 {@code event.shaped(...)} / {@code event.shapeless(...)} 调用文本，
     * 已按需追加 {@code .id(...)} 与 {@code .noMirror()}。
     *
     * @throws IllegalArgumentException 草稿不完整时抛出，消息为中文，可直接展示给用户
     */
    public static String renderRecipeCall(RecipeDraft draft, String fallbackNamespace) {
        return renderRecipeCall(draft, fallbackNamespace, "");
    }

    /**
     * 渲染单个配方，{@code outputNbt} 是产物格的 SNBT（没有就传空串）。
     *
     * <p>产物 NBT 单独传参而不是塞进 {@link RecipeDraft}，是因为草稿只描述「配方结构」，
     * 而 NBT 来自产物格里的真实 ItemStack，只有服务端保存那一刻才拿得到。
     */
    public static String renderRecipeCall(RecipeDraft draft, String fallbackNamespace, String outputNbt) {
        // 删除操作走 event.remove，不需要输入槽
        if (draft.operation() == Operation.REMOVE) {
            return renderRemoveCall(draft);
        }

        RecipeDraft d = draft.cropToGrid();

        if (d.outputItem() == null || d.outputItem().isBlank()) {
            throw new IllegalArgumentException("请先放置产物（产物格不能为空）");
        }
        if (d.outputCount() < 1) {
            throw new IllegalArgumentException("产物数量至少为 1");
        }

        String recipeId = normalizeRecipeId(d.recipeId(), fallbackNamespace, d.outputItem());
        String output = quoteOutput(d.outputItem(), d.outputCount(), outputNbt);

        StringBuilder sb = new StringBuilder();

        // 备注写成 // 行注释放在配方上方。
        // 注意不能用 # —— 这是 JavaScript（KubeJS 用 Rhino 跑 .js），# 是语法错误，
        // 会让整个脚本文件加载失败，而不是被当成注释。
        if (d.note() != null && !d.note().isBlank()) {
            for (String line : d.note().split("\r?\n")) {
                sb.append("// ").append(line.strip()).append('\n');
            }
        }

        switch (d.type().category()) {
            case CRAFTING -> {
                List<String> ingredients = d.ingredients();
                if (ingredients.isEmpty()) {
                    throw new IllegalArgumentException("请至少放入一种材料");
                }
                if (d.shapeless()) {
                    appendShapeless(sb, output, ingredients);
                } else {
                    appendShaped(sb, output, d);
                }
            }
            case COOKING -> appendCooking(sb, d, output);
            case SMITHING -> appendSmithing(sb, d, output);
            case FARMERS -> {
                if (d.type() == RecipeType.FARMERS_COOKING) {
                    appendFarmersCooking(sb, d, output);
                } else {
                    appendFarmersCutting(sb, d, output);
                }
            }
            case CREATE -> {
                if (d.type().isSequenced()) {
                    appendSequenced(sb, d, output);
                } else {
                    appendCreate(sb, d, output);
                }
            }
        }

        sb.append(".id('").append(recipeId).append("')");

        // 只有有序合成才谈得上镜像。KubeJS 默认镜像开启，因此只需要在关闭时追加。
        if (d.type().isCrafting() && !d.shapeless() && !d.mirrored()) {
            sb.append(".noMirror()");
        }

        return sb.toString();
    }

    /**
     * 烧炼配方：{@code event.smelting(产物, 原料).xp(经验).cookingTime(tick)}
     *
     * <p>KubeJS 的默认值是经验 0、时间 200 tick；取默认值时不写出来，生成的脚本更干净。
     */
    private static void appendCooking(StringBuilder sb, RecipeDraft d, String output) {
        List<String> in = d.inputCells();
        String input = firstNonBlank(in, "请把要烧炼的原料放进原料格");

        sb.append("event.").append(d.type().kubeJsMethod()).append("(\n");
        sb.append("  ").append(output).append(",\n");
        sb.append("  '").append(input).append("'\n");
        sb.append(")");

        if (d.xp() > 0D) {
            sb.append(".xp(").append(formatNumber(d.xp())).append(")");
        }
        // 默认值随类型走：营火 600、熔炉 200、机械动力 100。等于默认值就不写出来
        if (d.cookingTime() != d.type().defaultProcessingTime()) {
            sb.append(".cookingTime(").append(d.cookingTime()).append(")");
        }
    }

    /**
     * 锻造配方：{@code event.smithing(产物, 模板, 基础物品, 升级物品)}——三者都必填
     */
    private static void appendSmithing(StringBuilder sb, RecipeDraft d, String output) {
        List<String> in = d.inputCells();
        String template = nthNonBlank(in, 0, "请把锻造模板放进模板格");
        String base = nthNonBlank(in, 1, "请把基础物品放进基础物品格");
        String addition = nthNonBlank(in, 2, "请把升级物品放进升级物品格");

        sb.append("event.smithing(\n");
        sb.append("  ").append(output).append(",\n");
        sb.append("  '").append(template).append("',\n");
        sb.append("  '").append(base).append("',\n");
        sb.append("  '").append(addition).append("'\n");
        sb.append(")");
    }

    /**
     * 农夫乐事 · 烹饪锅。
     *
     * <p>KubeJSDelight 注册的是 {@code farmersdelight:cooking}，它挂在命名空间对象下，
     * 所以调用必须写成 {@code event.recipes.farmersdelight.cooking(...)}——
     * 写成 {@code event.cooking(...)} 在游戏里是 undefined。
     *
     * <p>参数顺序（来自 KubeJSDelight 1.1.2 的 schema）：
     * 材料数组 → 产物 → 经验 → 烧制时间(tick) → 容器(可选)。
     * 材料格是前 6 个槽，第 7 个槽是容器；容器留空就整个不写。
     */
    private static void appendFarmersCooking(StringBuilder sb, RecipeDraft d, String output) {
        List<String> in = d.inputCells();
        List<String> ingredients = new ArrayList<>();
        String container = null;
        for (int i = 0; i < 7 && i < in.size(); i++) {
            String c = in.get(i);
            if (c == null || c.isBlank()) {
                continue;
            }
            if (i == 6) {
                container = c;
            } else {
                ingredients.add(c);
            }
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("请至少放入一种材料");
        }

        sb.append("event.recipes.farmersdelight.cooking(\n");
        sb.append("  [\n");
        for (String ing : ingredients) {
            sb.append("    '").append(ing).append("',\n");
        }
        sb.append("  ],\n");
        sb.append("  ").append(output).append(",\n");
        sb.append("  ").append(formatNumber(d.xp())).append(",\n");
        sb.append("  ").append(d.cookingTime());
        if (container != null) {
            sb.append(",\n  '").append(container).append("'\n");
        } else {
            sb.append("\n");
        }
        sb.append(")");
    }

    /**
     * 农夫乐事 · 切菜板。
     *
     * <p>参数顺序：材料 → 工具 → 产物数组 → 音效(可选)。
     * 产物数组里第一项是主产物（100%），其余是额外产物，
     * 概率小于 1 的写成 {@code ChanceResult.of('物品', 0.75)}。
     */
    private static void appendFarmersCutting(StringBuilder sb, RecipeDraft d, String output) {
        List<String> in = d.inputCells();
        String input = nthNonBlank(in, 0, "请把要切的材料放进材料格");
        String tool = in.size() > 1 ? in.get(1) : null;
        if (tool == null || tool.isBlank()) {
            // 农夫乐事所有刀都带这个标签，留空时的合理默认
            tool = "#forge:tools/knives";
        }

        sb.append("event.recipes.farmersdelight.cutting(\n");
        sb.append("  '").append(input).append("',\n");
        sb.append("  '").append(tool).append("',\n");
        sb.append("  [\n");
        sb.append("    ").append(output);
        for (RecipeDraft.ExtraOutput extra : d.extraOutputs()) {
            if (extra == null || extra.item() == null || extra.item().isBlank()) {
                continue;
            }
            sb.append(",\n    ").append(chanceResult(extra));
        }
        sb.append("\n  ]");
        if (d.sound() != null && !d.sound().isBlank()) {
            sb.append(",\n  '").append(d.sound()).append("'");
        }
        sb.append("\n)");
    }

    /** 额外产物：概率满 1 就写普通物品，否则用 ChanceResult 包一层 */
    private static String chanceResult(RecipeDraft.ExtraOutput extra) {
        String item = extra.count() > 1 ? extra.count() + "x " + extra.item() : extra.item();
        if (extra.chance() >= 1D) {
            return "'" + item + "'";
        }
        return "ChanceResult.of('" + item + "', " + formatNumber(extra.chance()) + ")";
    }

    /**
     * 机械动力 · 处理配方。
     *
     * <p>Create 的十几个处理机器共用同一套 schema（产物数组 → 材料数组 →
     * processingTime → heatRequirement），所以生成逻辑只有这一份，机器名来自
     * {@link RecipeDraft#machine()}。
     *
     * <p>调用路径同样是命名空间对象：{@code event.recipes.create.crushing(...)}。
     *
     * <p>可选的几个参数一律用<b>链式方法</b>写（{@code .processingTime(100)}），
     * 而不是靠位置占坑——否则「跳过 processingTime 直接写 heatRequirement」
     * 就得凭空补一个 null，既难看又容易错。
     *
     * <p>{@code processingTime} 的默认值是 100 tick（不是原版烧炼的 200）；
     * 但粉碎/研磨/切割这三个 schema 是 {@code PROCESSING_WITH_TIME}，必须写出来。
     */
    private static void appendCreate(StringBuilder sb, RecipeDraft d, String output) {
        CreateMachine machine = d.machine();

        List<String> ingredients = new ArrayList<>();
        for (String c : d.inputCells()) {
            if (c != null && !c.isBlank()) {
                ingredients.add(c);
            }
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("请至少放入一种材料");
        }

        List<String> results = new ArrayList<>();
        // 主产物来自 quoteOutput，已经带引号；额外产物要在这里补上引号
        results.add(output);
        for (RecipeDraft.ExtraOutput extra : d.extraOutputs()) {
            if (extra == null || extra.item() == null || extra.item().isBlank()) {
                continue;
            }
            String item = extra.count() > 1 ? extra.count() + "x " + extra.item() : extra.item();
            results.add("'" + item + "'");
        }

        sb.append(machine.eventPath()).append("(\n");
        sb.append("  [\n");
        for (String r : results) {
            sb.append("    ").append(r).append(",\n");
        }
        sb.append("  ],\n");
        sb.append("  [\n");
        for (String ing : ingredients) {
            sb.append("    '").append(ing).append("',\n");
        }
        sb.append("  ]\n");
        sb.append(")");

        int defaultTime = d.type().defaultProcessingTime();
        if (machine.requiresTime() || d.cookingTime() != defaultTime) {
            sb.append(".processingTime(").append(d.cookingTime()).append(")");
        }
        if (d.heat() != null && !d.heat().isBlank()) {
            sb.append(".heatRequirement('").append(d.heat()).append("')");
        }
        if (machine.hasKeepHeldItem() && d.keepHeldItem()) {
            sb.append(".keepHeldItem(true)");
        }
    }

    /**
     * 机械动力 · 序列组装。
     *
     * <p>生成形如：
     * <pre>
     * event.recipes.create.sequenced_assembly(
     *   ['1x create:precision_mechanism'],
     *   'create:golden_sheet',
     *   [
     *     event.recipes.create.deploying('create:incomplete_precision_mechanism',
     *       ['create:incomplete_precision_mechanism', 'create:cogwheel']),
     *     event.recipes.create.pressing('create:incomplete_precision_mechanism',
     *       'create:incomplete_precision_mechanism')
     *   ]
     * ).transitionalItem('create:incomplete_precision_mechanism').loops(5)
     * </pre>
     *
     * <p>三个约定（来自 KubeJS Create 的 schema）：
     * <ul>
     *   <li>序列里每一步都是<b>嵌套配方</b>，输入和输出都必须是过渡物品</li>
     *   <li>只有 deploying 需要额外材料，pressing / cutting 只用过渡物品本身</li>
     *   <li>灌装要流体，本模组不处理流体，所以不提供</li>
     * </ul>
     */
    private static void appendSequenced(StringBuilder sb, RecipeDraft d, String output) {
        List<String> in = d.inputCells();
        String input = nthNonBlank(in, 0, "请把输入物品放进输入格");
        String transitional = nthNonBlank(in, 1, "请把过渡物品放进过渡格");

        String[] kinds = d.heat() == null ? new String[0] : d.heat().split(",");
        List<String> steps = new ArrayList<>();
        for (int i = 0; i < kinds.length && i < 4; i++) {
            String kind = kinds[i] == null ? "" : kinds[i].trim();
            if (kind.isEmpty()) {
                continue;
            }
            String stepItem = i + 2 < in.size() ? in.get(i + 2) : null;
            switch (kind) {
                case "deploying" -> {
                    if (stepItem == null || stepItem.isBlank()) {
                        throw new IllegalArgumentException(
                                "第 " + (i + 1) + " 步是「部署」，请放入这一步要消耗的物品");
                    }
                    steps.add("event.recipes.create.deploying(\n"
                            + "      '" + transitional + "',\n"
                            + "      ['" + transitional + "', '" + stepItem + "']\n"
                            + "    )");
                }
                case "pressing" -> steps.add("event.recipes.create.pressing('"
                        + transitional + "', '" + transitional + "')");
                case "cutting" -> steps.add("event.recipes.create.cutting('"
                        + transitional + "', '" + transitional + "')");
                default -> throw new IllegalArgumentException("未知的步骤类型：" + kind);
            }
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("序列组装至少要有一个步骤");
        }

        sb.append("event.recipes.create.sequenced_assembly(\n");
        sb.append("  [").append(output).append("],\n");
        sb.append("  '").append(input).append("',\n");
        sb.append("  [\n");
        for (int i = 0; i < steps.size(); i++) {
            sb.append("    ").append(steps.get(i));
            sb.append(i < steps.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n");
        sb.append(").transitionalItem('").append(transitional).append("')");
        sb.append(".loops(").append(Math.max(1, d.cookingTime())).append(")");
    }

    private static String firstNonBlank(List<String> cells, String error) {
        for (String c : cells) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        throw new IllegalArgumentException(error);
    }

    private static String nthNonBlank(List<String> cells, int index, String error) {
        String c = index < cells.size() ? cells.get(index) : null;
        if (c == null || c.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return c;
    }

    /** 整数不写小数点，例如 1.0 写成 1 */
    public static String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static void appendShaped(StringBuilder sb, String output, RecipeDraft d) {
        // 同一物品复用同一个字母，生成结果更干净
        Map<String, Character> letterByItem = new LinkedHashMap<>();
        Map<Character, String> itemByLetter = new LinkedHashMap<>();
        char nextLetter = 'A';

        // 先求非空区域的包围盒，裁掉外围空白行/列
        int w = d.type().gridWidth();
        int h = d.type().gridHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                String c = d.cell(x, y);
                if (c != null && !c.isBlank()) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) {
            throw new IllegalArgumentException("请至少放入一种材料");
        }

        List<String> rows = new ArrayList<>(maxY - minY + 1);
        for (int y = minY; y <= maxY; y++) {
            StringBuilder encoded = new StringBuilder();
            for (int x = minX; x <= maxX; x++) {
                String item = d.cell(x, y);
                if (item == null || item.isBlank()) {
                    encoded.append(' ');
                    continue;
                }
                Character letter = letterByItem.get(item);
                if (letter == null) {
                    letter = nextLetter++;
                    letterByItem.put(item, letter);
                    itemByLetter.put(letter, item);
                }
                encoded.append(letter.charValue());
            }
            rows.add(encoded.toString());
        }

        sb.append("event.shaped(\n");
        sb.append("  ").append(output).append(",\n");
        sb.append("  [\n");
        for (int i = 0; i < rows.size(); i++) {
            sb.append("    '").append(rows.get(i)).append("'").append(i < rows.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ],\n");
        sb.append("  {\n");
        List<Character> letters = new ArrayList<>(itemByLetter.keySet());
        for (int i = 0; i < letters.size(); i++) {
            Character c = letters.get(i);
            sb.append("    ").append(c.charValue()).append(": '").append(itemByLetter.get(c)).append("'")
              .append(i < letters.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  }\n");
        sb.append(")");
    }

    private static void appendShapeless(StringBuilder sb, String output, List<String> ingredients) {
        sb.append("event.shapeless(\n");
        sb.append("  ").append(output).append(",\n");
        sb.append("  [\n");
        for (int i = 0; i < ingredients.size(); i++) {
            sb.append("    '").append(ingredients.get(i)).append("'")
              .append(i < ingredients.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n");
        sb.append(")");
    }

    /**
     * KubeJS 约定：数量 1 直接写物品 ID，大于 1 写 {@code '4x minecraft:torch'}。
     *
     * <p>带 NBT 时写成 {@code '1x minecraft:potion {Potion:"minecraft:healing"}'}。
     * <b>物品 ID 与花括号之间那个空格不能省</b>：KubeJS 的 {@code ItemStackJS.parse(String)}
     * 用<b>第一个空格</b>切出物品 ID，剩下的部分只有以 {@code &#123;} 开头才会被当成 SNBT
     * （内部走 {@code NBTUtils.toTagCompound} → {@code TagParser.parseTag}）。
     * 写成 {@code 'minecraft:potion&#123;...&#125;'} 会让整个字符串被当成物品 ID，直接报错。
     */
    static String quoteOutput(String item, int count, String nbt) {
        StringBuilder sb = new StringBuilder();
        if (count > 1) {
            sb.append(count).append("x ");
        }
        sb.append(item);
        if (nbt != null && !nbt.isBlank()) {
            sb.append(' ').append(nbt.trim());
        }
        return "'" + escapeJs(sb.toString()) + "'";
    }

    static String quoteOutput(String item, int count) {
        return quoteOutput(item, count, "");
    }

    /**
     * JS 单引号字符串转义。
     *
     * <p>NBT 是玩家可控的（自定义名称、书的文本），里面完全可能有单引号或反斜杠；
     * 不转义会把生成的 .js 变成语法错误，导致整个脚本文件加载失败。
     */
    static String escapeJs(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("\\'");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 删除配方

    /**
     * 渲染 {@code event.remove(...)} 调用。
     *
     * <p>只支持按配方 ID 或按产物物品两种过滤器；KubeJS 还支持 input / mod / type 等，
     * 需要时按同样的方式扩展即可。
     */
    public static String renderRemoveCall(RecipeDraft draft) {
        if (draft.removeBy() == RemoveBy.OUTPUT) {
            String output = draft.outputItem();
            if (output == null || output.isBlank()) {
                throw new IllegalArgumentException("请把要删除的产物物品放进输出格");
            }
            return "event.remove({ output: '" + output + "' })";
        }
        return "event.remove({ id: '" + normalizeRemoveId(draft.recipeId()) + "' })";
    }

    /**
     * 规范化要删除的配方 ID。
     *
     * <p>删除时的目标绝大多数是原版配方（例如 {@code minecraft:torch}），
     * 所以没写命名空间时按 {@code minecraft} 补全，而不是本模组的命名空间。
     */
    public static String normalizeRemoveId(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("请填写要删除的配方 ID，例如 minecraft:torch");
        }
        int colon = value.indexOf(':');
        if (colon < 0) {
            return "minecraft:" + sanitize(value, "recipe");
        }
        return sanitize(value.substring(0, colon), "minecraft") + ":" + sanitize(value.substring(colon + 1), "recipe");
    }

    /** 一份草稿对应的托管块标识，用于同 ID 覆盖写入 */
    public static String blockIdFor(RecipeDraft draft, String fallbackNamespace) {
        if (draft.operation() == Operation.REMOVE) {
            if (draft.removeBy() == RemoveBy.OUTPUT) {
                String item = draft.outputItem() == null ? "unknown" : draft.outputItem();
                return "remove_output_" + sanitize(item.replace(':', '_'), "unknown");
            }
            return "remove_id_" + normalizeRemoveId(draft.recipeId()).replace(':', '_');
        }
        return normalizeRecipeId(draft.recipeId(), fallbackNamespace, draft.outputItem());
    }

    // ---------------------------------------------------------------- 文件合并

    /** 生成完整的托管块（含起止标记） */
    public static String renderBlock(String blockId, String recipeCall) {
        return renderBlock(blockId, List.of(recipeCall));
    }

    /**
     * 生成完整的托管块，可包含多条语句。
     *
     * <p>「修改配方」会用到：先 {@code event.remove(...)} 删掉原配方，
     * 再写入改好的配方，两条放在同一个托管块里，重保存时一起被替换。
     */
    public static String renderBlock(String blockId, List<String> calls) {
        StringBuilder sb = new StringBuilder();
        sb.append(MARK_BEGIN).append(blockId).append("\n");
        sb.append("// 由「简易配方修改」自动生成，请勿手动编辑本块（下次保存会覆盖）\n");
        sb.append("ServerEvents.recipes(event => {\n");
        for (String call : calls) {
            sb.append("  ").append(call.replace("\n", "\n  ")).append("\n");
        }
        sb.append("})\n");
        sb.append(MARK_END).append(blockId).append("\n");
        return sb.toString();
    }

    /**
     * 一份草稿要写入的全部语句。
     *
     * <p>「修改配方」时第一条是先删除原配方，其余情况就只有一条。
     */
    public static List<String> renderCalls(RecipeDraft draft, String fallbackNamespace) {
        return renderCalls(draft, fallbackNamespace, "");
    }

    /** 同上，额外带上产物的 SNBT */
    public static List<String> renderCalls(RecipeDraft draft, String fallbackNamespace, String outputNbt) {
        List<String> calls = new ArrayList<>();
        if (draft.operation() == Operation.MODIFY) {
            String source = draft.sourceRecipeId();
            if (source != null && !source.isBlank()) {
                calls.add("event.remove({ id: '" + normalizeRemoveId(source) + "' })");
            }
        }
        calls.add(renderRecipeCall(draft, fallbackNamespace, outputNbt));
        return calls;
    }

    /**
     * 把托管块并入已有文件内容：同 ID 的旧块整体替换，否则追加到文件末尾。
     * 用户手写的、不在标记内的内容一律原样保留。
     */
    public static String upsert(String existingContent, String blockId, String block) {
        String begin = MARK_BEGIN + blockId;
        String end = MARK_END + blockId;
        String content = existingContent == null ? "" : existingContent;

        int beginIdx = content.indexOf(begin);
        if (beginIdx >= 0) {
            int endIdx = content.indexOf(end, beginIdx);
            if (endIdx >= 0) {
                int endOfLine = content.indexOf('\n', endIdx);
                endOfLine = endOfLine < 0 ? content.length() : endOfLine + 1;
                return content.substring(0, beginIdx) + block + content.substring(endOfLine);
            }
        }

        StringBuilder sb = new StringBuilder(content);
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        if (sb.length() > 0) {
            sb.append('\n');
        }
        sb.append(block);
        return sb.toString();
    }

    /** 从文件内容里整体删掉一个托管块；块外内容原样保留 */
    public static String removeBlock(String existingContent, String blockId) {
        String content = existingContent == null ? "" : existingContent;
        int beginIdx = content.indexOf(MARK_BEGIN + blockId);
        if (beginIdx < 0) {
            return content;
        }
        int endIdx = content.indexOf(MARK_END + blockId, beginIdx);
        if (endIdx < 0) {
            return content;
        }
        int endOfLine = content.indexOf('\n', endIdx);
        endOfLine = endOfLine < 0 ? content.length() : endOfLine + 1;
        return content.substring(0, beginIdx) + content.substring(endOfLine);
    }

    /** 新文件的文件头 */
    public static String fileHeader() {
        return "// 本文件由「简易配方修改」(Simple Recipe Change) 生成\n"
             + "// 标记块之间的内容由模组托管，块外的内容不会被修改。\n"
             + "// KubeJS 会自动加载 kubejs/server_scripts 下的脚本，改动后可用 /reload 热重载。\n\n";
    }
}
