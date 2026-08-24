package com.serendisand.serendichat.chat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 检测消息中的玩家提及。
 * 直接输入玩家 ID 即可触发（兼容 @ID 写法），
 * 名字前后不能紧跟字母/数字/下划线，避免误匹配更长的单词或邮箱。
 */
public final class MentionDetector {

    private MentionDetector() {
    }

    public static List<Mention> find(MinecraftServer server, String message) {
        List<Mention> out = new ArrayList<>();
        if (server == null || message == null || message.isEmpty()) {
            return out;
        }
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            String name = target.getScoreboardName();
            if (name == null || name.isEmpty()) {
                continue;
            }
            // @ 可有可无；名字按字面量匹配并忽略大小写；边界检查防止匹配进更长单词
            Pattern p = Pattern.compile(
                    "(?<![A-Za-z0-9_])@?" + Pattern.quote(name) + "(?![A-Za-z0-9_])",
                    Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(message);
            while (m.find()) {
                out.add(new Mention(m.start(), m.end(), m.group(), target));
            }
        }
        // 去重叠：位置靠前优先，同一位置取更长的匹配
        out.sort(Comparator.comparingInt(Mention::start)
                .thenComparing(Comparator.<Mention>comparingInt(x -> x.end() - x.start()).reversed()));
        List<Mention> dedup = new ArrayList<>();
        int lastEnd = -1;
        for (Mention mention : out) {
            if (mention.start() >= lastEnd) {
                dedup.add(mention);
                lastEnd = mention.end();
            }
        }
        return dedup;
    }

    /** start/end 为消息中的位置；text 为实际命中的文本（可能带 @）；target 为被提及的在线玩家。 */
    public record Mention(int start, int end, String text, ServerPlayer target) {}
}
