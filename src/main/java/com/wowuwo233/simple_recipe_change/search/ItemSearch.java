package com.wowuwo233.simple_recipe_change.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 物品搜索的可搜索条目与匹配规则。
 *
 * <p>查询语法对齐 JEI 的习惯：
 * <ul>
 *   <li>{@code @modid} —— 按模组 ID 过滤（也可以只写 {@code @} 表示「要求是模组物品」）</li>
 *   <li>普通文本 —— 同时匹配物品 ID、显示名、全拼、拼音首字母</li>
 *   <li>多个词用空格分隔，全部命中才算匹配</li>
 * </ul>
 *
 * <p>不依赖 Minecraft，可脱机测试。
 */
public final class ItemSearch {

    /**
     * @param itemId      形如 {@code minecraft:oak_planks}
     * @param modId       命名空间部分，形如 {@code minecraft}
     * @param displayName 本地化显示名，形如 {@code 橡木木板}
     * @param pinyin      显示名的全拼（{@code xiangmumuban}）
     * @param initials    显示名的拼音首字母（{@code xmmb}）
     */
    public record Entry(String itemId, String modId, String displayName,
                        String pinyin, String initials) {

        public static Entry of(String itemId, String displayName) {
            String mod = itemId;
            int colon = itemId.indexOf(':');
            if (colon >= 0) {
                mod = itemId.substring(0, colon);
            }
            String[] forms = PinyinTable.pinyinForms(displayName);
            return new Entry(itemId, mod, displayName, forms[0], forms[1]);
        }
    }

    private ItemSearch() {
    }

    /** 空格分词，每个词都必须命中 */
    public static boolean matches(String query, Entry entry) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (String token : needle.split("\\s+")) {
            if (!token.isEmpty() && !matchesToken(token, entry)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesToken(String token, Entry entry) {
        if (token.startsWith("@")) {
            String mod = token.substring(1);
            // 只写 @ 时表示「只看模组物品」，这里不额外排除，交由调用方决定范围
            return mod.isEmpty() || entry.modId().toLowerCase(Locale.ROOT).contains(mod);
        }
        String name = entry.displayName() == null ? "" : entry.displayName().toLowerCase(Locale.ROOT);
        return entry.itemId().toLowerCase(Locale.ROOT).contains(token)
                || name.contains(token)
                || entry.pinyin().contains(token)
                || entry.initials().contains(token);
    }

    public static List<Entry> filter(String query, List<Entry> entries) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (matches(query, entry)) {
                out.add(entry);
            }
        }
        return out;
    }
}
