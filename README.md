# SerendiChat

<img src="https://raw.githubusercontent.com/Serendisand/SerendiChat/master/src/main/resources/assets/serendichat/icon.png" alt="SerendiChat" width="180" align="right" />

一个面向 **Minecraft 26.2** 的 [Fabric](https://fabricmc.net/) **服务端**聊天增强模组。它用「星数」系统为玩家在线时长提供可视化激励，并带来富文本聊天、@提及、物品展示、私信增强等一整套现代化聊天体验。

> 纯服务端模组（`environment: "server"`），客户端无需安装。

## 特性

### ⭐ 星数系统

- 星数 = 手动星数 + 在线奖励星数（每在线 `stars_per_hour` 小时获得 1 星）
- 星标随总星数分档变色：`0/20/40/60/80/100+` 各一档，颜色可在配置中自定义
- 达到 `rainbow_threshold`（默认 120★）后，聊天消息变为**彩虹渐变**
- 悬停玩家名可查看 手动星数 / 在线奖励 明细

### 💬 富文本消息

| 语法 | 效果 |
| --- | --- |
| `**粗体**` | **粗体** |
| `__下划线__` | 下划线 |
| `~~删除线~~` | 删除线 |
| `*斜体*` | *斜体* |
| `` `代码` `` | 深灰色代码 |
| `[item]` | 展示主手物品（按稀有度着色、显示数量，悬停查看完整物品数据；空手显示灰色占位） |
| `<3` `:heart:` `:star:` 等 | emoji 短代码替换（内置 18 个，支持在配置中扩展覆盖） |

- URL 自动识别并变为可点击链接（自动截断尾部标点，`www.` 自动补全协议）
- 渲染按 Unicode 代码点处理，不会拆坏 emoji 代理对

### 📣 @提及

- 直接输入玩家 ID 或 `@ID` 即可提及（忽略大小写，带边界检查避免误匹配）
- 被提及者收到铁砧音效 + actionbar 提示「xxx 在聊天中提及了你」
- 聊天中的名字高亮显示，点击可直接发起私信

### ✉️ 私信增强

- 接管原版 `/msg` `/tell` `/w` `/whisper`，新增 `/r` 回复最近联系人
- 双方各自视角渲染（发送方看到 `你 -> 对方`，接收方看到 `对方 -> 你`）
- 两种格式可选：`CHAT`（`[你 -> 玩家] 内容`）或 `ACTION`（`* 你 悄悄对 玩家 说: 内容*`）
- 接收方播放经验球提示音

### 🛡 其他

- **反垃圾冷却**：限制同一玩家两条消息的最小间隔秒数
- **管理员红字聊天**：管理员消息默认红色，可按玩家开关
- **聊天日志**：异步将全部聊天写入 `config/serendichat_chat.log`（PLAIN / JSON 格式），不阻塞主线程
- **CustomName 集成**：检测到 [Eclipse's Custom Name](https://modrinth.com/mod/fabric-custom-names) 时自动使用其 称号/昵称/后缀（反射调用，未安装则回退原版名）；同时建议搭配 LuckPerms

## 聊天格式

```
[120※] <Prefix Nickname Suffix> -> 消息内容
  │         │                      │
  │         │                      └─ 富文本消息（markdown/emoji/[item]/@提及/URL）
  │         └─ 玩家名块（点击发起私信，悬停显示星数明细）
  └─ 星标块（分档着色，达到阈值后彩虹）
```

方括号均可通过 `star_bracket` / `name_bracket` 开关。

## 命令

### 玩家命令

| 命令 | 说明 |
| --- | --- |
| `/serendichat` / `/serendichat help` | 显示帮助 |
| `/serendichat stars` | 查询自己的星数（含在线奖励明细） |
| `/serendichat topstars` | 查看星数排行榜（前 10，含离线玩家） |
| `/msg\|tell\|w\|whisper <玩家> <消息>` | 发送私信 |
| `/r <消息>` | 回复最近一次私信对象 |

### 管理员命令（需要游戏管理员权限）

| 命令 | 说明 |
| --- | --- |
| `/serendichat setstars <玩家> <0~1000>` | 设置玩家手动星数 |
| `/serendichat resetstars <玩家>` | 重置玩家手动星数 |
| `/serendichat admincolor <true\|false>` | 开关自己的管理员红色聊天 |
| `/serendichat reload` | 热重载配置文件 |

## 配置

首次启动自动生成 `config/serendichat.yml`，修改后执行 `/serendichat reload` 即时生效（emoji 缓存与聊天日志器会同步刷新）。完整默认配置如下：

```yaml
# ----- 聊天格式 -----
star_bracket: true          # 星标是否加 [] 包裹
name_bracket: true          # 玩家名是否加 <> 包裹

# ----- 颜色与星数 -----
admin_color: true           # 管理员默认红色聊天
rainbow_threshold: 120      # 触发彩虹消息的星数阈值
max_stars: 1000             # 最大星数上限
stars_per_hour: 1           # 每在线 N 小时获得 1 星
name_cache_max_size: 10000  # 排行榜离线玩家名缓存上限（LRU）

# ----- 富文本 -----
enable_markdown: true
enable_emoji: true
enable_item_display: true   # [item] 物品展示
emojis:                     # 自定义 emoji 映射（同名键覆盖内置值）
  ":smile:": "☺"
  ":fire:": "🔥"

# ----- 交互 -----
click_to_msg: true                  # 点击玩家名自动填入私信命令
msg_command_template: "/tell {player} "  # 私信命令模板，{player} 为占位符
enable_mention: true                # @提及
mention_sound: true                 # 提及提示音
url_click_enabled: true             # URL 可点击

# ----- 私信 -----
enable_private_msg: true
private_msg_format: "CHAT"   # CHAT 或 ACTION

# ----- 反垃圾 -----
spam_cooldown_seconds: 0    # 最小发言间隔秒数，0 不限制

# ----- 日志 -----
enable_chat_log: false
chat_log_format: "PLAIN"    # PLAIN 或 JSON

# ----- 星标颜色（可用 RAINBOW 表示彩虹）-----
stars_color_0: "GRAY"
stars_color_20: "GREEN"
stars_color_40: "GOLD"
stars_color_60: "AQUA"
stars_color_80: "BLUE"
stars_color_100: "RED"
```

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/server/) ≥ 0.19.3
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api) ≥ 0.158.0 放入 `mods/`
3. 从 [Releases](https://github.com/LengMC/SerendiChat/releases) 下载 SerendiChat jar 放入服务器 `mods/` 目录
4. （可选）安装 Eclipse's Custom Name / LuckPerms 以获得称号昵称支持
5. 启动服务器，配置文件生成于 `config/serendichat.yml`

## 构建

要求 **Java 25**：

```bash
./gradlew build
```

构建产物位于 `build/libs/`（含 `-sources` 源码包）。

## 数据存储

所有数据保存在服务器 `config/` 目录下，均为原子写入（先写临时文件再替换）+ 后台线程异步保存，停服时统一落盘：

| 文件 | 内容 |
| --- | --- |
| `serendichat.yml` | 主配置 |
| `serendichat_stars.properties` | 手动星数（UUID → 星数） |
| `serendichat_playtime.properties` | 累计在线分钟数 |
| `serendichat_admin.properties` | 管理员红字开关（UUID → bool） |
| `serendichat_names.properties` | UUID → 最近使用的玩家名（排行榜用） |
| `serendichat_chat.log` | 聊天记录（需开启 `enable_chat_log`） |

## 项目结构

```
src/main/java/com/serendisand/serendichat/
├── SerendiChat.java            # 入口：装配各组件，管理热重载
├── chat/
│   ├── ChatFormatter.java      # 格式化编排：星标 + 玩家名 + 富文本分段渲染
│   ├── MarkdownRenderer.java   # Markdown 渲染 & 按代码点逐字符着色
│   ├── EmojiReplacer.java      # emoji 短代码替换
│   ├── ItemDisplayRenderer.java# [item] 物品展示
│   ├── MentionDetector.java    # @提及检测（正则缓存 + 去重叠）
│   ├── UrlDetector.java        # URL 识别与标准化
│   ├── PrivateMessageManager.java # 私信路由与双视角渲染
│   └── ChatLogger.java         # 异步聊天日志
├── command/SerendiChatCommands.java # 全部命令注册（接管原版私信命令）
├── compat/CustomNameCompat.java     # CustomName 模组反射兼容层
├── config/
│   ├── ChatConfig.java         # 配置模型（含默认值）
│   └── ConfigManager.java      # YAML 加载 / 默认配置生成
├── data/PlayerDataManager.java # 星数/时长/名称持久化与反垃圾冷却
└── event/ServerEvents.java     # 服务端生命周期与聊天事件挂接
```

## 兼容性

| 环境 | 版本要求 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | ≥ 0.19.3 |
| Fabric API | ≥ 0.158.0 |
| Java | ≥ 25 |

## 许可证

[MIT](https://github.com/LengMC/SerendiChat?tab=MIT-1-ov-file)
