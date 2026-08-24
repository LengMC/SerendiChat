package com.serendisand.serendichat.chat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检测消息中的 @玩家名 提及。
 * 玩家名遵循 Minecraft 用户名规则：3-16 个字母/数字/下划线。
 * 为了避免邮箱误匹配，@ 必须是词首或空白之后。
 */
public final class MentionDetector {

    /** (?<![A-Za-z0-9_]) 排除邮箱地址；@ 之后 3-16 位合法用户名。 */
    private static final Pattern PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_])@([A-Za-z0-9_]{3,16})");

    private MentionDetector() {
    }

    public static List<Mention> find(MinecraftServer server, String message) {
        List<Mention> out = new ArrayList<>();
        if (server == null || message == null || message.isEmpty()) {
            return out;
        }
        Matcher m = PATTERN.matcher(message);
        while (m.find()) {
            String name = m.group(1);
            ServerPlayer target = server.getPlayerList().getPlayerByName(name);
            if (target != null) {
                out.add(new Mention(m.start(), m.end(), name, target));
            }
        }
        return out;
    }

    public record Mention(int start, int end, String name, ServerPlayer target) {}
}
