import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 把 pinyin-data 的 pinyin.txt 压成模组用的紧凑资源。
 *
 * 输出两行：
 *   第一行：所有汉字按码位顺序拼接
 *   第二行：对应读音（无声调、ü 记作 v），空格分隔
 * 两边按下标一一对应，体积小且解析简单。
 */
public class GenPinyin {
    public static void main(String[] args) throws Exception {
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);

        // 覆盖 CJK 扩展A + 基本区，足够覆盖游戏里的物品名
        int minCp = 0x3400;
        int maxCp = 0x9FFF;

        StringBuilder chars = new StringBuilder();
        List<String> syllables = new ArrayList<>();
        Map<Integer, String> table = new HashMap<>();

        for (String line : Files.readAllLines(in, StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int colon = line.indexOf(':');
            int hash = line.indexOf('#');
            if (colon < 0 || hash < 0) {
                continue;
            }
            String codePart = line.substring(0, colon).trim();
            if (!codePart.startsWith("U+")) {
                continue;
            }
            int cp;
            try {
                cp = Integer.parseInt(codePart.substring(2), 16);
            } catch (NumberFormatException e) {
                continue;
            }
            if (cp < minCp || cp > maxCp) {
                continue;
            }
            String readings = line.substring(colon + 1, hash).trim();
            if (readings.isEmpty()) {
                continue;
            }
            String first = readings.split(",")[0].trim();
            String syllable = stripTone(first);
            if (syllable.isEmpty()) {
                continue;
            }
            table.put(cp, syllable);
        }

        List<Integer> keys = new ArrayList<>(table.keySet());
        Collections.sort(keys);
        for (int cp : keys) {
            chars.appendCodePoint(cp);
            syllables.add(table.get(cp));
        }

        Files.createDirectories(out.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append(chars).append('\n');
        sb.append(String.join(" ", syllables)).append('\n');
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);

        System.out.println("汉字数 = " + keys.size());
        System.out.println("资源大小 = " + String.format("%.1f KB", Files.size(out) / 1024.0));
    }

    /** 去掉声调符号；ü 统一记成 v（中文输入习惯） */
    static String stripTone(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case 'ā', 'á', 'ǎ', 'à' -> sb.append('a');
                case 'ē', 'é', 'ě', 'è' -> sb.append('e');
                case 'ī', 'í', 'ǐ', 'ì' -> sb.append('i');
                case 'ō', 'ó', 'ǒ', 'ò' -> sb.append('o');
                case 'ū', 'ú', 'ǔ', 'ù' -> sb.append('u');
                case 'ǖ', 'ǘ', 'ǚ', 'ǜ', 'ü' -> sb.append('v');
                default -> {
                    if (c >= 'a' && c <= 'z') {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
