import com.wowuwo233.simple_recipe_change.search.ItemSearch;
import com.wowuwo233.simple_recipe_change.search.PinyinTable;

import java.util.List;

/** 脱离 Minecraft 验证拼音 / @mod 搜索。 */
public class SearchCheck {

    static int failures = 0;

    public static void main(String[] args) {
        ItemSearch.Entry planks = ItemSearch.Entry.of("minecraft:oak_planks", "橡木木板");
        ItemSearch.Entry torch = ItemSearch.Entry.of("minecraft:torch", "火把");
        ItemSearch.Entry diamond = ItemSearch.Entry.of("minecraft:diamond_pickaxe", "钻石镐");
        ItemSearch.Entry copper = ItemSearch.Entry.of("create:copper_ingot", "铜锭");

        section("拼音形式");
        eq("橡木木板 全拼", "xiangmumuban", planks.pinyin());
        eq("橡木木板 首字母", "xmmb", planks.initials());
        eq("钻石镐 全拼", "zuanshigao", diamond.pinyin());
        eq("铜锭 全拼", "tongding", copper.pinyin());
        eq("绿 -> lv 形式的 v", "lv", PinyinTable.pinyinForms("绿")[0]);

        section("匹配：中文");
        check("橡木", ItemSearch.matches("橡木", planks));
        check("木板", ItemSearch.matches("木板", planks));
        check("火把", ItemSearch.matches("火把", torch));
        check("无关词不匹配", !ItemSearch.matches("石头", planks));

        section("匹配：全拼");
        check("muban", ItemSearch.matches("muban", planks));
        check("xiangmu", ItemSearch.matches("xiangmu", planks));
        check("zuanshi", ItemSearch.matches("zuanshi", diamond));
        check("huoba", ItemSearch.matches("huoba", torch));
        check("不相关的拼音不匹配", !ItemSearch.matches("shiton", planks));

        section("匹配：首字母");
        check("xmmb", ItemSearch.matches("xmmb", planks));
        check("zsg", ItemSearch.matches("zsg", diamond));
        check("hb", ItemSearch.matches("hb", torch));

        section("匹配：@模组名（JEI 同款语法）");
        check("@minecraft 命中原版物品", ItemSearch.matches("@minecraft", planks));
        check("@create 命中模组物品", ItemSearch.matches("@create", copper));
        check("@create 不命中原版物品", !ItemSearch.matches("@create", planks));
        check("@mine 前缀命中", ItemSearch.matches("@mine", planks));
        check("@mc 不命中（与 JEI 一致，minecraft 里不含 mc）", !ItemSearch.matches("@mc", planks));
        check("单独 @ 不过滤", ItemSearch.matches("@", planks));

        section("匹配：物品 ID 与英文名");
        check("oak_planks", ItemSearch.matches("oak_planks", planks));
        check("diamond", ItemSearch.matches("diamond", diamond));

        section("匹配：空格分词，全部命中");
        check("'木板 @minecraft'", ItemSearch.matches("木板 @minecraft", planks));
        check("'木板 @create' 应失败", !ItemSearch.matches("木板 @create", planks));

        section("过滤列表");
        List<ItemSearch.Entry> all = List.of(planks, torch, diamond, copper);
        eq("muban 命中 1 条", "1", String.valueOf(ItemSearch.filter("muban", all).size()));
        eq("@minecraft 命中 3 条", "3", String.valueOf(ItemSearch.filter("@minecraft", all).size()));
        eq("空查询返回全部", "4", String.valueOf(ItemSearch.filter("", all).size()));

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CHECKS PASSED");
        } else {
            System.out.println(failures + " CHECK(S) FAILED");
            System.exit(1);
        }
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
}
