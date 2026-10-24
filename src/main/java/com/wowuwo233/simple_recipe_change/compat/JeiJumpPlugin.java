package com.wowuwo233.simple_recipe_change.compat;

import com.wowuwo233.simple_recipe_change.SimpleRecipeChangeMod;
import com.wowuwo233.simple_recipe_change.client.RecipeEditorScreen;
import com.wowuwo233.simple_recipe_change.network.ModNetwork;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;

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

    /** JEI 运行时，用来读「鼠标下是什么物品」 */
    private static IJeiRuntime runtime;

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    /**
     * 快捷键（默认 V）：把鼠标下（JEI 物品列表或书签栏）的物品放进编辑器第一个空格。
     *
     * <p>由 {@code ClientEvents} 在确认装了 JEI 之后才调用——没装 JEI 时这个类不会加载。
     */
    public static void pickHoveredInto(RecipeEditorScreen screen) {
        if (runtime == null) {
            return;
        }
        ItemStack stack = runtime.getIngredientListOverlay()
                .getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (stack == null || stack.isEmpty()) {
            stack = runtime.getBookmarkOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        }
        if (stack != null && !stack.isEmpty()) {
            screen.fillFirstEmptySlot(stack);
        }
    }

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(SimpleRecipeChangeMod.MODID, "jump_button");
    }

    /**
     * 允许从 JEI 把物品<b>拖</b>进编辑器：输入格直接填物品，产物格交给服务端替换。
     *
     * <p>目标区域按槽位算：前 {@code inputMenuCount()} 个是输入展示槽，
     * 紧跟其后的是产物格；禁用/多余的槽位（比如序列组装里被压印的那几格）不产生目标。
     */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(RecipeEditorScreen.class,
                new IGhostIngredientHandler<RecipeEditorScreen>() {
                    @Override
                    public <I> List<Target<I>> getTargetsTyped(RecipeEditorScreen screen,
                                                               ITypedIngredient<I> ingredient,
                                                               boolean doStart) {
                        List<Target<I>> targets = new ArrayList<>();
                        Object raw = ingredient.getIngredient();
                        boolean isItem = raw instanceof ItemStack stack && !stack.isEmpty();
                        boolean isFluid = raw instanceof FluidStack fluid && !fluid.isEmpty();
                        if (!isItem && !isFluid) {
                            return targets;
                        }
                        List<Slot> slots = screen.getMenu().slots;
                        for (int i = 0; i < slots.size(); i++) {
                            // 物品和流体各有各的可落区域：储罐格只认流体，物品格只认物品
                            boolean ok = isFluid
                                    ? screen.acceptsGhostFluidTarget(i)
                                    : screen.acceptsGhostDrop(i);
                            if (!ok) {
                                continue;
                            }
                            Slot slot = slots.get(i);
                            final int index = i;
                            Rect2i area = new Rect2i(screen.getGuiLeft() + slot.x,
                                    screen.getGuiTop() + slot.y, 16, 16);
                            targets.add(new Target<I>() {
                                @Override
                                public Rect2i getArea() {
                                    return area;
                                }

                                @Override
                                public void accept(I value) {
                                    if (value instanceof ItemStack dropped) {
                                        screen.acceptGhostDrop(index, dropped);
                                    } else if (value instanceof FluidStack fluid) {
                                        screen.acceptGhostFluidDrop(index,
                                                ForgeRegistries.FLUIDS.getKey(fluid.getFluid()).toString(),
                                                fluid.getAmount());
                                    }
                                }
                            });
                        }
                        return targets;
                    }

                    @Override
                    public void onComplete() {
                        // 落下时已经写好了，这里不用收尾
                    }
                });
    }

    @Override
    public void registerAdvanced(IAdvancedRegistration registration) {        IDrawable icon = registration.getJeiHelpers().getGuiHelper()
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
