package com.serendisand.serendichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");

    private final Path configFile;

    public ConfigManager(Path configFile) {
        this.configFile = configFile;
    }

    /** 加载配置到传入的 config 实例（支持热重载）；文件不存在时生成默认配置。 */
    public void load(ChatConfig config) {
        try {
            if (Files.notExists(configFile)) {
                createDefault();
                return;
            }

            Yaml yaml = new Yaml();
            try (InputStream in = Files.newInputStream(configFile)) {
                Map<String, Object> data = yaml.load(in);
                if (data != null) {
                    apply(data, config);
                }
            }
            LOGGER.info("Configuration loaded successfully from: {}", configFile);
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration, using defaults", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> data, ChatConfig config) {
        // 先重置 emoji 映射（避免热重载时累积）
        config.emojis = new LinkedHashMap<>();

        config.chatFormat = str(data, "chat_format", config.chatFormat);

        config.adminColor = bool(data, "admin_color", config.adminColor);
        config.rainbowThreshold = integer(data, "rainbow_threshold", config.rainbowThreshold);
        config.maxStars = integer(data, "max_stars", config.maxStars);
        config.starsPerHour = integer(data, "stars_per_hour", config.starsPerHour);
        config.nameCacheMaxSize = integer(data, "name_cache_max_size", config.nameCacheMaxSize);

        config.markdownEnabled = bool(data, "enable_markdown", true);
        config.emojiEnabled = bool(data, "enable_emoji", true);
        config.itemDisplayEnabled = bool(data, "enable_item_display", true);

        config.clickToMsgEnabled = bool(data, "click_to_msg", config.clickToMsgEnabled);
        config.msgCommandTemplate = str(data, "msg_command_template", config.msgCommandTemplate);
        config.mentionEnabled = bool(data, "enable_mention", config.mentionEnabled);
        config.mentionSoundEnabled = bool(data, "mention_sound", config.mentionSoundEnabled);
        config.urlClickEnabled = bool(data, "url_click_enabled", config.urlClickEnabled);

        config.privateMsgEnabled = bool(data, "enable_private_msg", config.privateMsgEnabled);
        config.privateMsgFormat = str(data, "private_msg_format", config.privateMsgFormat);

        config.spamCooldownSeconds = integer(data, "spam_cooldown_seconds", config.spamCooldownSeconds);

        config.chatLogEnabled = bool(data, "enable_chat_log", config.chatLogEnabled);
        config.chatLogFormat = str(data, "chat_log_format", config.chatLogFormat);

        Object emojiObj = data.get("emojis");
        if (emojiObj instanceof Map) {
            for (Map.Entry<String, Object> en : ((Map<String, Object>) emojiObj).entrySet()) {
                config.emojis.put(String.valueOf(en.getKey()), String.valueOf(en.getValue()));
            }
        }

        config.starsColor0 = str(data, "stars_color_0", config.starsColor0);
        config.starsColor20 = str(data, "stars_color_20", config.starsColor20);
        config.starsColor40 = str(data, "stars_color_40", config.starsColor40);
        config.starsColor60 = str(data, "stars_color_60", config.starsColor60);
        config.starsColor80 = str(data, "stars_color_80", config.starsColor80);
        config.starsColor100 = str(data, "stars_color_100", config.starsColor100);
    }

    private static String str(Map<String, Object> data, String key, String def) {
        Object v = data.get(key);
        return v instanceof String s ? s : def;
    }

    private static boolean bool(Map<String, Object> data, String key, boolean def) {
        Object v = data.get(key);
        return v instanceof Boolean b ? b : def;
    }

    private static int integer(Map<String, Object> data, String key, int def) {
        Object v = data.get(key);
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    private void createDefault() throws java.io.IOException {
        Files.createDirectories(configFile.getParent());
        String defaultConfig =
                "# SerendiChat 配置文件 (Minecraft 26.2)\n" +
                "# 重新加载配置: /serendichat reload\n\n" +
                "# ----- 聊天格式 -----\n" +
                "# 完整布局由 chat_format 模板定义，可用占位符:\n" +
                "#   {stars}    星标文本（数字 + ※）\n" +
                "#   {prefix}   玩家称号（无则为空字符串；用户负责加间距）\n" +
                "#   {nickname} 玩家昵称\n" +
                "#   {suffix}   玩家后缀（无则为空字符串）\n" +
                "#   {player}   合并名（prefix + nickname + suffix 自动加空格，带点击私信+悬停星数）\n" +
                "#   {message}  消息本体（emoji / markdown / [item] / @提及 / URL）\n" +
                "# 占位符之外的字符按装饰色渲染。\n" +
                "chat_format: \"[{stars}] <{player}] -> {message}\"\n\n" +
                "# ----- 颜色与星数 -----\n" +
                "admin_color: true\n" +
                "rainbow_threshold: 120\n" +
                "max_stars: 1000\n" +
                "# 在线时长获得星数：每 N 小时获得 1 星（默认 1）\n" +
                "stars_per_hour: 1\n" +
                "# 排行榜离线玩家名缓存上限（LRU，超出淘汰最久未使用的）\n" +
                "name_cache_max_size: 10000\n\n" +
                "# ----- 富文本 -----\n" +
                "enable_markdown: true\n" +
                "enable_emoji: true\n" +
                "# [item] 物品展示：消息中发送 [item] 时展示主手物品，悬停可查看物品详情\n" +
                "enable_item_display: true\n\n" +
                "# 自定义 emoji 映射 (键值都需要用引号包裹，键按长度优先匹配)\n" +
                "# 默认内置: \"<3\"/\":heart:\"→❤, \":)\"→☺, \":(\"→☹, \":star:\"→★, \":sun:\"→☀, \":moon:\"→☾,\n" +
                "#          \":check:\"→✔, \":x:\"→✘, \":note:\"→♪, \":scissors:\"→✂, \":skull:\"→☠, \":warning:\"→⚠,\n" +
                "#          \":sword:\"→⚔, \":bolt:\"→⚡, \":flower:\"→❀, \":snow:\"→❄, \":coffee:\"→☕\n" +
                "# 原生 emoji（如 😭）按 UTF-16 透传；客户端需有 emoji 字体资源包才能正确显示\n" +
                "emojis:\n" +
                "  \":smile:\": \"☺\"\n" +
                "  \":fire:\": \"🔥\"\n\n" +
                "# ----- 交互 -----\n" +
                "# 点击聊天中玩家名时自动填入私信命令\n" +
                "click_to_msg: true\n" +
                "# 私信命令模板，{player} 会被替换为玩家名\n" +
                "msg_command_template: \"/tell {player} \"\n" +
                "# 玩家提及：直接输入玩家 ID（或 @ID）即可提及，被提及者会收到铁砧音效和 actionbar 提示，\n" +
                "# 点击聊天中的名字可发起私信\n" +
                "enable_mention: true\n" +
                "mention_sound: true\n" +
                "# 消息中的 URL 自动变为可点击\n" +
                "url_click_enabled: true\n\n" +
                "# ----- 私信 -----\n" +
                "enable_private_msg: true\n" +
                "# 私信格式模板，可用占位符:\n" +
                "#   {from} / {to}                    合并显示名（带 prefix/nickname/suffix 自动空格）\n" +
                "#   {from_prefix} / {to_prefix}      称号（无则为空）\n" +
                "#   {from_nickname} / {to_nickname}  昵称\n" +
                "#   {from_suffix} / {to_suffix}      后缀（无则为空）\n" +
                "#   {message}                        消息正文\n" +
                "# 默认 CHAT 风格: [你 -> {to}] {message}\n" +
                "# ACTION 风格: \"* {from} 悄悄对 {to} 说: {message}*\"\n" +
                "# 纯昵称模板: \"<{from_nickname}> -> <{to_nickname}>: {message}\"\n" +
                "private_msg_format: \"[{from} -> {to}] {message}\"\n\n" +
                "# ----- 反垃圾 -----\n" +
                "# 同玩家两条消息之间的最少间隔秒数，0 表示不限制\n" +
                "spam_cooldown_seconds: 0\n\n" +
                "# ----- 日志 -----\n" +
                "# 将所有聊天记录写入 config/serendichat_chat.log\n" +
                "enable_chat_log: false\n" +
                "chat_log_format: \"PLAIN\"\n\n" +
                "# ----- 星标颜色 -----\n" +
                "stars_color_0: \"GRAY\"\n" +
                "stars_color_20: \"GREEN\"\n" +
                "stars_color_40: \"GOLD\"\n" +
                "stars_color_60: \"AQUA\"\n" +
                "stars_color_80: \"BLUE\"\n" +
                "stars_color_100: \"RED\"\n";
        Files.writeString(configFile, defaultConfig);
        LOGGER.info("Default configuration created: {}", configFile);
    }
}
