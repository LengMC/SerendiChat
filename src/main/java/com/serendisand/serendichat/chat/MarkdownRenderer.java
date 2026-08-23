package com.serendisand.serendichat.chat;

import com.serendisand.serendichat.config.ChatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天 Markdown 渲染：**粗体**、__下划线__、~~删除线~~、*斜体*、`代码`。
 */
public final class MarkdownRenderer {

    private static final Pattern PATTERN = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|__(.+?)__|~~(.+?)~~|\\*(.+?)\\*|`(.+?)`");

    private MarkdownRenderer() {
    }

    public static boolean contains(ChatConfig config, String text) {
        return config.markdownEnabled && PATTERN.matcher(text).find();
    }

    public static MutableComponent render(String text, ChatFormatting baseColor, boolean enabled) {
        if (!enabled) {
            return styledText(text, baseColor);
        }
        MutableComponent out = Component.empty();
        Matcher m = PATTERN.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                out.append(styledText(text.substring(last, m.start()), baseColor));
            }
            String g = m.group(1);
            boolean colored = false;
            Style style = Style.EMPTY;
            if (g != null) {
                style = style.withBold(true);
            } else if ((g = m.group(2)) != null) {
                style = style.withUnderlined(true);
            } else if ((g = m.group(3)) != null) {
                style = style.withStrikethrough(true);
            } else if ((g = m.group(4)) != null) {
                style = style.withItalic(true);
            } else {
                g = m.group(5);
                style = style.withItalic(true).withColor(ChatFormatting.DARK_GRAY);
                colored = true;
            }
            if (baseColor != null && !colored) {
                style = style.withColor(baseColor);
            }
            out.append(Component.literal(g).withStyle(style));
            last = m.end();
        }
        if (last < text.length()) {
            out.append(styledText(text.substring(last), baseColor));
        }
        return out;
    }

    private static MutableComponent styledText(String s, ChatFormatting color) {
        return color == null ? Component.literal(s) : Component.literal(s).withStyle(color);
    }
}
