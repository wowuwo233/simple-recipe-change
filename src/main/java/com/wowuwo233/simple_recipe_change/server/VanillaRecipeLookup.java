package com.wowuwo233.simple_recipe_change.server;

import com.wowuwo233.simple_recipe_change.kubejs.CreateMachine;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeDraft;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 按产物反查已有的配方（原版或其它模组添加的），供「修改配方 / 删除配方」模式展示与回填。
 *
 * <p>按当前选择的配方类型去对应的分类里找：合成类找 {@link CraftingRecipe}，
 * 烧炼类按熔炉/高炉/烟熏炉各自的 {@code RecipeType} 找，锻造类找 {@link SmithingTransformRecipe}。
 *
 * <p>材料尽量保留标签写法：如果一个材料的所有候选物品恰好等于某个物品标签的内容，
 * 就输出 {@code #minecraft:planks}，而不是退化成某一种具体木材。
 */
public final class VanillaRecipeLookup {

    /**
     * 查到的配方快照。
     *
     * @param cells 固定 9 项，含义与 {@link RecipeDraft#cells()} 一致
     */
    public record Found(
            String recipeId,
            String outputItem,
            String outputName,
            int outputCount,
            boolean shapeless,
            RecipeType suggestedType,
            List<String> cells,
            List<String> ingredientIds,
            String summary,
            double xp,
            int cookingTime,
            /** 副产物（切菜板 / Create 副产物），没有就是空表 */
            List<RecipeDraft.ExtraOutput> extras,
            /** Create 处理配方对应的机器 */
            CreateMachine machine,
            /** Create 处理配方的热度要求（heated / superheated / 空） */
            String heat
    ) {
        public Found {
            extras = extras == null ? List.of() : List.copyOf(extras);
            if (machine == null) {
                machine = CreateMachine.CRUSHING;
            }
            if (heat == null) {
                heat = "";
            }
        }
    }

    private VanillaRecipeLookup() {
    }

    /** 找不到返回 {@code null} */
    public static Found find(Level level, ItemStack output, RecipeType wanted) {
        if (level == null || output == null || output.isEmpty()) {
            return null;
        }
        RegistryAccess access = level.registryAccess();
        List<Recipe<?>> candidates = new ArrayList<>();

        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            if (!belongsTo(recipe, wanted)) {
                continue;
            }
            ItemStack result = recipe.getResultItem(access);
            if (!result.isEmpty() && ItemStack.isSameItem(result, output)) {
                candidates.add(recipe);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        // 结果稳定：按 ID 排序取第一个
        candidates.sort(Comparator.comparing(r -> r.getId().toString()));
        Recipe<?> chosen = candidates.get(0);

        return switch (wanted.category()) {
            case CRAFTING -> fromCrafting(chosen, access);
            case COOKING -> fromCooking((AbstractCookingRecipe) chosen, access, wanted);
            case SMITHING -> fromSmithing(chosen, access);
            // 农夫乐事 / 机械动力的配方类在它们自己的 mod 里，本模组不直接依赖，暂不支持反查
            case FARMERS, CREATE, THERMAL -> null;
        };
    }

    /** 这个配方是否属于所选类型 */
    private static boolean belongsTo(Recipe<?> recipe, RecipeType wanted) {
        return switch (wanted.category()) {
            case CRAFTING -> recipe instanceof CraftingRecipe;
            case COOKING -> recipe instanceof AbstractCookingRecipe
                    && recipe.getType() == mcTypeOf(wanted);
            case SMITHING -> recipe instanceof SmithingTransformRecipe;
            // 同上：不依赖农夫乐事 / 机械动力的类
            case FARMERS, CREATE, THERMAL -> false;
        };
    }

    private static net.minecraft.world.item.crafting.RecipeType<?> mcTypeOf(RecipeType wanted) {
        return switch (wanted) {
            case FURNACE -> net.minecraft.world.item.crafting.RecipeType.SMELTING;
            case BLAST_FURNACE -> net.minecraft.world.item.crafting.RecipeType.BLASTING;
            case SMOKER -> net.minecraft.world.item.crafting.RecipeType.SMOKING;
            case CAMPFIRE -> net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ 各类转换

    private static Found fromCrafting(Recipe<?> recipe, RegistryAccess access) {
        if (recipe instanceof ShapedRecipe shaped) {
            return fromShaped(shaped, access);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return fromShapeless(shapeless, access);
        }
        return null;
    }

    private static Found fromShaped(ShapedRecipe recipe, RegistryAccess access) {
        int width = Math.min(recipe.getWidth(), 3);
        int height = Math.min(recipe.getHeight(), 3);
        List<Ingredient> ingredients = recipe.getIngredients();

        List<String> cells = blankCells();
        Set<String> distinct = new LinkedHashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = x + y * recipe.getWidth();
                if (index >= ingredients.size()) {
                    continue;
                }
                String id = describe(ingredients.get(index));
                cells.set(x + y * 3, id);
                if (id != null) {
                    distinct.add(id);
                }
            }
        }

        RecipeType type = (recipe.getWidth() <= 2 && recipe.getHeight() <= 2)
                ? RecipeType.INVENTORY : RecipeType.CRAFTING_TABLE;
        return build(recipe.getId(), type, false, cells, distinct,
                recipe.getResultItem(access), 0D, RecipeDraft.DEFAULT_COOKING_TIME);
    }

    private static Found fromShapeless(ShapelessRecipe recipe, RegistryAccess access) {
        List<Ingredient> ingredients = recipe.getIngredients();
        List<String> cells = blankCells();
        Set<String> distinct = new LinkedHashSet<>();

        for (int i = 0; i < ingredients.size() && i < 9; i++) {
            String id = describe(ingredients.get(i));
            cells.set(i, id);
            if (id != null) {
                distinct.add(id);
            }
        }

        RecipeType type = ingredients.size() <= 4 ? RecipeType.INVENTORY : RecipeType.CRAFTING_TABLE;
        return build(recipe.getId(), type, true, cells, distinct,
                recipe.getResultItem(access), 0D, RecipeDraft.DEFAULT_COOKING_TIME);
    }

    private static Found fromCooking(AbstractCookingRecipe recipe, RegistryAccess access, RecipeType wanted) {
        List<Ingredient> ingredients = recipe.getIngredients();
        List<String> cells = blankCells();
        Set<String> distinct = new LinkedHashSet<>();

        if (!ingredients.isEmpty()) {
            String id = describe(ingredients.get(0));
            cells.set(0, id);
            if (id != null) {
                distinct.add(id);
            }
        }

        return build(recipe.getId(), wanted, false, cells, distinct,
                recipe.getResultItem(access), recipe.getExperience(), recipe.getCookingTime());
    }

    private static Found fromSmithing(Recipe<?> recipe, RegistryAccess access) {
        List<Ingredient> ingredients = recipe.getIngredients();
        List<String> cells = blankCells();
        Set<String> distinct = new LinkedHashSet<>();

        // 顺序与 SmithingTransformRecipe 一致：模板、基础物品、升级物品
        for (int i = 0; i < 3 && i < ingredients.size(); i++) {
            String id = describe(ingredients.get(i));
            cells.set(i, id);
            if (id != null) {
                distinct.add(id);
            }
        }

        return build(recipe.getId(), RecipeType.SMITHING, false, cells, distinct,
                recipe.getResultItem(access), 0D, RecipeDraft.DEFAULT_COOKING_TIME);
    }

    private static Found build(ResourceLocation recipeId, RecipeType type, boolean shapeless,
                               List<String> cells, Set<String> distinct, ItemStack result,
                               double xp, int cookingTime) {
        return build(recipeId, type, shapeless, cells, distinct, result, xp, cookingTime,
                List.of(), CreateMachine.CRUSHING, "");
    }

    private static Found build(ResourceLocation recipeId, RecipeType type, boolean shapeless,
                               List<String> cells, Set<String> distinct, ItemStack result,
                               double xp, int cookingTime,
                               List<RecipeDraft.ExtraOutput> extras, CreateMachine machine,
                               String heat) {
        String outputItem = result == null || result.isEmpty() ? "" : idOf(result);
        String outputName = result == null || result.isEmpty() ? "" : result.getHoverName().getString();
        int count = result == null || result.isEmpty() ? 1 : result.getCount();

        // 材料本身已经回填到输入槽了，这里只给类型信息，不重复列出材料名
        StringBuilder sb = new StringBuilder();
        sb.append(type.label());
        if (type.isCrafting()) {
            sb.append(" · ").append(shapeless ? "无序合成" : "有序合成");
        }
        if (type.hasCookingSettings()) {
            sb.append(" · 经验 ").append(trimNumber(xp))
              .append(" · ").append(cookingTime).append(" tick");
        }
        if (!distinct.isEmpty()) {
            sb.append(" · 共 ").append(distinct.size()).append(" 种材料");
        }

        return new Found(recipeId.toString(), outputItem, outputName, count, shapeless, type,
                cells, new ArrayList<>(distinct), sb.toString(), xp, cookingTime, extras, machine,
                heat);
    }

    private static String trimNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static List<String> blankCells() {
        List<String> cells = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            cells.add(null);
        }
        return cells;
    }

    /** 把一个材料描述成 KubeJS 可用的写法：优先 {@code #tag}，否则具体物品 ID */
    public static String describe(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        ItemStack[] stacks = ingredient.getItems();
        if (stacks.length == 0) {
            return null;
        }
        if (stacks.length > 1) {
            String tag = findExactTag(stacks);
            if (tag != null) {
                return tag;
            }
        }
        return idOf(stacks[0]);
    }

    /**
     * 找一个「内容恰好等于这组物品」的标签。
     *
     * <p>必须完全相等——换成一个更大的标签（比如 {@code minecraft:items}）会改变配方语义，
     * 让它接受原本不该接受的物品。
     */
    private static String findExactTag(ItemStack[] stacks) {
        Set<TagKey<Item>> common = null;
        for (ItemStack stack : stacks) {
            Set<TagKey<Item>> tags = new HashSet<>();
            stack.getTags().forEach(tags::add);
            if (common == null) {
                common = tags;
            } else {
                common.retainAll(tags);
            }
            if (common.isEmpty()) {
                return null;
            }
        }
        if (common == null || common.isEmpty()) {
            return null;
        }

        List<TagKey<Item>> sorted = new ArrayList<>(common);
        sorted.sort(Comparator.comparing(tag -> tag.location().toString()));

        for (TagKey<Item> tag : sorted) {
            Optional<? extends Iterable<Holder<Item>>> holders = BuiltInRegistries.ITEM.getTag(tag);
            if (holders.isEmpty()) {
                continue;
            }
            Set<Item> tagItems = new HashSet<>();
            for (Holder<Item> holder : holders.get()) {
                tagItems.add(holder.value());
            }
            if (tagItems.size() == stacks.length) {
                return "#" + tag.location();
            }
        }
        return null;
    }

    private static String idOf(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? null : key.toString();
    }

    // ================================================================== 按配方 ID 反查

    /**
     * 按配方 ID 查一条配方并转成编辑器能用的快照。
     *
     * <p>这是「搜索框输入配方 ID 直接跳过去」的入口。它对<b>所有模组</b>的配方一视同仁：
     * 先按 ID 从 {@code RecipeManager} 里取出来，再看它的配方类型能不能被编辑器表达。
     * 能表达的就按类型抽数据；不能表达的返回 {@code null}，由调用方给出「类型暂不支持」的提示，
     * 而不是静默什么都不做。
     *
     * <p>大部分模组的配方类是私有的，所以数据提取走反射（{@link #call}），
     * 拿不到就当没有——不会因为某个模组字段改名就崩。
     */
    public static Found byId(Level level, ResourceLocation id) {
        if (level == null || id == null) {
            return null;
        }
        Optional<? extends Recipe<?>> opt = level.getRecipeManager().byKey(id);
        if (opt.isEmpty()) {
            return null;
        }
        Recipe<?> recipe = opt.get();
        RecipeType type = typeOf(recipe);
        if (type == null) {
            return null;
        }
        RegistryAccess access = level.registryAccess();
        try {
            if (type == RecipeType.CREATE_SEQUENCED) {
                return fromSequenced(recipe, access);
            }
            if (type == RecipeType.FARMERS_CUTTING) {
                return fromCutting(recipe, access);
            }
            if (type == RecipeType.FARMERS_COOKING) {
                return fromCookingPot(recipe, access);
            }
            if (type.isCreate()) {
                return fromCreateProcessing(recipe, access, type);
            }
            return switch (type.category()) {
                case CRAFTING -> fromCrafting(recipe, access);
                case COOKING -> fromCooking((AbstractCookingRecipe) recipe, access, type);
                case SMITHING -> fromSmithing(recipe, access);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 配方类型 → 编辑器类型。
     *
     * <p>只按 {@code recipe.getType()} 的注册名判断，不认具体类：
     * 这样任何模组只要用的是这些类型，都能跳过来。
     */
    public static RecipeType typeOf(Recipe<?> recipe) {
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (key == null) {
            return null;
        }
        String ns = key.getNamespace();
        String path = key.getPath();
        if ("minecraft".equals(ns)) {
            return switch (path) {
                case "crafting" -> RecipeType.CRAFTING_TABLE;
                case "smelting" -> RecipeType.FURNACE;
                case "blasting" -> RecipeType.BLAST_FURNACE;
                case "smoking" -> RecipeType.SMOKER;
                case "campfire_cooking" -> RecipeType.CAMPFIRE;
                case "smithing" -> RecipeType.SMITHING;
                default -> null;
            };
        }
        if ("farmersdelight".equals(ns)) {
            return switch (path) {
                case "cooking" -> RecipeType.FARMERS_COOKING;
                case "cutting" -> RecipeType.FARMERS_CUTTING;
                default -> null;
            };
        }
        if ("create".equals(ns)) {
            if ("sequenced_assembly".equals(path)) {
                return RecipeType.CREATE_SEQUENCED;
            }
            return CreateMachine.byId(path) != null ? RecipeType.CREATE_PROCESSING : null;
        }
        return null;
    }

    /** 配方类型注册名，给「不支持」的提示用 */
    public static String typeIdOf(Recipe<?> recipe) {
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return key == null ? "?" : key.toString();
    }

    /** 农夫乐事 · 切菜板：材料 + 工具 + 若干产物（带概率） */
    private static Found fromCutting(Recipe<?> recipe, RegistryAccess access) {
        List<String> cells = blankCells();
        Set<String> distinct = new LinkedHashSet<>();

        List<Ingredient> ingredients = recipe.getIngredients();
        if (!ingredients.isEmpty()) {
            String id = describe(ingredients.get(0));
            cells.set(0, id);
            if (id != null) {
                distinct.add(id);
            }
        }
        if (call(recipe, "getTool") instanceof Ingredient tool) {
            String id = describe(tool);
            cells.set(1, id);
            if (id != null) {
                distinct.add(id);
            }
        }

        ItemStack main = recipe.getResultItem(access);
        List<RecipeDraft.ExtraOutput> extras = new ArrayList<>();
        if (call(recipe, "getRollableResults") instanceof List<?> rollable) {
            for (int i = 0; i < rollable.size(); i++) {
                Object entry = rollable.get(i);
                if (!(call(entry, "getStack") instanceof ItemStack stack) || stack.isEmpty()) {
                    continue;
                }
                if (i == 0) {
                    if (main == null || main.isEmpty()) {
                        main = stack;
                    }
                    continue;
                }
                double chance = call(entry, "getChance") instanceof Float f ? f : 1D;
                extras.add(new RecipeDraft.ExtraOutput(idOf(stack), stack.getCount(), chance));
            }
        }
        return build(recipe.getId(), RecipeType.FARMERS_CUTTING, false, cells, distinct,
                main, 0D, RecipeDraft.DEFAULT_COOKING_TIME, extras, CreateMachine.CRUSHING, "");
    }

    /** 农夫乐事 · 烹饪锅：6 个材料 + 容器 + 产物 */
    private static Found fromCookingPot(Recipe<?> recipe, RegistryAccess access) {
        List<String> cells = blankCells();
        Set<String> distinct = new LinkedHashSet<>();

        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 0; i < 6 && i < ingredients.size(); i++) {
            String id = describe(ingredients.get(i));
            cells.set(i, id);
            if (id != null) {
                distinct.add(id);
            }
        }
        if (call(recipe, "getOutputContainer") instanceof ItemStack container
                && !container.isEmpty()) {
            String id = idOf(container);
            cells.set(6, id);
            if (id != null) {
                distinct.add(id);
            }
        }

        double xp = call(recipe, "getExperience") instanceof Float f ? f : 0D;
        int time = call(recipe, "getCookTime") instanceof Integer i ? i : 200;
        return build(recipe.getId(), RecipeType.FARMERS_COOKING, false, cells, distinct,
                recipe.getResultItem(access), xp, time);
    }

    /** 机械动力 · 处理配方：材料 + 产物（带概率）+ 时间 + 热度 */
    private static Found fromCreateProcessing(Recipe<?> recipe, RegistryAccess access,
                                              RecipeType type) {
        List<String> cells = blankCells();
        Set<String> distinct = new LinkedHashSet<>();

        List<Ingredient> ingredients = recipe.getIngredients();
        for (int i = 0; i < 9 && i < ingredients.size(); i++) {
            String id = describe(ingredients.get(i));
            cells.set(i, id);
            if (id != null) {
                distinct.add(id);
            }
        }

        ItemStack main = recipe.getResultItem(access);
        List<RecipeDraft.ExtraOutput> extras = new ArrayList<>();
        if (call(recipe, "getRollableResults") instanceof List<?> rollable) {
            for (int i = 0; i < rollable.size(); i++) {
                Object entry = rollable.get(i);
                if (!(call(entry, "getStack") instanceof ItemStack stack) || stack.isEmpty()) {
                    continue;
                }
                if (i == 0) {
                    if (main == null || main.isEmpty()) {
                        main = stack;
                    }
                    continue;
                }
                double chance = call(entry, "getChance") instanceof Float f ? f : 1D;
                extras.add(new RecipeDraft.ExtraOutput(idOf(stack), stack.getCount(), chance));
            }
        }

        int time = call(recipe, "getProcessingDuration") instanceof Integer i
                ? i : RecipeDraft.DEFAULT_COOKING_TIME;
        String heat = "";
        if (call(recipe, "getRequiredHeat") instanceof Enum<?> required) {
            heat = switch (required.name()) {
                case "HEATED" -> "heated";
                case "SUPERHEATED" -> "superheated";
                default -> "";
            };
        }

        CreateMachine machine = CreateMachine.byId(
                BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).getPath());
        return build(recipe.getId(), type, false, cells, distinct, main, 0D, time, extras,
                machine == null ? CreateMachine.CRUSHING : machine, heat);
    }

    /** 反射调一个无参方法；拿不到就返回 {@code null}（模组改名/混淆都不至于崩） */
    private static Object call(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    /** 反射读公有字段（Create 的 {@code resultPool} 是 public field，没有 getter） */
    private static Object field(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field f = target.getClass().getField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static String typePath(Object recipe) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        ResourceLocation key = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
        return key == null ? null : key.getPath();
    }

    /**
     * 机械动力 · 序列组装。
     *
     * <p>编辑器那边的模型是：{@code cells[0]} 输入、{@code cells[1]} 过渡物品、
     * {@code cells[2..7]} 每步额外材料，{@code heat} 存步骤类型（逗号分隔，项数即步骤数），
     * {@code cookingTime} 存循环次数，{@code xp} 存成功率。
     *
     * <p>Create 的配方类在模组里，这里全部走反射：{@code getIngredient / getTransitionalItem /
     * getLoops / getSequence}，结果池读公有字段 {@code resultPool}。
     */
    private static Found fromSequenced(Recipe<?> recipe, RegistryAccess access) {
        List<String> cells = blankCells();
        Set<String> distinct = new LinkedHashSet<>();

        String input = call(recipe, "getIngredient") instanceof Ingredient ing
                ? describe(ing) : null;
        cells.set(0, input);
        if (input != null) {
            distinct.add(input);
        }
        String transitional = call(recipe, "getTransitionalItem") instanceof ItemStack stack
                ? idOf(stack) : null;
        cells.set(1, transitional);
        if (transitional != null) {
            distinct.add(transitional);
        }

        int loops = call(recipe, "getLoops") instanceof Integer i ? i : 4;

        List<String> kinds = new ArrayList<>();
        int skipped = 0;
        if (call(recipe, "getSequence") instanceof List<?> sequence) {
            for (Object step : sequence) {
                Object inner = call(step, "getRecipe");
                String path = typePath(inner);
                if (kinds.size() >= 6) {
                    skipped++;
                    continue;
                }
                switch (path == null ? "" : path) {
                    case "deploying" -> {
                        kinds.add("deploying");
                        String extra = null;
                        if (inner instanceof Recipe<?> stepRecipe) {
                            List<Ingredient> ingredients = stepRecipe.getIngredients();
                            if (ingredients.size() > 1) {
                                extra = describe(ingredients.get(1));
                            }
                        }
                        cells.set(2 + kinds.size() - 1, extra);
                        if (extra != null) {
                            distinct.add(extra);
                        }
                    }
                    case "pressing" -> kinds.add("pressing");
                    case "cutting" -> kinds.add("cutting");
                    // 灌装要流体，本模组不处理流体
                    default -> skipped++;
                }
            }
        }
        if (kinds.isEmpty()) {
            return null;
        }

        // 结果池：第一项是主产物，其余是废料。本模组只支持「主产物 + 1 个失败产物」，
        // 废料多于一个时取权重最大的那个，并在摘要里说明。
        ItemStack main = recipe.getResultItem(access);
        List<RecipeDraft.ExtraOutput> extras = new ArrayList<>();
        double success = 100D;
        int scrapCount = 0;
        if (field(recipe, "resultPool") instanceof List<?> pool && !pool.isEmpty()) {
            Object first = pool.get(0);
            if (call(first, "getStack") instanceof ItemStack s && !s.isEmpty()) {
                main = s;
            }
            if (pool.size() > 1) {
                double firstWeight = call(first, "getChance") instanceof Float f ? f : 1D;
                double total = firstWeight;
                Object best = null;
                double bestWeight = -1D;
                for (int i = 1; i < pool.size(); i++) {
                    double w = call(pool.get(i), "getChance") instanceof Float f ? f : 1D;
                    total += w;
                    if (w > bestWeight) {
                        bestWeight = w;
                        best = pool.get(i);
                    }
                }
                scrapCount = pool.size() - 1;
                if (total > 0D) {
                    success = Math.max(1D, Math.min(99D,
                            Math.round(firstWeight / total * 100D)));
                }
                if (best != null && call(best, "getStack") instanceof ItemStack scrap
                        && !scrap.isEmpty()) {
                    extras.add(new RecipeDraft.ExtraOutput(idOf(scrap), scrap.getCount(), 1D));
                }
            }
        }

        Found found = build(recipe.getId(), RecipeType.CREATE_SEQUENCED, false, cells, distinct,
                main, success, loops, extras, CreateMachine.CRUSHING, String.join(",", kinds));

        StringBuilder summary = new StringBuilder(found.summary())
                .append(" · ").append(kinds.size()).append(" 步 · 循环 ").append(loops);
        if (scrapCount > 1) {
            summary.append(" · 原配方有 ").append(scrapCount)
                    .append(" 个废料，这里只保留权重最大的 1 个");
        }
        if (skipped > 0) {
            summary.append(" · 跳过 ").append(skipped).append(" 个灌装/超出 6 步的步骤");
        }
        return withSummary(found, summary.toString());
    }

    private static Found withSummary(Found f, String summary) {
        return new Found(f.recipeId(), f.outputItem(), f.outputName(), f.outputCount(),
                f.shapeless(), f.suggestedType(), f.cells(), f.ingredientIds(), summary,
                f.xp(), f.cookingTime(), f.extras(), f.machine(), f.heat());
    }
}
