package com.flavor.huhobot;

import com.fasterxml.jackson.databind.node.ObjectNode;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Discord 侧工具。 */
public final class DiscordTools {

    private DiscordTools() {
    }

    public static void register(ToolRegistry registry, FlavorDiscordBridge plugin) {
        registry.register(new GuildInfo(plugin));
        registry.register(new MemberInfo(plugin));
        registry.register(new ListRoles(plugin));
        registry.register(new SendMessage(plugin));
        registry.register(new TimeoutMember(plugin));
        registry.register(new KickMember(plugin));
        registry.register(new BanMember(plugin));
        registry.register(new ManageRole(plugin));
    }

    // ------------------------------------------------------------------ 只读

    private static final class GuildInfo implements Tool {

        private final FlavorDiscordBridge plugin;

        private GuildInfo(FlavorDiscordBridge plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "dc_guild_info";
        }

        @Override
        public String description() {
            return "获取当前 Discord 服务器的基本信息（名称、成员数、频道数、创建时间）。";
        }

        @Override
        public ObjectNode parameters() {
            return Schemas.object();
        }

        @Override
        public boolean requiresApproval() {
            return false;
        }

        @Override
        public ToolResult execute(ToolContext context, ObjectNode arguments) {
            Guild guild = context.guild();
            if (guild == null) {
                return ToolResult.text("当前上下文没有 Discord 服务器。");
            }
            String text = "名称：" + guild.getName()
                    + "\nID：" + guild.getId()
                    + "\n所有者 ID：" + guild.getOwnerId()
                    + "\n成员数：" + guild.getMemberCount()
                    + "\n频道数：" + guild.getChannels().size()
                    + "\nBoost 数：" + guild.getBoostCount()
                    + "\n创建时间：" + guild.getTimeCreated();
            return ToolResult.card(text, "Discord 服务器信息", text);
        }
    }

    private static final class MemberInfo implements Tool {

        private final FlavorDiscordBridge plugin;

        private MemberInfo(FlavorDiscordBridge plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "dc_member_info";
        }

        @Override
        public String description() {
            return "查询 Discord 成员信息（ID、昵称、加入时间、角色）。支持用户 ID 或服务器昵称。";
        }

        @Override
        public ObjectNode parameters() {
            ObjectNode schema = Schemas.object();
            Schemas.property(schema, "member", "string", "用户 ID、@提及 或服务器昵称");
            Schemas.required(schema, "member");
            return schema;
        }

        @Override
        public boolean requiresApproval() {
            return false;
        }

        @Override
        public ToolResult execute(ToolContext context, ObjectNode arguments) {
            Member member = findMember(context.guild(), arguments.path("member").asText(""));
            String roles = member.getRoles().isEmpty()
                    ? "无"
                    : String.join(", ", member.getRoles().stream().map(Role::getName).toList());
            String text = "昵称：" + member.getEffectiveName()
                    + "\n用户名：" + member.getUser().getName()
                    + "\nID：" + member.getId()
                    + "\n加入时间：" + member.getTimeJoined()
                    + "\n账号创建：" + member.getUser().getTimeCreated()
                    + "\n角色：" + roles;
            return ToolResult.card(text, "成员信息", text);
        }
    }

    private static final class ListRoles implements Tool {

        private final FlavorDiscordBridge plugin;

        private ListRoles(FlavorDiscordBridge plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "dc_list_roles";
        }

        @Override
        public String description() {
            return "列出 Discord 服务器的角色（名称、ID、位置）。";
        }

        @Override
        public ObjectNode parameters() {
            return Schemas.object();
        }

        @Override
        public boolean requiresApproval() {
            return false;
        }

        @Override
        public ToolResult execute(ToolContext context, ObjectNode arguments) {
            Guild guild = context.guild();
            if (guild == null) {
                return ToolResult.text("当前上下文没有 Discord 服务器。");
            }
            List<Role> roles = guild.getRoles().stream()
                    .sorted(Comparator.comparingInt(Role::getPosition).reversed())
                    .toList();
            StringBuilder builder = new StringBuilder();
            for (Role role : roles) {
                builder.append("- ").append(role.getName())
                        .append(" (ID: ").append(role.getId())
                        .append(", 位置: ").append(role.getPosition())
                        .append(")\n");
            }
            String text = builder.toString().trim();
            return ToolResult.card(text, "角色列表", text);
        }
    }

    // ------------------------------------------------------------------ 写操作

    private static final class SendMessage implements Tool {

        private final FlavorDiscordBridge plugin;

        private SendMessage(FlavorDiscordBridge plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "dc_send_message";
        }

        @Override
        public String description() {
            return "向指定文本频道发送一条消息（默认当前频道）。不会解析 @everyone 等提及。";
        }

        @Override
        public ObjectNode parameters() {
            ObjectNode schema = Schemas.object();
            Schemas.property(schema, "message", "string", "消息内容（最多 2000 字）");
            Schemas.property(schema, "channel_id", "string", "可选，目标文本频道 ID；默认当前会话频道");
            Schemas.required(schema, "message");
            return schema;
        }

        @Override
        public boolean requiresApproval() {
            return false;
        }

        @Override
        public ToolResult execute(ToolContext context, ObjectNode arguments) {
            String content = arguments.path("message").asText("").trim();
            if (content.isEmpty()) {
                return ToolResult.text("消息内容为空。");
            }
            String channelId = arguments.path("channel_id").asText("").trim();
            MessageChannel channel = plugin.discordBot().messageChannel(
                    channelId.isBlank() ? context.channelId() : channelId);
            if (channel == null) {
                return ToolResult.text("找不到目标文本频道。");
            }
            Message message = channel.sendMessage(Text.truncate(content, 2000))
                    .setAllowedMentions(Collections.<Message.MentionType>emptyList())
                    .complete();
            return ToolResult.text("消息已发送，ID: " + message.getId());
        }
    }

    private static final class TimeoutMember implements Tool {

        private final FlavorDiscordBridge plugin;

        private TimeoutMember(FlavorDiscordBridge plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "dc_timeout_member";
        }

        @Override
        public String description() {
            return "禁言（timeout）Discord 成员，最长 10080 分钟（7 天）。需要管理员审批。";
        }

        @Override
        public ObjectNode parameters() {
            ObjectNode schema = Schemas.object();
            Schemas.property(schema, "member", "string", "用户 ID、@提及 或昵称");
            Schemas.property(schema, "minutes", "integer", "禁言分钟数，1-10080");
            Schemas.property(schema, "reason", "string", "可选，原因");
            Schemas.required(schema, "member", "minutes");
            return schema;
        }

        @Override
        public boolean requiresApproval() {
            return true;
        }

        @Override
        public ToolResult execute(ToolContext context, ObjectNode arguments) {
            Member member = findMember(context.guild(), arguments.path("member").asText(""));
            guard(context, member);
            int minutes = Math.min(10080, Math.max(1, arguments.path("minutes").asInt(10)));
            String reason = arguments.path("reason").asText("由 AI 助手执行");
            member.timeoutFor(Duration.ofMinutes(minutes)).reason(reason).complete();
            return ToolResult.text("已禁言 " + member.getEffectiveName() + " " + minutes + " 分钟。");
        }
    }

    private static final class KickMember implements Tool {

        private final FlavorDiscordBridge plugin;

        private KickMember(FlavorDiscordBridge plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "dc_kick_member";
        }

        @Override
        public String description() {
            return "将成员踢出 Discord 服务器。需要管理员审批。";
        }

        @Override
        public ObjectNode parameters() {
            ObjectNode schema = Schemas.object();
            Schemas.property(schema, "member", "string", "用户 ID、@提及 或昵称");
            Schemas.property(schema, "reason", "string", "可选，原因");
            Schemas.required(schema, "member");
            return schema;
        }

        @Override
        public boolean requiresApproval() {
            return true;
        }

        @Override
        public ToolResult execute(ToolContext context, ObjectNode arguments) {
            Member member = findMember(context.guild(), arguments.path("member").asText(""));
            guard(context, member);
            member.kick().reason(arguments.path("reason").asText("由 AI 助手执行")).complete();
            return ToolResult.text("已踢出成员：" + member.getEffectiveName());
        }
    }

    private static final class BanMember implements Tool {

        private final FlavorDiscordBridge plugin;

        private BanMember(FlavorDiscordBridge plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "dc_ban_member";
        }

        @Override
        public String description() {
            return "封禁（ban）Discord 成员，可删除最近消息。需要管理员审批。";
        }

        @Override
        public ObjectNode parameters() {
            ObjectNode schema = Schemas.object();
            Schemas.property(schema, "member", "string", "用户 ID、@提及 或昵称");
            Schemas.property(schema, "delete_days", "integer", "删除最近几天消息，0-7，默认 0");
            Schemas.property(schema, "reason", "string", "可选，原因");
            Schemas.required(schema, "member");
            return schema;
        }

        @Override
        public boolean requiresApproval() {
            return true;
        }

        @Override
        public ToolResult execute(ToolContext context, ObjectNode arguments) {
            Guild guild = context.guild();
            Member member = findMember(guild, arguments.path("member").asText(""));
            guard(context, member);
            int days = Math.min(7, Math.max(0, arguments.path("delete_days").asInt(0)));
            guild.ban(member.getUser(), days, java.util.concurrent.TimeUnit.DAYS)
                    .reason(arguments.path("reason").asText("由 AI 助手执行"))
                    .complete();
            return ToolResult.text("已封禁成员：" + member.getEffectiveName());
        }
    }

    private static final class ManageRole implements Tool {

        private final FlavorDiscordBridge plugin;

        private ManageRole(FlavorDiscordBridge plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "dc_manage_role";
        }

        @Override
        public String description() {
            return "给成员添加或移除角色。需要管理员审批，且不能操作机器人自己或服务器所有者。";
        }

        @Override
        public ObjectNode parameters() {
            ObjectNode schema = Schemas.object();
            Schemas.property(schema, "action", "string", "add 或 remove");
            Schemas.property(schema, "member", "string", "用户 ID、@提及 或昵称");
            Schemas.property(schema, "role", "string", "角色 ID 或角色名");
            Schemas.required(schema, "action", "member", "role");
            return schema;
        }

        @Override
        public boolean requiresApproval() {
            return true;
        }

        @Override
        public ToolResult execute(ToolContext context, ObjectNode arguments) {
            Guild guild = context.guild();
            Member member = findMember(guild, arguments.path("member").asText(""));
            guard(context, member);
            String action = arguments.path("action").asText("").toLowerCase(Locale.ROOT);
            if (!action.equals("add") && !action.equals("remove")) {
                return ToolResult.text("action 必须是 add 或 remove。");
            }
            Role role = findRole(guild, arguments.path("role").asText(""));
            if (action.equals("add")) {
                guild.addRoleToMember(member, role).complete();
            } else {
                guild.removeRoleFromMember(member, role).complete();
            }
            return ToolResult.text((action.equals("add") ? "已添加角色 " : "已移除角色 ")
                    + role.getName() + "：" + member.getEffectiveName());
        }
    }

    // ------------------------------------------------------------------ 工具方法

    static Member findMember(Guild guild, String query) {
        if (guild == null) {
            throw new IllegalArgumentException("当前上下文没有 Discord 服务器");
        }
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("缺少 member 参数");
        }
        String value = query.trim();
        if (value.matches("<@!?\\d+>")) {
            value = value.replaceAll("[^0-9]", "");
        }
        Member member = value.matches("\\d{15,20}") ? guild.getMemberById(value) : null;
        if (member == null) {
            String lower = value.toLowerCase(Locale.ROOT);
            member = guild.getMemberCache().stream()
                    .filter(candidate -> candidate.getEffectiveName().toLowerCase(Locale.ROOT).equals(lower)
                            || candidate.getUser().getName().toLowerCase(Locale.ROOT).equals(lower))
                    .findFirst()
                    .orElse(null);
        }
        if (member == null) {
            try {
                member = guild.retrieveMemberById(value).complete();
            } catch (Exception ignored) {
            }
        }
        if (member == null) {
            throw new IllegalArgumentException("找不到成员：" + query + "（可改用用户 ID）");
        }
        return member;
    }

    static Role findRole(Guild guild, String query) {
        if (guild == null || query == null || query.isBlank()) {
            throw new IllegalArgumentException("缺少 role 参数");
        }
        String value = query.trim();
        Role role = value.matches("\\d{15,20}") ? guild.getRoleById(value) : null;
        if (role == null) {
            role = guild.getRolesByName(value, true).stream().findFirst().orElse(null);
        }
        if (role == null) {
            throw new IllegalArgumentException("找不到角色：" + query);
        }
        return role;
    }

    private static void guard(ToolContext context, Member target) {
        if (target == null) {
            throw new IllegalArgumentException("找不到目标成员");
        }
        if (target.getId().equals(context.plugin().discordBot().selfId())) {
            throw new IllegalArgumentException("不能操作机器人自己");
        }
        if (target.isOwner()) {
            throw new IllegalArgumentException("不能操作服务器所有者");
        }
    }
}
