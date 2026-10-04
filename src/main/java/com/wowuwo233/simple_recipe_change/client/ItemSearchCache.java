package com.wowuwo233.simple_recipe_change.client;

import com.wowuwo233.simple_recipe_change.search.ItemSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 物品选择器的候选列表。
 *
 * <p>物品来自注册表（原版 + 所有模组），这正是 JEI 展示的那一套物品；
 * 因此不需要硬依赖 JEI 就能搜到全部物品。若将来要接入 JEI 的额外过滤，
 * 只要在这里加一个来源分支即可。
 */
@OnlyIn(Dist.CLIENT)
public final class ItemSearchCache {

    private static List<ItemSearch.Entry> allItems;
    private static List<ItemSearch.Entry> inventoryItems;

    private ItemSearchCache() {
    }

    /** 全部物品（注册表），首次调用时构建并缓存 */
    public static List<ItemSearch.Entry> allItems() {
        if (allItems == null) {
            List<ItemSearch.Entry> list = new ArrayList<>();
            for (var item : ForgeRegistries.ITEMS.getValues()) {
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) {
                    continue;
                }
                String id = ForgeRegistries.ITEMS.getKey(item).toString();
                list.add(ItemSearch.Entry.of(id, stack.getHoverName().getString()));
            }
            allItems = list;
        }
        return allItems;
    }

    /** 玩家背包里的物品（去重） */
    public static List<ItemSearch.Entry> inventoryItems() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<ItemSearch.Entry> list = new ArrayList<>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            if (seen.add(id)) {
                list.add(ItemSearch.Entry.of(id, stack.getHoverName().getString()));
            }
        }
        return list;
    }

    /** 玩家背包里该物品的数量，用于在列表里标注 */
    public static int countInInventory(String itemId) {
        Player player = Minecraft.getInstance().player;
        if (player == null || itemId == null) {
            return 0;
        }
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (itemId.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString())) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
