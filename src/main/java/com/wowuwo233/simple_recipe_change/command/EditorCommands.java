package com.wowuwo233.simple_recipe_change.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.wowuwo233.simple_recipe_change.SimpleRecipeChangeMod;
import com.wowuwo233.simple_recipe_change.kubejs.Operation;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndex;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndexEntry;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /simplerecipe status}：报告「我的配方」里存了什么。
 *
 * <p>排查配方冲突时最想知道三件事：一共几条、分别是什么类型、写进了哪个脚本文件。
 * 以前只能翻 {@code config/simple_recipe_change_index.json} 或者进游戏一条条看。
 */
@Mod.EventBusSubscriber(modid = SimpleRecipeChangeMod.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EditorCommands {

    private EditorCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("simplerecipe")
                .then(Commands.literal("status")
                        .executes(EditorCommands::status)));
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<RecipeIndexEntry> entries = RecipeIndex.load();

        source.sendSuccess(() -> Component.literal("简易配方修改 · 索引状态")
                .withStyle(ChatFormatting.GOLD), false);

        var indexPath = RecipeIndex.indexPath();
        boolean exists = Files.exists(indexPath);
        source.sendSuccess(() -> Component.literal("  索引文件：")
                .append(Component.literal(indexPath.toString())
                        .withStyle(exists ? ChatFormatting.GRAY : ChatFormatting.RED))
                .append(Component.literal(exists ? "" : "（不存在，还没有保存过）")
                        .withStyle(ChatFormatting.DARK_GRAY)), false);

        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  条目：0 条")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("  条目：" + entries.size() + " 条")
                .withStyle(ChatFormatting.WHITE), false);

        // 按操作分组
        Map<Operation, Integer> byOperation = new LinkedHashMap<>();
        for (RecipeIndexEntry entry : entries) {
            byOperation.merge(entry.operation(), 1, Integer::sum);
        }
        StringBuilder ops = new StringBuilder();
        byOperation.forEach((operation, count) ->
                ops.append(operation.label()).append(' ').append(count).append(" 条  "));
        source.sendSuccess(() -> Component.literal("  操作：" + ops.toString().trim())
                .withStyle(ChatFormatting.GRAY), false);

        // 按类型分组
        Map<RecipeType, Integer> byType = new LinkedHashMap<>();
        for (RecipeIndexEntry entry : entries) {
            byType.merge(entry.type(), 1, Integer::sum);
        }
        byType.forEach((type, count) -> source.sendSuccess(
                () -> Component.literal("    · " + type.label() + "：" + count + " 条")
                        .withStyle(ChatFormatting.GRAY), false));

        // 按文件分组：写入时是按文件合并的，这里能看出分布
        Map<String, Integer> byFile = new LinkedHashMap<>();
        for (RecipeIndexEntry entry : entries) {
            byFile.merge(entry.fileName(), 1, Integer::sum);
        }
        byFile.forEach((file, count) -> source.sendSuccess(
                () -> Component.literal("  文件 " + file + "：" + count + " 条")
                        .withStyle(ChatFormatting.DARK_GRAY), false));

        return entries.size();
    }
}
