# Flavor-HuHoBot-Discord-Bridge

Paper 1.21+ 的 Discord 桥接与管理插件，提供双向聊天、账号绑定、白名单联动、AI Agent 和本地 WebUI。

[![Modrinth](https://img.shields.io/badge/Modrinth-Download-1bd96a?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/plugin/flavor-huhoBot-discord-bridge)
[![GitHub](https://img.shields.io/badge/GitHub-Source-181717?style=for-the-badge&logo=github)](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge)
[![Issues](https://img.shields.io/badge/GitHub-Issues-181717?style=for-the-badge&logo=github)](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge/issues)
![Paper](https://img.shields.io/badge/Paper-1.21%2B-ffcc00?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21-f89820?style=for-the-badge)
![License](https://img.shields.io/badge/License-AGPL--3.0-007ec6?style=for-the-badge)

---

## 功能概览

| 功能 | 说明 |
|---|---|
| 双向聊天 | 游戏与 Discord 两个方向可分别使用不同频道，未填写频道 ID 的方向自动关闭 |
| 账号绑定 | 普通模式 `/bind <游戏ID>`；强制绑定模式进服拿码后 `/bind <验证码>` |
| 白名单联动 | 绑定成功后自动添加白名单，解绑自动移除，可配置 |
| 强制绑定 | 未绑定玩家进服被踢出并获得 5 位验证码，在 Discord 完成验证 |
| AI Agent | 通过 OpenAI 兼容接口执行服务器管理任务，内置 16 个工具 |
| 管理员审批 | 危险操作发送「批准 / 拒绝」按钮，默认禁止发起者自批 |
| 审计 | AI 工具调用写入 `audit.jsonl` |
| WebUI | 默认仅监听 `127.0.0.1`，Bearer Token 登录 |
| 文本格式 | 支持传统 `&` 颜色代码与 MiniMessage |
| 中英双语 | 语言文件包含界面文本与 Discord 命令描述 |

---

## 环境要求

| 项目 | 要求 |
|---|---|
| 服务端 | Paper 1.21+（已在 Paper 1.21.4 测试） |
| Java | Java 21 |
| Discord | Bot 应用，开启 Message Content 与 Server Members 特权 intent |
| AI（可选） | OpenAI Chat Completions 兼容接口，模型需要支持 function calling |

---

## 安装

1. 从 [Modrinth](https://modrinth.com/plugin/flavor-huhoBot-discord-bridge) 下载 `Flavor-HuHoBot-Discord-Bridge.jar`
2. 放入服务器的 `plugins/` 目录
3. 启动服务器，生成：
   ```
   plugins/Flavor-HuHoBot-Discord-Bridge/config.yml
   ```
4. 填写 Bot Token、服务器 ID、频道 ID
5. 执行 `/reload`，或重启服务器

---

## Discord Bot 配置

1. 打开 <https://discord.com/developers/applications> 新建应用
2. **Bot** 页面 → Reset Token → 填入 `discord.token`
3. **Privileged Gateway Intents** 开启：
   - Message Content Intent
   - Server Members Intent
4. **OAuth2 → URL Generator**：
   - Scopes：`bot`、`applications.commands`
   - Bot Permissions：
     - 查看频道
     - 发送消息
     - 嵌入链接
     - 添加文件
     - 阅读消息历史记录
   - AI 管理功能按需增加：
     - 管理成员（禁言）
     - 踢除成员
     - 封禁成员
     - 管理身份组（角色管理）
5. 使用生成的链接邀请机器人
6. 开启开发者模式，右键服务器/频道复制 ID

> OAuth2 权限只是上限。频道有单独权限覆盖时，仍需在 **频道设置 → 权限** 给机器人允许 `查看频道`、`发送消息`、`嵌入链接`。日志出现 `MESSAGE_SEND` 或 `MESSAGE_EMBED_LINKS` 缺失时，先检查频道覆盖。

---

## 配置

最小配置示例：

```yaml
discord:
  token: "Bot Token"
  guild-id: "服务器 ID"
  channels:
    game-to-discord: "游戏消息发往的频道 ID"
    discord-to-game: "读取 Discord 消息的频道 ID"
    agent: "AI 回复频道 ID"
  admin-role-ids: []

bridge:
  enabled: true

binding:
  force-bind: false

agent:
  enabled: true
  base-url: "https://api.openai.com/v1"
  api-key: "sk-..."
  model: "gpt-4o-mini"
  mode: manual

webui:
  bind: "127.0.0.1"
  port: 8080
```

### 常用配置

| 配置 | 说明 |
|---|---|
| `discord.channels.game-to-discord` | 游戏 → Discord 的频道；留空关闭该方向 |
| `discord.channels.discord-to-game` | Discord → 游戏的频道；留空关闭该方向 |
| `discord.channels.agent` | AI 任务与回复频道；留空使用执行命令的当前频道 |
| `discord.admin-role-ids` | 额外管理员角色 ID |
| `bridge.from-game` | 游戏 → Discord 格式，占位符 `{name}`、`{message}` |
| `bridge.from-discord` | Discord → 游戏格式，占位符 `{name}`、`{message}`、`{discord}`、`{mc}` |
| `bridge.start-with` | 仅转发指定前缀开头的游戏消息 |
| `link.whitelist-on-link` | 绑定成功后自动加白名单 |
| `link.whitelist-list-command` | Discord `/whitelist list` 执行的查询命令，默认 `whitelist list` |
| `binding.force-bind` | 开启强制绑定 |
| `binding.force-bind-groups` | 强制绑定踢出提示中展示的 Discord 服务器/频道/邀请链接 |
| `binding.verify-exempt` | 免强制绑定的玩家名 |
| `agent.mode` | `manual`：危险工具需审批；`auto`：只读，危险工具禁用 |
| `agent.max-steps` | 单任务最大模型轮次 |
| `agent.max-context-tokens` | 上下文预算 |
| `agent.daily-token-budget` | 每服务器每日 token 预算，`<=0` 不限 |
| `agent.allow-self-approval` | 是否允许发起者批准自己的请求 |
| `security.command-mode` | `blacklist`（默认）或 `allowlist` |
| `security.allowed-commands` | allowlist 模式下允许的命令 |
| `security.blocked-commands` | 始终拦截的命令词 |
| `webui.bind` / `webui.port` | WebUI 监听地址与端口 |

### 配置升级

当前结构版本：

```yaml
config-version: 4
```

- 启动或重载时自动检查 `config-version`
- 升级时保留已填写的值
- 不生成 `.bak` 文件，已有的 `config.yml.v*.bak` 会自动删除
- 手动把 `config-version` 改低后执行 `/reload` 也会触发升级
- v3 → v4 新增 `link.whitelist-list-command`

---

## 账号绑定

### 普通绑定

```yaml
binding:
  force-bind: false
```

Discord 执行：

```text
/bind value:<游戏ID>
```

写入绑定并执行 `whitelist add <游戏ID>`。

普通模式无法证明 Discord 用户真的拥有该游戏角色，适合信任环境或人工核对。

### 强制绑定

```yaml
binding:
  force-bind: true
```

流程：

```text
未绑定玩家进服
  → 被踢出并获得 5 位验证码
  → Discord 执行 /bind value:<验证码>
  → 写入绑定并加白名单
  → 重新进服
```

注意：

- 验证码 5 位数字，5 分钟有效，一次性
- 每个 Discord 用户每分钟最多尝试 10 次
- `binding.verify-exempt` 中的玩家不会被踢
- 拥有 `flavor-huhobot.bypass` 或 `flavor-huhobot.admin` 权限的玩家不会被踢
- **必须关闭原版白名单/其他白名单插件**，否则玩家在登录阶段就会被拦截

### 解除绑定

```text
/unbind
```

游戏内：

```text
/flavorhuhobot unbind
```

---

## 命令

### Discord Slash

| 命令 | 说明 | 权限 |
|---|---|---|
| `/agent run task:<任务>` | 执行 AI 任务 | 管理员 |
| `/agent new` | 清除当前 AI 上下文 | 管理员 |
| `/agent stop` | 停止自己的任务 | 管理员 |
| `/bind value:<游戏ID或验证码>` | 绑定 Minecraft 账号 | 所有人 |
| `/unbind` | 解除绑定 | 所有人 |
| `/status` | 服务器与机器人状态 | 所有人 |
| `/online` | 在线玩家 | 所有人 |
| `/whitelist add\|remove\|list` | 白名单管理 | 管理员 |
| `/console command:<命令>` | 执行控制台命令 | 管理员 |
| `/help` | 帮助 | 所有人 |
| `/reload` | 重载所有文件并重连机器人 | 管理员 |

“管理员”指服务器所有者、拥有 `管理服务器` 权限，或拥有 `discord.admin-role-ids` 中角色的成员。

### 游戏内命令

| 命令 | 说明 |
|---|---|
| `/flavorhuhobot unbind` | 解除绑定 |
| `/flavorhuhobot status` | 查看状态 |
| `/flavorhuhobot reload` | 重载所有文件并重连机器人 |
| `/flavorhuhobot help` | 帮助 |

主命令：`/flavorhuhobot`  
别名：`/fh`、`/discord`

`/fh help` 中的命令可以点击，点击后只会把命令填入聊天输入栏，不会直接执行。

---

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `flavor-huhobot.admin` | OP | 重载、控制台、白名单管理 |
| `flavor-huhobot.status` | 所有人 | 查看状态 |
| `flavor-huhobot.bypass` | OP | 绕过强制绑定 |

---

## AI Agent

### 配置

```yaml
agent:
  enabled: true
  base-url: "https://api.openai.com/v1"
  api-key: "sk-..."
  model: "gpt-4o-mini"
  mode: manual
  max-steps: 15
  max-context-tokens: 24000
  daily-token-budget: 200000
  allow-self-approval: false
  approval-timeout-seconds: 300
```

`base-url` 需要包含版本段，客户端会在后面拼接 `/chat/completions`。

### 使用

```text
/agent run task:查看服务器插件列表
/agent run task:查看最近 100 行日志有没有报错
/agent run task:给 Steve 添加白名单
/agent new
/agent stop
```

- 消息与回复发送到 `discord.channels.agent`，不创建子区
- 同一用户在同一频道的后续任务会复用上下文
- `/agent new` 清除该用户在当前服务器的上下文
- 危险操作需要管理员点击批准
- 默认禁止发起者批准自己的请求
- `auto` 模式只允许只读工具

### 工具

| 服务器工具 | 说明 | 审批 |
|---|---|---|
| `mc_get_plugins` | 插件列表 | 否 |
| `mc_get_command_help` | 命令帮助 | 否 |
| `mc_read_logs` | 读取日志 | 否 |
| `mc_run_command` | 执行控制台命令 | 是 |
| `mc_whitelist_add` | 添加白名单 | 是 |
| `mc_whitelist_remove` | 移除白名单 | 是 |
| `mc_whitelist_list` | 白名单列表 | 否 |
| `mc_player_info` | 玩家信息 | 否 |

| Discord 工具 | 说明 | 审批 |
|---|---|---|
| `dc_guild_info` | 服务器信息 | 否 |
| `dc_member_info` | 成员信息 | 否 |
| `dc_list_roles` | 角色列表 | 否 |
| `dc_send_message` | 发送消息 | 否 |
| `dc_timeout_member` | 禁言成员 | 是 |
| `dc_kick_member` | 踢出成员 | 是 |
| `dc_ban_member` | 封禁成员 | 是 |
| `dc_manage_role` | 添加/移除角色 | 是 |

---

## WebUI

启动后控制台输出一次性令牌：

```text
[Flavor-HuHoBot-Discord-Bridge] WebUI 访问令牌（仅首次生成）: xxxxxxxx
```

打开：

```text
http://127.0.0.1:8080
```

- 令牌哈希保存在 `webui-token`
- 忘记令牌：删除 `webui-token` 后重启
- 可修改常用配置、查看审计
- 不要把 WebUI 暴露到公网；远程访问请使用 SSH 隧道或反向代理 + HTTPS

---

## 文本格式

支持传统颜色代码：

```text
&a绿色文字
&c红色文字
```

也支持 MiniMessage：

```text
<red>红色文字</red>
<gradient:#00ffcc:#0099ff>渐变文字</gradient>
<bold><yellow>加粗黄色</yellow></bold>
```

两种格式可以混用：

```text
&a<bold>绿色加粗</bold>
```

帮助文本中的 `<游戏ID>` 不会被误解析为 MiniMessage。

---

## 数据文件

| 文件 | 说明 |
|---|---|
| `config.yml` | 主配置 |
| `lang/zh_CN.yml`、`lang/en_US.yml` | 语言文件 |
| `binds.json` | Discord 与 Minecraft 绑定数据 |
| `usage.json` | 每日 token 用量 |
| `audit.jsonl` | 工具调用审计 |
| `webui-token` | WebUI 令牌哈希 |

旧版 `links.json` 会在启动时自动迁移为 `binds.json`；如果 `binds.json` 已存在，则不会覆盖。

---

## 安全建议

1. `agent.mode` 保持 `manual`
2. `security.command-mode` 保持 `blacklist`，需要更严格时再改 `allowlist`
3. WebUI 仅监听 `127.0.0.1`
4. `discord.admin-role-ids` 只放可信角色
5. 不要给机器人 `Administrator` 权限
6. AI 会把日志、成员信息等发送到模型接口，请使用可信服务商
7. 定期检查 `audit.jsonl`

---

## 从源码构建

要求：

- JDK 21
- Maven 3.9+

```bash
cd Flavor-HuHoBot-Discord-Bridge
mvn clean package
```

产物：

```text
target/Flavor-HuHoBot-Discord-Bridge.jar
```

跳过测试：

```bash
mvn clean package -DskipTests
```

### 项目结构

```text
src/main/java/com/flavor-huhobot/   Java 源码
src/main/resources/                 plugin.yml、config.yml、lang/、web/
test/java/com/flavor/huhobot/       单元测试
pom.xml                             Maven 构建
```

---

## 常见问题

| 现象 | 处理 |
|---|---|
| 启动后 Discord 未连接 | 插件会自动重试 5 次；仍失败检查 token、网络、服务器时间 |
| Slash 命令不出现 | 邀请链接需要 `applications.commands`；重新授权并重启 |
| `MESSAGE_SEND` / `MESSAGE_EMBED_LINKS` | 在目标频道权限覆盖里给机器人放行 |
| 一直显示“正在响应” | 检查频道发送消息与嵌入链接权限 |
| AI 没有回复 | 检查 `agent.base-url`、`api-key`、`model`，模型需支持 function calling |
| AI 不执行命令 | `manual` 模式需要管理员批准；`auto` 模式禁用危险工具 |
| 强制绑定玩家进不来 | 关闭原版白名单/其他白名单插件 |
| `/bind` 提示输入 5 位验证码 | 已开启强制绑定，进服拿码后再绑定 |
| WebUI 401 | 使用控制台输出的令牌；忘记可删除 `webui-token` 重启 |

---

## 当前限制

- 单 Guild 设计
- 不支持控制台频道双向转发、背包渲染、脚本扩展、Velocity/Bungee
- `mc_run_command` 返回 `dispatchCommand` 的布尔结果，不捕获命令输出
- WebUI 使用本地令牌，未实现 Discord OAuth2
- 聊天桥接需要 Message Content 特权 intent
- AI 任务不创建线程，多个用户在同一频道会共用频道消息流

---

## 许可证

本项目使用 **GNU Affero General Public License v3.0（AGPL-3.0）**。

完整文本见 [LICENSE](LICENSE)。

---

## 链接

- [Modrinth](https://modrinth.com/plugin/flavor-huhoBot-discord-bridge)
- [源代码](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge)
- [问题反馈](https://github.com/dydaozhu/Flavor-HuHoBot-Discord-Bridge/issues)

---

**当前版本：1.0.0**
