package com.wowuwo233.simple_recipe_change.server;

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
            int cookingTime
    ) {
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
            case FARMERS, CREATE -> null;
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
            case FARMERS, CREATE -> false;
        };
    }

    private static net.minecraft.world.item.crafting.RecipeType<?> mcTypeOf(RecipeType wanted) {
        return switch (wanted) {
            case FURNACE -> net.minecraft.world.item.crafting.RecipeType.SMELTING;
            case BLAST_FURNACE -> net.minecraft.world.item.crafting.RecipeType.BLASTING;
            case SMOKER -> net.minecraft.world.item.crafting.RecipeType.SMOKING;
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
                cells, new ArrayList<>(distinct), sb.toString(), xp, cookingTime);
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
}
