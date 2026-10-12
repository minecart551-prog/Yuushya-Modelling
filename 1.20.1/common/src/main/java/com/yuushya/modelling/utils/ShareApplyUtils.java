package com.yuushya.modelling.utils;

import com.yuushya.modelling.blockentity.TransformData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 应用共享码时的资源结算：先用现有层回收的资源池抵扣，再扣背包，最后报告缺失。
 * Resource settlement when applying a share code: offset with a pool recycled from the
 * existing layers, then draw from the inventory, and finally report whatever is missing.
 * 客户端与服务端共用同一套逻辑以保证结果一致。Client and server share this logic so results match.
 */
public class ShareApplyUtils {

    /** built=实际建出的层 missing=缺失的方块 leftover=资源池剩余（应归还给玩家） */
    public record ApplyResult(List<TransformData> built, Map<Item, Integer> missing, Map<Item, Integer> leftover){}

    /**
     * 计算并扣费：逐层按顺序结算，能付得起才建这一层，付不起则记为缺失。
     * Compute and charge: settle layer by layer in order; a layer is only built when it can be paid for,
     * otherwise it is recorded as missing. Air / creative layers are always free.
     */
    public static ApplyResult computeApply(ShareUtils.ShareInformation shareInformation, List<TransformData> existing,
                                           Player player, boolean creative){
        //1. 回收池：把现有层里的方块当作可用资源 recycle the blocks already in the existing layers into a pool
        Map<Item,Integer> pool = new LinkedHashMap<>();
        if(!creative && existing != null){
            for(TransformData d : existing){
                if(d == null || d.blockState == null) continue;
                Block b = d.blockState.getBlock();
                if(b instanceof AirBlock) continue;
                Item it = b.asItem();
                if(it != Items.AIR) pool.merge(it, 1, Integer::sum);
            }
        }

        List<TransformData> built = new ArrayList<>();
        Map<Item,Integer> missing = new LinkedHashMap<>();
        if(shareInformation == null || shareInformation.blocks() == null){
            return new ApplyResult(built, missing, pool);
        }

        //2. 按顺序逐层结算 build in order
        for(ShareUtils.ShareInformation.ShareData shareData : shareInformation.blocks()){
            TransformData newData = shareData.transfer();
            Block b = newData.blockState.getBlock();
            //创造模式 / 空气层 / 无物品形态的方块：免费 creative / air / no item form: free
            if(creative || b instanceof AirBlock || b.asItem() == Items.AIR){
                built.add(newData);
                continue;
            }
            Item it = b.asItem();
            boolean paid = false;
            //先用回收池 pay from the recycled pool first
            Integer inPool = pool.get(it);
            if(inPool != null && inPool > 0){
                pool.put(it, inPool - 1);
                paid = true;
            }
            //再扣背包 then from the inventory
            else if(consumeFromInventory(player, it)){
                paid = true;
            }
            if(paid) built.add(newData);
            else missing.merge(it, 1, Integer::sum);
        }
        return new ApplyResult(built, missing, pool);
    }

    /** 从背包主栏扣一个，其次副手；若精确物品没有，则用其模板母物品抵扣。
     * consume one from the main inventory, then the offhand; when the exact item is absent,
     * a child block can still be paid for with its parent template item. */
    private static boolean consumeFromInventory(Player player, Item item){
        if(tryConsume(player, item)) return true;
        Item template = parentTemplateItem(item);
        return template != null && tryConsume(player, template);
    }

    private static boolean tryConsume(Player player, Item item){
        Inventory inv = player.getInventory();
        for(int i = 0; i < 36; i++){
            ItemStack stack = inv.getItem(i);
            if(stack.is(item) && stack.getCount() > 0){
                stack.shrink(1);
                if(stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
                inv.setChanged();
                return true;
            }
        }
        ItemStack off = player.getOffhandItem();
        if(off.is(item) && off.getCount() > 0){
            off.shrink(1);
            if(off.isEmpty()) player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    //=== 模板物品支持：通过反射映射“模板子方块 -> 模板母物品”，未装原版 Yuushya 模组时自动降级为精确匹配 ===
    // template item support: reflectively map a template child block to its parent template item;
    // when the base Yuushya mod is absent this degrades to exact-item matching.
    private static final String YUUSHYA_NS = "yuushya";
    private static final String TEMPLATE_CHILD_CLASS = "com.yuushya.item.TemplateChildBlockItem";
    private static Class<?> templateChildClass;
    private static Field templateBlockNameField;
    private static boolean templateResolved = false;

    private static synchronized void resolveTemplateSupport(){
        if(templateResolved) return;
        templateResolved = true;
        try {
            templateChildClass = Class.forName(TEMPLATE_CHILD_CLASS);
            templateBlockNameField = templateChildClass.getDeclaredField("templateBlockName");
            templateBlockNameField.setAccessible(true);
        } catch (Throwable t) {
            //原版 Yuushya 模组不在时静默降级 stay silent and fall back to exact matching
            templateChildClass = null;
            templateBlockNameField = null;
        }
    }

    /** 若该物品是某模板的子方块，则返回其模板母物品；否则返回 null。
     * If this item is a template child block, return its parent template item; otherwise null. */
    private static Item parentTemplateItem(Item item){
        if(item == null) return null;
        resolveTemplateSupport();
        if(templateChildClass == null || templateBlockNameField == null) return null;
        if(!templateChildClass.isInstance(item)) return null;
        try {
            String templateBlockName = (String) templateBlockNameField.get(item);
            if(templateBlockName == null || templateBlockName.isEmpty()) return null;
            Item template = BuiltInRegistries.ITEM.get(new ResourceLocation(YUUSHYA_NS, templateBlockName));
            return (template == null || template == Items.AIR) ? null : template;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 缺失提示：物品名白色，数量绿色，如 "Missing: dirt ×1, white concrete ×2"
     * missing report: item name in white, count in green, e.g. "Missing: dirt ×1, white concrete ×2" */
    public static Component missingMessage(Map<Item, Integer> missing){
        MutableComponent msg = Component.literal("Missing: ");
        boolean first = true;
        for(Map.Entry<Item, Integer> e : missing.entrySet()){
            if(!first) msg.append(Component.literal(", "));
            first = false;
            //物品名白色 the item name in white
            msg.append(new ItemStack(e.getKey()).getHoverName().copy().withStyle(ChatFormatting.WHITE));
            //数量绿色 the count in green
            msg.append(Component.literal(" ×" + e.getValue()).withStyle(ChatFormatting.GREEN));
        }
        return msg;
    }
}
