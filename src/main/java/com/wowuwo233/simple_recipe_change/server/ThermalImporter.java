package com.wowuwo233.simple_recipe_change.server;

import com.wowuwo233.simple_recipe_change.kubejs.RecipeDraft;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup.Found;

import static com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup.blankCells;
import static com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup.build;
import static com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup.call;
import static com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup.describe;
import static com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup.field;
import static com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup.idOf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 热力膨胀的配方导入器。
 *
 * <p>这个模组把配方存在自己的 Manager 里，{@code Recipe} 对象只有壳子，
 * 所以要分三层往下试：先找壳子里的 {@code IMachineRecipe}，再退回标准接口，
 * 最后退回读数据包 JSON。
 *
 * <p>抽成独立类是为了让 {@link VanillaRecipeLookup} 只负责「原版那几种」，
 * 各模组的读取细节各自隔离——以后加新模组只需再写一个 {@link RecipeImporter}。
 */
final class ThermalImporter implements RecipeImporter {

    @Override
    public boolean supports(RecipeType type) {
        return type.isThermal();
    }

    @Override
    public Found read(Level level, Recipe<?> recipe, RecipeType type,
                                          RegistryAccess access, ResourceLocation id) {
        Found machine = readMachine(recipe, type);
        Found found = machine != null ? machine : readThermal(recipe, access, type);
        return found != null ? found : readJson(level, id, type);
    }

Found readThermal(Recipe<?> recipe, RegistryAccess access, RecipeType type) {
        List<String> cells = blankCells();
        Set<String> distinct = new LinkedHashSet<>();
        // 热力膨胀的配方类没走标准接口，getIngredients() 常返回空表，这里退回反射
        List<Object> raw = new ArrayList<>();
        List<Ingredient> ingredients = recipe.getIngredients();
        if (!ingredients.isEmpty()) {
            raw.addAll(ingredients);
        } else {
            Object input = call(recipe, "getInput");
            if (input == null) {
                input = call(recipe, "getInputs");
            }
            if (input instanceof List<?> list) {
                raw.addAll(list);
            } else if (input instanceof Ingredient single) {
                raw.add(single);
            }
        }
        for (int i = 0; i < 9 && i < raw.size(); i++) {
            String id = raw.get(i) instanceof Ingredient ing ? describe(ing)
                    : raw.get(i) instanceof ItemStack stack ? idOf(stack) : null;
            cells.set(i, id);
            if (id != null) {
                distinct.add(id);
            }
        }

        ItemStack result = recipe.getResultItem(access);
        if (result.isEmpty()) {
            Object output = call(recipe, "getOutput");
            if (output == null) {
                output = call(recipe, "getOutputs");
            }
            if (output instanceof List<?> list && !list.isEmpty()) {
                output = list.get(0);
            }
            if (output instanceof ItemStack stack) {
                result = stack;
            }
        }
        // 映射类（rock_gen / hive_extractor / tree_extractor）没有标准材料表，按字段读
        if (distinct.isEmpty()) {
            String[] fieldNames = {"adjacent", "below", "hive", "trunk", "leaves", "item"};
            int cell = 0;
            for (String name : fieldNames) {
                String id = fieldAsId(recipe, name);
                if (id != null) {
                    cells.set(cell++, id);
                    distinct.add(id);
                }
            }
            if (result == null || result.isEmpty()) {
                String id = fieldAsId(recipe, "result");
                if (id != null) {
                    ItemStack stack = stackOfId(id);
                    if (stack != null) {
                        result = stack;
                    }
                }
            }
        }
        if (distinct.isEmpty() && (result == null || result.isEmpty())) {
            // 什么都读不出来：宁可报「打不开」，也不要开一个空界面让人以为坏了
            return null;
        }
        return build(recipe.getId(), type, false, cells, distinct, result, 0D,
                RecipeDraft.DEFAULT_COOKING_TIME);
    }

private Found readMachine(Recipe<?> recipe, RecipeType type) {
        Object machine = null;
        if (recipe.getClass().getName().endsWith("IMachineRecipe")) {
            machine = recipe;
        } else {
            for (java.lang.reflect.Field f : recipe.getClass().getDeclaredFields()) {
                if (f.getType().getName().endsWith("IMachineRecipe")) {
                    try {
                        f.setAccessible(true);
                        machine = f.get(recipe);
                    } catch (Exception ignored) {
                        // 拿不到就继续找
                    }
                    if (machine != null) {
                        break;
                    }
                }
            }
        }
        if (machine == null) {
            return null;
        }

        List<String> cells = blankCells();
        Set<String> distinct = new LinkedHashSet<>();
        int cell = 0;
        if (call(machine, "getInputItems") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof ItemStack stack && !stack.isEmpty() && cell < 9) {
                    String id = stack.getCount() > 1
                            ? stack.getCount() + "x " + idOf(stack) : idOf(stack);
                    cells.set(cell++, id);
                    distinct.add(id);
                }
            }
        }
        if (call(machine, "getInputFluids") instanceof List<?> fluids) {
            for (Object fluid : fluids) {
                if (cell >= 9) {
                    break;
                }
                String id = fluidCell(fluid);
                if (id != null) {
                    cells.set(cell++, id);
                    distinct.add(id);
                }
            }
        }
        if (distinct.isEmpty()) {
            return null;
        }

        ItemStack output = ItemStack.EMPTY;
        if (call(machine, "getOutputItems") instanceof List<?> outs && !outs.isEmpty()
                && outs.get(0) instanceof ItemStack first) {
            output = first.copy();
        }
        return build(recipe.getId(), type, false, cells, distinct, output, 0D,
                RecipeDraft.DEFAULT_COOKING_TIME);
    }

private static String fluidCell(Object fluidStack) {
        if (fluidStack == null) {
            return null;
        }
        Object fluid = call(fluidStack, "getFluid");
        Object amount = call(fluidStack, "getAmount");
        if (fluid == null) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.FLUIDS.getKey(
                (net.minecraft.world.level.material.Fluid) fluid);
        if (key == null) {
            return null;
        }
        int mb = amount instanceof Integer i ? i : 1000;
        return "fluid:" + key + ":" + mb;
    }

public Found readJson(Level level, ResourceLocation id, RecipeType type) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return null;
        }
        ResourceLocation file = new ResourceLocation(id.getNamespace(),
                "recipes/" + id.getPath() + ".json");
        try {
            var resource = serverLevel.getServer().getResourceManager().getResource(file);
            if (resource.isEmpty()) {
                return null;
            }
            com.google.gson.JsonObject json;
            try (var reader = resource.get().openAsReader()) {
                json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
            List<String> cells = blankCells();
            Set<String> distinct = new LinkedHashSet<>();
            List<String> ingredients = jsonList(json, "ingredients", "ingredient", "inputs", "input");
            for (int i = 0; i < 9 && i < ingredients.size(); i++) {
                String value = ingredients.get(i);
                if (value != null) {
                    cells.set(i, value);
                    distinct.add(value);
                }
            }
            List<String> results = jsonList(json, "results", "result", "outputs", "output");
            ItemStack output = ItemStack.EMPTY;
            if (!results.isEmpty() && results.get(0) != null) {
                String first = results.get(0);
                if (first.startsWith("fluid:")) {
                    // 纯流体产物：本模组产物格只放物品，跳过
                    results.remove(0);
                } else {
                    String plain = first.contains("x ") ? first.substring(first.indexOf("x ") + 2) : first;
                    ItemStack stack = stackOfId(plain);
                    if (stack != null) {
                        output = stack;
                        if (first.contains("x ")) {
                            try {
                                output.setCount(Integer.parseInt(first.substring(0, first.indexOf("x ")).trim()));
                            } catch (NumberFormatException ignored) {
                                // 数量解析失败就按 1
                            }
                        }
                    }
                }
            }
            if (distinct.isEmpty() && output.isEmpty()) {
                return null;
            }
            return build(id, type, false, cells, distinct, output, 0D,
                    RecipeDraft.DEFAULT_COOKING_TIME);
        } catch (Exception e) {
            return null;
        }
    }

private static List<String> jsonList(com.google.gson.JsonObject json, String... keys) {
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            if (!json.has(key)) {
                continue;
            }
            com.google.gson.JsonElement element = json.get(key);
            if (element.isJsonArray()) {
                for (com.google.gson.JsonElement child : element.getAsJsonArray()) {
                    String value = jsonIngredient(child);
                    if (value != null) {
                        out.add(value);
                    }
                }
            } else {
                String value = jsonIngredient(element);
                if (value != null) {
                    out.add(value);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return out;
    }

private static String jsonIngredient(com.google.gson.JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (!element.isJsonObject()) {
            return null;
        }
        com.google.gson.JsonObject obj = element.getAsJsonObject();
        if (obj.has("item")) {
            String id = obj.get("item").getAsString();
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
            return count > 1 ? count + "x " + id : id;
        }
        if (obj.has("tag")) {
            return "#" + obj.get("tag").getAsString();
        }
        if (obj.has("fluid")) {
            int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 1000;
            return "fluid:" + obj.get("fluid").getAsString() + ":" + amount;
        }
        return null;
    }

private static String fieldAsId(Object target, String name) {
        Object value = field(target, name);
        if (value instanceof Ingredient ingredient) {
            return describe(ingredient);
        }
        if (value instanceof ItemStack stack) {
            return stack.isEmpty() ? null : idOf(stack);
        }
        if (value instanceof net.minecraft.world.level.ItemLike itemLike) {
            return idOf(new ItemStack(itemLike));
        }
        return null;
    }

private static ItemStack stackOfId(String id) {
        net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(
                new ResourceLocation(id));
        return item == null ? null : new ItemStack(item);
    }
}
