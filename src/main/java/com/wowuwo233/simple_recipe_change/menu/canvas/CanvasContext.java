package com.wowuwo233.simple_recipe_change.menu.canvas;

import com.wowuwo233.simple_recipe_change.kubejs.Operation;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;

/**
 * 画布布局时需要知道的上下文。
 *
 * @param type          当前配方类型
 * @param operation     当前操作（添加 / 修改 / 删除）——合成类的产物格位置跟它有关
 * @param extraOutputs  要摆几个额外产物格
 */
public record CanvasContext(RecipeType type, Operation operation, int extraOutputs) {

    /** 合成类「添加」模式下材料在左、产物在右；「修改」模式反过来 */
    public boolean adding() {
        return operation == Operation.ADD;
    }
}
