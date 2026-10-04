package com.wowuwo233.simple_recipe_change.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** 按键绑定。默认 G 键打开配方编辑器。 */
public final class KeyMappings {

    public static final String CATEGORY = "key.categories.simple_recipe_change";

    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "key.simple_recipe_change.open_editor",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    private KeyMappings() {
    }
}
