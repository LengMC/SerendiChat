package com.serendisand.serendichat.compat;

import com.serendisand.serendichat.SerendiChat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

public class CustomNameCompat {

    private final Class<?> apiClass;
    private final Method getPrefixMethod;
    private final Method getDisplayNicknameMethod;
    private final Method getSuffixMethod;
    private final boolean available;

    private CustomNameCompat(Class<?> apiClass, Method getPrefixMethod,
                             Method getDisplayNicknameMethod, Method getSuffixMethod, boolean available) {
        this.apiClass = apiClass;
        this.getPrefixMethod = getPrefixMethod;
        this.getDisplayNicknameMethod = getDisplayNicknameMethod;
        this.getSuffixMethod = getSuffixMethod;
        this.available = available;
    }

    public static CustomNameCompat detect() {
        try {
            Class<?> apiClass = Class.forName("xyz.eclipseisoffline.eclipsescustomname.api.CustomNameApi");
            try {
                CustomNameCompat compat = new CustomNameCompat(apiClass,
                        apiClass.getMethod("getPrefix", ServerPlayer.class),
                        apiClass.getMethod("getDisplayNickname", ServerPlayer.class),
                        apiClass.getMethod("getSuffix", ServerPlayer.class),
                        true);
                SerendiChat.LOGGER.info("CustomName API detected successfully");
                return compat;
            } catch (NoSuchMethodException e) {
                SerendiChat.LOGGER.warn("CustomName API found but methods not compatible: {}", e.getMessage());
                try {
                    CustomNameCompat compat = new CustomNameCompat(apiClass, null,
                            apiClass.getMethod("getDisplayName", ServerPlayer.class), null, true);
                    SerendiChat.LOGGER.info("Using alternative CustomName API methods");
                    return compat;
                } catch (NoSuchMethodException ex) {
                    SerendiChat.LOGGER.warn("Alternative methods also not found");
                    return notAvailable();
                }
            }
        } catch (ClassNotFoundException e) {
            SerendiChat.LOGGER.info("CustomName API not found, using vanilla names");
            return notAvailable();
        } catch (Exception e) {
            SerendiChat.LOGGER.warn("Failed to initialize CustomName API: {}", e.getMessage());
            return notAvailable();
        }
    }

    private static CustomNameCompat notAvailable() {
        return new CustomNameCompat(null, null, null, null, false);
    }

    public boolean available() {
        return available;
    }

    public Component getPrefix(ServerPlayer player) {
        if (!available || getPrefixMethod == null) {
            return Component.empty();
        }
        try {
            Object result = getPrefixMethod.invoke(null, player);
            if (result instanceof Component) {
                return (Component) result;
            }
        } catch (Exception e) {
            SerendiChat.LOGGER.debug("Failed to get player prefix: {}", e.getMessage());
        }
        return Component.empty();
    }

    public Component getNickname(ServerPlayer player) {
        if (!available) {
            return Component.literal(player.getScoreboardName());
        }
        try {
            Method method = getDisplayNicknameMethod != null ? getDisplayNicknameMethod : getPrefixMethod;
            if (method != null) {
                Object result = method.invoke(null, player);
                if (result instanceof Component) {
                    return (Component) result;
                }
            }
        } catch (Exception e) {
            SerendiChat.LOGGER.debug("Failed to get player nickname: {}", e.getMessage());
        }
        return Component.literal(player.getScoreboardName());
    }

    public Component getSuffix(ServerPlayer player) {
        if (!available || getSuffixMethod == null) {
            return Component.empty();
        }
        try {
            Object result = getSuffixMethod.invoke(null, player);
            if (result instanceof Component) {
                return (Component) result;
            }
        } catch (Exception e) {
            SerendiChat.LOGGER.debug("Failed to get player suffix: {}", e.getMessage());
        }
        return Component.empty();
    }
}
