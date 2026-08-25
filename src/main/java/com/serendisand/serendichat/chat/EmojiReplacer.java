package com.serendisand.serendichat.chat;

import com.serendisand.serendichat.config.ChatConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /**
     * 缓存：键为 config.emojis 引用（ChatConfig 字段引用稳定），值为按键长倒序的 key 列表。
     * 用 ConcurrentHashMap 而不是 WeakHashMap：引用稳定，无需 GC 语义。
     */
    private static final Map<Map<String, String>, List<String>> SORTED_KEY_CACHE = new ConcurrentHashMap<>();

    private EmojiReplacer() {
    }

    public static String apply(ChatConfig config, String input) {
        if (!config.emojiEnabled || input.isEmpty()) {
            return input;
        }
        Map<String, String> custom = config.emojis;
        // 默认键集为空（用户没改过）——直接复用静态表 + 缓存
        Map<String, String> source;
        List<String> keys;
        if (custom == null || custom.isEmpty()) {
            source = DEFAULT_EMOJIS;
            keys = sortedKeysOf(DEFAULT_EMOJIS);
        } else {
            // 自定义键集：合并视图只构建一次（不可变引用，hashCode 稳定可作缓存键）
            source = merge(custom);
            keys = SORTED_KEY_CACHE.computeIfAbsent(source, EmojiReplacer::sortKeysByLengthDesc);
        }
        String out = input;
        for (String key : keys) {
            String value = source.get(key);
            if (value != null && !value.isEmpty()) {
                out = out.replace(key, value);
            }
        }
        return out;
    }

    /** 清空缓存（热重载时调用）。 */
    public static void invalidateCache() {
        SORTED_KEY_CACHE.clear();
    }

    // ----- 内部 -----

    private static List<String> sortedKeysOf(Map<String, String> map) {
        return SORTED_KEY_CACHE.computeIfAbsent(map, EmojiReplacer::sortKeysByLengthDesc);
    }

    private static Map<String, String> merge(Map<String, String> custom) {
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
