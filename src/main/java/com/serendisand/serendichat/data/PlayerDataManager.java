package com.serendisand.serendichat.data;

import com.serendisand.serendichat.config.ChatConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 玩家数据管理器。数据库（Arks DataManager / SQLite）是唯一持久层：
 * - 玩家进服时按需加载该玩家的一行数据为会话缓存，不做启动全量载入；
 * - 星数 / 管理员颜色等修改即时落库；在线时长在玩家退出与停服时落库；
 * - 排行榜等离线查询直接读数据库。
 * 旧版 serendichat_*.properties 仅用于一次性迁移：存在则导入并改名为 *.migrated，
 * 之后启动检测不到原文件名即自动跳过。
 */
public class PlayerDataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");

    private final ChatConfig config;

    // ----- 旧版 Properties 存档路径：仅迁移时读取 -----
    private final Path starsFile;
    private final Path adminFile;
    private final Path playtimeFile;
    private final Path namesFile;

    /**
     * 在线玩家会话：该玩家数据库行的内存镜像 + 本次在线的时长累加。
     * 仅服务器线程访问（进服/退服/聊天/命令都在服务器线程），落盘前在此构建不可变快照。
     */
    private static final class Session {
        final String uuid;
        String name;
        Integer manualStars;       // null = 未设置（沿用缺省语义）
        Boolean adminColorEnabled; // null = 未手动切换
        int playtimeMinutes;       // 已含历史累计，本次在线继续累加
        long baseTime;             // 时长累加基准点

        Session(String uuid) {
            this.uuid = uuid;
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    /** 最近一次发言时间（毫秒），用于反垃圾冷却；仅内存态，不持久化。 */
    private final Map<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    /** UUID -> 最后使用的玩家名，用于排行榜等场景显示离线玩家。
     * 访问序 LRU 限制内存占用；上限由配置 name_cache_max_size 控制（最小 100）。 */
    private final Map<String, String> playerNames = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > Math.max(100, config.nameCacheMaxSize);
                }
            });

    /** 数据库写入统一走这个单线程池：命令/事件线程只提交任务，IO 不阻塞游戏循环。 */
    private final ExecutorService savePool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SerendiChat-DataSave");
        t.setDaemon(true);
        return t;
    });

    /** 数据库存储后端；打开失败时为 null（纯内存模式：功能可用但重启丢失未落盘数据）。 */
    private volatile PlayerDataStorage storage;

    public PlayerDataManager(ChatConfig config) {
        this.config = config;
        Path dir = FabricLoader.getInstance().getConfigDir();
        this.starsFile = dir.resolve("serendichat_stars.properties");
        this.adminFile = dir.resolve("serendichat_admin.properties");
        this.playtimeFile = dir.resolve("serendichat_playtime.properties");
        this.namesFile = dir.resolve("serendichat_names.properties");

        PlayerDataStorage opened = null;
        try {
            opened = new PlayerDataStorage(dir.resolve("serendichat.db"));
        } catch (Exception e) {
            LOGGER.error("Failed to open player database '{}', falling back to memory-only mode",
                    dir.resolve("serendichat.db"), e);
        }
        this.storage = opened;
    }

    public void onJoin(ServerPlayer player) {
        String uuid = player.getStringUUID();
        String name = player.getScoreboardName();

        // 按需加载该玩家的行；不存在则从零开始。
        // 星数与管理员颜色不写默认值：读取时已有缺省逻辑，避免把默认值固化进存档
        Session session = new Session(uuid);
        PlayerDataEntity row = readRowQuietly(uuid);
        if (row != null) {
            session.manualStars = row.getManualStars();
            session.adminColorEnabled = row.getAdminColorEnabled();
            session.playtimeMinutes =
                    row.getPlaytimeMinutes() != null ? row.getPlaytimeMinutes() : 0;
        }
        session.name = name;
        session.baseTime = System.currentTimeMillis();
        sessions.put(uuid, session);
        playerNames.put(uuid, name);

        // 名字异步写库（行不存在则创建），供离线后排行榜显示
        upsertFieldAsync(uuid, r -> r.setPlayerName(name));
    }

    public void onDisconnect(ServerPlayer player) {
        Session session = sessions.remove(player.getStringUUID());
        if (session == null) return;
        accruePlayTime(session, System.currentTimeMillis());
        flushSessionAsync(session);
    }

    public void updatePlayTime(ServerPlayer player) {
        Session session = sessions.get(player.getStringUUID());
        if (session == null) return;
        accruePlayTime(session, System.currentTimeMillis());
    }

    /** 把 baseTime 以来的整分钟累加进会话，保留不足 1 分钟的余量避免系统性少记。 */
    private void accruePlayTime(Session session, long now) {
        long diffMinutes = (now - session.baseTime) / 60000;
        if (diffMinutes >= 1) {
            session.playtimeMinutes += (int) diffMinutes;
            session.baseTime += diffMinutes * 60000L;
        }
    }

    public int getManualStars(String uuid) {
        Session session = sessions.get(uuid);
        if (session != null) {
            return session.manualStars != null ? session.manualStars : 0;
        }
        PlayerDataEntity row = readRowQuietly(uuid);
        return row != null && row.getManualStars() != null ? row.getManualStars() : 0;
    }

    public int getPlaytimeStars(ServerPlayer player) {
        Session session = sessions.get(player.getStringUUID());
        if (session == null) return 0;

        int hours = session.playtimeMinutes / 60;
        return hours / config.starsPerHour;
    }

    /** 按 UUID 查询在线奖励星数（仅用于排行榜，不带活跃检查）。 */
    public int getPlaytimeStarsByUuid(String uuid) {
        if (config.starsPerHour <= 0) return 0;
        int minutes;
        Session session = sessions.get(uuid);
        if (session != null) {
            minutes = session.playtimeMinutes;
        } else {
            PlayerDataEntity row = readRowQuietly(uuid);
            minutes = row != null && row.getPlaytimeMinutes() != null ? row.getPlaytimeMinutes() : 0;
        }
        return (minutes / 60) / config.starsPerHour;
    }

    /** 按 UUID 查询总星数（手动 + 在线奖励）。 */
    public int getTotalStarsByUuid(String uuid) {
        return getManualStars(uuid) + getPlaytimeStarsByUuid(uuid);
    }

    /** 所有出现过的玩家 UUID（有星数或时长记录的），供排行榜使用。 */
    public java.util.Set<String> knownUuids() {
        PlayerDataStorage st = storage;
        if (st == null) {
            return new java.util.HashSet<>(sessions.keySet());
        }
        try {
            java.util.Set<String> out = new java.util.HashSet<>();
            for (PlayerDataEntity row : st.loadAll()) {
                if (row.getManualStars() != null || row.getPlaytimeMinutes() != null) {
                    out.add(row.getUuid());
                }
            }
            return out;
        } catch (Exception e) {
            LOGGER.error("Failed to query known players from database", e);
            return new java.util.HashSet<>(sessions.keySet());
        }
    }

    /** 按 UUID 获取缓存的玩家名；离线未知返回 null。缓存未命中时回源数据库。 */
    public String getNameByUuid(String uuid) {
        String cached = playerNames.get(uuid);
        if (cached != null) return cached;
        PlayerDataEntity row = readRowQuietly(uuid);
        if (row != null && row.getPlayerName() != null && !row.getPlayerName().isEmpty()) {
            playerNames.put(uuid, row.getPlayerName());
            return row.getPlayerName();
        }
        return null;
    }

    /** 当前所有玩家的 (uuid, manualStars) 快照，供排行榜使用。 */
    public Map<String, Integer> snapshotStars() {
        Map<String, Integer> out = new HashMap<>();
        PlayerDataStorage st = storage;
        if (st != null) {
            try {
                for (PlayerDataEntity row : st.loadAll()) {
                    if (row.getManualStars() != null) {
                        out.put(row.getUuid(), row.getManualStars());
                    }
                }
                return out;
            } catch (Exception e) {
                LOGGER.error("Failed to query stars snapshot from database", e);
            }
        }
        for (Session session : sessions.values()) {
            if (session.manualStars != null) {
                out.put(session.uuid, session.manualStars);
            }
        }
        return out;
    }

    public void setStars(String uuid, int stars) {
        Session session = sessions.get(uuid);
        if (session != null) {
            session.manualStars = stars;
        }
        upsertFieldAsync(uuid, r -> r.setManualStars(stars));
    }

    public void resetStars(String uuid) {
        Session session = sessions.get(uuid);
        if (session != null) {
            session.manualStars = null;
        }
        // 星数列必须显式写 NULL：update(entity) 会跳过 null 字段，
        // 否则旧值残留在数据库中，下次加载会"复活"
        submitSave(() -> {
            PlayerDataStorage st = storage;
            if (st == null) return;
            try {
                st.clearManualStars(uuid);
            } catch (Exception e) {
                LOGGER.error("Failed to reset stars in database for {}", uuid, e);
            }
        });
    }

    public boolean isAdminColorEnabled(String uuid, boolean defaultValue) {
        Session session = sessions.get(uuid);
        if (session != null) {
            return session.adminColorEnabled != null ? session.adminColorEnabled : defaultValue;
        }
        PlayerDataEntity row = readRowQuietly(uuid);
        return row != null && row.getAdminColorEnabled() != null
                ? row.getAdminColorEnabled()
                : defaultValue;
    }

    public void setAdminColorEnabled(String uuid, boolean enabled) {
        Session session = sessions.get(uuid);
        if (session != null) {
            session.adminColorEnabled = enabled;
        }
        upsertFieldAsync(uuid, r -> r.setAdminColorEnabled(enabled));
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

    /**
     * 启动初始化：旧版 Properties 存在则一次性迁移入库，成功后改名 *.migrated，
     * 下次启动检测不到原文件名即跳过。迁移失败保持原名，下次启动自动重试。
     */
    public void loadAll() {
        List<PlayerDataEntity> legacy = collectLegacyEntities();
        if (legacy.isEmpty()) return;

        PlayerDataStorage st = storage;
        if (st == null) {
            LOGGER.warn("Database unavailable; legacy import skipped and will retry next start");
            return;
        }
        try {
            st.upsertAll(legacy);
            renameMigratedFiles();
            LOGGER.info("Imported {} legacy property entries into database", legacy.size());
        } catch (Exception e) {
            LOGGER.error("Failed to import legacy data; will retry next start", e);
        }
    }

    /** 异步保存所有在线玩家的会话数据（供外部触发的全量保存）。 */
    public void saveAllAsync() {
        for (Session session : sessions.values()) {
            flushSessionAsync(session);
        }
    }

    /** 落库所有在线玩家后关闭数据库连接池（服务器关闭时调用）。 */
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
        closeStorage();
    }

    // ----- 内部工具 -----

    private void closeStorage() {
        PlayerDataStorage st = storage;
        storage = null;
        if (st != null) {
            try {
                st.close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close player database cleanly", e);
            }
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

    /** 单行读取；存储不可用或查询失败时返回 null（降级为缺省值语义）。 */
    private PlayerDataEntity readRowQuietly(String uuid) {
        PlayerDataStorage st = storage;
        if (st == null) return null;
        try {
            return st.findById(uuid);
        } catch (Exception e) {
            LOGGER.error("Failed to load player row from database: {}", uuid, e);
            return null;
        }
    }

    /** 异步合并写单个字段：读取-修改-upsert 全部在保存线程串行执行，无并发覆盖。 */
    private void upsertFieldAsync(String uuid, Consumer<PlayerDataEntity> mutator) {
        submitSave(() -> {
            PlayerDataStorage st = storage;
            if (st == null) return;
            try {
                PlayerDataEntity row = st.findById(uuid);
                if (row == null) {
                    row = new PlayerDataEntity(uuid, null, null, null, null);
                }
                mutator.accept(row);
                st.upsertAll(List.of(row));
            } catch (Exception e) {
                LOGGER.error("Failed to persist player data for {}", uuid, e);
            }
        });
    }

    /** 在调用线程构建会话快照，交由保存线程落库。 */
    private void flushSessionAsync(Session session) {
        PlayerDataEntity snapshot = new PlayerDataEntity(session.uuid, session.name,
                session.manualStars, session.playtimeMinutes, session.adminColorEnabled);
        submitSave(() -> {
            PlayerDataStorage st = storage;
            if (st == null) return;
            try {
                st.upsertAll(List.of(snapshot));
            } catch (Exception e) {
                LOGGER.error("Failed to persist player data for {}", session.uuid, e);
            }
        });
    }

    // ----- 旧版 Properties 迁移 -----

    /** 把四个旧文件合并为按 UUID 聚合的实体列表；文件缺失或解析失败的部分自动跳过。 */
    private List<PlayerDataEntity> collectLegacyEntities() {
        Map<String, PlayerDataEntity> rows = new LinkedHashMap<>();

        Properties stars = readLegacyProperties(starsFile);
        if (stars != null) {
            for (String uuid : stars.stringPropertyNames()) {
                try {
                    rows.computeIfAbsent(uuid, this::newLegacyRow)
                            .setManualStars(Integer.parseInt(stars.getProperty(uuid)));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        Properties admin = readLegacyProperties(adminFile);
        if (admin != null) {
            for (String uuid : admin.stringPropertyNames()) {
                rows.computeIfAbsent(uuid, this::newLegacyRow)
                        .setAdminColorEnabled(Boolean.parseBoolean(admin.getProperty(uuid)));
            }
        }

        Properties playtime = readLegacyProperties(playtimeFile);
        if (playtime != null) {
            for (String uuid : playtime.stringPropertyNames()) {
                try {
                    rows.computeIfAbsent(uuid, this::newLegacyRow)
                            .setPlaytimeMinutes(Integer.parseInt(playtime.getProperty(uuid)));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        Properties names = readLegacyProperties(namesFile);
        if (names != null) {
            for (String uuid : names.stringPropertyNames()) {
                String value = names.getProperty(uuid);
                if (value != null && !value.isEmpty()) {
                    rows.computeIfAbsent(uuid, this::newLegacyRow).setPlayerName(value);
                }
            }
        }

        return new ArrayList<>(rows.values());
    }

    private PlayerDataEntity newLegacyRow(String uuid) {
        return new PlayerDataEntity(uuid, null, null, null, null);
    }

    private Properties readLegacyProperties(Path file) {
        if (Files.notExists(file)) return null;
        try {
            Properties props = new Properties();
            try (var in = Files.newInputStream(file)) {
                props.load(in);
            }
            return props;
        } catch (Exception e) {
            LOGGER.error("Failed to read legacy data file {}", file.getFileName(), e);
            return null;
        }
    }

    /** 导入成功后把旧文件改名为 *.migrated 保留备份，下次启动即跳过迁移。 */
    private void renameMigratedFiles() {
        for (Path file : new Path[]{starsFile, adminFile, playtimeFile, namesFile}) {
            if (Files.notExists(file)) continue;
            Path migrated = file.resolveSibling(file.getFileName().toString() + ".migrated");
            try {
                Files.move(file, migrated, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                LOGGER.warn("Failed to mark legacy file {} as migrated", file.getFileName(), e);
            }
        }
    }
}
