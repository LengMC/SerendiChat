package com.serendisand.serendichat.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.serendisand.serendichat.data.PlayerDataManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public class SerendiChatCommands {

    private final PlayerDataManager data;
    private final Runnable reloadAction;

    public SerendiChatCommands(PlayerDataManager data, Runnable reloadAction) {
        this.data = data;
        this.reloadAction = reloadAction;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("serendichat")
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
                    .then(Commands.literal("reload")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .executes(ctx -> {
                                reloadAction.run();
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§a配置已重新加载！"), false);
                                return 1;
                            }))
            );
        });
    }
}
