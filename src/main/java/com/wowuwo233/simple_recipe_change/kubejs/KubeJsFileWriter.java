package com.wowuwo233.simple_recipe_change.kubejs;

import com.wowuwo233.simple_recipe_change.SimpleRecipeChangeMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 把配方写入 {@code kubejs/server_scripts/}，由 KubeJS 读取，并同步维护「我的配方」索引。
 *
 * <p>必须在<b>服务端</b>调用：单人游戏时集成服务器与客户端共享同一个游戏目录，
 * 专用服务器上则写到服务器自己的 kubejs 目录，两边位置都正确。
 */
public final class KubeJsFileWriter {

    private KubeJsFileWriter() {
    }

    /** {@code <游戏目录>/kubejs/server_scripts} */
    public static Path serverScriptsDir() {
        return FMLPaths.GAMEDIR.get().resolve("kubejs").resolve("server_scripts");
    }

    /**
     * 保存结果。
     *
     * @param ok      是否成功
     * @param message 简短提示，可直接显示在界面状态栏
     * @param path    写入文件的绝对路径；失败时为 {@code null}
     * @param entry   写入成功后对应的索引记录；失败时为 {@code null}
     */
    public record Result(boolean ok, String message, String path, RecipeIndexEntry entry) {
    }

    public static Result save(RecipeDraft draft, String requestedFileName) {
        return save(draft, requestedFileName, "");
    }

    /**
     * 保存配方。
     *
     * @param outputNbt 产物格的 SNBT（含花括号）；没有 NBT 时传空串。
     *                  由服务端从产物槽里的真实 ItemStack 取，客户端传不了这个。
     */
    public static Result save(RecipeDraft draft, String requestedFileName, String outputNbt) {
        try {
            // 这几步都可能抛出带中文说明的 IllegalArgumentException，交给下面统一处理
            List<String> calls = KubeJsWriter.renderCalls(draft, SimpleRecipeChangeMod.MODID, outputNbt);
            String blockId = KubeJsWriter.blockIdFor(draft, SimpleRecipeChangeMod.MODID);
            String block = KubeJsWriter.renderBlock(blockId, calls);

            String fileName = KubeJsWriter.normalizeFileName(requestedFileName);
            Path gameDir = FMLPaths.GAMEDIR.get();
            boolean kubeJsInstalled = Files.isDirectory(gameDir.resolve("kubejs"));

            Path dir = serverScriptsDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName).toAbsolutePath();

            String existing = Files.exists(file)
                    ? Files.readString(file, StandardCharsets.UTF_8)
                    : KubeJsWriter.fileHeader();

            Files.writeString(file, KubeJsWriter.upsert(existing, blockId, block), StandardCharsets.UTF_8);

            RecipeIndexEntry entry = RecipeIndexEntry.fromDraft(blockId, fileName, draft, outputNbt);
            boolean indexDropped = RecipeIndex.upsert(entry);

            String message = draft.operation().label() + "：已写入 " + fileName + "\n标识：" + blockId;
            if (!kubeJsInstalled) {
                message += "\n注意：未检测到 kubejs 目录，安装 KubeJS 后该文件才会生效";
            }
            if (indexDropped) {
                // 脚本已经写进去了，只是索引放不下——必须说出来，否则「我的配方」会莫名少一条
                message += "\n注意：索引已达 " + RecipeIndex.MAX_ENTRIES
                        + " 条上限，最旧的一条已从「我的配方」移出（它的 .js 脚本仍然有效）";
            }
            return new Result(true, message, file.toString(), entry);
        } catch (IllegalArgumentException e) {
            return new Result(false, e.getMessage(), null, null);
        } catch (Exception e) {
            return new Result(false, "写入失败：" + e, null, null);
        }
    }

    /**
     * 删除一条已写过的配方：先从 .js 文件里整块删掉，再移除索引记录。
     * 手写的脚本内容不受影响。
     */
    /**
     * 只改一条配方的备注，不动配方本身。
     *
     * <p>做法是文本手术：定位 BEGIN/END 标记，把中间那几行 {@code //} 注释替换掉。
     * 比「重建整块」安全——重建需要把索引条目还原成 RecipeDraft，容易丢字段。
     *
     * @param note 新备注，允许换行；空串表示删除备注
     */
    public static Result updateNote(String blockId, String fileName, String note) {
        try {
            Path file = serverScriptsDir().resolve(KubeJsWriter.normalizeFileName(fileName));
            if (!Files.exists(file)) {
                return new Result(false, "找不到脚本文件：" + fileName, null, null);
            }
            List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));

            int begin = -1;
            int end = -1;
            for (int i = 0; i < lines.size(); i++) {
                String text = lines.get(i).trim();
                if (text.startsWith("// ==== SIMPLE_RECIPE_CHANGE:BEGIN " + blockId)) {
                    begin = i;
                } else if (text.startsWith("// ==== SIMPLE_RECIPE_CHANGE:END " + blockId)) {
                    end = i;
                    break;
                }
            }
            if (begin < 0 || end < 0) {
                return new Result(false, "脚本里找不到这条配方：" + blockId, null, null);
            }

            // 跳过紧跟在 BEGIN 后面的「自动生成，请勿手动编辑」提示行
            int insertAt = begin + 1;
            if (insertAt < end && lines.get(insertAt).trim().startsWith("// 由")) {
                insertAt++;
            }
            // 删掉原有的备注行（连续的 // 开头）
            int cursor = insertAt;
            while (cursor < end && lines.get(cursor).trim().startsWith("//")) {
                lines.remove(cursor);
                end--;
            }

            if (note != null && !note.isBlank()) {
                List<String> noteLines = new ArrayList<>();
                for (String line : note.split("\r?\n")) {
                    noteLines.add("// " + line.strip());
                }
                lines.addAll(insertAt, noteLines);
            }

            Files.write(file, lines, StandardCharsets.UTF_8);
            return new Result(true, "备注已更新", file.toString(), null);
        } catch (Exception e) {
            return new Result(false, "更新备注失败：" + e.getMessage(), null, null);
        }
    }

    public static Result deleteEntry(String blockId) {
        try {
            RecipeIndexEntry entry = RecipeIndex.find(blockId);
            if (entry == null) {
                return new Result(false, "索引里找不到这条配方", null, null);
            }

            Path file = serverScriptsDir().resolve(entry.fileName()).toAbsolutePath();
            if (Files.exists(file)) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                Files.writeString(file, KubeJsWriter.removeBlock(content, blockId), StandardCharsets.UTF_8);
            }

            RecipeIndex.remove(blockId);
            return new Result(true, "已删除 " + entry.fileName() + " 中的 " + blockId, file.toString(), null);
        } catch (Exception e) {
            return new Result(false, "删除失败：" + e, null, null);
        }
    }
}
