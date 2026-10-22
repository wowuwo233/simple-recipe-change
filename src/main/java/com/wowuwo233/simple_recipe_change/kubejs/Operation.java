package com.wowuwo233.simple_recipe_change.kubejs;

/**
 * 编辑器要执行的操作。三者互相独立，界面上是三个分开的按钮，不做循环合并。
 */
public enum Operation {
    /** 新增一个配方：从零设计一个新配方，不需要反查原版配方 */
    ADD("添加配方"),

    /**
     * 修改已有配方。
     *
     * <p>原版配方无法原地修改，所以生成的内容是「先删掉原配方，再写入修改后的版本」，
     * 两条语句放在同一个托管块里，保证一起被替换。
     */
    MODIFY("修改配方"),

    /** 删除已有配方：按产物或按配方 ID 精确删除 */
    REMOVE("删除配方");

    private final String label;

    Operation(String label) {
        this.label = label;
    }

    /** 中文按钮名 */
    public String label() {
        return label;
    }

    /** 是否需要在输出格放物品并据此查找原版配方 */
    public boolean needsLookup() {
        return this != ADD;
    }

    public static Operation byOrdinal(int ordinal) {
        Operation[] all = values();
        return (ordinal >= 0 && ordinal < all.length) ? all[ordinal] : ADD;
    }
}
