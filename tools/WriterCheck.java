import com.wowuwo233.simple_recipe_change.kubejs.KubeJsWriter;
import com.wowuwo233.simple_recipe_change.kubejs.Operation;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeDraft;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import com.wowuwo233.simple_recipe_change.kubejs.RemoveBy;

import java.util.ArrayList;
import java.util.List;

/** 脱离 Minecraft 验证 KubeJS 脚本生成结果。 */
public class WriterCheck {

    static int failures = 0;

    public static void main(String[] args) {
        shapedTorch();
        shapedMirroredOn();
        shapelessDandelion();
        inventory2x2();
        patternTrimmingAndLetterReuse();
        recipeIdNormalization();
        fileNameNormalization();
        scriptUpsert();
        validationErrors();
        removalByRecipeId();
        removalByOutput();
        modifyRendersRemoveThenAdd();
        blockRemovalFromFile();
        cookingRecipes();
        smithingRecipe();

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------ 用例

    static void shapedTorch() {
        // 工作台 3x3，纵向：煤 在上、木棍 在下；结果 4 个火把；不镜像
        List<String> cells = blank(9);
        cells.set(0, "minecraft:coal");
        cells.set(3, "minecraft:stick");
        RecipeDraft d = new RecipeDraft(RecipeType.CRAFTING_TABLE, false, false,
                "simple_recipe_change:cheap_torch", cells, "minecraft:torch", 4);
        String call = KubeJsWriter.renderRecipeCall(d, "simple_recipe_change");
        section("工作台合成 / 不镜像 / 4 个火把");
        System.out.println(KubeJsWriter.renderBlock("simple_recipe_change:cheap_torch", call));
        check("包含 event.shaped", call.contains("event.shaped("));
        check("产物写成 4x", call.contains("'4x minecraft:torch'"));
        check("关闭镜像用 .noMirror()", call.contains(".noMirror()"));
        check("配方 ID 正确", call.contains(".id('simple_recipe_change:cheap_torch')"));
        check("图案裁成两行", call.contains("'A'") && call.contains("'B'"));
    }

    static void shapedMirroredOn() {
        List<String> cells = blank(9);
        cells.set(0, "minecraft:oak_planks");
        cells.set(1, "minecraft:oak_planks");
        cells.set(3, "minecraft:oak_planks");
        cells.set(4, "minecraft:oak_planks");
        RecipeDraft d = new RecipeDraft(RecipeType.CRAFTING_TABLE, false, true,
                "", cells, "minecraft:crafting_table", 1);
        String call = KubeJsWriter.renderRecipeCall(d, "simple_recipe_change");
        section("镜像开启（默认，不输出 .noMirror）");
        System.out.println(call);
        check("不输出 .noMirror()", !call.contains(".noMirror"));
        check("数量 1 不写 x 前缀", call.contains("'minecraft:crafting_table'"));
        check("空配方名自动生成", call.contains(".id('simple_recipe_change:auto_minecraft_crafting_table')"));
        check("相同材料复用字母 A", call.contains("'AA'"));
    }

    static void shapelessDandelion() {
        List<String> cells = blank(9);
        cells.set(0, "minecraft:bone_meal");
        cells.set(1, "minecraft:yellow_dye");
        cells.set(2, "minecraft:ender_pearl");
        RecipeDraft d = new RecipeDraft(RecipeType.CRAFTING_TABLE, true, true,
                "mypack:my_dandelion", cells, "minecraft:dandelion", 3);
        String call = KubeJsWriter.renderRecipeCall(d, "simple_recipe_change");
        section("无序合成");
        System.out.println(call);
        check("使用 event.shapeless", call.contains("event.shapeless("));
        check("产物 3x", call.contains("'3x minecraft:dandelion'"));
        check("无序不输出 .noMirror", !call.contains(".noMirror"));
        check("三种材料都在", call.contains("minecraft:bone_meal")
                && call.contains("minecraft:yellow_dye") && call.contains("minecraft:ender_pearl"));
    }

    static void inventory2x2() {
        // 2×2 取的是 3×3 容器里的左上四格，即下标 0、1、3、4。
        // 诱饵放在第 3 列（2、5、8）和第 3 行（6、7），cropToGrid 必须把它们丢掉。
        List<String> cells = blank(9);
        cells.set(0, "minecraft:stick");
        cells.set(1, "minecraft:stick");
        cells.set(3, "minecraft:stick");
        cells.set(4, "minecraft:stick");
        cells.set(2, "minecraft:DIAMOND_BLOCK_SHOULD_BE_IGNORED");
        cells.set(5, "minecraft:DIAMOND_BLOCK_SHOULD_BE_IGNORED");
        cells.set(6, "minecraft:DIAMOND_BLOCK_SHOULD_BE_IGNORED");
        cells.set(7, "minecraft:DIAMOND_BLOCK_SHOULD_BE_IGNORED");
        cells.set(8, "minecraft:DIAMOND_BLOCK_SHOULD_BE_IGNORED");
        RecipeDraft base = new RecipeDraft(RecipeType.INVENTORY, false, true,
                "simple_recipe_change:two_by_two", cells, "minecraft:ladder", 3);
        String call = KubeJsWriter.renderRecipeCall(base, "simple_recipe_change");
        section("物品栏合成 2x2（多余的行/列应被裁掉）");
        System.out.println(call);
        check("忽略 2x2 之外的格子", !call.contains("DIAMOND_BLOCK_SHOULD_BE_IGNORED"));
        check("图案是 2x2", call.contains("'AA'"));
        check("产物 3x", call.contains("'3x minecraft:ladder'"));
    }

    static void patternTrimmingAndLetterReuse() {
        // 图案在第 2、3 列，第 1 列空 -> 应裁至紧凑；且 A/B/C 复用在同一物品
        List<String> cells = blank(9);
        cells.set(1, "minecraft:iron_ingot");
        cells.set(2, "minecraft:iron_ingot");
        cells.set(4, "minecraft:iron_ingot");
        cells.set(5, "minecraft:iron_ingot");
        cells.set(7, "minecraft:iron_ingot");
        cells.set(8, "minecraft:iron_ingot");
        RecipeDraft d = new RecipeDraft(RecipeType.CRAFTING_TABLE, false, true,
                "", cells, "minecraft:iron_block", 1);
        String call = KubeJsWriter.renderRecipeCall(d, "simple_recipe_change");
        section("图案裁剪 + 字母复用");
        System.out.println(call);
        check("图案为 AA/AA/AA", call.contains("'AA',\n    'AA',\n    'AA'"));
        check("只用一个字母 A", !call.contains("B:"));
    }

    static void recipeIdNormalization() {
        section("配方名规范化");
        eq("无名 -> 自动生成", "simple_recipe_change:auto_minecraft_torch",
                KubeJsWriter.normalizeRecipeId("", "simple_recipe_change", "minecraft:torch"));
        eq("自动补命名空间", "simple_recipe_change:my_recipe",
                KubeJsWriter.normalizeRecipeId("my_recipe", "simple_recipe_change", "minecraft:torch"));
        eq("保留已有命名空间", "mypack:my_recipe",
                KubeJsWriter.normalizeRecipeId("mypack:my_recipe", "simple_recipe_change", "minecraft:torch"));
        eq("非法字符被替换", "mypack:my_recipe",
                KubeJsWriter.normalizeRecipeId("MyPack:My Recipe!", "simple_recipe_change", "minecraft:torch"));
    }

    static void fileNameNormalization() {
        section("文件名规范化");
        eq("补 .js", "recipes.js", KubeJsWriter.normalizeFileName("recipes"));
        eq("保留 .js", "my_recipes.js", KubeJsWriter.normalizeFileName("my_recipes.js"));
        eq("去掉路径", "evil.js", KubeJsWriter.normalizeFileName("../../evil.js"));
        eq("兜底默认名", "simple_recipe_change_recipes.js", KubeJsWriter.normalizeFileName(""));
        eq("中文与空格被折叠掉", "my_name.js", KubeJsWriter.normalizeFileName("my 配方 name.js"));
        eq("纯中文退化为默认名", "simple_recipe_change_recipes.js", KubeJsWriter.normalizeFileName("中文名字"));
    }

    static void scriptUpsert() {
        section("托管块合并");
        String blockA = KubeJsWriter.renderBlock("id_a", "event.shaped('1x minecraft:stone', ['A'], {A: 'minecraft:dirt'})");
        String blockB = KubeJsWriter.renderBlock("id_b", "event.shaped('1x minecraft:dirt', ['A'], {A: 'minecraft:stone'})");

        String file = KubeJsWriter.upsert(KubeJsWriter.fileHeader(), "id_a", blockA);
        file = KubeJsWriter.upsert(file, "id_b", blockB);
        check("两个块都在", file.contains("BEGIN id_a") && file.contains("BEGIN id_b"));
        check("手写内容被保留", file.contains("KubeJS 会自动加载"));

        String blockA2 = KubeJsWriter.renderBlock("id_a",
                "event.shaped('8x minecraft:stone', ['A'], {A: 'minecraft:dirt'})");
        file = KubeJsWriter.upsert(file, "id_a", blockA2);
        long countA = file.lines().filter(l -> l.contains("BEGIN id_a")).count();
        eq("同 ID 只保留一个 BEGIN", "1", String.valueOf(countA));
        check("内容已更新", file.contains("'8x minecraft:stone'"));
        check("id_b 仍在", file.contains("BEGIN id_b"));

        String handwritten = "// 我手写的脚本\nServerEvents.recipes(event => {})\n";
        String merged = KubeJsWriter.upsert(handwritten, "id_c", KubeJsWriter.renderBlock("id_c", "event.shapeless('1x minecraft:dirt', ['minecraft:stone'])"));
        check("不破坏手写内容", merged.startsWith(handwritten));
        check("新块被追加", merged.contains("BEGIN id_c"));
    }

    static void validationErrors() {
        section("校验报错");
        RecipeDraft noOutput = new RecipeDraft(RecipeType.CRAFTING_TABLE, false, true, "", blank(9), null, 0);
        expectError("缺产物", () -> KubeJsWriter.renderRecipeCall(noOutput, "simple_recipe_change"));

        List<String> withOutput = blank(9);
        RecipeDraft noIngredients = new RecipeDraft(RecipeType.CRAFTING_TABLE, false, true, "", withOutput, "minecraft:stone", 1);
        expectError("缺材料", () -> KubeJsWriter.renderRecipeCall(noIngredients, "simple_recipe_change"));
    }

    static void removalByRecipeId() {
        section("删除配方：按配方 ID");
        RecipeDraft draft = new RecipeDraft(RecipeType.CRAFTING_TABLE, false, true,
                "minecraft:torch", blank(9), null, 0,
                Operation.REMOVE, RemoveBy.ID);
        String call = KubeJsWriter.renderRecipeCall(draft, "simple_recipe_change");
        System.out.println(KubeJsWriter.renderBlock(KubeJsWriter.blockIdFor(draft, "simple_recipe_change"), call));
        check("使用 event.remove", call.contains("event.remove("));
        check("按 id 过滤", call.equals("event.remove({ id: 'minecraft:torch' })"));
        check("不产生 shaped", !call.contains("event.shaped"));
        check("托管块标识", KubeJsWriter.blockIdFor(draft, "simple_recipe_change")
                .equals("remove_id_minecraft_torch"));

        eq("无命名空间补 minecraft", "minecraft:torch", KubeJsWriter.normalizeRemoveId("torch"));
        eq("已有命名空间保留", "farmersdelight:cutting/cake", KubeJsWriter.normalizeRemoveId("farmersdelight:cutting/cake"));
        expectError("配方 ID 为空时报错", () -> KubeJsWriter.normalizeRemoveId("  "));
    }

    static void removalByOutput() {
        section("删除配方：按产物物品");
        RecipeDraft draft = new RecipeDraft(RecipeType.CRAFTING_TABLE, false, true,
                "", blank(9), "minecraft:torch", 1,
                Operation.REMOVE, RemoveBy.OUTPUT);
        String call = KubeJsWriter.renderRecipeCall(draft, "simple_recipe_change");
        System.out.println(KubeJsWriter.renderBlock(KubeJsWriter.blockIdFor(draft, "simple_recipe_change"), call));
        check("按 output 过滤", call.equals("event.remove({ output: 'minecraft:torch' })"));
        check("托管块标识", KubeJsWriter.blockIdFor(draft, "simple_recipe_change")
                .equals("remove_output_minecraft_torch"));

        RecipeDraft empty = new RecipeDraft(RecipeType.CRAFTING_TABLE, false, true,
                "", blank(9), null, 0, Operation.REMOVE, RemoveBy.OUTPUT);
        expectError("输出格为空时报错", () -> KubeJsWriter.renderRecipeCall(empty, "simple_recipe_change"));

        // 删除与添加用同一个文件时，托管块互不覆盖
        String file = KubeJsWriter.upsert(KubeJsWriter.fileHeader(),
                "remove_id_minecraft_torch", KubeJsWriter.renderBlock("remove_id_minecraft_torch", call));
        file = KubeJsWriter.upsert(file, "simple_recipe_change:my_recipe",
                KubeJsWriter.renderBlock("simple_recipe_change:my_recipe",
                        "event.shapeless('1x minecraft:stick', ['minecraft:oak_planks'])"));
        check("删除块与配方块共存",
                file.contains("BEGIN remove_id_minecraft_torch") && file.contains("BEGIN simple_recipe_change:my_recipe"));
    }

    static void modifyRendersRemoveThenAdd() {
        section("修改配方：先删原配方再写新版本");
        List<String> cells = blank(9);
        cells.set(0, "minecraft:coal");
        cells.set(3, "minecraft:stick");
        RecipeDraft draft = new RecipeDraft(RecipeType.CRAFTING_TABLE, false, true,
                "simple_recipe_change:cheap_torch", cells, "minecraft:torch", 8,
                Operation.MODIFY, RemoveBy.OUTPUT)
                .withSourceRecipeId("minecraft:torch");

        List<String> calls = KubeJsWriter.renderCalls(draft, "simple_recipe_change");
        eq("修改模式产生两条语句", "2", String.valueOf(calls.size()));
        check("第一条删除原配方", calls.get(0).equals("event.remove({ id: 'minecraft:torch' })"));
        check("第二条写入新配方", calls.get(1).contains("event.shaped("));
        check("产物为 8x", calls.get(1).contains("'8x minecraft:torch'"));

        String block = KubeJsWriter.renderBlock("simple_recipe_change:cheap_torch", calls);
        check("同一个托管块里两条都在",
                block.contains("event.remove(") && block.contains("event.shaped("));
        System.out.println(block);

        List<String> addOnly = KubeJsWriter.renderCalls(draft.withOperation(Operation.ADD), "simple_recipe_change");
        eq("添加模式只有一条语句", "1", String.valueOf(addOnly.size()));

        // 没有原配方 ID 时不该凭空生成 remove
        List<String> noSource = KubeJsWriter.renderCalls(
                draft.withSourceRecipeId(""), "simple_recipe_change");
        eq("无原配方时不做 remove", "1", String.valueOf(noSource.size()));
    }

    static void blockRemovalFromFile() {
        section("从 .js 文件里移除托管块");
        String blockA = KubeJsWriter.renderBlock("id_a", "event.remove({ id: 'minecraft:torch' })");
        String blockB = KubeJsWriter.renderBlock("id_b",
                "event.shaped('1x minecraft:stone', ['A'], {A: 'minecraft:dirt'})");
        String file = "// 我手写的脚本\n" + blockA + "\n" + blockB;

        String after = KubeJsWriter.removeBlock(file, "id_a");
        check("id_a 已被整块移除", !after.contains("BEGIN id_a") && !after.contains("END id_a"));
        check("id_b 仍在", after.contains("BEGIN id_b"));
        check("手写内容保留", after.contains("我手写的脚本"));

        String untouched = KubeJsWriter.removeBlock(after, "根本不存在的 id");
        eq("移除不存在的块不改动内容", after, untouched);
    }

    static void cookingRecipes() {
        section("烧炼配方：熔炉 / 高炉 / 烟熏炉");
        List<String> cells = blank(9);
        cells.set(0, "minecraft:sand");
        RecipeDraft base = new RecipeDraft(RecipeType.FURNACE, false, true,
                "simple_recipe_change:fast_glass", cells, "minecraft:glass", 1,
                Operation.ADD, RemoveBy.OUTPUT);

        String call = KubeJsWriter.renderRecipeCall(base, "simple_recipe_change");
        System.out.println(call);
        check("使用 event.smelting", call.contains("event.smelting("));
        check("原料是沙子", call.contains("'minecraft:sand'"));
        check("默认经验 0 不写出", !call.contains(".xp("));
        check("默认时间 200 不写出", !call.contains(".cookingTime("));

        String custom = KubeJsWriter.renderRecipeCall(
                base.withXp(0.35).withCookingTime(100), "simple_recipe_change");
        System.out.println(custom);
        check("写出经验", custom.contains(".xp(0.35)"));
        check("写出烧制时间", custom.contains(".cookingTime(100)"));

        check("高炉用 event.blasting", KubeJsWriter
                .renderRecipeCall(base.withType(RecipeType.BLAST_FURNACE), "x").contains("event.blasting("));
        check("烟熏炉用 event.smoking", KubeJsWriter
                .renderRecipeCall(base.withType(RecipeType.SMOKER), "x").contains("event.smoking("));

        RecipeDraft noInput = new RecipeDraft(RecipeType.FURNACE, false, true, "",
                blank(9), "minecraft:glass", 1);
        expectError("烧炼缺原料时报错", () -> KubeJsWriter.renderRecipeCall(noInput, "x"));

        // 整数经验不写小数点
        check("经验 1.0 写成 1", KubeJsWriter
                .renderRecipeCall(base.withXp(1.0), "x").contains(".xp(1)"));
    }

    static void smithingRecipe() {
        section("锻造配方：模板 + 基础物品 + 升级物品");
        List<String> cells = blank(9);
        cells.set(0, "minecraft:netherite_upgrade_smithing_template");
        cells.set(1, "minecraft:diamond_sword");
        cells.set(2, "minecraft:netherite_ingot");

        RecipeDraft d = new RecipeDraft(RecipeType.SMITHING, false, true,
                "simple_recipe_change:my_sword", cells, "minecraft:netherite_sword", 1,
                Operation.ADD, RemoveBy.OUTPUT);
        String call = KubeJsWriter.renderRecipeCall(d, "simple_recipe_change");
        System.out.println(call);
        check("使用 event.smithing", call.contains("event.smithing("));
        check("先模板再基础物品",
                call.indexOf("netherite_upgrade_smithing_template") < call.indexOf("diamond_sword"));
        check("基础物品在升级物品之前",
                call.indexOf("diamond_sword") < call.indexOf("netherite_ingot"));
        check("锻造不写 .noMirror", !call.contains(".noMirror"));

        List<String> onlyTwo = blank(9);
        onlyTwo.set(0, "minecraft:netherite_upgrade_smithing_template");
        onlyTwo.set(1, "minecraft:diamond_sword");
        RecipeDraft missingAddition = new RecipeDraft(RecipeType.SMITHING, false, true, "",
                onlyTwo, "minecraft:netherite_sword", 1);
        expectError("缺升级物品时报错", () -> KubeJsWriter.renderRecipeCall(missingAddition, "x"));

        List<String> onlyBase = blank(9);
        onlyBase.set(1, "minecraft:diamond_sword");
        onlyBase.set(2, "minecraft:netherite_ingot");
        RecipeDraft missingTemplate = new RecipeDraft(RecipeType.SMITHING, false, true, "",
                onlyBase, "minecraft:netherite_sword", 1);
        expectError("缺模板时报错", () -> KubeJsWriter.renderRecipeCall(missingTemplate, "x"));
    }

    // ------------------------------------------------------------ 工具

    static List<String> blank(int n) {
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(null);
        }
        return list;
    }

    static void section(String title) {
        System.out.println();
        System.out.println("================ " + title + " ================");
    }

    static void check(String what, boolean ok) {
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + what);
        if (!ok) {
            failures++;
        }
    }

    static void eq(String what, String expected, String actual) {
        boolean ok = expected.equals(actual);
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + what
                + (ok ? "" : "  期望=" + expected + "  实际=" + actual));
        if (!ok) {
            failures++;
        }
    }

    static void expectError(String what, Runnable r) {
        try {
            r.run();
            check(what + "（应抛异常但没有）", false);
        } catch (IllegalArgumentException e) {
            System.out.println("  [PASS] " + what + " -> " + e.getMessage());
        }
    }
}
