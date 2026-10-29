package com.flavor.huhobot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.awt.Color;

/** 常用 Embed 与权限判断。 */
public final class DiscordUtil {

    public static final Color BLURPLE = new Color(0x5865F2);
    public static final Color GREEN = new Color(0x57F287);
    public static final Color YELLOW = new Color(0xFEE75C);
    public static final Color RED = new Color(0xED4245);

    private DiscordUtil() {
    }

    public static EmbedBuilder base(String title) {
        return new EmbedBuilder().setColor(BLURPLE).setTitle(title == null ? "" : title);
    }

    public static EmbedBuilder ok(String title) {
        return new EmbedBuilder().setColor(GREEN).setTitle(title == null ? "" : title);
    }

    public static EmbedBuilder warn(String title) {
        return new EmbedBuilder().setColor(YELLOW).setTitle(title == null ? "" : title);
    }

    public static EmbedBuilder error(String title) {
        return new EmbedBuilder().setColor(RED).setTitle(title == null ? "" : title);
    }

    /** 服务器管理员：服务器管理权限、服务器所有者，或配置的管理角色。 */
    public static boolean isAdmin(FlavorDiscordBridge plugin, Member member) {
        if (member == null) {
            return false;
        }
        if (member.isOwner()) {
            return true;
        }
        if (member.hasPermission(Permission.MANAGE_SERVER)) {
            return true;
        }
        String memberId = member.getId();
        for (String roleId : plugin.config().adminRoleIds()) {
            if (member.getRoles().stream().anyMatch(role -> role.getId().equals(roleId))) {
                return true;
            }
        }
        return false;
    }

    public static Guild guild(FlavorDiscordBridge plugin) {
        return plugin.discordBot().guild();
    }
}
