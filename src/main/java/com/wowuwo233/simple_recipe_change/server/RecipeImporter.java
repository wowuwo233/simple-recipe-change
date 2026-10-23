package com.wowuwo233.simple_recipe_change.server;

import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

/**
 * 单个模组的配方导入器。
 *
 * <p>「按配方 ID 打开编辑器」和「JEI 跳转」都走这里：每个模组把「怎么从它的配方对象里
 * 读出材料/产物」封装成一个实现，{@link VanillaRecipeLookup} 只负责原版那几种。
 *
 * <p>以前这些逻辑是一串 if-else 堆在 {@code byId} 里，热力膨胀那种要试三层
 * （IMachineRecipe → 标准接口 → 数据包 JSON）的分支把主流程淹了。
 */
interface RecipeImporter {

    /** 这个导入器管不管这种配方类型 */
    boolean supports(RecipeType type);

    /**
     * 读出来；读不出来返回 {@code null}（由调用方决定报错还是换下一个导入器）。
     *
     * @param id 配方 ID，读数据包 JSON 时要用
     */
    VanillaRecipeLookup.Found read(Level level, Recipe<?> recipe, RecipeType type,
                                   RegistryAccess access, ResourceLocation id);
}
