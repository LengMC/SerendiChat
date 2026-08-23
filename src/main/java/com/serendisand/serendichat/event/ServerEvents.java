package com.serendisand.serendichat.event;

import com.serendisand.serendichat.SerendiChat;
import com.serendisand.serendichat.chat.ChatFormatter;
import com.serendisand.serendichat.data.PlayerDataManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ServerEvents {

    private final PlayerDataManager data;
    private final ChatFormatter formatter;

    public ServerEvents(PlayerDataManager data, ChatFormatter formatter) {
        this.data = data;
        this.formatter = formatter;
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

                Component formattedMessage = formatter.format(sender, rawMessage);

                MinecraftServer server = sender.level().getServer();
                if (server != null) {
                    server.getPlayerList().broadcastSystemMessage(formattedMessage, false);
                }

                data.updatePlayTime(sender);
                return false;
            } catch (Exception e) {
                SerendiChat.LOGGER.error("Failed to format chat message", e);
                return true;
            }
        });
    }
}
