package com.serendisand.serendichat.chat;

import com.serendisand.serendichat.compat.CustomNameCompat;
import com.serendisand.serendichat.config.ChatConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私信路由与渲染。
 * 支持 /msg、/tell、/whisper、/r 四种命令。
 * 格式由配置 private_msg_format 模板字符串决定，支持 {from} {to} {message} 占位符。
 * 发送方视角左侧显示"你"，接收方视角右侧显示"你"。
 */
public class PrivateMessageManager {

    /** 接收方提示音（常量提升，避免每条私信查一次注册表）。 */
    private static final SoundEvent XP_PICKUP = BuiltInRegistries.SOUND_EVENT.getValue(
            net.minecraft.resources.Identifier.tryParse("minecraft:entity.experience_orb.pickup"));

    /** 记录每个玩家最近一次发送/接收私信的对象，用于 /r 回复。 */
    private final Map<String, String> lastTarget = new ConcurrentHashMap<>();

    private final ChatConfig config;
    private final CustomNameCompat customName;

    public PrivateMessageManager(ChatConfig config, CustomNameCompat customName) {
        this.config = config;
        this.customName = customName;
    }

    /** 玩家断线时清理回复目标记录，避免 Map 无限增长。 */
    public void onDisconnect(ServerPlayer player) {
        lastTarget.remove(player.getStringUUID());
    }

    /** 发送私信。返回 true 表示成功；失败时通过 feedback 返回错误信息。 */
    public boolean send(ServerPlayer from, ServerPlayer target, String message) {
        if (!config.privateMsgEnabled) {
            from.sendSystemMessage(Component.literal("私信功能已禁用").withStyle(ChatFormatting.RED));
            return false;
        }
        if (target == null) {
            from.sendSystemMessage(Component.literal("用法: /msg <玩家> <消息>").withStyle(ChatFormatting.RED));
            return false;
        }
        if (message == null || message.isBlank()) {
            from.sendSystemMessage(Component.literal("消息不能为空").withStyle(ChatFormatting.RED));
            return false;
        }
        if (target.getUUID().equals(from.getUUID())) {
            from.sendSystemMessage(Component.literal("你不能给自己发私信").withStyle(ChatFormatting.RED));
            return false;
        }

        // 双方各看一份：发送方看到"你 -> 对方"；接收方看到"对方 -> 你"
        from.sendSystemMessage(buildMessage(from, target, message, true));
        target.sendSystemMessage(buildMessage(from, target, message, false));

        lastTarget.put(from.getStringUUID(), target.getScoreboardName());
        lastTarget.put(target.getStringUUID(), from.getScoreboardName());

        // 给接收方播放提示音
        playNotification(target);
        return true;
    }

    /**
     * 兼容旧签名：按名字查找目标玩家。
     * 用于脚本等非命令路径；命令路径走 ServerPlayer 重载，可享受自动补全。
     */
    public boolean send(ServerPlayer from, String targetName, String message) {
        if (targetName == null || targetName.isBlank()) {
            from.sendSystemMessage(Component.literal("用法: /msg <玩家> <消息>").withStyle(ChatFormatting.RED));
            return false;
        }
        ServerPlayer target = from.level().getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            from.sendSystemMessage(Component.literal("玩家 " + targetName + " 不在线或不存在")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        return send(from, target, message);
    }

    /** /r 回复最近一次私信对象。 */
    public boolean reply(ServerPlayer from, String message) {
        String targetName = lastTarget.get(from.getStringUUID());
        if (targetName == null) {
            from.sendSystemMessage(Component.literal("没有人可以回复").withStyle(ChatFormatting.RED));
            return false;
        }
        return send(from, targetName, message);
    }

    /**
     * 构建私信消息本体。
     * fromIsReader=true 表示渲染视角是发送方（左侧显示"你"），否则渲染视角是接收方（右侧显示"你"）。
     * 格式由配置 private_msg_format 模板字符串决定，支持的占位符:
     *   {from} / {to}                  —— 合并显示名（含 prefix + nickname + suffix + 自动空格，点击发起 /msg 回复）
     *   {from_prefix} / {to_prefix}    —— 称号（无则为空字符串）
     *   {from_nickname} / {to_nickname}—— 昵称
     *   {from_suffix} / {to_suffix}    —— 后缀（无则为空字符串）
     *   {message}                      —— 消息正文
     * 占位符外的字面量按装饰色 DARK_GRAY 渲染。
     * 默认模板 "[{from} -> {to}] {message}" 对应 CHAT 风格；想要 ACTION 风格可改为
     * "* {from} 悄悄对 {to} 说: {message}*"，或纯昵称模板 "<{from_nickname}> -> <{to_nickname}>: {message}"。
     */
    private Component buildMessage(ServerPlayer from, ServerPlayer to, String message, boolean fromIsReader) {
        MutableComponent you = Component.literal("你").withStyle(ChatFormatting.YELLOW);
        MutableComponent fromCombined = fromIsReader ? you.copy() : buildName(from);
        MutableComponent toCombined = fromIsReader ? buildName(to) : you.copy();

        Component fromPrefix = customName.getPrefix(from);
        Component fromNickname = customName.getNickname(from);
        Component fromSuffix = customName.getSuffix(from);
        Component toPrefix = customName.getPrefix(to);
        Component toNickname = customName.getNickname(to);
        Component toSuffix = customName.getSuffix(to);

        MutableComponent messageComp = Component.literal(message).withStyle(ChatFormatting.WHITE);

        java.util.Map<String, MutableComponent> parts = new java.util.LinkedHashMap<>();
        parts.put("{from}", fromCombined);
        parts.put("{to}", toCombined);
        parts.put("{from_prefix}", fromPrefix.copy());
        parts.put("{from_nickname}", fromNickname.copy());
        parts.put("{from_suffix}", fromSuffix.copy());
        parts.put("{to_prefix}", toPrefix.copy());
        parts.put("{to_nickname}", toNickname.copy());
        parts.put("{to_suffix}", toSuffix.copy());
        parts.put("{message}", messageComp);
        return ChatFormatter.applyTemplate(config.privateMsgFormat, parts);
    }

    private void playNotification(ServerPlayer target) {
        if (XP_PICKUP != null) {
            // 26.x 中 Player.playSound(SoundEvent, float, float) 是唯一带音量音调的签名
            target.playSound(XP_PICKUP, 0.6f, 1.4f);
        }
    }

    /** 复用 ChatFormatter 的玩家名构建逻辑（含 prefix/suffix 空格分隔）。 */
    private MutableComponent buildName(ServerPlayer player) {
        Component prefix = customName.getPrefix(player);
        Component nickname = customName.getNickname(player);
        Component suffix = customName.getSuffix(player);
        boolean hasPrefix = !prefix.getString().isEmpty();
        boolean hasSuffix = !suffix.getString().isEmpty();

        MutableComponent name = Component.empty();
        if (hasPrefix) {
            name.append(prefix).append(Component.literal(" "));
        }
        name.append(nickname);
        if (hasSuffix) {
            name.append(Component.literal(" ")).append(suffix);
        }
        // 点击该玩家名直接发起 /msg 回复
        if (config.clickToMsgEnabled) {
            String target = player.getScoreboardName();
            String command = config.msgCommandTemplate.replace("{player}", target);
            name = name.withStyle(style -> style.withClickEvent(
                    new net.minecraft.network.chat.ClickEvent.SuggestCommand(command)));
        }
        return name;
    }
}
