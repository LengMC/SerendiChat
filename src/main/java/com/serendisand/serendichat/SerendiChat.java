package com.serendisand.serendichat;

import com.serendisand.serendichat.chat.ChatFormatter;
import com.serendisand.serendichat.chat.ChatLogger;
import com.serendisand.serendichat.chat.EmojiReplacer;
import com.serendisand.serendichat.chat.PrivateMessageManager;
import com.serendisand.serendichat.command.SerendiChatCommands;
import com.serendisand.serendichat.compat.CustomNameCompat;
import com.serendisand.serendichat.config.ChatConfig;
import com.serendisand.serendichat.config.ConfigManager;
import com.serendisand.serendichat.data.PlayerDataManager;
import com.serendisand.serendichat.event.ServerEvents;
import com.serendisand.serendichat.metrics.Metrics;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class SerendiChat implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SerendiChat for Minecraft 26.2");

        Path configDir = FabricLoader.getInstance().getConfigDir();

        ChatConfig config = new ChatConfig();
        ConfigManager configManager = new ConfigManager(configDir.resolve("serendichat.yml"));
        configManager.load(config);

        // bStats 匿名指标（需在 bstats.org 注册并填 plugin_id 后才会真正上报）
        if (config.bstatsEnabled) {
            Metrics.init(config.bstatsPluginId);
        }

        PlayerDataManager data = new PlayerDataManager(config);
        CustomNameCompat customName = CustomNameCompat.detect();
        ChatFormatter formatter = new ChatFormatter(config, data, customName);
        // 用引用持有 logger，reload 时原子换新实例，事件侧通过 supplier 始终拿到最新的
        AtomicReference<ChatLogger> chatLogger = new AtomicReference<>(new ChatLogger(config));
        PrivateMessageManager pm = new PrivateMessageManager(config, customName);

        new ServerEvents(config, data, formatter, chatLogger::get, pm).register();
        new SerendiChatCommands(data, pm, () -> {
            configManager.load(config);
            EmojiReplacer.invalidateCache();
            // 换新 logger 以响应 chatLogEnabled 切换；旧实例排空剩余队列后关闭
            ChatLogger old = chatLogger.getAndUpdate(prev -> new ChatLogger(config));
            if (old != null) {
                old.close();
            }
        }).register();

        LOGGER.info("SerendiChat initialized successfully! CustomName API: {}",
                customName.available() ? "✓ Available" : "✗ Not available");
    }
}
