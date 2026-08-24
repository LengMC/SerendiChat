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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

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

                    // 给被 @ 玩家单独播放提示音（仅本人听到）
                    if (config.mentionEnabled && config.mentionSoundEnabled) {
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

    /** 给所有被 @ 且非发送者本人的玩家播放提示音。 */
    private void notifyMentions(MinecraftServer server, ServerPlayer sender, String message) {
        List<MentionDetector.Mention> mentions = MentionDetector.find(server, message);
        if (mentions.isEmpty()) return;

        Set<java.util.UUID> notified = new HashSet<>();
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(
                net.minecraft.resources.ResourceLocation.parse("minecraft:entity.experience_orb.pickup"));
        if (event == null) return;

        for (MentionDetector.Mention mention : mentions) {
            if (notified.add(mention.target().getUUID())
                    && !mention.target().getUUID().equals(sender.getUUID())) {
                mention.target().playSound(event, SoundSource.PLAYERS, 0.6f, 1.6f);
            }
        }
    }
}
