package com.serendisand.serendichat.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局聊天配置。所有字段均有默认值，可通过 YAML 配置覆盖。
 */
public class ChatConfig {

    // ----- 聊天格式 -----
    /** 完整布局为 [星数※] <称号 昵称 后缀> -> 消息，由 star_bracket/name_bracket 控制包裹 */
    public boolean starBracketEnabled = true;
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
    /** 离线玩家名字缓存上限（LRU，超出淘汰最久未使用的） */
    public int nameCacheMaxSize = 10000;

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
    /** 私信显示风格: CHAT（[你 -> 玩家] 内容）或 ACTION（* 你 悄悄对 玩家 说: 内容*） */
    public String privateMsgFormat = "CHAT";

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
}
