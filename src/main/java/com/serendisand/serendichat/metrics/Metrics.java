package com.serendisand.serendichat.metrics;

import net.fabricmc.loader.api.FabricLoader;
import org.bstats.MetricsBase;
import org.bstats.config.MetricsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SerendiChat bStats 指标门面。
 *
 * <p>底层委托给官方 <code>org.bstats.MetricsBase</code>（bstats-base 3.x）——
 * bstats-fabric 这个 artifact 不存在于 Maven Central（bstats-metrics 仓库也未提供
 * fabric 模块），所以这里直接使用平台无关的核心库，并自行实现 Fabric 端的
 * 配置 / 调度 / 平台数据三件事。
 *
 * <p>调度策略：MetricsBase 自带 {@code ScheduledThreadPoolExecutor}，所以
 * {@code submitTaskConsumer} 传 {@code null}，让上报走默认线程池；
 * 唯一需要从服务器线程读取的字段（playerAmount）通过 {@link #setPlayerCount(int)}
 * 写入原子变量，上报线程从原子变量读取，避开跨线程直接访问 MinecraftServer。
 */
public final class Metrics {

    private static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");
    private static volatile MetricsBase instance;
    private static volatile boolean userEnabled;
    private static final AtomicInteger PLAYER_COUNT = new AtomicInteger(0);
    private static volatile String mcVersion = "unknown";
    private static volatile String loaderVersion = "unknown";

    private Metrics() {}

    /**
     * 初始化 bStats 指标。仅在 plugin id &gt; 0 时实际生效；否则静默跳过。
     * 用户可在 {@code config/bstats/config.txt} 中关闭（{@code enabled=false}）。
     *
     * @param platform bStats 后台注册时填写的平台名（拼到上报 URL {@code /api/v2/data/<platform>}，
     *                 必须与后台一致，否则会返回 HTTP 404 "Software not found"）
     * @param pluginId 在 https://bstats.org/what-is-my-plugin-id 处获得的 plugin id
     * @param pluginVersion 当前 mod 版本，用于上报
     */
    public static void init(String platform, int pluginId, String pluginVersion) {
        if (pluginId <= 0) {
            LOGGER.info("bStats plugin id 未配置（=0），跳过指标初始化");
            return;
        }
        if (instance != null) {
            return;
        }
        try {
            File bStatsDir = new File(
                    FabricLoader.getInstance().getConfigDir().toFile(), "bstats");
            bStatsDir.mkdirs();
            File configFile = new File(bStatsDir, "config.txt");

            MetricsConfig cfg = new MetricsConfig(configFile, true);
            userEnabled = cfg.isEnabled();

            // 进程内固定的版本字段，上报时只需读，无需每 tick 更新
            mcVersion = versionOf("minecraft");
            loaderVersion = versionOf("fabricloader");

            MetricsBase base = new MetricsBase(
                    platform,
                    cfg.getServerUUID(),
                    pluginId,
                    userEnabled,
                    builder -> {
                        builder.appendField("playerAmount", PLAYER_COUNT.get());
                        builder.appendField("minecraftVersion", mcVersion);
                        builder.appendField("fabricLoaderVersion", loaderVersion);
                        builder.appendField("javaVersion", System.getProperty("java.version"));
                        builder.appendField("osName", System.getProperty("os.name"));
                        builder.appendField("osArch", System.getProperty("os.arch"));
                        builder.appendField("osVersion", System.getProperty("os.version"));
                        builder.appendField("coreCount", Runtime.getRuntime().availableProcessors());
                    },
                    builder -> builder.appendField("pluginVersion", pluginVersion),
                    null,                                       // submitTaskConsumer: 用默认线程池
                    () -> true,                                 // checkServiceEnabledSupplier
                    (msg, err) -> {
                        if (cfg.isLogErrorsEnabled()) LOGGER.warn(msg, err);
                    },
                    msg -> {
                        if (cfg.isLogSentDataEnabled() || cfg.isLogResponseStatusTextEnabled()) {
                            LOGGER.info(msg);
                        }
                    },
                    cfg.isLogErrorsEnabled(),
                    cfg.isLogSentDataEnabled(),
                    cfg.isLogResponseStatusTextEnabled(),
                    true                                        // skipRelocateCheck: 直接用上游 jar
            );
            instance = base;
            LOGGER.info("bStats 指标初始化完成（plugin id: {}, enabled: {}）", pluginId, userEnabled);
        } catch (Throwable t) {
            LOGGER.warn("bStats 指标初始化失败", t);
        }
    }

    /**
     * 由服务器 tick 回调调用，把当前在线玩家数写入原子变量；
     * bStats 上报线程会在 appendPlatformDataConsumer 和自定义图表里读取该值。
     */
    public static void setPlayerCount(int count) {
        PLAYER_COUNT.set(count);
    }

    /**
     * 上报线程安全地读取最近一次 tick 写入的玩家数；
     * 避免直接从 bStats 调度线程访问 MinecraftServer 内部状态（数据竞争风险）。
     */
    public static int getPlayerCount() {
        return PLAYER_COUNT.get();
    }

    /** @return 进程内的 Minecraft 版本字符串（如 "26.2"）。 */
    public static String mcVersion() {
        return mcVersion;
    }

    /** @return 进程内的 Fabric Loader 版本字符串。 */
    public static String loaderVersion() {
        return loaderVersion;
    }

    /** @return 底层 bStats MetricsBase；若 plugin id 未配置则返回 {@code null}。 */
    public static MetricsBase get() {
        return instance;
    }

    /** @return 用户是否启用了 bStats 且底层已成功初始化。 */
    public static boolean isEnabled() {
        return userEnabled && instance != null;
    }

    /** 关闭底层调度器。 */
    public static void shutdown() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    private static String versionOf(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}