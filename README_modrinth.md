# Flavor-HuHoBot-Discord-Bridge

**Turn Discord into a management panel for your Minecraft server.**

A Discord bridge and management plugin for Paper 1.21+, featuring two-way chat, account linking, whitelist sync, an AI Agent, and a local WebUI.

[![Modrinth](https://img.shields.io/badge/Modrinth-Download-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/plugin/flavor-huhoBot-discord-bridge)
[![GitHub](https://img.shields.io/badge/GitHub-Source-181717?style=for-the-badge&logo=github)](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge)
[![Issues](https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github)](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge/issues)
![Paper](https://img.shields.io/badge/Paper-1.21%2B-ffcc00?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21-f89820?style=for-the-badge)
![License](https://img.shields.io/badge/License-AGPL--3.0-007ec6?style=for-the-badge)

**English** | [中文](#中文)

---

## English

### Why Choose It

- **One plugin for bridging and management** — no separate chat-sync or whitelist plugin required
- **An AI Agent that can actually operate the server** — inspect logs, run commands, manage the whitelist, and moderate Discord members
- **Approval for dangerous actions** — sensitive AI operations require an administrator to click Approve
- **Local WebUI** — edit common settings and review audits without exposing it to the internet
- **Chinese and English UI** — language files cover both in-game messages and Discord command descriptions
- **Simple deployment** — Paper 1.21+ and Java 21; drop the jar into `plugins/`

### Features

#### Two-Way Chat Bridge

Game chat and Discord messages can use different channels.

```yaml
discord:
  channels:
    game-to-discord: "Channel for game messages"
    discord-to-game: "Channel read for Discord messages"
```

Leave either channel ID empty to disable that direction.

#### Account Linking

Direct mode:

```text
/bind value:<Minecraft ID>
```

Force-bind mode:

```text
Player joins → gets kicked with a 5-digit code → /bind value:<code> → rejoins
```

Unlink with:

```text
/unbind
```

In game:

```text
/flavorhuhobot unbind
```

#### AI Agent

Manage the server from Discord with natural language:

```text
/agent run task:List the server plugins
/agent run task:Check the last 100 log lines for errors
/agent run task:Whitelist Steve
/agent run task:Timeout @someone for 10 minutes
```

16 built-in tools:

- Minecraft: plugin list, command help, logs, run command, whitelist add/remove/list, player info
- Discord: guild info, member info, role list, send message, timeout, kick, ban, role management

In `manual` mode, dangerous tools send Approve / Reject buttons. By default, the requester cannot approve their own action.

#### Clickable Help

Run this in game:

```text
/fh help
```

The command list is clickable. Clicking a command places it in the chat input without executing it.

#### Local WebUI

```text
http://127.0.0.1:8080
```

Listens on localhost by default, uses a one-time token, and lets you edit common settings and review audits.

#### Text Formatting

Legacy color codes and MiniMessage are both supported:

```text
&aGreen text
<red>Red text</red>
<gradient:#00ffcc:#0099ff>Gradient text</gradient>
```

### Quick Start

1. Download `Flavor-HuHoBot-Discord-Bridge.jar` and place it in `plugins/`
2. Start the server, then edit `plugins/Flavor-HuHoBot-Discord-Bridge/config.yml`
3. Fill in the bot token, guild ID, and channel IDs
4. When inviting the bot, select:
   - Scopes: `bot`, `applications.commands`
   - Permissions: View Channels, Send Messages, Embed Links, Read Message History
5. Run `/reload`, or restart the server

Full installation, permissions, configuration, and command reference:

**[Full Documentation (GitHub)](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge)**

### Requirements

| Item | Requirement |
|---|---|
| Server | Paper 1.21+ |
| Java | Java 21 |
| Discord | Message Content and Server Members intents enabled |
| AI (optional) | OpenAI Chat Completions compatible endpoint with function calling |

### Common Commands

| Command | Description |
|---|---|
| `/agent run task:<task>` | Run an AI task |
| `/agent new` | Clear AI context |
| `/agent stop` | Stop the current task |
| `/bind value:<Minecraft ID or code>` | Link an account |
| `/unbind` | Unlink the account |
| `/status` | Server status |
| `/online` | Online players |
| `/whitelist add\|remove\|list` | Whitelist management |
| `/console command:<command>` | Run a console command |
| `/reload` | Reload all files and reconnect the bot |

In-game main command: `/flavorhuhobot`  
Aliases: `/fh`, `/discord`

### Config Upgrade

Current config version:

```yaml
config-version: 4
```

The plugin upgrades old configs automatically and preserves existing values. No `.bak` files are created.

The legacy `links.json` file is migrated to `binds.json` automatically.

### Compatibility

- Paper 1.21+
- Java 21
- Single guild
- No console-channel relay, inventory rendering, scripting, Velocity, or Bungee support
- Chat bridging requires the Message Content privileged intent

### Links

- [Download on Modrinth](https://modrinth.com/plugin/flavor-huhoBot-discord-bridge)
- [Source Code](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge)
- [Issue Tracker](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge/issues)
- [Full Documentation](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge)

### License

**GNU Affero General Public License v3.0 (AGPL-3.0)**

See [LICENSE](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge/blob/main/LICENSE) for the full text.

---

## 中文

**把 Discord 变成 Minecraft 服务器的管理面板。**

一个面向 Paper 1.21+ 的 Discord 桥接与管理插件：双向聊天、账号绑定、白名单、AI Agent 和本地 WebUI，全部集成在一个 jar 里。

### 为什么选择它

- **一个插件完成桥接与管理**：不需要再装额外的聊天同步或白名单插件
- **AI 真正能操作服务器**：不只是聊天，还能查日志、执行命令、管理白名单和 Discord 成员
- **危险操作有审批**：AI 执行敏感操作前必须由管理员点击批准
- **本地 WebUI**：不暴露公网也能改配置、看审计
- **中英双语界面**：语言文件同时包含游戏提示与 Discord 命令描述
- **部署简单**：Paper 1.21+、Java 21，丢进 `plugins/` 即可

### 功能

#### 双向聊天桥接

游戏消息和 Discord 消息可以分别使用不同频道。

```yaml
discord:
  channels:
    game-to-discord: "游戏消息发往的频道"
    discord-to-game: "读取 Discord 消息的频道"
```

任意一个频道 ID 留空，该方向自动关闭。

#### 账号绑定

普通模式：

```text
/bind value:<游戏ID>
```

强制绑定模式：

```text
玩家进服 → 被踢出并获得 5 位验证码 → /bind value:<验证码> → 重新进服
```

解除绑定：

```text
/unbind
```

游戏内：

```text
/flavorhuhobot unbind
```

#### AI Agent

在 Discord 里用自然语言管理服务器：

```text
/agent run task:查看服务器插件列表
/agent run task:查看最近 100 行日志有没有报错
/agent run task:给 Steve 添加白名单
/agent run task:禁言 @某人 10 分钟
```

支持 16 个工具：

- 服务器：插件列表、命令帮助、日志、执行命令、白名单增删查、玩家信息
- Discord：服务器信息、成员信息、角色列表、发消息、禁言、踢出、封禁、角色管理

危险工具在 `manual` 模式下会发送「批准 / 拒绝」按钮，默认不允许发起者自己批准。

#### 可点击帮助

游戏内执行：

```text
/fh help
```

命令列表支持点击，点击后会把命令填入聊天输入栏，不会直接执行。

#### 本地 WebUI

```text
http://127.0.0.1:8080
```

默认只监听本地，使用一次性令牌登录，可修改常用配置、查看审计。

#### 文本格式

支持传统颜色代码与 MiniMessage：

```text
&a绿色文字
<red>红色文字</red>
<gradient:#00ffcc:#0099ff>渐变文字</gradient>
```

### 快速开始

1. 下载 `Flavor-HuHoBot-Discord-Bridge.jar`，放入 `plugins/`
2. 启动服务器，编辑 `plugins/Flavor-HuHoBot-Discord-Bridge/config.yml`
3. 填写 Bot Token、服务器 ID、频道 ID
4. 邀请机器人时勾选：
   - Scopes：`bot`、`applications.commands`
   - 权限：查看频道、发送消息、嵌入链接、阅读消息历史
5. 执行 `/reload`，或重启服务器

详细安装、权限、配置和命令说明：

**[完整文档（GitHub）](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge)**

### 环境要求

| 项目 | 要求 |
|---|---|
| 服务端 | Paper 1.21+ |
| Java | Java 21 |
| Discord | 开启 Message Content 与 Server Members intent |
| AI（可选） | OpenAI Chat Completions 兼容接口，支持 function calling |

### 常用命令

| 命令 | 说明 |
|---|---|
| `/agent run task:<任务>` | 执行 AI 任务 |
| `/agent new` | 清除 AI 上下文 |
| `/agent stop` | 停止当前任务 |
| `/bind value:<游戏ID或验证码>` | 绑定账号 |
| `/unbind` | 解除绑定 |
| `/status` | 服务器状态 |
| `/online` | 在线玩家 |
| `/whitelist add\|remove\|list` | 白名单管理 |
| `/console command:<命令>` | 执行控制台命令 |
| `/reload` | 重载所有文件并重连机器人 |

游戏内主命令：`/flavorhuhobot`  
别名：`/fh`、`/discord`

### 配置升级

当前配置版本：

```yaml
config-version: 4
```

插件会自动升级旧配置并保留已填写的值，不生成 `.bak` 备份。

旧版 `links.json` 会自动迁移为 `binds.json`。

### 兼容性

- Paper 1.21+
- Java 21
- 单 Guild
- 不支持控制台频道双向转发、背包渲染、脚本扩展、Velocity/Bungee
- 聊天桥接需要 Message Content 特权 intent

### 链接

- [Modrinth 下载](https://modrinth.com/plugin/flavor-huhoBot-discord-bridge)
- [源代码](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge)
- [问题反馈](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge/issues)
- [完整文档](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge)

### 许可证

**GNU Affero General Public License v3.0（AGPL-3.0）**

完整文本见 [LICENSE](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge/blob/main/LICENSE)。

---

**Current version / 当前版本：1.0.0**
