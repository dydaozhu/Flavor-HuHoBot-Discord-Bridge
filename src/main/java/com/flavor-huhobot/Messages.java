package com.flavor.huhobot;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** 轻量 i18n：从 messages_<language>.yml 读取，缺失 key 回落到内置中文默认值。 */
public final class Messages {

    private static final Map<String, String> DEFAULTS = defaults();

    private final FlavorDiscordBridge plugin;
    private final Map<String, String> values = new LinkedHashMap<>();

    public Messages(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    public void load() {
        values.clear();
        values.putAll(DEFAULTS);

        String language = plugin.config().language();
        File langDirectory = new File(plugin.getDataFolder(), "lang");
        if (!langDirectory.isDirectory()) {
            langDirectory.mkdirs();
        }
        // 先用 jar 内语言文件补齐新增的键（例如 Discord 命令描述）
        try (InputStream stream = plugin.getResource("lang/" + language + ".yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                for (String key : defaults.getKeys(true)) {
                    String value = defaults.getString(key);
                    if (value != null) {
                        values.put(key, clean(value));
                    }
                }
            }
        } catch (Exception ignored) {
            // 资源读取失败时继续使用内置默认值
        }

        File file = new File(langDirectory, language + ".yml");
        if (!file.isFile()) {
            try {
                plugin.saveResource("lang/" + language + ".yml", false);
            } catch (IllegalArgumentException ignored) {
                // 资源不存在时继续使用内置默认值
            }
        }
        if (file.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(true)) {
                String value = yaml.getString(key);
                if (value != null) {
                    values.put(key, clean(value));
                }
            }
        }
    }

    /** 去掉语言文件句尾多余的标点，兼容末尾颜色代码。 */
    private static String clean(String value) {
        String result = value;
        for (int i = 0; i < 3; i++) {
            String next = result.replaceAll(
                    "(?i)([。．.；;，,！!？?：:])((?:&[0-9a-fk-or])*)$", "$2");
            if (next.equals(result)) {
                break;
            }
            result = next;
        }
        return result;
    }

    public String raw(String key) {
        return values.getOrDefault(key, key);
    }

    /** 插件前缀，用于所有游戏内提示。 */
    public String prefix() {
        return raw("prefix");
    }

    public String get(String key) {
        return get(key, Map.of());
    }

    public String get(String key, Map<String, String> placeholders) {
        String template = raw(key).replace("{prefix}", prefix());
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return template;
    }

    /** Discord 不渲染 Minecraft 颜色，所有发往 Discord 的文本都要先清理。 */
    public String discord(String key) {
        return Text.stripColorCodes(get(key));
    }

    public String discord(String key, Map<String, String> placeholders) {
        return Text.stripColorCodes(get(key, placeholders));
    }

    /** 带插件前缀的游戏内文本；模板里已经包含 {prefix} 时不会重复添加。 */
    public String getPrefixed(String key, Map<String, String> placeholders) {
        String text = get(key, placeholders);
        String prefix = prefix();
        return text.startsWith(prefix) ? text : prefix + text;
    }

    public String getPrefixed(String key) {
        return getPrefixed(key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (sender != null) {
            sender.sendMessage(Text.component(getPrefixed(key, placeholders)));
        }
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    private static Map<String, String> defaults() {
        Map<String, String> map = new HashMap<>();
        map.put("prefix", "&8[&bFlavor&3Discord&8] &r");
        map.put("no-permission", "&c你没有权限执行此操作");
        map.put("player-only", "&c此命令只能由游戏内玩家执行");
        map.put("unknown-command", "&c未知子命令，使用 &e/flavorhuhobot help &c查看帮助");
        map.put("reloaded", "&a配置、语言、绑定与用量数据已重载，Discord 正在重新连接");
        map.put("discord-not-ready", "&cDiscord 机器人尚未连接，请检查 token、网络与 intent 设置");
        map.put("link-success", "&a绑定成功：&e{player}");
        map.put("link-already", "&e你已经绑定了角色 &6{player}&e，如需更换，请先执行 &f/unbind");
        map.put("link-player-taken", "&c该游戏角色已被其他 Discord 用户绑定");
        map.put("link-discord-taken", "&c该 Discord 用户已绑定角色 &6{player}");
        map.put("link-none", "&e你还没有绑定任何角色");
        map.put("link-removed", "&a已解除绑定");
        map.put("link-required", "&c请先绑定 Minecraft 账号后再聊天");
        map.put("force-bind-kick", "&c你尚未绑定 Minecraft 账号\n&e验证码：&f{code}\n&e请在 Discord 执行 &f/bind {code}\n&7Discord：{groups}\n&7绑定后重新进入服务器");
        map.put("bind-success", "&a绑定成功：&e{player}&a，请重新进入服务器");
        map.put("bind-invalid", "&c验证码无效或已使用");
        map.put("bind-expired", "&c验证码已过期，请重新进入服务器获取");
        map.put("bind-rate-limited", "&c尝试过于频繁，请稍后再试");
        map.put("agent-disabled", "&cAI Agent 未启用，请先在 config.yml 中开启 agent.enabled");
        map.put("agent-no-key", "&cAI Agent 未配置 base-url 或 api-key");
        map.put("agent-started", "&aAI 任务已开始");
        map.put("agent-stopped", "&e已请求停止当前 AI 任务");
        map.put("agent-busy", "&e你有一个任务正在运行，请使用 /agent stop 停止或等待完成");
        map.put("agent-context-cleared", "&aAI 上下文已清除");
        map.put("agent-context-busy", "&e当前有任务运行或等待审批，请先 /agent stop 再清除上下文");
        map.put("agent-no-channel-permission", "&c机器人没有在目标频道发送消息/嵌入链接的权限，请检查频道权限覆盖");
        map.put("agent-budget", "&c今日 AI token 预算已用尽，请联系管理员");
        map.put("agent-error", "&cAI 调用失败：{error}");
        map.put("agent-max-steps", "&e已达到最大执行步数，任务自动结束");
        map.put("approval-title", "需要管理员审批");
        map.put("approval-approved", "&a操作已批准执行");
        map.put("approval-rejected", "&c操作已被拒绝");
        map.put("approval-timeout", "&e审批超时，操作已自动拒绝");
        map.put("approval-no-permission", "&c你没有审批权限");
        map.put("approval-self", "&c不能批准自己发起的请求");
        map.put("approval-expired", "&c该审批已过期或不存在");
        map.put("console-executed", "&a命令已提交：&f{command}");
        map.put("console-denied", "&c命令被安全策略拒绝：{reason}");
        map.put("whitelist-added", "&a已添加白名单：&e{player}");
        map.put("whitelist-removed", "&a已移除白名单：&e{player}");
        map.put("whitelist-empty", "&e白名单为空");
        map.put("status-title", "服务器状态");
        map.put("status-server", "服务器");
        map.put("status-discord", "Discord");
        map.put("status-agent", "AI Agent");
        map.put("status-online", "在线玩家");
        map.put("status-links", "已绑定账号");
        map.put("status-tokens", "今日 token");
        map.put("help-title", "Flavor-HuHoBot-Discord-Bridge 帮助");
        map.put("help-line", "&e/bind <游戏ID或验证码> &7- 绑定 Minecraft 账号");
        map.put("tool-denied", "&c工具调用被拒绝：{reason}");
        map.put("command.agent.description", "AI 管理助手");
        map.put("command.agent.run", "执行一个 AI 任务");
        map.put("command.agent.task", "任务描述");
        map.put("command.agent.stop", "停止当前 AI 任务");
        map.put("command.agent.new", "清除当前 AI 上下文");
        map.put("command.bind.description", "绑定 Minecraft 账号");
        map.put("command.bind.value", "游戏ID（未开强制绑定）或 5 位验证码（开启强制绑定）");
        map.put("command.unbind.description", "解除 Minecraft 账号绑定");
        map.put("command.status.description", "查看服务器与机器人状态");
        map.put("command.online.description", "查看在线玩家");
        map.put("command.whitelist.description", "白名单管理");
        map.put("command.whitelist.add", "添加玩家");
        map.put("command.whitelist.remove", "移除玩家");
        map.put("command.whitelist.list", "列出白名单");
        map.put("command.whitelist.player", "玩家名");
        map.put("command.console.description", "执行控制台命令（管理员）");
        map.put("command.console.command", "命令（不含前导 /）");
        map.put("command.help.description", "查看帮助");
        map.put("command.reload.description", "重载所有文件并重连机器人（管理员）");
        return Map.copyOf(map);
    }
}
