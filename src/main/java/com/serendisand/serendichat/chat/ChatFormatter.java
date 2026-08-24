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

        boolean hasRichContent = MarkdownRenderer.contains(config, processed)
                || (config.itemDisplayEnabled && processed.contains(ItemDisplayRenderer.TAG))
                || hasMentions(player, processed)
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
            messageComp = renderRichMessage(player, processed, baseColor);
        }

        return Component.empty()
                .append(buildStarBlock(totalStars))
                .append(Component.literal(" "))
                .append(buildPlayerBlock(player, manualStars, playtimeStars))
                .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(messageComp);
    }

    // ===================== 星标块 =====================

    private MutableComponent buildStarBlock(int stars) {
        MutableComponent inner = buildStarInner(stars);
        if (!config.starBracketEnabled) {
            return inner;
        }
        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(inner)
                .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
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

    // ===================== 玩家名块 =====================

    /**
     * 构建玩家显示名（含 prefix/nickname/suffix 空格分隔），
     * 并叠加点击私信 + 悬停信息两个交互事件。
     */
    private MutableComponent buildPlayerBlock(ServerPlayer player, int manualStars, int playtimeStars) {
        Component prefix = customName.getPrefix(player);
        Component nickname = customName.getNickname(player);
        Component suffix = customName.getSuffix(player);
        boolean hasPrefix = !prefix.getString().isEmpty();
        boolean hasSuffix = !suffix.getString().isEmpty();

        MutableComponent name = Component.empty();
        if (hasPrefix) {
            name.append(prefix).append(Component.literal(" "));
        }
        name.append(nickname);
        if (hasSuffix) {
            name.append(Component.literal(" ")).append(suffix);
        }

        Style style = Style.EMPTY;
        if (config.clickToMsgEnabled) {
            String target = player.getScoreboardName();
            String command = config.msgCommandTemplate.replace("{player}", target);
            style = style.withClickEvent(new ClickEvent.SuggestCommand(command));
        }
        style = style.withHoverEvent(new HoverEvent.ShowText(
                buildPlayerHover(player, manualStars, playtimeStars)));
        name = name.withStyle(style);

        if (!config.nameBracketEnabled) {
            return name;
        }
        return Component.empty()
                .append(Component.literal("<").withStyle(ChatFormatting.GRAY))
                .append(name)
                .append(Component.literal(">").withStyle(ChatFormatting.GRAY));
    }

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

    private boolean hasMentions(ServerPlayer player, String text) {
        return config.mentionEnabled
                && !MentionDetector.find(player.level().getServer(), text).isEmpty();
    }

    private boolean hasUrls(String text) {
        return config.urlClickEnabled && !UrlDetector.find(text).isEmpty();
    }

    /**
     * 富文本渲染：mention / url / [item] / markdown 全部按位置切片渲染，
     * 切片按出现位置排序、依次输出。
     */
    private MutableComponent renderRichMessage(ServerPlayer player, String message, ChatFormatting baseColor) {
        List<Segment> segments = collectSegments(player, message);

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
            cursor = seg.end;
        }
        if (cursor < message.length()) {
            out.append(ItemDisplayRenderer.render(player,
                    message.substring(cursor), baseColor, config.markdownEnabled));
        }
        return out;
    }

    private List<Segment> collectSegments(ServerPlayer player, String message) {
        List<Segment> segments = new ArrayList<>();

        if (config.itemDisplayEnabled) {
            int from = 0;
            int idx;
            while ((idx = message.indexOf(ItemDisplayRenderer.TAG, from)) >= 0) {
                segments.add(Segment.item(idx, idx + ItemDisplayRenderer.TAG.length()));
                from = idx + ItemDisplayRenderer.TAG.length();
            }
        }

        if (config.mentionEnabled) {
            for (MentionDetector.Mention m : MentionDetector.find(player.level().getServer(), message)) {
                segments.add(Segment.mention(m.start(), m.end(), m.target(), m.text()));
            }
        }

        if (config.urlClickEnabled) {
            for (UrlDetector.UrlMatch u : UrlDetector.find(message)) {
                segments.add(Segment.url(u.start(), u.end(), u.url()));
            }
        }

        // 按 start 升序；同位置时优先长度大的（更具体的匹配）
        segments.sort(Comparator.<Segment>comparingInt(s -> s.start).thenComparingInt(s -> -(s.end - s.start)));
        return segments;
    }

    // ===================== 段渲染 =====================

    /** 抽象的"在消息中占据 [start,end) 的特殊片段"。 */
    private static final class Segment {
        final int start;
        final int end;
        final Kind kind;
        // mention / url 数据
        final ServerPlayer mentionTarget;
        final String mentionText;
        final String url;

        private Segment(int start, int end, Kind kind, ServerPlayer mentionTarget, String mentionText, String url) {
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.mentionTarget = mentionTarget;
            this.mentionText = mentionText;
            this.url = url;
        }

        static Segment item(int s, int e) {
            return new Segment(s, e, Kind.ITEM, null, null, null);
        }

        static Segment mention(int s, int e, ServerPlayer target, String text) {
            return new Segment(s, e, Kind.MENTION, target, text, null);
        }

        static Segment url(int s, int e, String url) {
            return new Segment(s, e, Kind.URL, null, null, url);
        }

        MutableComponent render(ChatFormatter f, ServerPlayer player, String message, ChatFormatting baseColor) {
            return switch (kind) {
                case ITEM -> ItemDisplayRenderer.render(player,
                        message.substring(start, end), baseColor, f.config.markdownEnabled);
                case MENTION -> {
                    MutableComponent comp = Component.literal(mentionText)
                            .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
                    String cmd = f.config.msgCommandTemplate.replace("{player}", mentionTarget.getScoreboardName());
                    comp = comp.withStyle(style -> style
                            .withClickEvent(new ClickEvent.SuggestCommand(cmd))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("点击私信 " + mentionTarget.getScoreboardName()))));
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
