package com.wowuwo233.simple_recipe_change.network;

import com.wowuwo233.simple_recipe_change.SimpleRecipeChangeMod;
import com.wowuwo233.simple_recipe_change.client.ClientScreenHooks;
import com.wowuwo233.simple_recipe_change.kubejs.CreateMachine;
import com.wowuwo233.simple_recipe_change.kubejs.KubeJsFileWriter;
import com.wowuwo233.simple_recipe_change.kubejs.Operation;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeDraft;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndex;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeIndexEntry;
import com.wowuwo233.simple_recipe_change.kubejs.RecipeType;
import com.wowuwo233.simple_recipe_change.kubejs.RemoveBy;
import com.wowuwo233.simple_recipe_change.menu.RecipeEditorMenu;
import com.wowuwo233.simple_recipe_change.server.VanillaRecipeLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
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
        CHANNEL.registerMessage(id++, LoadRecipeByIdPacket.class,
                LoadRecipeByIdPacket::encode, LoadRecipeByIdPacket::decode,
                LoadRecipeByIdPacket::handle);        CHANNEL.registerMessage(id++, ReloadPacket.class,
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
        CHANNEL.sendToServer(new OpenEditorPacket(""));
    }

    /** 从 JEI 的配方页点按钮进来：打开编辑器并直接载入这条配方 */
    public static void requestOpenEditorWithRecipe(String recipeId) {
        CHANNEL.sendToServer(new OpenEditorPacket(recipeId == null ? "" : recipeId));
    }

    /** 客户端切换操作 / 类型 / Create 机器时调用（服务端执行） */
    public static void requestSetMode(Operation operation, RecipeType type, CreateMachine machine) {
        CHANNEL.sendToServer(new SetModePacket(operation.ordinal(), type.ordinal(),
                machine == null ? 0 : machine.ordinal()));
    }

    public static void requestClearEditor() {
        CHANNEL.sendToServer(new ClearEditorPacket());
    }

    public static void requestSaveRecipe(String fileName, String recipeId, boolean shapeless,
                                         boolean mirrored, int typeOrdinal, int removeByOrdinal,
                                         double xp, int cookingTime, List<String> inputCells,
                                         List<RecipeDraft.ExtraOutput> extraOutputs,
                                         int machineOrdinal, String heat, String note,
                                         boolean carryNbt) {
        CHANNEL.sendToServer(new SaveRecipePacket(fileName, recipeId, shapeless, mirrored,
                typeOrdinal, removeByOrdinal, xp, cookingTime, encodeCells(inputCells),
                encodeExtraOutputs(extraOutputs), machineOrdinal, heat == null ? "" : heat,
                note == null ? "" : note, carryNbt));
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
                                       List<String> inputCells, boolean replaceInputs,
                                       List<RecipeDraft.ExtraOutput> extraOutputs,
                                       int machineOrdinal, String heat, String note) {
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
                found == null ? RecipeDraft.DEFAULT_COOKING_TIME : found.cookingTime(),
                encodeExtraOutputs(extraOutputs),
                machineOrdinal,
                heat == null ? "" : heat,
                note == null ? "" : note));
    }

    /** 从「我的配方」载入后推送完整状态，包含要回填到输入框的文本与输入槽材料 */
    public static void sendLoadedState(ServerPlayer player, RecipeEditorMenu menu, RecipeIndexEntry entry) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EditorStatePacket(
                entry.operation().ordinal(),
                entry.type().ordinal(), entry.shapeless(), entry.mirrored(), true,
                entry.recipeId(), entry.fileName(), entry.sourceRecipeId(),
                false, "", encodeCells(entry.cells()), true,
                entry.xp(), entry.cookingTime(), encodeExtraOutputs(entry.extraOutputs()),
                entry.machine().ordinal(), entry.heat(), entry.note()));
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

    /**
     * 编码切菜板的额外产物。
     *
     * <p>每行一条，格式 {@code 物品ID|数量|概率}；概率是 0–1 的小数。
     * 空列表编码成空串。
     */
    public static String encodeExtraOutputs(List<RecipeDraft.ExtraOutput> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (RecipeDraft.ExtraOutput e : list) {
            if (e == null || e.item() == null || e.item().isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(e.item()).append('|').append(e.count()).append('|').append(e.chance());
        }
        return sb.toString();
    }

    /** {@link #encodeExtraOutputs} 的逆操作；解析不了的行直接跳过 */
    public static List<RecipeDraft.ExtraOutput> decodeExtraOutputs(String encoded) {
        List<RecipeDraft.ExtraOutput> out = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return out;
        }
        for (String line : encoded.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length < 3) {
                continue;
            }
            try {
                out.add(new RecipeDraft.ExtraOutput(parts[0],
                        Integer.parseInt(parts[1].trim()),
                        Double.parseDouble(parts[2].trim())));
            } catch (NumberFormatException ignored) {
                // 坏行跳过，不影响其它产物
            }
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

    /**
     * 产物格里的 SNBT（含花括号）；没有 NBT 时返回空串。
     *
     * <p>只能在服务端读：客户端发过来的只有物品 ID 与数量，NBT 拿不到；
     * 而产物槽是真实槽位，服务端这边就是玩家放进去的那个 ItemStack。
     */
    private static String itemNbt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        CompoundTag tag = stack.getTag();
        return tag == null || tag.isEmpty() ? "" : tag.toString();
    }

    // ================================================================== 打开编辑器

    public record OpenEditorPacket(String recipeId) {
        public static void encode(OpenEditorPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.recipeId == null ? "" : msg.recipeId, 256);
        }

        public static OpenEditorPacket decode(FriendlyByteBuf buf) {
            return new OpenEditorPacket(buf.readUtf(256));
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

                String raw = msg.recipeId == null ? "" : msg.recipeId.trim();
                if (raw.isEmpty()) {
                    return;
                }
                // 等两 tick 再回填：客户端要先收到「打开界面」的包并建好 Screen，
                // 紧接着发的状态包才有人接，否则会被丢掉。
                MinecraftServer server = player.getServer();
                if (server == null) {
                    return;
                }
                server.tell(new TickTask(server.getTickCount() + 2, () -> {
                    if (!(player.containerMenu instanceof RecipeEditorMenu menu)) {
                        return;
                    }
                    ResourceLocation id = ResourceLocation.tryParse(raw);
                    VanillaRecipeLookup.Found found = id == null ? null
                            : VanillaRecipeLookup.byId(player.serverLevel(), id);
                    if (found == null) {
                        sendSaveResult(player, false,
                                "打不开配方 " + raw + "（可能类型暂不支持）", null);
                        return;
                    }
                    menu.applyFoundById(player, found);
                }));
            });
            ctx.setPacketHandled(true);
        }
    }

    // ================================================================== 切换模式

    public record SetModePacket(int operationOrdinal, int typeOrdinal, int machineOrdinal) {
        public static void encode(SetModePacket msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.operationOrdinal);
            buf.writeVarInt(msg.typeOrdinal);
            buf.writeVarInt(msg.machineOrdinal);
        }

        public static SetModePacket decode(FriendlyByteBuf buf) {
            return new SetModePacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(SetModePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null && player.containerMenu instanceof RecipeEditorMenu menu) {
                    menu.setMode(player, Operation.byOrdinal(msg.operationOrdinal),
                            RecipeType.byOrdinal(msg.typeOrdinal),
                            CreateMachine.byOrdinal(msg.machineOrdinal));
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
                                   double xp, int cookingTime, String inputCells,
                                   String extraOutputs, int machineOrdinal, String heat,
                                   String note,
                                   /** 是否把产物格里的 NBT 写进脚本 */
                                   boolean carryNbt) {

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
            buf.writeUtf(msg.extraOutputs == null ? "" : msg.extraOutputs, 2048);
            buf.writeVarInt(msg.machineOrdinal);
            buf.writeUtf(msg.heat == null ? "" : msg.heat, 64);
            buf.writeUtf(msg.note == null ? "" : msg.note, 512);
            buf.writeBoolean(msg.carryNbt);
        }

        public static SaveRecipePacket decode(FriendlyByteBuf buf) {
            return new SaveRecipePacket(buf.readUtf(256), buf.readUtf(256),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readDouble(), buf.readVarInt(), buf.readUtf(2048), buf.readUtf(2048),
                    buf.readVarInt(), buf.readUtf(64), buf.readUtf(512), buf.readBoolean());
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
                        msg.cookingTime(),
                        decodeExtraOutputs(msg.extraOutputs()),
                        "",
                        CreateMachine.byOrdinal(msg.machineOrdinal()),
                        msg.heat(),
                        false,
                        msg.note());

                KubeJsFileWriter.Result result = KubeJsFileWriter.save(draft, msg.fileName(),
                        msg.carryNbt() ? itemNbt(outputStack) : "");
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

    /**
     * 客户端：按配方 ID 跳转。
     *
     * <p>服务端从 {@code RecipeManager} 里按 ID 取出配方，判断类型能不能被编辑器表达，
     * 能的话把整条配方回填过去（类型、材料、产物、副产物一起）。
     */
    public record LoadRecipeByIdPacket(String recipeId) {
        public static void encode(LoadRecipeByIdPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.recipeId == null ? "" : msg.recipeId, 256);
        }

        public static LoadRecipeByIdPacket decode(FriendlyByteBuf buf) {
            return new LoadRecipeByIdPacket(buf.readUtf(256));
        }

        public static void handle(LoadRecipeByIdPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !(player.containerMenu instanceof RecipeEditorMenu menu)) {
                    return;
                }
                String raw = msg.recipeId == null ? "" : msg.recipeId.trim();
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null) {
                    sendSaveResult(player, false, "配方 ID 格式不对，应该是 namespace:path", null);
                    return;
                }
                if (player.serverLevel().getRecipeManager().byKey(id).isEmpty()) {
                    sendSaveResult(player, false, "没找到配方：" + raw, null);
                    return;
                }
                VanillaRecipeLookup.Found found = VanillaRecipeLookup.byId(player.serverLevel(), id);
                if (found == null) {
                    var recipe = player.serverLevel().getRecipeManager().byKey(id).orElseThrow();
                    sendSaveResult(player, false,
                            "配方 " + raw + " 的类型（" + VanillaRecipeLookup.typeIdOf(recipe)
                                    + "）暂不支持编辑", null);
                    return;
                }
                menu.applyFoundById(player, found);
            });
            ctx.setPacketHandled(true);
        }
    }

    /** 客户端：请求按 ID 跳转 */
    public static void requestLoadRecipeById(String recipeId) {
        CHANNEL.sendToServer(new LoadRecipeByIdPacket(recipeId));
    }

    /** 把按 ID 查到的配方整体推给客户端（类型、材料、产物、副产物、时间/热度都带） */
    public static void sendRecipeById(ServerPlayer player, RecipeEditorMenu menu,
                                      VanillaRecipeLookup.Found found) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EditorStatePacket(
                Operation.MODIFY.ordinal(),
                found.suggestedType().ordinal(), found.shapeless(), true, true,
                found.recipeId(), "", found.recipeId(),
                true, found.summary(),
                encodeCells(found.cells()), true,
                found.xp(), found.cookingTime(), encodeExtraOutputs(found.extras()),
                found.machine().ordinal(), found.heat(), ""));
    }

    // ================================================================== 重载

    /**
     * 重新加载资源，等同 {@code /reload}。
     *
     * <p><b>要求 OP 等级 2</b>（单人游戏直接放行）。这条限制是刻意的：
     * {@code server.reloadResources(...)} 会重建全部数据包与配方，一次就是几百毫秒的
     * 服务端停顿，若任何玩家都能触发，几个人连点就能把服务器卡住。
     *
     * <p>被拒时会回一句聊天提示，不再静默失败——之前注释写着「不依赖 OP」，
     * 实现里却调了 {@link #canWrite}，玩家点了没反应也看不出原因。
     */
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
                if (player == null) {
                    return;
                }
                if (!canWrite(player)) {
                    player.displayClientMessage(
                            Component.literal("[简易配方修改] 重载需要管理员权限（OP 等级 2）"), false);
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
                                    double xp, int cookingTime, String extraOutputs,
                                    int machineOrdinal, String heat, String note) {

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
            buf.writeUtf(msg.extraOutputs == null ? "" : msg.extraOutputs, 2048);
            buf.writeVarInt(msg.machineOrdinal);
            buf.writeUtf(msg.heat == null ? "" : msg.heat, 64);
            buf.writeUtf(msg.note == null ? "" : msg.note, 512);
        }

        public static EditorStatePacket decode(FriendlyByteBuf buf) {
            return new EditorStatePacket(buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readUtf(256), buf.readUtf(256), buf.readUtf(256),
                    buf.readBoolean(), buf.readUtf(1024), buf.readUtf(2048),
                    buf.readBoolean(), buf.readDouble(), buf.readVarInt(), buf.readUtf(2048),
                    buf.readVarInt(), buf.readUtf(64), buf.readUtf(512));
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
