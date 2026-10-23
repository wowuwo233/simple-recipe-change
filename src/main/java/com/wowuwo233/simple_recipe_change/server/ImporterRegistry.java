package com.wowuwo233.simple_recipe_change.server;

import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 导入器注册表。
 *
 * <p>加一个模组 = 写一个 {@link RecipeImporter} 实现 + 在 {@link #IMPORTERS} 加一行，
 * 不用再改 {@code byId} 的主流程。
 */
final class ImporterRegistry {

    private static final List<RecipeImporter> IMPORTERS = List.of(
            new ThermalImporter()
    );

    static VanillaRecipeLookup.Found read(Level level, Recipe<?> recipe, RecipeType type,
                                          RegistryAccess access, ResourceLocation id) {
        for (RecipeImporter importer : IMPORTERS) {
            if (!importer.supports(type)) {
                continue;
            }
            VanillaRecipeLookup.Found found = importer.read(level, recipe, type, access, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private ImporterRegistry() {
    }
}
