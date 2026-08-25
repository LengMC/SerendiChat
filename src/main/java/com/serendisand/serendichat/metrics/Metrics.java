package com.serendisand.serendichat.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SerendiChat bStats 指标门面。
 *
 * <p>底层委托给官方 {@code org.bstats.fabric.Metrics}（bstats-fabric 3.x），
 * 本类仅持有单例并提供安全的初始化与自定义图表注册入口，
 * 避免业务代码直接依赖 bStats 包路径。
 *
 * <p>使用流程：在 {@code onInitialize} 中调用一次 {@link #init(int)}；
 * 之后通过 {@link #get()} 获取底层实例以注册自定义图表。
 * 同一进程内重复初始化会被忽略。
 */
public final class Metrics {

    private static final Logger LOGGER = LoggerFactory.getLogger("SerendiChat");
    private static volatile org.bstats.fabric.Metrics instance;

    private Metrics() {}

    /**
     * 初始化 bStats 指标。仅在 plugin id &gt; 0 时实际生效；否则静默跳过。
     *
     * @param pluginId 在 https://bstats.org/what-is-my-plugin-id 处获得的 plugin id
     */
    public static void init(int pluginId) {
        if (pluginId <= 0) {
            LOGGER.info("bStats plugin id 未配置（=0），跳过指标初始化");
            return;
        }
        if (instance != null) {
            return;
        }
        try {
            instance = new org.bstats.fabric.Metrics(pluginId);
            LOGGER.info("bStats 指标已初始化（plugin id: {}）", pluginId);
        } catch (Throwable t) {
            LOGGER.warn("bStats 指标初始化失败", t);
        }
    }

    /** @return 底层 bStats Metrics 实例；若未启用则返回 {@code null}。 */
    public static org.bstats.fabric.Metrics get() {
        return instance;
    }

    /** @return 是否已经成功初始化。 */
    public static boolean isEnabled() {
        return instance != null;
    }
}