package com.serendisand.serendichat.data;

import cn.arkmillion.core.annotation.Column;
import cn.arkmillion.core.annotation.Id;
import cn.arkmillion.core.annotation.Table;
import cn.arkmillion.core.enums.GenerationType;

/**
 * 玩家数据实体，对应 SQLite 表 {@code sc_player_data}，每个 UUID 一行。
 * 字段为包装类型且允许 null：null 表示"从未设置"，读回时不写入对应的内存 Map，
 * 以保持与旧版 Properties 存储相同的缺省语义（如管理员颜色未手动切换时跟随配置默认值）。
 */
@Table(name = "sc_player_data", comment = "SerendiChat player data")
public class PlayerDataEntity {

    @Id(strategy = GenerationType.NONE)
    @Column(name = "uuid", length = 36, nullable = false)
    private String uuid;

    /** 最近一次上线时使用的玩家名（派生缓存，供排行榜显示离线玩家）。 */
    @Column(name = "name", length = 64)
    private String playerName;

    /** 手动设置的星数；null = 未设置。 */
    @Column(name = "manual_stars")
    private Integer manualStars;

    /** 累计在线分钟数；null = 无记录。 */
    @Column(name = "playtime_minutes")
    private Integer playtimeMinutes;

    /** 管理员红色聊天的显式开关；null = 未手动切换。 */
    @Column(name = "admin_color")
    private Boolean adminColorEnabled;

    public PlayerDataEntity() {
    }

    public PlayerDataEntity(String uuid, String playerName, Integer manualStars,
                            Integer playtimeMinutes, Boolean adminColorEnabled) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.manualStars = manualStars;
        this.playtimeMinutes = playtimeMinutes;
        this.adminColorEnabled = adminColorEnabled;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public Integer getManualStars() {
        return manualStars;
    }

    public void setManualStars(Integer manualStars) {
        this.manualStars = manualStars;
    }

    public Integer getPlaytimeMinutes() {
        return playtimeMinutes;
    }

    public void setPlaytimeMinutes(Integer playtimeMinutes) {
        this.playtimeMinutes = playtimeMinutes;
    }

    public Boolean getAdminColorEnabled() {
        return adminColorEnabled;
    }

    public void setAdminColorEnabled(Boolean adminColorEnabled) {
        this.adminColorEnabled = adminColorEnabled;
    }
}
