package com.serendisand.serendichat.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天 Markdown 渲染：**粗体**、__下划线__、~~删除线~~、*斜体*、`代码`。
 * 渲染时按 Unicode 代码点迭代，避免把 emoji 代理对拆成两个孤立的 ?。
 */
public final class MarkdownRenderer {

    private static final Pattern PATTERN = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|__(.+?)__|~~(.+?)~~|\\*(.+?)\\*|`(.+?)`");

    private MarkdownRenderer() {
    }

    public static boolean contains(com.serendisand.serendichat.config.ChatConfig config, String text) {
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

    /**
     * 按代码点（而非 UTF-16 char）遍历文本生成 Component。
     * 用于彩虹渲染等需要逐字符着色的场景，避免拆分 emoji 代理对。
     */
    public static MutableComponent renderByCodepoint(String text, ChatFormatting[] palette) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        MutableComponent out = Component.empty();
        int i = 0;
        int pi = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int charCount = Character.charCount(cp);
            ChatFormatting color = palette == null || palette.length == 0 ? null : palette[pi % palette.length];
            String chunk = text.substring(i, i + charCount);
            if (color == null) {
                out.append(Component.literal(chunk));
            } else {
                out.append(Component.literal(chunk).withStyle(color));
            }
            i += charCount;
            pi++;
        }
        return out;
    }

    private static MutableComponent styledText(String s, ChatFormatting color) {
        return color == null ? Component.literal(s) : Component.literal(s).withStyle(color);
    }
}
