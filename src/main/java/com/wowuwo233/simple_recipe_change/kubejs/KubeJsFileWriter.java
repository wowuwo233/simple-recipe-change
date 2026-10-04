package com.wowuwo233.simple_recipe_change.kubejs;

import com.wowuwo233.simple_recipe_change.SimpleRecipeChangeMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        try {
            // 这几步都可能抛出带中文说明的 IllegalArgumentException，交给下面统一处理
            List<String> calls = KubeJsWriter.renderCalls(draft, SimpleRecipeChangeMod.MODID);
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

            RecipeIndexEntry entry = RecipeIndexEntry.fromDraft(blockId, fileName, draft);
            RecipeIndex.upsert(entry);

            String message = draft.operation().label() + "：已写入 " + fileName + "\n标识：" + blockId;
            if (!kubeJsInstalled) {
                message += "\n注意：未检测到 kubejs 目录，安装 KubeJS 后该文件才会生效";
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
