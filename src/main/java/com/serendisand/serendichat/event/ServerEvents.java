package com.serendisand.serendichat.event;

import com.serendisand.serendichat.SerendiChat;
import com.serendisand.serendichat.chat.ChatFormatter;
import com.serendisand.serendichat.chat.ChatLogger;
import com.serendisand.serendichat.chat.MentionDetector;
import com.serendisand.serendichat.chat.PrivateMessageManager;
import com.serendisand.serendichat.config.ChatConfig;
import com.serendisand.serendichat.data.PlayerDataManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ServerEvents {

    private static final SoundEvent ANVIL_LAND = BuiltInRegistries.SOUND_EVENT.getValue(
            Identifier.tryParse("minecraft:block.anvil.land"));

    private final ChatConfig config;
    private final PlayerDataManager data;
    private final ChatFormatter formatter;
    /** reload 会换新 ChatLogger 实例，通过 supplier 始终取最新的。 */
    private final Supplier<ChatLogger> chatLogger;
    private final PrivateMessageManager pm;

    public ServerEvents(ChatConfig config, PlayerDataManager data, ChatFormatter formatter,
                        Supplier<ChatLogger> chatLogger, PrivateMessageManager pm) {
        this.config = config;
        this.data = data;
        this.formatter = formatter;
        this.chatLogger = chatLogger;
        this.pm = pm;
    }

    public void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            data.loadAll();
            SerendiChat.LOGGER.info("All data loaded successfully");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // 关闭前把所有挂起的异步写入落盘
            data.shutdownAndSave();
            SerendiChat.LOGGER.info("All data saved successfully");
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player != null) {
                data.onJoin(player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player != null) {
                data.onDisconnect(player);
                pm.onDisconnect(player);
            }
        });

        // 取消原生聊天广播，改为广播格式化后的消息
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, bound) -> {
            try {
                if (sender == null) {
                    return true;
                }

                String rawMessage = message.decoratedContent().getString();
                if (rawMessage == null || rawMessage.isEmpty()) {
                    return true;
                }

                // 反垃圾冷却
                if (!data.checkCooldown(sender, config.spamCooldownSeconds)) {
                    return false;
                }

                Component formattedMessage = formatter.format(sender, rawMessage);

                MinecraftServer server = sender.level().getServer();
                if (server != null) {
                    server.getPlayerList().broadcastSystemMessage(formattedMessage, false);

                    // 给被提及玩家播放铁砧音效 + actionbar 提示（仅本人可见/可听）
                    if (config.mentionEnabled) {
                        notifyMentions(server, sender, rawMessage);
                    }
                }

                // 日志记录（纯文本）
                chatLogger.get().log(sender.getScoreboardName(), rawMessage);

                data.updatePlayTime(sender);
                return false;
            } catch (Exception e) {
                SerendiChat.LOGGER.error("Failed to format chat message", e);
                return true;
            }
        });
    }

    /** 给所有被提及（非发送者本人）的玩家播放铁砧音效并显示 actionbar 提示。 */
    private void notifyMentions(MinecraftServer server, ServerPlayer sender, String message) {
        List<MentionDetector.Mention> mentions = MentionDetector.find(server, message);
        if (mentions.isEmpty()) return;

        Set<java.util.UUID> notified = new HashSet<>();
        // 提示内容只与发送者有关，循环外构建一次
        MutableComponent tip = Component.empty()
                .append(Component.literal("[!] ")
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(sender.getScoreboardName())
                        .withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" 在聊天中提及了你")
                        .withStyle(ChatFormatting.YELLOW));

        for (MentionDetector.Mention mention : mentions) {
            ServerPlayer target = mention.target();
            if (target.getUUID().equals(sender.getUUID())) continue;
            if (!notified.add(target.getUUID())) continue;

            if (config.mentionSoundEnabled && ANVIL_LAND != null) {
                // 26.x: Player.playSound(SoundEvent, float, float)
                target.playSound(ANVIL_LAND, 0.4f, 1.0f);
            }

            target.sendOverlayMessage(tip);
        }
    }
}
