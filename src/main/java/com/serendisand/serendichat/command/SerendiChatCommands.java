package com.serendisand.serendichat.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.serendisand.serendichat.chat.PrivateMessageManager;
import com.serendisand.serendichat.data.PlayerDataManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
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
            // 原版 /msg /tell /w 会与自定义格式冲突（Brigadier 中先注册者优先），
            // 先移除原版节点，让本模组的私信格式生效
            removeVanillaPrivateMsgCommands(dispatcher);

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
                                                        Component.literal("已设置 " + target.getName().getString() + " 的星数为 " + stars)
                                                                .withStyle(ChatFormatting.GREEN), false);
                                                return 1;
                                            }))))
                    .then(Commands.literal("resetstars")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                        data.resetStars(target.getStringUUID());
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("已重置 " + target.getName().getString() + " 的星数")
                                                        .withStyle(ChatFormatting.GREEN), false);
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
                                                Component.literal("管理员红色聊天 ")
                                                        .withStyle(ChatFormatting.GREEN)
                                                        .append(Component.literal(enabled ? "已启用" : "已禁用")
                                                                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
                                        return 1;
                                    })))
                    .then(Commands.literal("stars")
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                int manualStars = data.getManualStars(player.getStringUUID());
                                int playtimeStars = data.getPlaytimeStars(player);
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("你的星数: ").withStyle(ChatFormatting.GREEN)
                                                .append(Component.literal(String.valueOf(manualStars + playtimeStars))
                                                        .withStyle(ChatFormatting.YELLOW))
                                                .append(Component.literal(" (在线时长奖励: ").withStyle(ChatFormatting.GREEN))
                                                .append(Component.literal(String.valueOf(playtimeStars))
                                                        .withStyle(ChatFormatting.YELLOW))
                                                .append(Component.literal(")").withStyle(ChatFormatting.GREEN)), false);
                                return 1;
                            }))
                    .then(Commands.literal("topstars")
                            .executes(ctx -> {
                                List<Map.Entry<String, Integer>> entries = collectStars();
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("===== 星数排行榜（前 10）=====")
                                                .withStyle(ChatFormatting.GOLD), false);
                                for (int i = 0; i < 10; i++) {
                                    final int rank = i + 1;
                                    if (i >= entries.size()) {
                                        // 人数不足 10 时，后面的名次显示 "-"
                                        final int r = rank;
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("#" + r).withStyle(ChatFormatting.YELLOW)
                                                        .append(Component.literal(" -").withStyle(ChatFormatting.GRAY)), false);
                                        continue;
                                    }
                                    Map.Entry<String, Integer> e = entries.get(i);
                                    String name = resolveName(ctx.getSource(), e.getKey());
                                    int total = e.getValue();
                                    int playtime = data.getPlaytimeStarsByUuid(e.getKey());
                                    final String n = name;
                                    final int t = total;
                                    final int p = playtime;
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("#" + rank).withStyle(ChatFormatting.YELLOW)
                                                    .append(Component.literal(" " + n).withStyle(ChatFormatting.GREEN))
                                                    .append(Component.literal(" - ").withStyle(ChatFormatting.GRAY))
                                                    .append(Component.literal(String.valueOf(t)).withStyle(ChatFormatting.YELLOW))
                                                    .append(Component.literal(" 星 ").withStyle(ChatFormatting.GRAY))
                                                    .append(Component.literal("(奖励 " + p + ")")
                                                            .withStyle(ChatFormatting.DARK_GRAY)), false);
                                }
                                return 1;
                            }))
                    .then(Commands.literal("reload")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .executes(ctx -> {
                                reloadAction.run();
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("配置已重新加载！").withStyle(ChatFormatting.GREEN), false);
                                return 1;
                            }))
            );

            // ----- 私信命令: /msg /tell /w /whisper 都注册一份 -----
            for (String cmd : new String[]{"msg", "tell", "whisper", "w"}) {
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

    /** 排行榜数据：手动星 + 在线奖励星 的总星数，按总星数降序。 */
    private List<Map.Entry<String, Integer>> collectStars() {
        Map<String, Integer> totals = new java.util.LinkedHashMap<>();
        for (String uuid : data.knownUuids()) {
            totals.put(uuid, data.getTotalStarsByUuid(uuid));
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(totals.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        return entries;
    }

    private String resolveName(CommandSourceStack source, String uuid) {
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return "-";
        }
        if (source.getServer() != null) {
            ServerPlayer online = source.getServer().getPlayerList().getPlayer(id);
            if (online != null) return online.getScoreboardName();
        }
        // 离线玩家使用缓存的名称；未知则显示 "-"
        String cached = data.getNameByUuid(uuid);
        return cached != null ? cached : "-";
    }

    /**
     * 移除原版的 /msg /tell /w 命令节点。
     * Brigadier 注册同名 literal 时是合并而非覆盖，且先注册者（原版）在歧义解析中优先，
     * 导致本模组的私信格式永远不生效，因此这里直接删除原版节点后再注册自己的。
     */
    @SuppressWarnings("unchecked")
    private static void removeVanillaPrivateMsgCommands(
            com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = dispatcher.getRoot();
        for (String cmd : new String[]{"msg", "tell", "w"}) {
            com.mojang.brigadier.tree.CommandNode<CommandSourceStack> node = root.getChild(cmd);
            if (node == null) continue;
            try {
                java.lang.reflect.Field children = com.mojang.brigadier.tree.CommandNode.class
                        .getDeclaredField("children");
                children.setAccessible(true);
                ((Map<String, com.mojang.brigadier.tree.CommandNode<?>>) children.get(root)).remove(cmd);
                java.lang.reflect.Field literals = com.mojang.brigadier.tree.CommandNode.class
                        .getDeclaredField("literals");
                literals.setAccessible(true);
                Object lit = literals.get(root);
                if (lit instanceof Map<?, ?> m) {
                    ((Map<String, ?>) m).remove(cmd);
                }
            } catch (ReflectiveOperationException | ClassCastException e) {
                com.serendisand.serendichat.SerendiChat.LOGGER
                        .warn("Failed to remove vanilla command /{}: {}", cmd, e.getMessage());
            }
        }
    }

    private static void sendHelp(CommandSourceStack source) {
        source.sendSuccess(() ->
                Component.literal("===== SerendiChat 命令帮助 =====").withStyle(ChatFormatting.GOLD), false);
        helpLine(source, "/serendichat stars", "查询你的星数", false);
        helpLine(source, "/serendichat topstars", "查看星数排行榜", false);
        helpLine(source, "/msg|tell|w|whisper <玩家> <消息>", "发送私信", false);
        helpLine(source, "/r <消息>", "回复最近一次私信", false);
        helpLine(source, "/serendichat help", "显示本帮助信息", false);
        helpLine(source, "/serendichat setstars <玩家> <星数>", "设置玩家星数", true);
        helpLine(source, "/serendichat resetstars <玩家>", "重置玩家星数", true);
        helpLine(source, "/serendichat admincolor <true|false>", "开关管理员红色聊天", true);
        helpLine(source, "/serendichat reload", "重载配置文件", true);
    }

    private static void helpLine(CommandSourceStack source, String cmd, String desc, boolean admin) {
        source.sendSuccess(() -> {
            MutableComponent line = Component.empty()
                    .append(Component.literal(cmd).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" - " + desc).withStyle(ChatFormatting.GRAY));
            if (admin) {
                line.append(Component.literal(" (管理员)").withStyle(ChatFormatting.DARK_GRAY));
            }
            return line;
        }, false);
    }
}
