package com.wowuwo233.simple_recipe_change.kubejs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 「我的配方」索引：记录本模组写过哪些配方，存在
 * {@code kubejs/server_scripts/simple_recipe_change.index.json}。
 *
 * <p>只记元数据，真正的脚本内容仍在各个 .js 文件里。这样「查看自己写过的配方」
 * 不需要反向解析 JavaScript。
 */
public final class RecipeIndex {

    public static final String FILE_NAME = "simple_recipe_change.index.json";
    public static final int MAX_ENTRIES = 500;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private RecipeIndex() {
    }

    public static Path indexPath() {
        return KubeJsFileWriter.serverScriptsDir().resolve(FILE_NAME);
    }

    public static List<RecipeIndexEntry> load() {
        List<RecipeIndexEntry> out = new ArrayList<>();
        Path path = indexPath();
        if (!Files.exists(path)) {
            return out;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(text);
            if (root != null && root.isJsonObject()) {
                JsonElement entries = root.getAsJsonObject().get("entries");
                if (entries != null && entries.isJsonArray()) {
                    for (JsonElement e : entries.getAsJsonArray()) {
                        if (e.isJsonObject()) {
                            out.add(RecipeIndexEntry.fromJson(e.getAsJsonObject()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 索引损坏时当作空列表，不影响 .js 文件本身
        }
        return out;
    }

    /**
     * 写回索引文件。
     *
     * @return 是否因为超过 {@link #MAX_ENTRIES} 而丢弃了最旧的记录
     */
    private static boolean save(List<RecipeIndexEntry> entries) {
        try {
            Path path = indexPath();
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("note", "「简易配方修改」的配方索引，删除本文件不影响已生成的 js 脚本");
            JsonArray arr = new JsonArray();
            int limit = Math.min(entries.size(), MAX_ENTRIES);
            for (int i = 0; i < limit; i++) {
                arr.add(entries.get(i).toJson());
            }
            root.add("entries", arr);
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
            return entries.size() > limit;
        } catch (Exception e) {
            // 索引写失败不该让保存流程失败
            return false;
        }
    }

    /**
     * 新增或按 blockId 覆盖一条记录。
     *
     * <p>新记录插在最前面，所以触顶时被挤掉的是<b>最旧</b>的一条——那条配方的
     * .js 脚本仍然存在、仍然生效，只是「我的配方」列表里看不到它了。
     * 调用方需要把这件事告诉玩家，否则就是静默丢数据。
     *
     * @return 是否因为达到 {@link #MAX_ENTRIES} 上限而丢弃了一条旧记录
     */
    public static boolean upsert(RecipeIndexEntry entry) {
        List<RecipeIndexEntry> entries = load();
        entries.removeIf(e -> e.blockId().equals(entry.blockId()));
        entries.add(0, entry);
        return save(entries);
    }

    public static void remove(String blockId) {
        List<RecipeIndexEntry> entries = load();
        entries.removeIf(e -> e.blockId().equals(blockId));
        save(entries);
    }

    public static RecipeIndexEntry find(String blockId) {
        for (RecipeIndexEntry e : load()) {
            if (e.blockId().equals(blockId)) {
                return e;
            }
        }
        return null;
    }

    /** 供网络包传输的紧凑 JSON 列表 */
    public static String toJsonList(List<RecipeIndexEntry> entries) {
        JsonArray arr = new JsonArray();
        for (RecipeIndexEntry e : entries) {
            arr.add(e.toJson());
        }
        return GSON.toJson(arr);
    }

    /** 解析 IndexListPacket 携带的 JSON；出错时返回空列表 */
    public static List<RecipeIndexEntry> parseJsonList(String json) {
        List<RecipeIndexEntry> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root != null && root.isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray()) {
                    if (e.isJsonObject()) {
                        out.add(RecipeIndexEntry.fromJson(e.getAsJsonObject()));
                    }
                }
            }
        } catch (Exception ignored) {
            // 解析失败按空列表处理
        }
        return out;
    }
}
