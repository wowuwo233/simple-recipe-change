package com.wowuwo233.simple_recipe_change.search;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 汉字读音表，用于拼音搜索。
 *
 * <p>数据来自 {@code pinyin-data}（Unihan 读音），已被压成两行资源：
 * 第一行是所有汉字按码位升序拼接，第二行是对应读音（无声调、{@code ü} 记作 {@code v}），
 * 空格分隔，两边按下标对应。这里用码位数组 + 二分查找，避免为两万多字建哈希表。
 *
 * <p>不依赖 Minecraft，可脱机测试。
 */
public final class PinyinTable {

    private static final String RESOURCE = "/simple_recipe_change/pinyin.txt";
    private static final int CJK_START = 0x3400;
    private static final int CJK_END = 0x9FFF;

    private static int[] codepoints = new int[0];
    private static String[] syllables = new String[0];
    private static boolean loaded = false;

    private PinyinTable() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (InputStream in = PinyinTable.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String charLine = reader.readLine();
            String syllableLine = reader.readLine();
            if (charLine == null || syllableLine == null) {
                return;
            }
            String[] parts = syllableLine.trim().isEmpty() ? new String[0] : syllableLine.trim().split(" ");
            List<Integer> cps = new ArrayList<>(charLine.length());
            charLine.codePoints().forEach(cps::add);

            int n = Math.min(cps.size(), parts.length);
            codepoints = new int[n];
            syllables = new String[n];
            for (int i = 0; i < n; i++) {
                codepoints[i] = cps.get(i);
                syllables[i] = parts[i];
            }
        } catch (Exception e) {
            codepoints = new int[0];
            syllables = new String[0];
        }
    }

    /** 单个汉字的读音；不是汉字或查不到时返回 {@code null} */
    public static String syllableOf(char c) {
        ensureLoaded();
        if (c < CJK_START || c > CJK_END) {
            return null;
        }
        int lo = 0;
        int hi = codepoints.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (codepoints[mid] == c) {
                return syllables[mid];
            }
            if (codepoints[mid] < c) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return null;
    }

    /**
     * 把一段文本转成拼音。
     *
     * @return 长度为 2 的数组：{@code [全拼, 首字母]}；非汉字按小写字母数字原样保留
     */
    public static String[] pinyinForms(String text) {
        ensureLoaded();
        StringBuilder full = new StringBuilder();
        StringBuilder initials = new StringBuilder();
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                String syllable = syllableOf(c);
                if (syllable != null && !syllable.isEmpty()) {
                    full.append(syllable);
                    initials.append(syllable.charAt(0));
                } else if (c < 128 && Character.isLetterOrDigit(c)) {
                    char lower = Character.toLowerCase(c);
                    full.append(lower);
                    initials.append(lower);
                }
            }
        }
        return new String[]{full.toString(), initials.toString()};
    }
}
