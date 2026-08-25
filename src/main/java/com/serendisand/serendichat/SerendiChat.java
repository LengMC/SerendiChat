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
import com.serendisand.serendichat.metrics.MetricsCharts;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class SerendiChat implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");

    /**
     * bStats plugin id 与平台名。
     * 面板: https://bstats.org/plugin/bukkit/SerendiChat/33632
     * 平台名必须与 bStats 后台注册时填写的一致——上报 URL 是
     * {@code https://bstats.org/api/v2/data/<platform>}，平台不对会返回 HTTP 404 "Software not found"。
     * 开关由 {@code config/bstats/config.txt} 控制（用户可设 {@code enabled=false} 关闭）。
     */
    private static final int BSTATS_PLUGIN_ID = 33632;
    private static final String BSTATS_PLATFORM = "bukkit";

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SerendiChat for Minecraft 26.2");

        Path configDir = FabricLoader.getInstance().getConfigDir();

        ChatConfig config = new ChatConfig();
        ConfigManager configManager = new ConfigManager(configDir.resolve("serendichat.yml"));
        configManager.load(config);

        // bStats 匿名指标：开关完全交给 bStats 自己的 config/bstats/config.txt，
        // 这里只负责按 plugin id 初始化底层。plugin id <= 0 时 Metrics.init 静默跳过。
        String pluginVersion = FabricLoader.getInstance()
                .getModContainer("serendichat")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        Metrics.init(BSTATS_PLATFORM, BSTATS_PLUGIN_ID, pluginVersion);
        MetricsCharts.register();

        // 关服时关闭 bStats 调度器，避免调度线程在 server thread 已停后还尝试 POST
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Metrics.shutdown());

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
