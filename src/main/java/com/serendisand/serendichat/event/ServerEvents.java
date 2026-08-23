package com.serendisand.serendichat.event;

import com.serendisand.serendichat.SerendiChat;
import com.serendisand.serendichat.chat.ChatFormatter;
import com.serendisand.serendichat.data.PlayerDataManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

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

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            try {
                if (sender == null) {
                    return;
                }

                String rawMessage = extractRawMessage(message);
                if (rawMessage == null || rawMessage.isEmpty()) {
                    return;
                }

                Component formattedMessage = formatter.format(sender, rawMessage);

                try {
                    Method setMessageMethod = params.getClass().getMethod("setMessage", Component.class);
                    setMessageMethod.invoke(params, formattedMessage);
                } catch (Exception e) {
                    try {
                        java.lang.reflect.Field field = params.getClass().getDeclaredField("message");
                        field.setAccessible(true);
                        field.set(params, formattedMessage);
                    } catch (Exception ex) {
                        SerendiChat.LOGGER.error("Failed to set formatted message", ex);
                    }
                }

                data.updatePlayTime(sender);

            } catch (Exception e) {
                SerendiChat.LOGGER.error("Failed to format chat message", e);
            }
        });
    }

    private String extractRawMessage(Object message) {
        try {
            Object content = message.getClass().getMethod("getContent").invoke(message);
            if (content instanceof Component) {
                return ((Component) content).getString();
            }
        } catch (Exception ignored) {
        }
        try {
            Method getStringMethod = message.getClass().getMethod("getString");
            return (String) getStringMethod.invoke(message);
        } catch (Exception ignored) {
        }
        try {
            return message.toString();
        } catch (Exception e) {
            SerendiChat.LOGGER.warn("Failed to get message content");
            return null;
        }
    }
}
