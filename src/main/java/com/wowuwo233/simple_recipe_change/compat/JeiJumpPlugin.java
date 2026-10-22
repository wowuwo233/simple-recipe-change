package com.wowuwo233.simple_recipe_change.compat;

import com.wowuwo233.simple_recipe_change.SimpleRecipeChangeMod;
import com.wowuwo233.simple_recipe_change.network.ModNetwork;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

/**
 * 在 JEI 的每张配方页右上角加一个「打开编辑器」按钮。
 *
 * <p>用 JEI 的 {@code addRecipeButtonFactory}：它对<b>每一个</b>配方布局都会要一个按钮控制器，
 * 所以不管是原版、农夫乐事、机械动力还是别的模组，只要是 {@link Recipe} 且有 ID，
 * 点一下就能带着这条配方跳进编辑器。
 *
 * <p>整个类只在 JEI 装了的时候才会被加载（{@code @JeiPlugin} 由 JEI 自己扫描），
 * 没装 JEI 的整合包不受影响，也不会因为缺类而崩。
 */
@JeiPlugin
public class JeiJumpPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(SimpleRecipeChangeMod.MODID, "jump_button");
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {
        IDrawable icon = registration.getJeiHelpers().getGuiHelper()
                .createDrawableItemStack(new ItemStack(Items.WRITABLE_BOOK));

        registration.addRecipeButtonFactory(new IRecipeButtonControllerFactory() {
            @Override
            public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> layout) {
                return new IIconButtonController() {
                    @Override
                    public void initState(IButtonState state) {
                        state.setIcon(icon);
                        // 没有 ID 的配方（比如某些动态生成的）不显示按钮，免得点了没反应
                        state.setVisible(recipeIdOf(layout.getRecipe()) != null);
                    }

                    @Override
                    public boolean onPress(IJeiUserInput input) {
                        String id = recipeIdOf(layout.getRecipe());
                        if (id == null) {
                            return false;
                        }
                        ModNetwork.requestOpenEditorWithRecipe(id);
                        return true;
                    }

                    @Override
                    public void getTooltips(ITooltipBuilder tooltip) {
                        tooltip.add(Component.literal("用「简易配方修改」打开"));
                        String id = recipeIdOf(layout.getRecipe());
                        if (id != null) {
                            tooltip.add(Component.literal(id).withStyle(ChatFormatting.GRAY));
                        }
                    }
                };
            }
        });
    }

    private static String recipeIdOf(Object recipe) {
        if (recipe instanceof Recipe<?> r && r.getId() != null) {
            return r.getId().toString();
        }
        return null;
    }
}
