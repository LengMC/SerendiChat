package com.serendisand.serendichat.chat;

import com.serendisand.serendichat.compat.CustomNameCompat;
import com.serendisand.serendichat.config.ChatConfig;
import com.serendisand.serendichat.data.PlayerDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 消息格式化编排：星标前缀 + 玩家名 + 富文本消息（emoji/markdown/[item]/mention/url）。
 * 输出示例: [120※] <Prefix Nickname Suffix> -> 消息
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

        String processed = EmojiReplacer.apply(config, message);

        // 提及只查一次，富文本判断与分段渲染共用结果
        List<MentionDetector.Mention> mentions = config.mentionEnabled
                ? MentionDetector.find(player.level().getServer(), processed)
                : List.<MentionDetector.Mention>of();

        boolean hasRichContent = MarkdownRenderer.contains(config, processed)
                || (config.itemDisplayEnabled && processed.contains(ItemDisplayRenderer.TAG))
                || !mentions.isEmpty()
                || hasUrls(processed);

        boolean useAdminColor = data.isAdminColorEnabled(uuid, config.adminColor)
                && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

        MutableComponent messageComp;
        if (useAdminColor && !hasRichContent) {
            // 管理员纯红字：按 codepoint 着色以兼容 emoji
            messageComp = MarkdownRenderer.renderByCodepoint(processed, new ChatFormatting[]{ChatFormatting.RED});
        } else if (totalStars >= config.rainbowThreshold && !hasRichContent) {
            messageComp = MarkdownRenderer.renderByCodepoint(processed, RAINBOW);
        } else {
            ChatFormatting baseColor = useAdminColor ? ChatFormatting.RED : ChatFormatting.WHITE;
            messageComp = renderRichMessage(player, processed, baseColor, mentions);
        }

        MutableComponent starsComp = buildStarBlock(totalStars);
        // 提供多个独立占位符 + 一个合并的 {player}，用户可按需选用
        Component prefixComp = customName.getPrefix(player);
        Component nicknameComp = customName.getNickname(player);
        Component suffixComp = customName.getSuffix(player);

        MutableComponent playerComp = Component.empty();
        if (!prefixComp.getString().isEmpty()) {
            playerComp.append(prefixComp).append(Component.literal(" "));
        }
        playerComp.append(nicknameComp);
        if (!suffixComp.getString().isEmpty()) {
            playerComp.append(Component.literal(" ")).append(suffixComp);
        }
        // {player} 仍带点击私信 + 悬停星数明细；prefix/nickname/suffix 不附带（用户可自由排版）
        // 注意保留 lambda 入参的现有样式（CustomName API 可能带颜色/样式），不能从 EMPTY 起步
        playerComp = playerComp.withStyle(style -> {
            Style s = style;
            if (config.clickToMsgEnabled) {
                String cmd = config.msgCommandTemplate.replace("{player}", player.getScoreboardName());
                s = s.withClickEvent(new ClickEvent.SuggestCommand(cmd));
            }
            return s.withHoverEvent(new HoverEvent.ShowText(
                    buildPlayerHover(player, manualStars, playtimeStars)));
        });

        java.util.Map<String, MutableComponent> parts = new java.util.LinkedHashMap<>();
        parts.put("{stars}", starsComp);
        parts.put("{prefix}", prefixComp.copy());
        parts.put("{nickname}", nicknameComp.copy());
        parts.put("{suffix}", suffixComp.copy());
        parts.put("{player}", playerComp);
        parts.put("{message}", messageComp);
        return applyTemplate(config.chatFormat, parts);
    }

    /**
     * 用占位符模板把多个 Component 拼成完整消息。
     * 模板外的字面量字符按装饰色 DARK_GRAY 渲染。
     * parts 为占位符名 → 渲染组件 的映射；模板里出现但 parts 里没有的占位符会被忽略（按字面量渲染）。
     * 若模板里出现的占位符全部都不在 parts 中（例如拼写错误），回落到默认模板以避免静默坏掉输出。
     */
    static MutableComponent applyTemplate(String template, java.util.Map<String, MutableComponent> parts) {
        // 先扫描一遍：找出模板中真实出现的占位符；只要有至少一个匹配的，就信任模板
        java.util.Set<String> used = new java.util.LinkedHashSet<>();
        for (String key : parts.keySet()) {
            if (template.indexOf(key) >= 0) used.add(key);
        }
        String tmpl = used.isEmpty() ? defaultTemplate(parts.keySet()) : template;

        MutableComponent out = Component.empty();
        int cursor = 0;
        int len = tmpl.length();
        while (cursor < len) {
            int nextPos = -1;
            String nextKey = null;
            // 同时出现位置相同时按占位符长度优先（避免 {from} 抢走 {from_prefix} 的匹配）
            for (String key : used.isEmpty() ? parts.keySet() : used) {
                int idx = tmpl.indexOf(key, cursor);
                if (idx < 0) continue;
                if (nextPos < 0 || idx < nextPos
                        || (idx == nextPos && key.length() > nextKey.length())) {
                    nextPos = idx;
                    nextKey = key;
                }
            }
            if (nextPos < 0) {
                appendLiteral(out, tmpl, cursor, len);
                break;
            }
            appendLiteral(out, tmpl, cursor, nextPos);
            out.append(parts.get(nextKey));
            cursor = nextPos + nextKey.length();
        }
        return out;
    }

    private static String defaultTemplate(java.util.Set<String> keys) {
        // 兼容占位符集合的默认模板；有 {player} 时直接用合并名，否则用 prefix/nickname/suffix
        StringBuilder sb = new StringBuilder("[");
        if (keys.contains("{stars}")) sb.append("{stars}");
        sb.append("] ");
        if (keys.contains("{player}")) {
            sb.append("{player}");
        } else {
            if (keys.contains("{prefix}")) sb.append("{prefix} ");
            if (keys.contains("{nickname}")) sb.append("{nickname}");
            if (keys.contains("{suffix}")) sb.append(" {suffix}");
        }
        sb.append(" -> ");
        if (keys.contains("{message}")) sb.append("{message}");
        return sb.toString();
    }

    /** 把模板里 [from, to) 范围的字面量按装饰色追加到组件；空范围直接跳过。 */
    private static void appendLiteral(MutableComponent out, String template, int from, int to) {
        if (to <= from) return;
        out.append(Component.literal(template.substring(from, to)).withStyle(ChatFormatting.DARK_GRAY));
    }

    // ===================== 星标块 =====================

    /** 仅渲染星标文本本身（含 ※ 与分档配色），不再自带括号——括号由模板控制。 */
    private MutableComponent buildStarBlock(int stars) {
        return buildStarInner(stars);
    }

    private MutableComponent buildStarInner(int stars) {
        String text = stars + "※";
        ChatFormatting color = getStarColor(stars);

        if (color == null) {
            return MarkdownRenderer.renderByCodepoint(text, RAINBOW);
        }
        return Component.literal(text).withStyle(color);
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

    // ===================== 玩家名辅助 =====================

    private Component buildPlayerHover(ServerPlayer player, int manualStars, int playtimeStars) {
        MutableComponent hover = Component.empty();
        hover.append(Component.literal(player.getScoreboardName()).withStyle(ChatFormatting.GREEN));
        hover.append(Component.literal("\n手动星数: ").withStyle(ChatFormatting.GRAY));
        hover.append(Component.literal(String.valueOf(manualStars)).withStyle(ChatFormatting.YELLOW));
        hover.append(Component.literal("  在线奖励: ").withStyle(ChatFormatting.GRAY));
        hover.append(Component.literal(String.valueOf(playtimeStars)).withStyle(ChatFormatting.YELLOW));
        hover.append(Component.literal("\n点击发起私信").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return hover;
    }

    // ===================== 富文本消息 =====================

    private boolean hasUrls(String text) {
        return config.urlClickEnabled && !UrlDetector.find(text).isEmpty();
    }

    /**
     * 富文本渲染：mention / url / [item] / markdown 全部按位置切片渲染，
     * 切片按出现位置排序、依次输出。
     */
    private MutableComponent renderRichMessage(ServerPlayer player, String message, ChatFormatting baseColor,
                                               List<MentionDetector.Mention> mentions) {
        List<Segment> segments = collectSegments(player, message, mentions);

        if (segments.isEmpty()) {
            return ItemDisplayRenderer.render(player, message, baseColor, config.markdownEnabled);
        }

        MutableComponent out = Component.empty();
        int cursor = 0;
        for (Segment seg : segments) {
            if (seg.start > cursor) {
                out.append(ItemDisplayRenderer.render(player,
                        message.substring(cursor, seg.start), baseColor, config.markdownEnabled));
            }
            out.append(seg.render(this, player, message, baseColor));
            cursor = Math.max(cursor, seg.end);
        }
        if (cursor < message.length()) {
            out.append(ItemDisplayRenderer.render(player,
                    message.substring(cursor), baseColor, config.markdownEnabled));
        }
        return out;
    }

    private List<Segment> collectSegments(ServerPlayer player, String message,
                                          List<MentionDetector.Mention> mentions) {
        List<Segment> segments = new ArrayList<>();

        if (config.itemDisplayEnabled) {
            int from = 0;
            int idx;
            while ((idx = message.indexOf(ItemDisplayRenderer.TAG, from)) >= 0) {
                segments.add(Segment.item(idx, idx + ItemDisplayRenderer.TAG.length()));
                from = idx + ItemDisplayRenderer.TAG.length();
            }
        }

        for (MentionDetector.Mention m : mentions) {
            segments.add(Segment.mention(m.start(), m.end(), m.target()));
        }

        if (config.urlClickEnabled) {
            for (UrlDetector.UrlMatch u : UrlDetector.find(message)) {
                segments.add(Segment.url(u.start(), u.end(), u.url()));
            }
        }

        // 按 start 升序；同位置时优先长度大的（更具体的匹配）
        segments.sort(Comparator.<Segment>comparingInt(s -> s.start).thenComparingInt(s -> -(s.end - s.start)));
        // 去重叠：不同检测器的区间可能交叠（如玩家名叫 "Item" 会命中 "[item]"），保留更早/更长的
        List<Segment> dedup = new ArrayList<>(segments.size());
        int lastEnd = -1;
        for (Segment s : segments) {
            if (s.start >= lastEnd) {
                dedup.add(s);
                lastEnd = s.end;
            }
        }
        return dedup;
    }

    // ===================== 段渲染 =====================

    /** 抽象的"在消息中占据 [start,end) 的特殊片段"。 */
    private static final class Segment {
        final int start;
        final int end;
        final Kind kind;
        // mention / url 数据
        final ServerPlayer mentionTarget;
        final String url;

        private Segment(int start, int end, Kind kind, ServerPlayer mentionTarget, String url) {
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.mentionTarget = mentionTarget;
            this.url = url;
        }

        static Segment item(int s, int e) {
            return new Segment(s, e, Kind.ITEM, null, null);
        }

        static Segment mention(int s, int e, ServerPlayer target) {
            return new Segment(s, e, Kind.MENTION, target, null);
        }

        static Segment url(int s, int e, String url) {
            return new Segment(s, e, Kind.URL, null, url);
        }

        MutableComponent render(ChatFormatter f, ServerPlayer player, String message, ChatFormatting baseColor) {
            return switch (kind) {
                case ITEM -> ItemDisplayRenderer.render(player,
                        message.substring(start, end), baseColor, f.config.markdownEnabled);
                case MENTION -> {
                    // 无论输入是 "ID" 还是 "@ID"，输出都强制统一为 "@ID"（只保留一个 @）
                    String targetName = mentionTarget.getScoreboardName();
                    MutableComponent comp = Component.literal("@" + targetName)
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
                    String cmd = f.config.msgCommandTemplate.replace("{player}", targetName);
                    comp = comp.withStyle(style -> style
                            .withClickEvent(new ClickEvent.SuggestCommand(cmd))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("点击私信 " + targetName))));
                    yield comp;
                }
                case URL -> {
                    String display = message.substring(start, end);
                    MutableComponent link = Component.literal(display)
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE)
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent.OpenUrl(
                                            URI.create(UrlDetector.normalizeForClick(url)))));
                    yield link;
                }
            };
        }

        enum Kind { ITEM, MENTION, URL }
    }
}
