package com.wowuwo233.simple_recipe_change.client;

import com.wowuwo233.simple_recipe_change.SimpleRecipeChangeMod;
import com.wowuwo233.simple_recipe_change.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端运行时事件：监听 G 键。 */
@Mod.EventBusSubscriber(modid = SimpleRecipeChangeMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (KeyMappings.PICK_FROM_JEI.consumeClick()) {
            // 先判 JEI 在不在，再碰插件类——不然没装 JEI 时会因为缺类崩掉
            if (minecraft.screen instanceof RecipeEditorScreen screen
                    && net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
                com.wowuwo233.simple_recipe_change.compat.JeiJumpPlugin.pickHoveredInto(screen);
            }
        }
        while (KeyMappings.OPEN_EDITOR.consumeClick()) {
            // 只有没开任何界面时才请求打开编辑器，否则会顶掉聊天栏等界面
            if (minecraft.screen == null && minecraft.player != null) {
                ModNetwork.requestOpenEditor();
            }
        }
    }
}
