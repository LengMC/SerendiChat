# SerendiChat

A **server-side** [Fabric](https://fabricmc.net/) chat enhancement mod for **Minecraft 26.2**. It adds a "star" system that rewards playtime with visual progression, plus a full suite of modern chat features: rich text, mentions, item display, and enhanced private messaging.

> Purely server-side (`environment: "server"`) — no client installation required.

[English](README_EN.md) | [中文](README.md)

## Features

### ⭐ Star System

- Stars = manual stars + playtime reward stars (1 star per `stars_per_hour` hour(s) online)
- Star badge changes color by tier at `0/20/40/60/80/100+`, each tier configurable
- At or above `rainbow_threshold` (default 120★), chat messages become **rainbow-colored**
- Hover over a player's name to see manual stars / playtime reward breakdown

### 💬 Rich Text Messages

| Syntax | Effect |
| --- | --- |
| `**bold**` | **Bold** |
| `__underline__` | Underline |
| `~~strikethrough~~` | Strikethrough |
| `*italic*` | *Italic* |
| `` `code` `` | Dark gray code |
| `[item]` | Displays held item (colored by rarity, shows count, hover to view full item data; gray placeholder when empty-handed) |
| `<3` `:heart:` `:star:` etc. | Emoji shortcode replacement (18 built-in, extendable/overridable via config) |

- URLs are auto-detected and become clickable links (trailing punctuation trimmed, `www.` auto-completes protocol)
- Rendering iterates by Unicode code point, so emoji surrogate pairs are never split

### 📣 Mentions

- Type a player's ID directly (or `@ID`) to mention them (case-insensitive, boundary-checked to avoid false matches)
- Mentioned players receive an anvil sound + actionbar notification "xxx mentioned you in chat"
- Names in chat are highlighted; clicking them opens a private message command

### ✉️ Enhanced Private Messages

- Takes over vanilla `/msg` `/tell` `/w` `/whisper`, plus `/r` to reply to your last contact
- Rendered per-viewer (sender sees `You -> Target`, receiver sees `Target -> You`)
- Two formats: `CHAT` (`[You -> Player] message`) or `ACTION` (`* You whisper to Player: message*`)
- Receiver hears an XP orb notification sound

### 🛡 Extras

- **Anti-spam cooldown**: minimum seconds between messages per player
- **Admin red chat**: admin messages are red by default, toggleable per player
- **Chat logging**: asynchronously writes all chat to `config/serendichat_chat.log` (PLAIN / JSON), never blocking the main thread
- **CustomName integration**: automatically uses prefix/nickname/suffix when [Eclipse's Custom Name](https://modrinth.com/mod/custom-name) is detected (via reflection; falls back to vanilla names); LuckPerms also recommended

## Chat Format

```
[120※] <Prefix Nickname Suffix> -> message content
  │         │                      │
  │         │                      └─ rich text message (markdown/emoji/[item]/mentions/URL)
  │         └─ player name block (click to DM, hover for star breakdown)
  └─ star badge (tiered colors, rainbow past threshold)
```

Both bracket pairs can be toggled via `star_bracket` / `name_bracket`.

## Commands

### Player Commands

| Command | Description |
| --- | --- |
| `/serendichat` / `/serendichat help` | Show help |
| `/serendichat stars` | Check your stars (with playtime breakdown) |
| `/serendichat topstars` | View star leaderboard (top 10, includes offline players) |
| `/msg\|tell\|w\|whisper <player> <message>` | Send a private message |
| `/r <message>` | Reply to your most recent private message |

### Admin Commands (requires gamemaster permission)

| Command | Description |
| --- | --- |
| `/serendichat setstars <player> <0~1000>` | Set a player's manual stars |
| `/serendichat resetstars <player>` | Reset a player's manual stars |
| `/serendichat admincolor <true\|false>` | Toggle your own admin red chat |
| `/serendichat reload` | Hot-reload the config file |

## Configuration

`config/serendichat.yml` is generated on first startup. After editing, run `/serendichat reload` for instant effect (emoji caches and the chat logger refresh accordingly). Full default config:

```yaml
# ----- Chat format -----
star_bracket: true          # wrap star badge in []
name_bracket: true          # wrap player name in <>

# ----- Colors & stars -----
admin_color: true           # admins get red chat by default
rainbow_threshold: 120      # star threshold for rainbow messages
max_stars: 1000             # maximum star cap
stars_per_hour: 1           # gain 1 star per N hours online
name_cache_max_size: 10000  # offline name cache limit for leaderboard (LRU)

# ----- Rich text -----
enable_markdown: true
enable_emoji: true
enable_item_display: true   # [item] display of held item
emojis:                     # custom emoji map (same key overrides built-ins)
  ":smile:": "☺"
  ":fire:": "🔥"

# ----- Interactions -----
click_to_msg: true                  # clicking a name fills in the DM command
msg_command_template: "/tell {player} "  # DM template; {player} placeholder
enable_mention: true                # @mentions
mention_sound: true                 # mention sound
url_click_enabled: true             # clickable URLs

# ----- Private messages -----
enable_private_msg: true
private_msg_format: "CHAT"   # CHAT or ACTION

# ----- Anti-spam -----
spam_cooldown_seconds: 0    # min seconds between messages, 0 = unlimited

# ----- Logging -----
enable_chat_log: false
chat_log_format: "PLAIN"    # PLAIN or JSON

# ----- Star colors (use RAINBOW for rainbow) -----
stars_color_0: "GRAY"
stars_color_20: "GREEN"
stars_color_40: "GOLD"
stars_color_60: "AQUA"
stars_color_80: "BLUE"
stars_color_100: "RED"
```

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/server/) ≥ 0.19.3
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) ≥ 0.158.0 into `mods/`
3. Download the SerendiChat jar from [Releases](https://github.com/LengMC/SerendiChat/releases) into the server's `mods/` directory
4. (Optional) Install Eclipse's Custom Name / LuckPerms for title & nickname support
5. Start the server; the config file is generated at `config/serendichat.yml`

## Building

Requires **Java 25**:

```bash
./gradlew build
```

Build artifacts are output to `build/libs/` (including `-sources` jar).

## Data Storage

All data lives in the server's `config/` directory. Files are written atomically (temp file + move) on a background thread, with everything flushed on shutdown:

| File | Contents |
| --- | --- |
| `serendichat.yml` | Main config |
| `serendichat_stars.properties` | Manual stars (UUID → stars) |
| `serendichat_playtime.properties` | Accumulated online minutes |
| `serendichat_admin.properties` | Admin red-chat toggles (UUID → bool) |
| `serendichat_names.properties` | UUID → last known player name (for leaderboard) |
| `serendichat_chat.log` | Chat log (requires `enable_chat_log`) |

## Project Structure

```
src/main/java/com/serendisand/serendichat/
├── SerendiChat.java            # Entry point: wires components, manages hot reload
├── chat/
│   ├── ChatFormatter.java      # Formatting pipeline: stars + name + rich text segments
│   ├── MarkdownRenderer.java   # Markdown rendering & code-point coloring
│   ├── EmojiReplacer.java      # Emoji shortcode replacement
│   ├── ItemDisplayRenderer.java# [item] display
│   ├── MentionDetector.java    # Mention detection (regex cache + dedup)
│   ├── UrlDetector.java        # URL detection & normalization
│   ├── PrivateMessageManager.java # PM routing & dual-view rendering
│   └── ChatLogger.java         # Async chat logger
├── command/SerendiChatCommands.java # All commands (takes over vanilla PM commands)
├── compat/CustomNameCompat.java     # CustomName mod reflection compat layer
├── config/
│   ├── ChatConfig.java         # Config model (with defaults)
│   └── ConfigManager.java      # YAML loading / default config generation
├── data/PlayerDataManager.java # Stars/playtime/name persistence & anti-spam cooldown
└── event/ServerEvents.java     # Server lifecycle & chat event hooks
```

## Compatibility

| Environment | Requirement |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | ≥ 0.19.3 |
| Fabric API | ≥ 0.158.0 |
| Java | ≥ 25 |

## License

[MIT](https://github.com/LengMC/SerendiChat/blob/main/LICENSE)
