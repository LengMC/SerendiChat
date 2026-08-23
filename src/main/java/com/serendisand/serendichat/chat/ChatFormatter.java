package com.serendisand.serendichat.chat;

import com.serendisand.serendichat.compat.CustomNameCompat;
import com.serendisand.serendichat.config.ChatConfig;
import com.serendisand.serendichat.data.PlayerDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * 消息格式化编排：星标前缀 + 玩家名 + 富文本消息（emoji/markdown/[item]）。
 * 优先级：含 markdown 或 [item] 时走富文本渲染，否则按管理员红字/彩虹/白字。
 */
public class ChatFormatter {

    private static final ChatFormatting[] RAINBOW = {
            ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW,
            ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.BLUE,
            ChatFormatting.LIGHT_PURPLE
    };

    private final ChatConfig config;
    private final PlayerDataManager data;
    private final CustomNameCompat customName;

    public ChatFormatter(ChatConfig config, PlayerDataManager data, CustomNameCompat customName) {
        this.config = config;
        this.data = data;
        this.customName = customName;
    }

    public Component format(ServerPlayer player, String message) {
        String uuid = player.getStringUUID();

        int manualStars = data.getManualStars(uuid);
        int playtimeStars = data.getPlaytimeStars(player);
        int totalStars = Math.min(manualStars + playtimeStars, config.maxStars);

        MutableComponent playerDisplay = Component.empty();
        Component prefix = customName.getPrefix(player);
        if (prefix != null && !prefix.getString().isEmpty()) {
            playerDisplay.append(prefix);
        }
        playerDisplay.append(customName.getNickname(player));
        Component suffix = customName.getSuffix(player);
        if (suffix != null && !suffix.getString().isEmpty()) {
            playerDisplay.append(suffix);
        }

        boolean useAdminColor = data.isAdminColorEnabled(uuid, config.adminColor)
                && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

        String processed = EmojiReplacer.apply(config, message);
        boolean hasRichContent = MarkdownRenderer.contains(config, processed)
                || (config.itemDisplayEnabled && processed.contains(ItemDisplayRenderer.TAG));

        MutableComponent messageComp;
        if (useAdminColor && !hasRichContent) {
            messageComp = Component.literal(processed).withStyle(ChatFormatting.RED);
        } else if (totalStars >= config.rainbowThreshold && !hasRichContent) {
            messageComp = buildRainbowMessage(processed);
        } else {
            ChatFormatting baseColor = useAdminColor ? ChatFormatting.RED : ChatFormatting.WHITE;
            messageComp = ItemDisplayRenderer.render(player, processed, baseColor, config.markdownEnabled);
        }

        return Component.empty()
                .append(buildStarBlock(totalStars))
                .append(Component.literal(" "))
                .append(Component.literal("<").withStyle(ChatFormatting.GRAY))
                .append(playerDisplay)
                .append(Component.literal(">").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(messageComp);
    }

    private MutableComponent buildStarBlock(int stars) {
        if (stars <= 0) {
            return Component.literal("0※").withStyle(ChatFormatting.GRAY);
        }

        int displayStars = Math.min(stars, config.maxStars);
        ChatFormatting color = getStarColor(stars);

        if (color == null) {
            MutableComponent result = Component.empty();
            for (int i = 0; i < Math.min(displayStars, 50); i++) {
                result.append(Component.literal("※").withStyle(RAINBOW[i % RAINBOW.length]));
            }
            if (displayStars > 50) {
                result.append(Component.literal("...").withStyle(ChatFormatting.GRAY));
            }
            return result;
        }

        return Component.literal("※".repeat(Math.min(displayStars, 50))).withStyle(color);
    }

    private ChatFormatting getStarColor(int stars) {
        if (stars < 20) return parseColor(config.starsColor0);
        if (stars < 40) return parseColor(config.starsColor20);
        if (stars < 60) return parseColor(config.starsColor40);
        if (stars < 80) return parseColor(config.starsColor60);
        if (stars < 100) return parseColor(config.starsColor80);
        if (stars < config.rainbowThreshold) return parseColor(config.starsColor100);
        return null;
    }

    private ChatFormatting parseColor(String colorName) {
        if ("RAINBOW".equalsIgnoreCase(colorName)) {
            return null;
        }
        try {
            return ChatFormatting.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatFormatting.GRAY;
        }
    }

    private MutableComponent buildRainbowMessage(String message) {
        MutableComponent result = Component.empty();

        int maxLength = Math.min(message.length(), 200);
        for (int i = 0; i < maxLength; i++) {
            result.append(Component.literal(String.valueOf(message.charAt(i)))
                    .withStyle(RAINBOW[i % RAINBOW.length]));
        }
        if (message.length() > 200) {
            result.append(Component.literal("...").withStyle(ChatFormatting.GRAY));
        }
        return result;
    }
}
