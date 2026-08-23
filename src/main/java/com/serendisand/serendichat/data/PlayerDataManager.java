package com.serendisand.serendichat.data;

import com.serendisand.serendichat.config.ChatConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerDataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");

    private final ChatConfig config;
    private final Path starsFile;
    private final Path adminFile;
    private final Path playtimeFile;

    private final Map<String, Integer> playerStars = new ConcurrentHashMap<>();
    private final Map<String, Boolean> adminColorEnabled = new ConcurrentHashMap<>();
    private final Map<String, Long> playerOnlineTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> playerPlayTimeMinutes = new ConcurrentHashMap<>();

    public PlayerDataManager(ChatConfig config) {
        this.config = config;
        Path dir = FabricLoader.getInstance().getConfigDir();
        this.starsFile = dir.resolve("serendichat_stars.properties");
        this.adminFile = dir.resolve("serendichat_admin.properties");
        this.playtimeFile = dir.resolve("serendichat_playtime.properties");
    }

    public void onJoin(ServerPlayer player) {
        String uuid = player.getStringUUID();
        playerStars.putIfAbsent(uuid, 0);
        adminColorEnabled.putIfAbsent(uuid, config.adminColor);
        playerOnlineTime.putIfAbsent(uuid, System.currentTimeMillis());
        playerPlayTimeMinutes.putIfAbsent(uuid, new AtomicInteger(0));
    }

    public void onDisconnect(ServerPlayer player) {
        updatePlayTime(player);
        saveAll();
    }

    public void updatePlayTime(ServerPlayer player) {
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

    public int getManualStars(String uuid) {
        return playerStars.getOrDefault(uuid, 0);
    }

    public int getPlaytimeStars(ServerPlayer player) {
        String uuid = player.getStringUUID();
        AtomicInteger minutes = playerPlayTimeMinutes.get(uuid);
        if (minutes == null) return 0;

        int hours = minutes.get() / 60;
        return hours / config.starsPerHour;
    }

    public void setStars(String uuid, int stars) {
        playerStars.put(uuid, stars);
        saveStars();
    }

    public void resetStars(String uuid) {
        playerStars.remove(uuid);
        saveStars();
    }

    public boolean isAdminColorEnabled(String uuid, boolean defaultValue) {
        return adminColorEnabled.getOrDefault(uuid, defaultValue);
    }

    public void setAdminColorEnabled(String uuid, boolean enabled) {
        adminColorEnabled.put(uuid, enabled);
        saveAdminColors();
    }

    public void loadAll() {
        loadStars();
        loadAdminColors();
        loadPlayTime();
    }

    public void saveAll() {
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
            LOGGER.error("Failed to save play time data", e);
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
            LOGGER.error("Failed to load play time data", e);
        }
    }
}
