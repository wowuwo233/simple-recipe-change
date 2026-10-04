package com.wowuwo233.simple_recipe_change.menu;

import com.wowuwo233.simple_recipe_change.SimpleRecipeChangeMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 配方编辑菜单的注册表。 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, SimpleRecipeChangeMod.MODID);

    public static final RegistryObject<MenuType<RecipeEditorMenu>> RECIPE_EDITOR =
            MENUS.register("recipe_editor", () -> IForgeMenuType.create(
                    (windowId, inventory, data) -> new RecipeEditorMenu(windowId, inventory)));

    private ModMenus() {
    }
}
