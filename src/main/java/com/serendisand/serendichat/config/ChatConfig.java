package com.serendisand.serendichat.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局聊天配置。所有字段均有默认值，可通过 YAML 配置覆盖。
 */
public class ChatConfig {

    // ----- 聊天格式 -----
    /** 聊天格式字符串（占位符: {stars}, {prefix}, {nickname}, {suffix}, {message}） */
    public String format = "[{stars}※] <{prefix} {nickname} {suffix}> -> {message}";
    /** 星标是否用 [] 包裹 */
    public boolean starBracketEnabled = true;
    /** 玩家名是否用 <> 包裹 */
    public boolean nameBracketEnabled = true;

    // ----- 颜色与星数 -----
    /** 管理员是否默认使用红色聊天 */
    public boolean adminColor = true;
    /** 触发彩虹消息的星数阈值 */
    public int rainbowThreshold = 120;
    /** 最大星数（防止溢出） */
    public int maxStars = 1000;
    /** 每 N 小时在线时长获得 1 星 */
    public int starsPerHour = 1;

    // ----- 富文本 -----
    public boolean markdownEnabled = true;
    public boolean emojiEnabled = true;
    public boolean itemDisplayEnabled = true;
    public Map<String, String> emojis = new LinkedHashMap<>();

    // ----- 交互 -----
    /** 点击玩家名时是否自动填入私信命令 */
    public boolean clickToMsgEnabled = true;
    /** 私信命令模板，{player} 会被替换为玩家名 */
    public String msgCommandTemplate = "/tell {player} ";
    /** 是否启用 @玩家 提及 */
    public boolean mentionEnabled = true;
    /** 被 @ 时是否播放提示音 */
    public boolean mentionSoundEnabled = true;
    /** 是否启用 URL 点击打开 */
    public boolean urlClickEnabled = true;

    // ----- 私信 -----
    public boolean privateMsgEnabled = true;
    /** 私信显示风格: ACTION（* 玩家1 悄悄对 玩家2 说*）或 CHAT（[私信] ...） */
    public String privateMsgFormat = "ACTION";

    // ----- 反垃圾 -----
    /** 同玩家两条消息之间最少间隔秒数，0 表示不限制 */
    public int spamCooldownSeconds = 0;

    // ----- 日志 -----
    public boolean chatLogEnabled = false;
    /** 日志格式: PLAIN（纯文本）或 JSON */
    public String chatLogFormat = "PLAIN";

    // ----- 星标颜色 -----
    public String starsColor0 = "GRAY";
    public String starsColor20 = "GREEN";
    public String starsColor40 = "GOLD";
    public String starsColor60 = "AQUA";
    public String starsColor80 = "BLUE";
    public String starsColor100 = "RED";
    public String starsColorRainbow = "RAINBOW";
}
