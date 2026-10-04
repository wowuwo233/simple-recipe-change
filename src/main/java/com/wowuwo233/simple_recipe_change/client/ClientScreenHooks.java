package com.wowuwo233.simple_recipe_change.client;

import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndex;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndexEntry;
import com.wowuwo233.simple_recipe_change.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 只在客户端执行的网络回包处理。用 DistExecutor 隔离，专用服务器不会加载本类。 */
public final class ClientScreenHooks {

    /** 「我的配方」列表的本地缓存，由 IndexListPacket 刷新 */
    private static List<RecipeIndexEntry> cachedIndex = List.of();

    private ClientScreenHooks() {
    }

    public static List<RecipeIndexEntry> cachedIndex() {
        return cachedIndex;
    }

    public static void onSaveResult(boolean ok, String message, String path) {
        Minecraft minecraft = Minecraft.getInstance();

        // 界面状态栏空间有限，只放简短提示
        if (minecraft.screen instanceof RecipeEditorScreen screen) {
            screen.setStatus(ok, message);
        }

        // 完整文件路径发到聊天栏，方便查看和复制
        if (minecraft.player != null) {
            Component line;
            if (ok && path != null && !path.isBlank()) {
                line = Component.literal("[简易配方修改] 配方已保存，文件路径：")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(path).withStyle(ChatFormatting.YELLOW));
            } else if (ok) {
                line = Component.literal("[简易配方修改] " + message).withStyle(ChatFormatting.GREEN);
            } else {
                line = Component.literal("[简易配方修改] 操作失败：")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(message == null ? "" : message)
                                .withStyle(ChatFormatting.YELLOW));
            }
            minecraft.player.displayClientMessage(line, false);
        }
    }

    public static void onEditorState(ModNetwork.EditorStatePacket msg) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RecipeEditorScreen screen) {
            screen.applyServerState(msg);
        }
    }

    public static void onIndexList(ModNetwork.IndexListPacket msg) {
        cachedIndex = RecipeIndex.parseJsonList(msg.json());

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RecipeListScreen listScreen) {
            listScreen.refresh();
        }

        if (!msg.ok() && !msg.message().isBlank() && minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("[简易配方修改] " + msg.message()).withStyle(ChatFormatting.RED),
                    false);
        }
    }
}
