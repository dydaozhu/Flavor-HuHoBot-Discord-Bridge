package com.flavor.huhobot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.Instant;

/** 审批按钮回调。 */
public final class ButtonListener extends ListenerAdapter {

    private static final String PREFIX = "fhd:";

    private final FlavorDiscordBridge plugin;

    public ButtonListener(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith(PREFIX)) {
            return;
        }
        String[] parts = componentId.split(":");
        if (parts.length != 3) {
            return;
        }
        String action = parts[1];
        String approvalId = parts[2];
        Approval approval = plugin.agentService().approvals().get(approvalId);
        if (approval == null) {
            event.reply(plugin.messages().discord("approval-expired")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            event.reply(plugin.messages().discord("no-permission")).setEphemeral(true).queue();
            return;
        }
        if (!plugin.config().allowSelfApproval() && member.getId().equals(approval.actorId())) {
            event.reply(plugin.messages().discord("approval-self")).setEphemeral(true).queue();
            return;
        }
        if (!plugin.discordBot().isAdmin(member)) {
            event.reply(plugin.messages().discord("approval-no-permission")).setEphemeral(true).queue();
            return;
        }

        boolean approved = "approve".equals(action);
        event.editMessageEmbeds(resultEmbed(approval, approved, member)).setComponents().queue(
                null,
                error -> plugin.getLogger().warning("更新审批卡片失败: " + error.getMessage())
        );
        plugin.agentService().approvals().decide(approvalId, member.getId(), approved);
    }

    private MessageEmbed resultEmbed(Approval approval, boolean approved, Member member) {
        EmbedBuilder embed = approved
                ? DiscordUtil.ok("已批准")
                : DiscordUtil.error("已拒绝");
        embed.addField("工具", approval.toolName(), true)
                .addField("审批人", member.getEffectiveName(), true)
                .addField("参数", "```json\n" + truncate(approval.argsJson()) + "\n```", false)
                .setTimestamp(Instant.now());
        return embed.build();
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 900 ? value.substring(0, 900) + "…" : value;
    }
}
