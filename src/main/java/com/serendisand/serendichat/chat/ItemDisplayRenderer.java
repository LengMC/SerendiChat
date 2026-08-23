package com.serendisand.serendichat.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * 消息中 [item] 标记的物品展示：替换为主手物品名（按稀有度着色），
 * 悬停可查看完整物品数据；空手时显示灰色占位。
 */
public final class ItemDisplayRenderer {

    public static final String TAG = "[item]";

    private ItemDisplayRenderer() {
    }

    public static MutableComponent render(ServerPlayer player, String message,
                                          ChatFormatting baseColor, boolean markdownEnabled) {
        if (!message.contains(TAG)) {
            return MarkdownRenderer.render(message, baseColor, markdownEnabled);
        }
        MutableComponent out = Component.empty();
        int idx;
        int last = 0;
        while ((idx = message.indexOf(TAG, last)) >= 0) {
            if (idx > last) {
                out.append(MarkdownRenderer.render(message.substring(last, idx), baseColor, markdownEnabled));
            }
            out.append(heldItem(player));
            last = idx + TAG.length();
        }
        if (last < message.length()) {
            out.append(MarkdownRenderer.render(message.substring(last), baseColor, markdownEnabled));
        }
        return out;
    }

    private static MutableComponent heldItem(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return Component.literal(TAG).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        }
        MutableComponent name = Component.empty().append(stack.getHoverName()).withStyle(stack.getRarity().color());
        if (stack.getCount() > 1) {
            name.append(Component.literal(" x" + stack.getCount()).withStyle(ChatFormatting.YELLOW));
        }
        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
                .append(name)
                .append(Component.literal("]").withStyle(ChatFormatting.GRAY))
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromStack(stack))));
    }
}
