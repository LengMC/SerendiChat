package com.serendisand.serendichat.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单 URL 检测 + 截断。
 * 支持 http(s):// 和 www. 开头，结尾为常见的 URL 安全字符。
 * 注意：截断后必须保留原文长度以便与原字符串位置对齐（避免错位渲染）。
 */
public final class UrlDetector {

    private static final Pattern PATTERN = Pattern.compile(
            "(?i)\\b((?:https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)");

    private UrlDetector() {
    }

    public static List<UrlMatch> find(String message) {
        List<UrlMatch> out = new ArrayList<>();
        if (message == null || message.isEmpty()) {
            return out;
        }
        Matcher m = PATTERN.matcher(message);
        while (m.find()) {
            String url = m.group(1);
            // 截断尾部常见标点（避免 "...bla." 中的点被吞掉）
            int trim = 0;
            while (trim < url.length()) {
                char c = url.charAt(url.length() - 1 - trim);
                if (c == ')' || c == ']' || c == '.' || c == ',' || c == ';' || c == ':') {
                    trim++;
                } else {
                    break;
                }
            }
            if (trim >= url.length()) {
                // URL 全部都是标点（理论不会发生），跳过
                continue;
            }
            int matchEnd = m.end() - trim;
            String trimmed = url.substring(0, url.length() - trim);
            out.add(new UrlMatch(m.start(), matchEnd, trimmed));
        }
        return out;
    }

    /** 标准化 URL：缺协议的 www. 自动补 https:// 用于点击事件。 */
    public static String normalizeForClick(String url) {
        if (url.regionMatches(true, 0, "www.", 0, 4)) {
            return "https://" + url;
        }
        return url;
    }

    public record UrlMatch(int start, int end, String url) {}
}
