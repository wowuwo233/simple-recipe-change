package com.wowuwo233.simple_recipe_change.kubejs;

/**
 * 删除配方时的匹配依据，对应 KubeJS 的移除过滤器。
 *
 * <p>KubeJS 文档中的可用过滤器远不止这两种（还有 input / mod / type 等），
 * 这里先实现最常用的两个，后续按 {@code event.remove} 的其余键扩展即可。
 */
public enum RemoveBy {
    /** {@code event.remove({ id: 'minecraft:torch' })} —— 按配方 ID 精确删除 */
    ID("按配方 ID"),
    /** {@code event.remove({ output: 'minecraft:torch' })} —— 删除所有产出该物品的配方 */
    OUTPUT("按产物物品");

    private final String label;

    RemoveBy(String label) {
        this.label = label;
    }

    /** 中文显示名 */
    public String label() {
        return label;
    }

    public RemoveBy next() {
        RemoveBy[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static RemoveBy byOrdinal(int ordinal) {
        RemoveBy[] all = values();
        return (ordinal >= 0 && ordinal < all.length) ? all[ordinal] : ID;
    }
}
