package com.wowuwo233.simple_recipe_change;

import com.mojang.logging.LogUtils;
import com.wowuwo233.simple_recipe_change.menu.ModMenus;
import com.wowuwo233.simple_recipe_change.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.List;

/**
 * Simple Recipe Change -- 主类。
 *
 * <p>本模组不添加任何物品或方块，只提供游戏内的配方编辑器：
 * 按 <b>G</b> 打开，可以添加 / 修改 / 删除配方，结果写成 KubeJS 脚本。
 */
@Mod(SimpleRecipeChangeMod.MODID)
public class SimpleRecipeChangeMod {

    /** 必须与 gradle.properties 的 mod_id 以及 mods.toml 一致 */
    public static final String MODID = "simple_recipe_change";

    private static final Logger LOGGER = LogUtils.getLogger();

    public SimpleRecipeChangeMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // 装了农夫乐事/机械动力、却没装它们对应的 KubeJS 适配时，本模组照常能打开，
        // 但生成出来的脚本在游戏里不会生效——玩家只会以为「这模组坏了」。
        // 与其让人摸不着头脑，不如在加载阶段就把原因说清楚并停下。
        List<String> missing = ModCompat.missingCompanions();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("""
                    Simple Recipe Change 拒绝加载：检测到以下模组装了本体，却缺少对应的 KubeJS 适配，
                    生成的配方不会生效，因此提前停止而不是让你进游戏后才发现。

                      - %s

                    请补装对应的 KubeJS 适配；如果你并不需要那个模组的配方类型，
                    也可以直接移除对应的本体模组。""".formatted(String.join("\n  - ", missing)));
        }

        modEventBus.addListener(this::commonSetup);

        // 注册编辑器菜单（按 G 打开）与网络通道
        ModMenus.MENUS.register(modEventBus);
        ModNetwork.register();

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("简单配方修改已加载：游戏内按 G 打开配方编辑器");
    }
}
