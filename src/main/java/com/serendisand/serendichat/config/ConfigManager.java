package com.serendisand.serendichat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
        config.format = (String) data.getOrDefault("format", config.format);
        config.adminColor = (Boolean) data.getOrDefault("admin_color", config.adminColor);
        config.rainbowThreshold = (Integer) data.getOrDefault("rainbow_threshold", config.rainbowThreshold);
        config.maxStars = (Integer) data.getOrDefault("max_stars", config.maxStars);
        config.starsPerHour = (Integer) data.getOrDefault("stars_per_hour", config.starsPerHour);

        config.markdownEnabled = (Boolean) data.getOrDefault("enable_markdown", true);
        config.emojiEnabled = (Boolean) data.getOrDefault("enable_emoji", true);
        config.itemDisplayEnabled = (Boolean) data.getOrDefault("enable_item_display", true);

        Object emojiObj = data.get("emojis");
        if (emojiObj instanceof Map) {
            for (Map.Entry<String, Object> en : ((Map<String, Object>) emojiObj).entrySet()) {
                config.emojis.put(String.valueOf(en.getKey()), String.valueOf(en.getValue()));
            }
        }

        config.starsColor0 = (String) data.getOrDefault("stars_color_0", config.starsColor0);
        config.starsColor20 = (String) data.getOrDefault("stars_color_20", config.starsColor20);
        config.starsColor40 = (String) data.getOrDefault("stars_color_40", config.starsColor40);
        config.starsColor60 = (String) data.getOrDefault("stars_color_60", config.starsColor60);
        config.starsColor80 = (String) data.getOrDefault("stars_color_80", config.starsColor80);
        config.starsColor100 = (String) data.getOrDefault("stars_color_100", config.starsColor100);
        config.starsColorRainbow = (String) data.getOrDefault("stars_color_rainbow", config.starsColorRainbow);
    }

    private void createDefault() throws java.io.IOException {
        Files.createDirectories(configFile.getParent());
        String defaultConfig =
                "# SerendiChat 配置文件 (Minecraft 26.2)\n" +
                "# 重新加载配置: /serendichat reload\n\n" +
                "# 聊天格式\n" +
                "# 可用占位符:\n" +
                "#   {stars}    - 星数显示\n" +
                "#   {prefix}   - 称号（前缀）\n" +
                "#   {nickname} - 昵称\n" +
                "#   {suffix}   - 后缀\n" +
                "#   {message}  - 消息内容\n" +
                "format: \"[{stars}※] <{prefix}{nickname}{suffix}> -> {message}\"\n\n" +
                "# 管理员是否使用红色聊天\n" +
                "admin_color: true\n\n" +
                "# 开启彩虹消息所需的星数阈值\n" +
                "rainbow_threshold: 120\n\n" +
                "# 最大星数显示（防止溢出）\n" +
                "max_stars: 1000\n\n" +
                "# 在线时长获得星数：每N小时获得1星\n" +
                "stars_per_hour: 5\n\n" +
                "# Markdown 渲染: **粗体**, *斜体*, __下划线__, ~~删除线~~, `代码`\n" +
                "enable_markdown: true\n\n" +
                "# Emoji 转换（见下方 emojis 映射）\n" +
                "enable_emoji: true\n\n" +
                "# [item] 物品展示：消息中发送 [item] 时展示主手物品，悬停可查看物品详情\n" +
                "enable_item_display: true\n\n" +
                "# 自定义 emoji 映射 (键值都需要用引号包裹，键按长度优先匹配)\n" +
                "# 默认内置: \"<3\"/\":heart:\"→❤, \":)\"→☺, \":(\"→☹, \":star:\"→★, \":sun:\"→☀, \":moon:\"→☾,\n" +
                "#          \":check:\"→✔, \":x:\"→✘, \":note:\"→♪, \":scissors:\"→✂, \":skull:\"→☠, \":warning:\"→⚠,\n" +
                "#          \":sword:\"→⚔, \":bolt:\"→⚡, \":flower:\"→❀, \":snow:\"→❄, \":coffee:\"→☕\n" +
                "emojis:\n" +
                "  \":smile:\": \"☺\"\n" +
                "  \":fire:\": \"🔥\"\n" +
                "\n" +
                "# 星数颜色配置 (可用颜色: GRAY, GREEN, GOLD, AQUA, BLUE, RED, LIGHT_PURPLE, RAINBOW)\n" +
                "stars_color_0: \"GRAY\"\n" +
                "stars_color_20: \"GREEN\"\n" +
                "stars_color_40: \"GOLD\"\n" +
                "stars_color_60: \"AQUA\"\n" +
                "stars_color_80: \"BLUE\"\n" +
                "stars_color_100: \"RED\"\n" +
                "stars_color_rainbow: \"RAINBOW\"\n";
        Files.writeString(configFile, defaultConfig);
        LOGGER.info("Default configuration created: {}", configFile);
    }
}
