package com.serendisand.serendichat;

import com.serendisand.serendichat.chat.ChatFormatter;
import com.serendisand.serendichat.chat.EmojiReplacer;
import com.serendisand.serendichat.chat.ChatLogger;
import com.serendisand.serendichat.chat.PrivateMessageManager;
import com.serendisand.serendichat.command.SerendiChatCommands;
import com.serendisand.serendichat.compat.CustomNameCompat;
import com.serendisand.serendichat.config.ChatConfig;
import com.serendisand.serendichat.config.ConfigManager;
import com.serendisand.serendichat.data.PlayerDataManager;
import com.serendisand.serendichat.event.ServerEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class SerendiChat implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SerendiChat for Minecraft 26.2");

        Path configDir = FabricLoader.getInstance().getConfigDir();

        ChatConfig config = new ChatConfig();
        ConfigManager configManager = new ConfigManager(configDir.resolve("serendichat.yml"));
        configManager.load(config);

        PlayerDataManager data = new PlayerDataManager(config);
        CustomNameCompat customName = CustomNameCompat.detect();
        ChatFormatter formatter = new ChatFormatter(config, data, customName);
        ChatLogger chatLogger = new ChatLogger(config);
        PrivateMessageManager pm = new PrivateMessageManager(config, customName);

        new ServerEvents(config, data, formatter, chatLogger).register();
        new SerendiChatCommands(data, pm, () -> {
            configManager.load(config);
            EmojiReplacer.invalidateCache();
        }).register();

        LOGGER.info("SerendiChat initialized successfully! CustomName API: {}",
                customName.available() ? "✓ Available" : "✗ Not available");
    }
}
