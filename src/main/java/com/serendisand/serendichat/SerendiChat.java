package com.serendisand.serendichat;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SerendiChat implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");

    private static final String ITEM_TAG = "[item]";
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|__(.+?)__|~~(.+?)~~|\\*(.+?)\\*|`(.+?)`");

    private static final Map<String, String> DEFAULT_EMOJIS = new LinkedHashMap<>();
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

    private final Map<String, Integer> playerStars = new ConcurrentHashMap<>();
    private final Map<String, Boolean> adminColorEnabled = new ConcurrentHashMap<>();
    private final Map<String, Long> playerOnlineTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> playerPlayTimeMinutes = new ConcurrentHashMap<>();
    
    private final Path starsFile = FabricLoader.getInstance().getConfigDir().resolve("serendichat_stars.properties");
    private final Path adminFile = FabricLoader.getInstance().getConfigDir().resolve("serendichat_admin.properties");
    private final Path playtimeFile = FabricLoader.getInstance().getConfigDir().resolve("serendichat_playtime.properties");
    private final Path configFile = FabricLoader.getInstance().getConfigDir().resolve("serendichat.yml");

    private static long serverStartMillis = 0L;
    private ChatConfig config = new ChatConfig();
    
    private boolean customNameAvailable = false;
    private Class<?> customNameApiClass;
    private Method getPrefixMethod;
    private Method getDisplayNicknameMethod;
    private Method getSuffixMethod;

    public static class ChatConfig {
        public String format = "[{stars}※] <{prefix}{nickname}{suffix}> -> {message}";
        public boolean adminColor = true;
        public int rainbowThreshold = 120;
        public int maxStars = 1000;
        public int starsPerHour = 5;

        public boolean markdownEnabled = true;
        public boolean emojiEnabled = true;
        public boolean itemDisplayEnabled = true;
        public Map<String, String> emojis = new LinkedHashMap<>(DEFAULT_EMOJIS);
        
        public String starsColor0 = "GRAY";
        public String starsColor20 = "GREEN";
        public String starsColor40 = "GOLD";
        public String starsColor60 = "AQUA";
        public String starsColor80 = "BLUE";
        public String starsColor100 = "RED";
        public String starsColorRainbow = "RAINBOW";
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SerendiChat for Minecraft 26.2");

        detectCustomNameAPI();
        loadConfig();
        registerEvents();
        registerCommands();

        LOGGER.info("SerendiChat initialized successfully! CustomName API: {}", 
                    customNameAvailable ? "✓ Available" : "✗ Not available");
    }

    private void detectCustomNameAPI() {
        try {
            customNameApiClass = Class.forName("xyz.eclipseisoffline.eclipsescustomname.api.CustomNameApi");
            getPrefixMethod = customNameApiClass.getMethod("getPrefix", ServerPlayer.class);
            getDisplayNicknameMethod = customNameApiClass.getMethod("getDisplayNickname", ServerPlayer.class);
            getSuffixMethod = customNameApiClass.getMethod("getSuffix", ServerPlayer.class);
            customNameAvailable = true;
            LOGGER.info("CustomName API detected successfully");
        } catch (ClassNotFoundException e) {
            LOGGER.info("CustomName API not found, using vanilla names");
        } catch (NoSuchMethodException e) {
            LOGGER.warn("CustomName API found but methods not compatible: {}", e.getMessage());
            try {
                getDisplayNicknameMethod = customNameApiClass.getMethod("getDisplayName", ServerPlayer.class);
                customNameAvailable = true;
                LOGGER.info("Using alternative CustomName API methods");
            } catch (NoSuchMethodException ex) {
                LOGGER.warn("Alternative methods also not found");
                customNameAvailable = false;
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize CustomName API: {}", e.getMessage());
            customNameAvailable = false;
        }
    }

    private void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverStartMillis = System.currentTimeMillis();
            loadAllData();
            LOGGER.info("All data loaded successfully");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            saveAllData();
            LOGGER.info("All data saved successfully");
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player != null) {
                String uuid = player.getStringUUID();
                playerStars.putIfAbsent(uuid, 0);
                adminColorEnabled.putIfAbsent(uuid, config.adminColor);
                playerOnlineTime.putIfAbsent(uuid, System.currentTimeMillis());
                playerPlayTimeMinutes.putIfAbsent(uuid, new AtomicInteger(0));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (player != null) {
                savePlayerData(player);
            }
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            try {
                if (sender == null) {
                    return;
                }
                
                ServerPlayer player = sender;
                
                String rawMessage = "";
                try {
                    Object content = message.getClass().getMethod("getContent").invoke(message);
                    if (content instanceof Component) {
                        rawMessage = ((Component) content).getString();
                    }
                } catch (Exception e1) {
                    try {
                        Method getStringMethod = message.getClass().getMethod("getString");
                        rawMessage = (String) getStringMethod.invoke(message);
                    } catch (Exception e2) {
                        try {
                            rawMessage = message.toString();
                        } catch (Exception e3) {
                            LOGGER.warn("Failed to get message content");
                            return;
                        }
                    }
                }
                
                if (rawMessage == null || rawMessage.isEmpty()) {
                    return;
                }
                
                Component formattedMessage = formatChatMessage(player, rawMessage);
                
                try {
                    Method setMessageMethod = params.getClass().getMethod("setMessage", Component.class);
                    setMessageMethod.invoke(params, formattedMessage);
                } catch (Exception e) {
                    try {
                        java.lang.reflect.Field field = params.getClass().getDeclaredField("message");
                        field.setAccessible(true);
                        field.set(params, formattedMessage);
                    } catch (Exception ex) {
                        LOGGER.error("Failed to set formatted message", ex);
                    }
                }
                
                updatePlayerOnlineTime(player);
                
            } catch (Exception e) {
                LOGGER.error("Failed to format chat message", e);
            }
        });
    }

    private Component getPlayerPrefix(ServerPlayer player) {
        if (!customNameAvailable || getPrefixMethod == null) {
            return Component.empty();
        }
        try {
            Object result = getPrefixMethod.invoke(null, player);
            if (result instanceof Component) {
                return (Component) result;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to get player prefix: {}", e.getMessage());
        }
        return Component.empty();
    }

    private Component getPlayerNickname(ServerPlayer player) {
        if (!customNameAvailable) {
            return Component.literal(player.getScoreboardName());
        }
        try {
            Method method = getDisplayNicknameMethod != null ? getDisplayNicknameMethod : getPrefixMethod;
            if (method != null) {
                Object result = method.invoke(null, player);
                if (result instanceof Component) {
                    return (Component) result;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to get player nickname: {}", e.getMessage());
        }
        return Component.literal(player.getScoreboardName());
    }

    private Component getPlayerSuffix(ServerPlayer player) {
        if (!customNameAvailable || getSuffixMethod == null) {
            return Component.empty();
        }
        try {
            Object result = getSuffixMethod.invoke(null, player);
            if (result instanceof Component) {
                return (Component) result;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to get player suffix: {}", e.getMessage());
        }
        return Component.empty();
    }

    private void updatePlayerOnlineTime(ServerPlayer player) {
        String uuid = player.getStringUUID();
        Long lastUpdate = playerOnlineTime.get(uuid);
        if (lastUpdate == null) {
            playerOnlineTime.put(uuid, System.currentTimeMillis());
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long diffMinutes = (currentTime - lastUpdate) / 60000;
        
        if (diffMinutes >= 1) {
            AtomicInteger minutes = playerPlayTimeMinutes.get(uuid);
            if (minutes != null) {
                minutes.addAndGet((int) diffMinutes);
            }
            playerOnlineTime.put(uuid, currentTime);
        }
    }

    private int getPlaytimeStars(ServerPlayer player) {
        String uuid = player.getStringUUID();
        AtomicInteger minutes = playerPlayTimeMinutes.get(uuid);
        if (minutes == null) return 0;
        
        int hours = minutes.get() / 60;
        return hours / config.starsPerHour;
    }

    private Component buildStarBlock(int stars) {
        if (stars <= 0) {
            return Component.literal("0※").withStyle(ChatFormatting.GRAY);
        }

        int displayStars = Math.min(stars, config.maxStars);
        ChatFormatting color = getStarColor(stars);

        if (color == null) {
            MutableComponent result = Component.empty();
            ChatFormatting[] rainbow = {
                ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW,
                ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.BLUE,
                ChatFormatting.LIGHT_PURPLE
            };
            for (int i = 0; i < Math.min(displayStars, 50); i++) {
                ChatFormatting fmt = rainbow[i % rainbow.length];
                result.append(Component.literal("※").withStyle(fmt));
            }
            if (displayStars > 50) {
                result.append(Component.literal("...").withStyle(ChatFormatting.GRAY));
            }
            return result;
        }

        return Component.literal("※".repeat(Math.min(displayStars, 50))).withStyle(color);
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

    private Component buildRainbowMessage(String message) {
        ChatFormatting[] rainbow = {
            ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW,
            ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.BLUE,
            ChatFormatting.LIGHT_PURPLE
        };
        MutableComponent result = Component.empty();
        
        int maxLength = Math.min(message.length(), 200);
        for (int i = 0; i < maxLength; i++) {
            char c = message.charAt(i);
            ChatFormatting fmt = rainbow[i % rainbow.length];
            result.append(Component.literal(String.valueOf(c)).withStyle(fmt));
        }
        if (message.length() > 200) {
            result.append(Component.literal("...").withStyle(ChatFormatting.GRAY));
        }
        return result;
    }

    private String applyEmojis(String input) {
        if (!config.emojiEnabled || config.emojis.isEmpty()) {
            return input;
        }
        List<String> keys = new ArrayList<>(config.emojis.keySet());
        keys.sort((a, b) -> b.length() - a.length());
        for (String key : keys) {
            input = input.replace(key, config.emojis.get(key));
        }
        return input;
    }

    private boolean containsMarkdown(String text) {
        return config.markdownEnabled && MARKDOWN_PATTERN.matcher(text).find();
    }

    private MutableComponent renderItemTags(ServerPlayer player, String message, ChatFormatting baseColor) {
        if (!config.itemDisplayEnabled || !message.contains(ITEM_TAG)) {
            return renderMarkdown(message, baseColor);
        }
        MutableComponent out = Component.empty();
        int idx;
        int last = 0;
        while ((idx = message.indexOf(ITEM_TAG, last)) >= 0) {
            if (idx > last) {
                out.append(renderMarkdown(message.substring(last, idx), baseColor));
            }
            out.append(buildHeldItemComponent(player));
            last = idx + ITEM_TAG.length();
        }
        if (last < message.length()) {
            out.append(renderMarkdown(message.substring(last), baseColor));
        }
        return out;
    }

    private MutableComponent buildHeldItemComponent(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return Component.literal(ITEM_TAG).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
        }
        MutableComponent name = Component.empty().append(stack.getHoverName()).withStyle(stack.getRarity().color());
        if (stack.getCount() > 1) {
            name.append(Component.literal(" x" + stack.getCount()).withStyle(ChatFormatting.YELLOW));
        }
        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
                .append(name)
                .append(Component.literal("]").withStyle(ChatFormatting.GRAY))
                .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromStack(stack))));
    }

    private MutableComponent renderMarkdown(String text, ChatFormatting baseColor) {
        if (!config.markdownEnabled) {
            return styledText(text, baseColor);
        }
        MutableComponent out = Component.empty();
        Matcher m = MARKDOWN_PATTERN.matcher(text);
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

    private MutableComponent styledText(String s, ChatFormatting color) {
        return color == null ? Component.literal(s) : Component.literal(s).withStyle(color);
    }

    public Component formatChatMessage(ServerPlayer player, String message) {
        String uuid = player.getStringUUID();
        
        int manualStars = playerStars.getOrDefault(uuid, 0);
        int playtimeStars = getPlaytimeStars(player);
        int totalStars = manualStars + playtimeStars;
        totalStars = Math.min(totalStars, config.maxStars);

        Component prefix = getPlayerPrefix(player);
        Component nickname = getPlayerNickname(player);
        Component suffix = getPlayerSuffix(player);

        MutableComponent playerDisplay = Component.empty();
        if (prefix != null && !prefix.getString().isEmpty()) {
            playerDisplay.append(prefix);
        }
        playerDisplay.append(nickname != null ? nickname : Component.literal(player.getScoreboardName()));
        if (suffix != null && !suffix.getString().isEmpty()) {
            playerDisplay.append(suffix);
        }

        Component starBlock = buildStarBlock(totalStars);

        boolean isAdmin = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        boolean adminColorEnabledLocal = adminColorEnabled.getOrDefault(uuid, config.adminColor);
        boolean useAdminColor = isAdmin && adminColorEnabledLocal;
        
        String processed = applyEmojis(message);
        boolean hasRichContent = containsMarkdown(processed)
                || (config.itemDisplayEnabled && processed.contains(ITEM_TAG));

        Component messageComp;
        if (useAdminColor && !hasRichContent) {
            messageComp = Component.literal(processed).withStyle(ChatFormatting.RED);
        } else if (totalStars >= config.rainbowThreshold && !hasRichContent) {
            messageComp = buildRainbowMessage(processed);
        } else {
            ChatFormatting baseColor = useAdminColor ? ChatFormatting.RED : ChatFormatting.WHITE;
            messageComp = renderItemTags(player, processed, baseColor);
        }

        return Component.empty()
                .append(starBlock)
                .append(Component.literal(" "))
                .append(Component.literal("<").withStyle(ChatFormatting.GRAY))
                .append(playerDisplay)
                .append(Component.literal(">").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(messageComp);
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("serendichat")
                    .then(Commands.literal("setstars")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("stars", IntegerArgumentType.integer(0, 1000))
                                            .executes(ctx -> {
                                                ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                int stars = IntegerArgumentType.getInteger(ctx, "stars");
                                                playerStars.put(target.getStringUUID(), stars);
                                                saveStars();
                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("§a已设置 " + target.getName().getString() + " 的星数为 " + stars), false);
                                                return 1;
                                            }))))
                    .then(Commands.literal("resetstars")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                        playerStars.remove(target.getStringUUID());
                                        saveStars();
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§a已重置 " + target.getName().getString() + " 的星数"), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("admincolor")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .then(Commands.argument("enabled", BoolArgumentType.bool())
                                    .executes(ctx -> {
                                        boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                                        adminColorEnabled.put(player.getStringUUID(), enabled);
                                        saveAdminColors();
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§a管理员红色聊天 " + (enabled ? "§a已启用" : "§c已禁用")), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("stars")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                int stars = playerStars.getOrDefault(player.getStringUUID(), 0);
                                int playtimeStars = getPlaytimeStars(player);
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§a你的星数: §e" + stars + " §a(在线时长奖励: §e" + playtimeStars + "§a)"), false);
                                return 1;
                            }))
                    .then(Commands.literal("reload")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .executes(ctx -> {
                                loadConfig();
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§a配置已重新加载！"), false);
                                return 1;
                            }))
            );
        });
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        try {
            if (Files.notExists(configFile)) {
                createDefaultConfig();
                return;
            }

            Yaml yaml = new Yaml();
            try (InputStream in = Files.newInputStream(configFile)) {
                Map<String, Object> data = yaml.load(in);
                if (data != null) {
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
            }
            LOGGER.info("Configuration loaded successfully from: {}", configFile);
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration, using defaults", e);
        }
    }

    private void createDefaultConfig() throws java.io.IOException {
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

    private void loadAllData() {
        loadStars();
        loadAdminColors();
        loadPlayTime();
    }

    private void saveAllData() {
        saveStars();
        saveAdminColors();
        savePlayTime();
    }

    private void saveStars() {
        try {
            Properties props = new Properties();
            for (Map.Entry<String, Integer> e : playerStars.entrySet()) {
                props.setProperty(e.getKey(), String.valueOf(e.getValue()));
            }
            Files.createDirectories(starsFile.getParent());
            try (var out = Files.newOutputStream(starsFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(out, "SerendiChat player stars");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save stars data", e);
        }
    }

    private void loadStars() {
        try {
            if (Files.notExists(starsFile)) return;
            Properties props = new Properties();
            try (var in = Files.newInputStream(starsFile)) {
                props.load(in);
            }
            for (String key : props.stringPropertyNames()) {
                try {
                    playerStars.put(key, Integer.parseInt(props.getProperty(key)));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load stars data", e);
        }
    }

    private void saveAdminColors() {
        try {
            Properties props = new Properties();
            for (Map.Entry<String, Boolean> e : adminColorEnabled.entrySet()) {
                props.setProperty(e.getKey(), String.valueOf(e.getValue()));
            }
            Files.createDirectories(adminFile.getParent());
            try (var out = Files.newOutputStream(adminFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(out, "SerendiChat admin colors");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save admin colors", e);
        }
    }

    private void loadAdminColors() {
        try {
            if (Files.notExists(adminFile)) return;
            Properties props = new Properties();
            try (var in = Files.newInputStream(adminFile)) {
                props.load(in);
            }
            for (String key : props.stringPropertyNames()) {
                adminColorEnabled.put(key, Boolean.parseBoolean(props.getProperty(key)));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load admin colors", e);
        }
    }

    private void savePlayTime() {
        try {
            Properties props = new Properties();
            for (Map.Entry<String, AtomicInteger> e : playerPlayTimeMinutes.entrySet()) {
                props.setProperty(e.getKey(), String.valueOf(e.getValue().get()));
            }
            Files.createDirectories(playtimeFile.getParent());
            try (var out = Files.newOutputStream(playtimeFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(out, "SerendiChat play time");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save play time", e);
        }
    }

    private void loadPlayTime() {
        try {
            if (Files.notExists(playtimeFile)) return;
            Properties props = new Properties();
            try (var in = Files.newInputStream(playtimeFile)) {
                props.load(in);
            }
            for (String key : props.stringPropertyNames()) {
                try {
                    playerPlayTimeMinutes.put(key, new AtomicInteger(Integer.parseInt(props.getProperty(key))));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load play time", e);
        }
    }

    private void savePlayerData(ServerPlayer player) {
        String uuid = player.getStringUUID();
        updatePlayerOnlineTime(player);
        saveStars();
        saveAdminColors();
        savePlayTime();
    }

    public int getPlayerStars(ServerPlayer player) {
        return playerStars.getOrDefault(player.getStringUUID(), 0);
    }

    public boolean isAdminColorEnabled(ServerPlayer player) {
        return adminColorEnabled.getOrDefault(player.getStringUUID(), config.adminColor);
    }

    public ChatConfig getConfig() {
        return config;
    }
}