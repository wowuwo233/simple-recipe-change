package com.wowuwo233.simple_recipe_change.kubejs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 「我的配方」列表里的一条记录：本模组写过的一份配方的完整快照。
 *
 * <p>存快照的原因是可编辑性——生成的 .js 是脚本代码，没法可靠地反向解析；
 * 所以保存时同时把结构化数据记在索引文件里，之后就能原样载回编辑器改。
 */
public record RecipeIndexEntry(
        String blockId,
        String fileName,
        Operation operation,
        RecipeType type,
        boolean shapeless,
        boolean mirrored,
        String recipeId,
        String sourceRecipeId,
        String outputItem,
        int outputCount,
        List<String> cells,
        double xp,
        int cookingTime,
        List<RecipeDraft.ExtraOutput> extraOutputs
) {
    public RecipeIndexEntry {
        cells = cells == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(cells));
        extraOutputs = extraOutputs == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(extraOutputs));
        if (outputItem == null) {
            outputItem = "";
        }
        if (recipeId == null) {
            recipeId = "";
        }
        if (sourceRecipeId == null) {
            sourceRecipeId = "";
        }
        if (cookingTime <= 0) {
            cookingTime = RecipeDraft.DEFAULT_COOKING_TIME;
        }
    }

    /** 便捷构造：不带烧炼参数 */
    public RecipeIndexEntry(String blockId, String fileName, Operation operation, RecipeType type,
                            boolean shapeless, boolean mirrored, String recipeId, String sourceRecipeId,
                            String outputItem, int outputCount, List<String> cells) {
        this(blockId, fileName, operation, type, shapeless, mirrored, recipeId, sourceRecipeId,
                outputItem, outputCount, cells, 0D, RecipeDraft.DEFAULT_COOKING_TIME, List.of());
    }

    public static RecipeIndexEntry fromDraft(String blockId, String fileName, RecipeDraft draft) {
        List<String> cells = new ArrayList<>(draft.cells());
        while (cells.size() < 9) {
            cells.add(null);
        }
        return new RecipeIndexEntry(blockId, fileName, draft.operation(), draft.type(),
                draft.shapeless(), draft.mirrored(), draft.recipeId(), draft.sourceRecipeId(),
                draft.outputItem() == null ? "" : draft.outputItem(), draft.outputCount(), cells,
                draft.xp(), draft.cookingTime(), draft.extraOutputs());
    }

    /** 还原成编辑器用的草稿 */
    public RecipeDraft toDraft() {
        List<String> copy = new ArrayList<>(cells);
        while (copy.size() < 9) {
            copy.add(null);
        }
        return new RecipeDraft(type, shapeless, mirrored, recipeId, copy,
                outputItem.isBlank() ? null : outputItem, outputCount, operation, RemoveBy.OUTPUT,
                sourceRecipeId, xp, cookingTime, extraOutputs, "");
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("blockId", blockId);
        o.addProperty("file", fileName);
        o.addProperty("operation", operation.name());
        o.addProperty("type", type.name());
        o.addProperty("shapeless", shapeless);
        o.addProperty("mirrored", mirrored);
        o.addProperty("recipeId", recipeId);
        o.addProperty("sourceRecipeId", sourceRecipeId);
        o.addProperty("output", outputItem);
        o.addProperty("outputCount", outputCount);
        o.addProperty("xp", xp);
        o.addProperty("cookingTime", cookingTime);
        JsonArray arr = new JsonArray();
        for (String c : cells) {
            arr.add(c == null ? JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(c));
        }
        o.add("cells", arr);

        // 切菜板的额外产物（含概率）；其它类型是空数组
        JsonArray extras = new JsonArray();
        for (RecipeDraft.ExtraOutput e : extraOutputs) {
            JsonObject eo = new JsonObject();
            eo.addProperty("item", e.item());
            eo.addProperty("count", e.count());
            eo.addProperty("chance", e.chance());
            extras.add(eo);
        }
        o.add("extraOutputs", extras);
        return o;
    }

    public static RecipeIndexEntry fromJson(JsonObject o) {
        List<String> cells = new ArrayList<>();
        JsonElement cellsEl = o.get("cells");
        if (cellsEl != null && cellsEl.isJsonArray()) {
            for (JsonElement e : cellsEl.getAsJsonArray()) {
                cells.add(e.isJsonNull() ? null : e.getAsString());
            }
        }
        List<RecipeDraft.ExtraOutput> extras = new ArrayList<>();
        JsonElement extrasEl = o.get("extraOutputs");
        if (extrasEl != null && extrasEl.isJsonArray()) {
            for (JsonElement e : extrasEl.getAsJsonArray()) {
                if (!e.isJsonObject()) {
                    continue;
                }
                JsonObject eo = e.getAsJsonObject();
                extras.add(new RecipeDraft.ExtraOutput(
                        str(eo, "item"),
                        eo.has("count") ? eo.get("count").getAsInt() : 1,
                        eo.has("chance") ? eo.get("chance").getAsDouble() : 1D));
            }
        }
        return new RecipeIndexEntry(
                str(o, "blockId"),
                str(o, "file"),
                operationOf(str(o, "operation")),
                typeOf(str(o, "type")),
                o.has("shapeless") && o.get("shapeless").getAsBoolean(),
                !o.has("mirrored") || o.get("mirrored").getAsBoolean(),
                str(o, "recipeId"),
                str(o, "sourceRecipeId"),
                str(o, "output"),
                o.has("outputCount") ? o.get("outputCount").getAsInt() : 0,
                cells,
                o.has("xp") ? o.get("xp").getAsDouble() : 0D,
                o.has("cookingTime") ? o.get("cookingTime").getAsInt() : RecipeDraft.DEFAULT_COOKING_TIME,
                extras);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static Operation operationOf(String name) {
        try {
            return Operation.valueOf(name);
        } catch (Exception e) {
            return Operation.ADD;
        }
    }

    private static RecipeType typeOf(String name) {
        try {
            return RecipeType.valueOf(name);
        } catch (Exception e) {
            return RecipeType.CRAFTING_TABLE;
        }
    }
}
