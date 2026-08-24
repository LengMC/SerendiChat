package com.serendisand.serendichat.event;

import com.serendisand.serendichat.SerendiChat;
import com.serendisand.serendichat.chat.ChatFormatter;
import com.serendisand.serendichat.chat.ChatLogger;
import com.serendisand.serendichat.chat.MentionDetector;
import com.serendisand.serendichat.config.ChatConfig;
import com.serendisand.serendichat.data.PlayerDataManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ServerEvents {

    private final ChatConfig config;
    private final PlayerDataManager data;
    private final ChatFormatter formatter;
    private final ChatLogger chatLogger;

    public ServerEvents(ChatConfig config, PlayerDataManager data, ChatFormatter formatter, ChatLogger chatLogger) {
        this.config = config;
        this.data = data;
        this.formatter = formatter;
        this.chatLogger = chatLogger;
    }

    public void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            data.loadAll();
            SerendiChat.LOGGER.info("All data loaded successfully");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            data.saveAll();
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
                chatLogger.log(sender.getScoreboardName(), rawMessage);

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
        net.minecraft.sounds.SoundEvent event = config.mentionSoundEnabled
                ? BuiltInRegistries.SOUND_EVENT.getValue(
                        net.minecraft.resources.Identifier.tryParse("minecraft:block.anvil.land"))
                : null;

        for (MentionDetector.Mention mention : mentions) {
            ServerPlayer target = mention.target();
            if (target.getUUID().equals(sender.getUUID())) continue;
            if (!notified.add(target.getUUID())) continue;

            // 铁砧提示音
            if (event != null) {
                // 26.x: Player.playSound(SoundEvent, float, float)
                target.playSound(event, 0.4f, 1.0f);
            }

            // actionbar: [!] xxx 在聊天中提及了你
            MutableComponent tip = net.minecraft.network.chat.Component.empty()
                    .append(net.minecraft.network.chat.Component.literal("[!] ")
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                    .append(net.minecraft.network.chat.Component.literal(sender.getScoreboardName())
                            .withStyle(ChatFormatting.GOLD))
                    .append(net.minecraft.network.chat.Component.literal(" 在聊天中提及了你")
                            .withStyle(ChatFormatting.YELLOW));
            target.sendOverlayMessage(tip);
        }
    }
}
