package com.serendisand.serendichat.data;

import com.serendisand.serendichat.config.ChatConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerDataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");

    private final ChatConfig config;
    private final Path starsFile;
    private final Path adminFile;
    private final Path playtimeFile;
    private final Path namesFile;

    private final Map<String, Integer> playerStars = new ConcurrentHashMap<>();
    private final Map<String, Boolean> adminColorEnabled = new ConcurrentHashMap<>();
    private final Map<String, Long> playerOnlineTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> playerPlayTimeMinutes = new ConcurrentHashMap<>();
    /** 最近一次发言时间（毫秒），用于反垃圾冷却。 */
    private final Map<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    /** UUID -> 最后使用的玩家名，用于排行榜等场景显示离线玩家。
     * 纯派生缓存（可由玩家重新上线再生），使用访问序 LRU 限制内存占用；
     * 上限由配置 name_cache_max_size 控制（最小 100），支持热重载。 */
    private final Map<String, String> playerNames = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > Math.max(100, config.nameCacheMaxSize);
                }
            });

    /** 磁盘写入统一走这个单线程池：主线程只做内存快照，IO 不阻塞游戏循环。 */
    private final ExecutorService savePool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SerendiChat-DataSave");
        t.setDaemon(true);
        return t;
    });

    public PlayerDataManager(ChatConfig config) {
        this.config = config;
        Path dir = FabricLoader.getInstance().getConfigDir();
        this.starsFile = dir.resolve("serendichat_stars.properties");
        this.adminFile = dir.resolve("serendichat_admin.properties");
        this.playtimeFile = dir.resolve("serendichat_playtime.properties");
        this.namesFile = dir.resolve("serendichat_names.properties");
    }

    public void onJoin(ServerPlayer player) {
        String uuid = player.getStringUUID();
        // 星数与管理员颜色不写入默认值：读取时已有缺省逻辑，避免把默认值固化进存档
        playerOnlineTime.putIfAbsent(uuid, System.currentTimeMillis());
        playerPlayTimeMinutes.putIfAbsent(uuid, new AtomicInteger(0));
        playerNames.put(uuid, player.getScoreboardName());
    }

    public void onDisconnect(ServerPlayer player) {
        updatePlayTime(player);
        saveAllAsync();
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
            // 从上次基准点推进整分钟，保留不足 1 分钟的余量，避免活跃玩家时长被系统性少记
            playerOnlineTime.put(uuid, lastUpdate + diffMinutes * 60000L);
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

    /** 按 UUID 查询在线奖励星数（仅用于排行榜，不带活跃检查）。 */
    public int getPlaytimeStarsByUuid(String uuid) {
        if (config.starsPerHour <= 0) return 0;
        AtomicInteger minutes = playerPlayTimeMinutes.get(uuid);
        if (minutes == null) return 0;
        return (minutes.get() / 60) / config.starsPerHour;
    }

    /** 按 UUID 查询总星数（手动 + 在线奖励）。 */
    public int getTotalStarsByUuid(String uuid) {
        return getManualStars(uuid) + getPlaytimeStarsByUuid(uuid);
    }

    /** 所有出现过的玩家 UUID（含只有在线记录的），供排行榜使用。 */
    public java.util.Set<String> knownUuids() {
        java.util.Set<String> out = new java.util.HashSet<>();
        out.addAll(playerStars.keySet());
        out.addAll(playerPlayTimeMinutes.keySet());
        return out;
    }

    /** 按 UUID 获取缓存的玩家名；离线或未知返回 null。 */
    public String getNameByUuid(String uuid) {
        return playerNames.get(uuid);
    }

    /** 当前所有玩家的 (uuid, manualStars) 快照，供排行榜使用。 */
    public Map<String, Integer> snapshotStars() {
        return Map.copyOf(playerStars);
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

    /**
     * 检查并更新玩家的发言冷却。
     * 返回 true 表示允许发言；false 表示被冷却拦截并已向玩家发送提示。
     */
    public boolean checkCooldown(ServerPlayer player, int cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            lastMessageTime.put(player.getStringUUID(), System.currentTimeMillis());
            return true;
        }
        String uuid = player.getStringUUID();
        long now = System.currentTimeMillis();
        Long last = lastMessageTime.get(uuid);
        if (last != null && (now - last) < cooldownSeconds * 1000L) {
            long wait = (cooldownSeconds * 1000L - (now - last) + 999) / 1000;
            player.sendSystemMessage(Component.literal("发言过快，请等待 " + wait + " 秒")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        lastMessageTime.put(uuid, now);
        return true;
    }

    public void loadAll() {
        loadStars();
        loadAdminColors();
        loadPlayTime();
        loadNames();
    }

    /** 异步保存全部数据：Properties 快照在当前线程构建，磁盘写入交给后台线程池。 */
    public void saveAllAsync() {
        saveStars();
        saveAdminColors();
        savePlayTime();
        saveNames();
    }

    /** 提交所有挂起的写入并等待落盘（服务器关闭时调用）；之后若有保存请求则退化为同步执行。 */
    public void shutdownAndSave() {
        saveAllAsync();
        savePool.shutdown();
        try {
            if (!savePool.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warn("Save pool did not finish in time, forcing shutdown");
                savePool.shutdownNow();
            }
        } catch (InterruptedException e) {
            savePool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void submitSave(Runnable task) {
        try {
            savePool.execute(task);
        } catch (RejectedExecutionException e) {
            // 池已关闭（如停服竞态）：退回当前线程同步写，保证数据不丢
            task.run();
        }
    }

    /**
     * 原子写 Properties 文件：先写临时文件再 move 替换，
     * 避免进程在覆写中途崩溃导致整个数据文件损坏。
     */
    private static void writeAtomically(Path target, Properties props, String comment) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            try (var out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(out, comment);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save {}", target.getFileName(), e);
        }
    }

    private void saveStars() {
        Properties props = new Properties();
        for (Map.Entry<String, Integer> e : playerStars.entrySet()) {
            props.setProperty(e.getKey(), String.valueOf(e.getValue()));
        }
        submitSave(() -> writeAtomically(starsFile, props, "SerendiChat player stars"));
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
        Properties props = new Properties();
        for (Map.Entry<String, Boolean> e : adminColorEnabled.entrySet()) {
            props.setProperty(e.getKey(), String.valueOf(e.getValue()));
        }
        submitSave(() -> writeAtomically(adminFile, props, "SerendiChat admin colors"));
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
        Properties props = new Properties();
        for (Map.Entry<String, AtomicInteger> e : playerPlayTimeMinutes.entrySet()) {
            props.setProperty(e.getKey(), String.valueOf(e.getValue().get()));
        }
        submitSave(() -> writeAtomically(playtimeFile, props, "SerendiChat play time"));
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

    private void saveNames() {
        Properties props = new Properties();
        // synchronizedMap 的迭代必须在监视器锁内进行，避免并发访问序调整导致 CME
        synchronized (playerNames) {
            for (Map.Entry<String, String> e : playerNames.entrySet()) {
                props.setProperty(e.getKey(), e.getValue());
            }
        }
        submitSave(() -> writeAtomically(namesFile, props, "SerendiChat player names"));
    }

    private void loadNames() {
        try {
            if (Files.notExists(namesFile)) return;
            Properties props = new Properties();
            try (var in = Files.newInputStream(namesFile)) {
                props.load(in);
            }
            for (String key : props.stringPropertyNames()) {
                playerNames.put(key, props.getProperty(key));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load player names", e);
        }
    }
}
