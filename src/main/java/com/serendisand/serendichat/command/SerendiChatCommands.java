package com.serendisand.serendichat.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.serendisand.serendichat.chat.PrivateMessageManager;
import com.serendisand.serendichat.data.PlayerDataManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SerendiChatCommands {

    private final PlayerDataManager data;
    private final PrivateMessageManager pm;
    private final Runnable reloadAction;

    public SerendiChatCommands(PlayerDataManager data, PrivateMessageManager pm, Runnable reloadAction) {
        this.data = data;
        this.pm = pm;
        this.reloadAction = reloadAction;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("serendichat")
                    .executes(ctx -> {
                        sendHelp(ctx.getSource());
                        return 1;
                    })
                    .then(Commands.literal("help")
                            .executes(ctx -> {
                                sendHelp(ctx.getSource());
                                return 1;
                            }))
                    .then(Commands.literal("setstars")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .then(Commands.argument("stars", IntegerArgumentType.integer(0, 1000))
                                            .executes(ctx -> {
                                                ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                                int stars = IntegerArgumentType.getInteger(ctx, "stars");
                                                data.setStars(target.getStringUUID(), stars);
                                                ctx.getSource().sendSuccess(() ->
                                                        Component.literal("§a已设置 " + target.getName().getString() + " 的星数为 " + stars), false);
                                                return 1;
                                            }))))
                    .then(Commands.literal("resetstars")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                        data.resetStars(target.getStringUUID());
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
                                        data.setAdminColorEnabled(player.getStringUUID(), enabled);
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§a管理员红色聊天 " + (enabled ? "§a已启用" : "§c已禁用")), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("stars")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                int manualStars = data.getManualStars(player.getStringUUID());
                                int playtimeStars = data.getPlaytimeStars(player);
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§a你的星数: §e" + (manualStars + playtimeStars)
                                                + " §a(在线时长奖励: §e" + playtimeStars + "§a)"), false);
                                return 1;
                            }))
                    .then(Commands.literal("topstars")
                            .executes(ctx -> {
                                List<Map.Entry<String, Integer>> entries = collectStars();
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§6===== 星数排行榜（前 10）====="), false);
                                int rank = 1;
                                for (Map.Entry<String, Integer> e : entries) {
                                    String name = resolveName(ctx.getSource(), e.getKey());
                                    int total = e.getValue();
                                    int playtime = data.getPlaytimeStarsByUuid(e.getKey());
                                    final int r = rank;
                                    final String n = name;
                                    final int t = total;
                                    final int p = playtime;
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("§e#" + r + " §a" + n
                                                    + " §7- §e" + t + " §7星 §8(奖励 " + p + ")"), false);
                                    rank++;
                                    if (rank > 10) break;
                                }
                                return 1;
                            }))
                    .then(Commands.literal("reload")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .executes(ctx -> {
                                reloadAction.run();
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§a配置已重新加载！"), false);
                                return 1;
                            }))
            );

            // ----- 私信命令: /msg /tell /whisper 都注册一份 -----
            for (String cmd : new String[]{"msg", "tell", "whisper"}) {
                dispatcher.register(Commands.literal(cmd)
                        .then(Commands.argument("target", StringArgumentType.word())
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String target = StringArgumentType.getString(ctx, "target");
                                            String message = StringArgumentType.getString(ctx, "message");
                                            return pm.send(player, target, message) ? 1 : 0;
                                        }))));
            }

            // /r 回复
            dispatcher.register(Commands.literal("r")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                String message = StringArgumentType.getString(ctx, "message");
                                return pm.reply(player, message) ? 1 : 0;
                            })));
        });
    }

    private List<Map.Entry<String, Integer>> collectStars() {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(data.snapshotStars().entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        return entries;
    }

    private String resolveName(CommandSourceStack source, String uuid) {
        if (source.getServer() != null) {
            ServerPlayer online = source.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(uuid));
            if (online != null) return online.getScoreboardName();
        }
        return uuid.substring(0, Math.min(8, uuid.length()));
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6===== SerendiChat 命令帮助 ====="), false);
        source.sendSuccess(() -> Component.literal("§e/serendichat stars §7- 查询你的星数"), false);
        source.sendSuccess(() -> Component.literal("§e/serendichat topstars §7- 查看星数排行榜"), false);
        source.sendSuccess(() -> Component.literal("§e/serendichat help §7- 显示本帮助信息"), false);
        source.sendSuccess(() -> Component.literal("§e/msg|告诉|whisper <玩家> <消息> §7- 发送私信"), false);
        source.sendSuccess(() -> Component.literal("§e/r <消息> §7- 回复最近一次私信"), false);
        source.sendSuccess(() -> Component.literal("§e/serendichat setstars <玩家> <星数> §7- 设置玩家星数 §8(管理员)"), false);
        source.sendSuccess(() -> Component.literal("§e/serendichat resetstars <玩家> §7- 重置玩家星数 §8(管理员)"), false);
        source.sendSuccess(() -> Component.literal("§e/serendichat admincolor <true|false> §7- 开关管理员红色聊天 §8(管理员)"), false);
        source.sendSuccess(() -> Component.literal("§e/serendichat reload §7- 重载配置文件 §8(管理员)"), false);
    }
}
