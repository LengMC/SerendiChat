package com.serendisand.serendichat.metrics;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SingleLineChart;

import java.util.Map;

/**
 * 把要上报给 bStats 的自定义图表集中注册到 Fabric 生命周期事件。
 *
 * <p>必须在 {@link Metrics#init(int, String)} 之后调用。
 * tick 回调把在线玩家数写入原子变量供上报线程读取，避免跨线程访问 MinecraftServer。
 * SERVER_STARTED 后再注册自定义图表，因为图表的 Callable 只在每次上报时调用，
 * 但注册动作本身上报线程都会触发。
 */
public final class MetricsCharts {

    private MetricsCharts() {}

    public static void register() {
        // 每个 server tick 更新一次玩家数（每 tick 都设同一个 int，开销可忽略）
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Metrics.setPlayerCount(server.getPlayerList().getPlayerCount());
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!Metrics.isEnabled()) return;
            var m = Metrics.get();

            // 1. 在线玩家数（时间序列图）
            m.addCustomChart(new SingleLineChart(
                    "players_online",
                    () -> server.getPlayerList().getPlayerCount()));

            // 2. Minecraft 版本分布
            String mcVer = Metrics.mcVersion();
            m.addCustomChart(new AdvancedPie(
                    "minecraft_version",
                    () -> Map.of("v" + mcVer, 1)));

            // 3. Fabric Loader 版本分布
            String loaderVer = Metrics.loaderVersion();
            m.addCustomChart(new AdvancedPie(
                    "loader_version",
                    () -> Map.of("v" + loaderVer, 1)));
        });
    }
}