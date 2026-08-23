package com.serendisand.serendichat.chat;

import com.serendisand.serendichat.config.ChatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public static String apply(ChatConfig config, String input) {
        if (!config.emojiEnabled || input.isEmpty()) {
            return input;
        }
        Map<String, String> map = new LinkedHashMap<>(DEFAULT_EMOJIS);
        map.putAll(config.emojis);

        List<String> keys = new ArrayList<>(map.keySet());
        keys.sort((a, b) -> b.length() - a.length());

        String out = input;
        for (String key : keys) {
            out = out.replace(key, map.get(key));
        }
        return out;
    }
}
