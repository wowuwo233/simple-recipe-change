package com.wowuwo233.simple_recipe_change;

import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import net.minecraftforge.fml.ModList;

/**
 * 运行环境检测。
 *
 * <p>本模组只负责把配方写成 KubeJS 脚本，真正让配方生效的是 KubeJS，
 * 所以 KubeJS 在 {@code mods.toml} 里被声明成<b>硬依赖</b>——没装时 Forge 直接拒绝加载并报错，
 * 不会出现「模组能开但配方全不生效」这种让人摸不着头脑的情况。
 *
 * <p>农夫乐事的两种配方类型则依赖另外两个模组（农夫乐事本体 + KubeJSDelight），
 * 没装时就把它们从界面上藏起来，只留原版分组。
 *
 * <p>这里刻意用 {@link ModList} 而不是直接引用那两个模组的类——那样会在没装的整合包里
 * 抛 {@code NoClassDefFoundError}。
 */
public final class ModCompat {

    public static final String KUBEJS = "kubejs";
    public static final String FARMERS_DELIGHT = "farmersdelight";
    public static final String KUBEJS_DELIGHT = "kubejsdelight";

    private ModCompat() {
    }

    public static boolean hasKubeJS() {
        return isLoaded(KUBEJS);
    }

    /** 农夫乐事的配方类型要能用，本体和它的 KubeJS 适配都得装上 */
    public static boolean hasFarmersDelight() {
        return isLoaded(FARMERS_DELIGHT) && isLoaded(KUBEJS_DELIGHT);
    }

    /** 这个分组在当前整合包里能不能选 */
    public static boolean available(RecipeType.Group group) {
        return group != RecipeType.Group.FARMERS_DELIGHT || hasFarmersDelight();
    }

    /** 有没有一个以上分组可用（只有一个时「分组」按钮就没必要点了） */
    public static boolean hasMultipleGroups() {
        return hasFarmersDelight();
    }

    private static boolean isLoaded(String modId) {
        try {
            return ModList.get() != null && ModList.get().isLoaded(modId);
        } catch (Throwable ignored) {
            // 加载早期 ModList 可能还没准备好，按「没装」处理总比崩了强
            return false;
        }
    }
}
