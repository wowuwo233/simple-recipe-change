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
