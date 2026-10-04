package com.wowuwo233.simple_recipe_change.network;

import com.wowuwo233.simple_recipe_change.SimpleRecipeChangeMod;
import com.wowuwo233.simple_recipe_change.client.ClientScreenHooks;
import com.wowuwo233.simple_recipe_change.kubejs.KubeJsFileWriter;
import com.wowuwo233.simple_recipe_change.kubejs.Operation;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeDraft;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndex;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndexEntry;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import com.wowuwo233.simple_recipe_change.kubejs.RemoveBy;
import com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu;
import com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 网络通道。
 *
 * <p>编辑器界面是客户端的，但配方文件、原版配方查询、以及「我的配方」索引都在服务端，
 * 所以这些交互都走这里。
 */
public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryParse(SimpleRecipeChangeMod.MODID + ":main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, OpenEditorPacket.class,
                OpenEditorPacket::encode, OpenEditorPacket::decode, OpenEditorPacket::handle);
        CHANNEL.registerMessage(id++, SetModePacket.class,
                SetModePacket::encode, SetModePacket::decode, SetModePacket::handle);
        CHANNEL.registerMessage(id++, ClearEditorPacket.class,
                ClearEditorPacket::encode, ClearEditorPacket::decode, ClearEditorPacket::handle);
        CHANNEL.registerMessage(id++, SaveRecipePacket.class,
                SaveRecipePacket::encode, SaveRecipePacket::decode, SaveRecipePacket::handle);
        CHANNEL.registerMessage(id++, RequestIndexPacket.class,
                RequestIndexPacket::encode, RequestIndexPacket::decode, RequestIndexPacket::handle);
        CHANNEL.registerMessage(id++, LoadIndexPacket.class,
                LoadIndexPacket::encode, LoadIndexPacket::decode, LoadIndexPacket::handle);
        CHANNEL.registerMessage(id++, DeleteIndexPacket.class,
                DeleteIndexPacket::encode, DeleteIndexPacket::decode, DeleteIndexPacket::handle);
        CHANNEL.registerMessage(id++, PickItemPacket.class,
                PickItemPacket::encode, PickItemPacket::decode, PickItemPacket::handle);
        CHANNEL.registerMessage(id++, ReloadPacket.class,
                ReloadPacket::encode, ReloadPacket::decode, ReloadPacket::handle);
        CHANNEL.registerMessage(id++, EditorStatePacket.class,
                EditorStatePacket::encode, EditorStatePacket::decode, EditorStatePacket::handle);
        CHANNEL.registerMessage(id++, IndexListPacket.class,
                IndexListPacket::encode, IndexListPacket::decode, IndexListPacket::handle);
        CHANNEL.registerMessage(id, SaveResultPacket.class,
                SaveResultPacket::encode, SaveResultPacket::decode, SaveResultPacket::handle);
    }

    // ------------------------------------------------------------------ 客户端入口

    public static void requestOpenEditor() {
        CHANNEL.sendToServer(new OpenEditorPacket());
    }

    public static void requestSetMode(Operation operation, RecipeType type) {
        CHANNEL.sendToServer(new SetModePacket(operation.ordinal(), type.ordinal()));
    }

    public static void requestClearEditor() {
        CHANNEL.sendToServer(new ClearEditorPacket());
    }

    public static void requestSaveRecipe(String fileName, String recipeId, boolean shapeless,
                                         boolean mirrored, int typeOrdinal, int removeByOrdinal,
                                         double xp, int cookingTime, List<String> inputCells) {
        CHANNEL.sendToServer(new SaveRecipePacket(fileName, recipeId, shapeless, mirrored,
                typeOrdinal, removeByOrdinal, xp, cookingTime, encodeCells(inputCells)));
    }

    /** 客户端请求重新加载资源（等同 /reload），让 KubeJS 立刻读到刚写好的脚本 */
    public static void requestReload() {
        CHANNEL.sendToServer(new ReloadPacket());
    }

    public static void requestIndex() {
        CHANNEL.sendToServer(new RequestIndexPacket());
    }

    public static void requestLoad(String blockId) {
        CHANNEL.sendToServer(new LoadIndexPacket(blockId));
    }

    public static void requestDelete(String blockId) {
        CHANNEL.sendToServer(new DeleteIndexPacket(blockId));
    }

    /** 选中一个物品放进产物格（输入槽由客户端自行维护） */
    public static void requestPickItem(String itemId) {
        CHANNEL.sendToServer(new PickItemPacket(itemId));
    }

    // ------------------------------------------------------------------ 服务端出口

    public static void sendSaveResult(ServerPlayer player, boolean ok, String message, String path) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveResultPacket(ok, message, path));
    }

    public static void sendIndexList(ServerPlayer player, boolean ok, String message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new IndexListPacket(RecipeIndex.toJsonList(RecipeIndex.load()), ok, message));
    }

    /**
     * 推送当前编辑器状态。
     *
     * @param inputCells     要回填到输入槽的材料；客户端用它画展示槽
     * @param replaceInputs  是否真的覆盖客户端的输入槽。只有「查到原版配方自动回填」「从我的配方载入」
     *                       「清空」才为 true——否则添加模式下玩家刚放好的原料会被一次又一次清掉
     */
    public static void sendEditorState(ServerPlayer player, RecipeEditorMenu menu,
                                       List<String> inputCells, boolean replaceInputs) {
        VanillaRecipeLookup.Found found = menu.getLastFound();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EditorStatePacket(
                menu.getOperation().ordinal(),
                menu.getRecipeType().ordinal(), false, false, false,
                "", "", menu.getSourceRecipeId(),
                found != null,
                found == null ? "" : found.summary(),
                encodeCells(inputCells),
                replaceInputs,
                found == null ? 0D : found.xp(),
                found == null ? RecipeDraft.DEFAULT_COOKING_TIME : found.cookingTime()));
    }

    /** 从「我的配方」载入后推送完整状态，包含要回填到输入框的文本与输入槽材料 */
    public static void sendLoadedState(ServerPlayer player, RecipeEditorMenu menu, RecipeIndexEntry entry) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EditorStatePacket(
                entry.operation().ordinal(),
                entry.type().ordinal(), entry.shapeless(), entry.mirrored(), true,
                entry.recipeId(), entry.fileName(), entry.sourceRecipeId(),
                false, "", encodeCells(entry.cells()), true,
                entry.xp(), entry.cookingTime()));
    }

    /** 写文件属于管理操作：专用服务器要求 OP，单人游戏直接放行 */
    static boolean canWrite(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        boolean integrated = server != null && !server.isDedicatedServer();
        return integrated || player.hasPermissions(2);
    }

    static String encodeCells(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String c = cells.get(i);
            if (c != null) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static List<String> decodeCells(String encoded) {
        List<String> out = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return out;
        }
        for (String part : encoded.split(",", -1)) {
            out.add(part.isEmpty() ? null : part);
        }
        return out;
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? null : key.toString();
    }

    // ================================================================== 打开编辑器

    public record OpenEditorPacket() {
        public static void encode(OpenEditorPacket msg, FriendlyByteBuf buf) {
        }

        public static OpenEditorPacket decode(FriendlyByteBuf buf) {
            return new OpenEditorPacket();
        }

        public static void handle(OpenEditorPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }
                player.openMenu(new SimpleMenuProvider(
                        (windowId, inventory, p) -> new RecipeEditorMenu(windowId, inventory),
                        Component.literal("配方编辑器")));
            });
            ctx.setPacketHandled(true);
        }
    }

    // ================================================================== 切换模式

    public record SetModePacket(int operationOrdinal, int typeOrdinal) {
        public static void encode(SetModePacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.operationOrdinal);
            buf.writeVarInt(msg.typeOrdinal);
        }

        public static SetModePacket decode(FriendlyByteBuf buf) {
            return new SetModePacket(buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(SetModePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null && player.containerMenu instanceof RecipeEditorMenu menu) {
                    menu.setMode(player, Operation.byOrdinal(msg.operationOrdinal),
                            RecipeType.byOrdinal(msg.typeOrdinal));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    // ================================================================== 清空

    public record ClearEditorPacket() {
        public static void encode(ClearEditorPacket msg, FriendlyByteBuf buf) {
        }

        public static ClearEditorPacket decode(FriendlyByteBuf buf) {
            return new ClearEditorPacket();
        }

        public static void handle(ClearEditorPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null && player.containerMenu instanceof RecipeEditorMenu menu) {
                    menu.clearEditor(player);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    // ================================================================== 保存

    public record SaveRecipePacket(String fileName, String recipeId,
                                   boolean shapeless, boolean mirrored,
                                   int typeOrdinal, int removeByOrdinal,
                                   double xp, int cookingTime, String inputCells) {

        public static void encode(SaveRecipePacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.fileName == null ? "" : msg.fileName, 256);
            buf.writeUtf(msg.recipeId == null ? "" : msg.recipeId, 256);
            buf.writeBoolean(msg.shapeless);
            buf.writeBoolean(msg.mirrored);
            buf.writeVarInt(msg.typeOrdinal);
            buf.writeVarInt(msg.removeByOrdinal);
            buf.writeDouble(msg.xp);
            buf.writeVarInt(msg.cookingTime);
            buf.writeUtf(msg.inputCells == null ? "" : msg.inputCells, 2048);
        }

        public static SaveRecipePacket decode(FriendlyByteBuf buf) {
            return new SaveRecipePacket(buf.readUtf(256), buf.readUtf(256),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readDouble(), buf.readVarInt(), buf.readUtf(2048));
        }

        public static void handle(SaveRecipePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }
                if (!(player.containerMenu instanceof RecipeEditorMenu menu)) {
                    sendSaveResult(player, false, "编辑界面已经关闭，请重新按 G 打开", null);
                    return;
                }
                if (!canWrite(player)) {
                    sendSaveResult(player, false, "需要管理员权限才能写入配方文件", null);
                    return;
                }

                // 输入槽由客户端维护（展示槽），这里直接用客户端传来的材料 ID，
                // 因此 #标签 写法能原样保留
                List<String> cells = decodeCells(msg.inputCells());
                ItemStack outputStack = menu.getOutputContainer().getItem(0);

                RecipeDraft draft = new RecipeDraft(
                        RecipeType.byOrdinal(msg.typeOrdinal()),
                        msg.shapeless(),
                        msg.mirrored(),
                        msg.recipeId(),
                        cells,
                        itemId(outputStack),
                        outputStack.getCount(),
                        menu.getOperation(),
                        RemoveBy.byOrdinal(msg.removeByOrdinal()),
                        menu.getSourceRecipeId(),
                        msg.xp(),
                        msg.cookingTime());

                KubeJsFileWriter.Result result = KubeJsFileWriter.save(draft, msg.fileName());
                sendSaveResult(player, result.ok(), result.message(), result.path());
            });
            ctx.setPacketHandled(true);
        }
    }

    // ================================================================== 我的配方

    public record RequestIndexPacket() {
        public static void encode(RequestIndexPacket msg, FriendlyByteBuf buf) {
        }

        public static RequestIndexPacket decode(FriendlyByteBuf buf) {
            return new RequestIndexPacket();
        }

        public static void handle(RequestIndexPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    sendIndexList(player, true, "");
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record LoadIndexPacket(String blockId) {
        public static void encode(LoadIndexPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.blockId == null ? "" : msg.blockId, 256);
        }

        public static LoadIndexPacket decode(FriendlyByteBuf buf) {
            return new LoadIndexPacket(buf.readUtf(256));
        }

        public static void handle(LoadIndexPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !(player.containerMenu instanceof RecipeEditorMenu menu)) {
                    return;
                }
                RecipeIndexEntry entry = RecipeIndex.find(msg.blockId);
                if (entry == null) {
                    sendSaveResult(player, false, "索引里找不到这条配方", null);
                    return;
                }
                menu.applyEntry(player, entry);
                sendLoadedState(player, menu, entry);
                sendSaveResult(player, true, "已载入：" + entry.blockId(), null);
            });
            ctx.setPacketHandled(true);
        }
    }

    public record DeleteIndexPacket(String blockId) {
        public static void encode(DeleteIndexPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.blockId == null ? "" : msg.blockId, 256);
        }

        public static DeleteIndexPacket decode(FriendlyByteBuf buf) {
            return new DeleteIndexPacket(buf.readUtf(256));
        }

        public static void handle(DeleteIndexPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }
                if (!canWrite(player)) {
                    sendIndexList(player, false, "需要管理员权限才能删除配方文件内容");
                    return;
                }
                KubeJsFileWriter.Result result = KubeJsFileWriter.deleteEntry(msg.blockId);
                sendIndexList(player, result.ok(), result.message());
            });
            ctx.setPacketHandled(true);
        }
    }

    // ================================================================== 选择产物

    /**
     * 只用来把物品放进<b>产物格</b>（真实槽位）。
     * 输入槽是客户端自绘的展示槽，选中物品直接在本地改 ID，不必走服务端。
     */
    public record PickItemPacket(String itemId) {
        public static void encode(PickItemPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.itemId == null ? "" : msg.itemId, 256);
        }

        public static PickItemPacket decode(FriendlyByteBuf buf) {
            return new PickItemPacket(buf.readUtf(256));
        }

        public static void handle(PickItemPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !(player.containerMenu instanceof RecipeEditorMenu menu)) {
                    return;
                }
                ItemStack stack = RecipeEditorMenu.stackOf(msg.itemId());
                if (stack.isEmpty()) {
                    return;
                }
                menu.getOutputContainer().setItem(0, stack);
            });
            ctx.setPacketHandled(true);
        }
    }

    // ================================================================== 重载

    /** 重新加载资源，等同 /reload；由服务端直接执行，不依赖玩家是否有 OP 权限 */
    public record ReloadPacket() {
        public static void encode(ReloadPacket msg, FriendlyByteBuf buf) {
        }

        public static ReloadPacket decode(FriendlyByteBuf buf) {
            return new ReloadPacket();
        }

        public static void handle(ReloadPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !canWrite(player)) {
                    return;
                }
                MinecraftServer server = player.getServer();
                if (server != null) {
                    server.reloadResources(server.getPackRepository().getSelectedIds());
                    player.displayClientMessage(
                            Component.literal("[简易配方修改] 已重新加载，KubeJS 现在读得到新脚本"),
                            false);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    // ================================================================== 服务端 -> 客户端

    public record EditorStatePacket(int operationOrdinal,
                                    int typeOrdinal, boolean shapeless, boolean mirrored,
                                    boolean applyText,
                                    String recipeId, String fileName, String sourceRecipeId,
                                    boolean found, String foundSummary, String inputCells,
                                    boolean replaceInputs,
                                    double xp, int cookingTime) {

        public static void encode(EditorStatePacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.operationOrdinal);
            buf.writeVarInt(msg.typeOrdinal);
            buf.writeBoolean(msg.shapeless);
            buf.writeBoolean(msg.mirrored);
            buf.writeBoolean(msg.applyText);
            buf.writeUtf(msg.recipeId == null ? "" : msg.recipeId, 256);
            buf.writeUtf(msg.fileName == null ? "" : msg.fileName, 256);
            buf.writeUtf(msg.sourceRecipeId == null ? "" : msg.sourceRecipeId, 256);
            buf.writeBoolean(msg.found);
            buf.writeUtf(msg.foundSummary == null ? "" : msg.foundSummary, 1024);
            buf.writeUtf(msg.inputCells == null ? "" : msg.inputCells, 2048);
            buf.writeBoolean(msg.replaceInputs);
            buf.writeDouble(msg.xp);
            buf.writeVarInt(msg.cookingTime);
        }

        public static EditorStatePacket decode(FriendlyByteBuf buf) {
            return new EditorStatePacket(buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readUtf(256), buf.readUtf(256), buf.readUtf(256),
                    buf.readBoolean(), buf.readUtf(1024), buf.readUtf(2048),
                    buf.readBoolean(), buf.readDouble(), buf.readVarInt());
        }

        public static void handle(EditorStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientScreenHooks.onEditorState(msg)));
            ctx.setPacketHandled(true);
        }
    }

    public record IndexListPacket(String json, boolean ok, String message) {
        public static void encode(IndexListPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.json == null ? "" : msg.json, 1 << 20);
            buf.writeBoolean(msg.ok);
            buf.writeUtf(msg.message == null ? "" : msg.message, 512);
        }

        public static IndexListPacket decode(FriendlyByteBuf buf) {
            return new IndexListPacket(buf.readUtf(1 << 20), buf.readBoolean(), buf.readUtf(512));
        }

        public static void handle(IndexListPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientScreenHooks.onIndexList(msg)));
            ctx.setPacketHandled(true);
        }
    }

    public record SaveResultPacket(boolean ok, String message, String path) {
        public static void encode(SaveResultPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.ok);
            buf.writeUtf(msg.message == null ? "" : msg.message, 512);
            buf.writeUtf(msg.path == null ? "" : msg.path, 1024);
        }

        public static SaveResultPacket decode(FriendlyByteBuf buf) {
            return new SaveResultPacket(buf.readBoolean(), buf.readUtf(512), buf.readUtf(1024));
        }

        public static void handle(SaveResultPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientScreenHooks.onSaveResult(msg.ok(), msg.message(), msg.path())));
            ctx.setPacketHandled(true);
        }
    }
}
