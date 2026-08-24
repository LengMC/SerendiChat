package com.serendisand.serendichat.chat;

import com.serendisand.serendichat.config.ChatConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 短代码 emoji 替换：":heart:" → ❤ 等。
 * 原生 UTF-16 emoji（如 😭）按字符串原样透传给客户端——客户端字体不显示则是客户端问题。
 * 内置按键值长度倒序排序，避免短键误匹配长键。
 */
public final class EmojiReplacer {

    /** 默认映射，用户配置中的同名键会覆盖默认值。 */
    public static final Map<String, String> DEFAULT_EMOJIS = new LinkedHashMap<>();

    static {
        DEFAULT_EMOJIS.put("<3", "❤");
        DEFAULT_EMOJIS.put(":heart:", "❤");
        DEFAULT_EMOJIS.put(":)", "☺");
        DEFAULT_EMOJIS.put(":(", "☹");
        DEFAULT_EMOJIS.put(":star:", "★");
        DEFAULT_EMOJIS.put(":sun:", "☀");
        DEFAULT_EMOJIS.put(":moon:", "☾");
        DEFAULT_EMOJIS.put(":check:", "✔");
        DEFAULT_EMOJIS.put(":x:", "✘");
        DEFAULT_EMOJIS.put(":note:", "♪");
        DEFAULT_EMOJIS.put(":scissors:", "✂");
        DEFAULT_EMOJIS.put(":skull:", "☠");
        DEFAULT_EMOJIS.put(":warning:", "⚠");
        DEFAULT_EMOJIS.put(":sword:", "⚔");
        DEFAULT_EMOJIS.put(":bolt:", "⚡");
        DEFAULT_EMOJIS.put(":flower:", "❀");
        DEFAULT_EMOJIS.put(":snow:", "❄");
        DEFAULT_EMOJIS.put(":coffee:", "☕");
    }

    private EmojiReplacer() {
    }

    /**
     * 对输入字符串执行短代码 emoji 替换。
     * 原生 emoji 字符（如 U+1F62D 😭）按字面量透传。
     * 每次调用基于 config 缓存一份按键长倒序的列表，热重载时通过 cache() 失效。
     */
    public static String apply(ChatConfig config, String input) {
        if (!config.emojiEnabled || input.isEmpty()) {
            return input;
        }
        Map<String, String> merged = merge(config.emojis);
        List<String> keys = merged.isEmpty() ? Collections.emptyList() : sortedKeys.get(merged);
        if (keys == null) {
            keys = sortKeysByLengthDesc(merged);
            sortedKeys.put(merged, keys);
        }
        String out = input;
        for (String key : keys) {
            String value = merged.get(key);
            if (value != null && !value.isEmpty()) {
                out = out.replace(key, value);
            }
        }
        return out;
    }

    /** 清空缓存（热重载时调用）。 */
    public static void invalidateCache() {
        sortedKeys.clear();
    }

    // ----- 内部 -----

    private static final Map<Map<String, String>, List<String>> sortedKeys =
            Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static Map<String, String> merge(Map<String, String> custom) {
        if (custom == null || custom.isEmpty()) {
            return DEFAULT_EMOJIS;
        }
        Map<String, String> merged = new LinkedHashMap<>(DEFAULT_EMOJIS);
        merged.putAll(custom);
        return merged;
    }

    private static List<String> sortKeysByLengthDesc(Map<String, String> map) {
        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return keys;
    }
}
