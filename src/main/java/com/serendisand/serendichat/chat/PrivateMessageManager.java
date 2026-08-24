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
 * 显示样式: [prefix namesuffix -> 目标] 内容，方括号灰色，其余按 prefix+nickname+suffix 着色。
 */
public class PrivateMessageManager {

    /** 记录每个玩家最近一次发送/接收私信的对象，用于 /r 回复。 */
    private final Map<String, String> lastTarget = new ConcurrentHashMap<>();

    private final ChatConfig config;
    private final CustomNameCompat customName;

    public PrivateMessageManager(ChatConfig config, CustomNameCompat customName) {
        this.config = config;
        this.customName = customName;
    }

    /** 发送私信。返回 true 表示成功；失败时通过 feedback 返回错误信息。 */
    public boolean send(ServerPlayer from, String targetName, String message) {
        if (!config.privateMsgEnabled) {
            from.sendSystemMessage(Component.literal("§c私信功能已禁用").withStyle(ChatFormatting.RED));
            return false;
        }
        if (targetName == null || targetName.isBlank()) {
            from.sendSystemMessage(Component.literal("§c用法: /msg <玩家> <消息>").withStyle(ChatFormatting.RED));
            return false;
        }
        if (message == null || message.isBlank()) {
            from.sendSystemMessage(Component.literal("§c消息不能为空").withStyle(ChatFormatting.RED));
            return false;
        }
        ServerPlayer target = from.level().getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            from.sendSystemMessage(Component.literal("§c玩家 " + targetName + " 不在线或不存在")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        if (target.getUUID().equals(from.getUUID())) {
            from.sendSystemMessage(Component.literal("§c你不能给自己发私信").withStyle(ChatFormatting.RED));
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

    /** /r 回复最近一次私信对象。 */
    public boolean reply(ServerPlayer from, String message) {
        String targetName = lastTarget.get(from.getStringUUID());
        if (targetName == null) {
            from.sendSystemMessage(Component.literal("§c没有人可以回复").withStyle(ChatFormatting.RED));
            return false;
        }
        return send(from, targetName, message);
    }

    /**
     * 构建私信消息本体。
     * fromIsReader=true 表示渲染视角是发送方（左侧显示"你"），否则渲染视角是接收方（右侧显示"你"）。
     * 样式: [from -> to] 内容，方括号灰色。
     */
    private Component buildMessage(ServerPlayer from, ServerPlayer to, String message, boolean fromIsReader) {
        MutableComponent bracketOpen = Component.literal("[").withStyle(ChatFormatting.GRAY);
        MutableComponent bracketClose = Component.literal("]").withStyle(ChatFormatting.GRAY);
        MutableComponent arrow = Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY);

        MutableComponent left = fromIsReader
                ? Component.literal("你").withStyle(ChatFormatting.YELLOW)
                : buildName(from);
        MutableComponent right = fromIsReader
                ? buildName(to)
                : Component.literal("你").withStyle(ChatFormatting.YELLOW);

        return Component.empty()
                .append(bracketOpen)
                .append(left)
                .append(arrow)
                .append(right)
                .append(bracketClose)
                .append(Component.literal(" "))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
    }

    private void playNotification(ServerPlayer target) {
        // 26.x: 通过注册表查找 SoundEvent，避免直接依赖具体常量名（跨版本稳定性更好）
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(
                net.minecraft.resources.Identifier.tryParse("minecraft:entity.experience_orb.pickup"));
        if (event != null) {
            // 26.x 中 Player.playSound(SoundEvent, float, float) 是唯一带音量音调的签名
            target.playSound(event, 0.6f, 1.4f);
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
