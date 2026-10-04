package com.wowuwo233.simple_recipe_change.client;

import com.wowuwo233.simple_recipe_change.SimpleRecipeChangeMod;
import com.wowuwo233.simple_recipe_change.menu.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 客户端注册：按键绑定与菜单界面。 */
@Mod.EventBusSubscriber(modid = SimpleRecipeChangeMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyMappings.OPEN_EDITOR);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(
                ModMenus.RECIPE_EDITOR.get(), RecipeEditorScreen::new));
    }
}
