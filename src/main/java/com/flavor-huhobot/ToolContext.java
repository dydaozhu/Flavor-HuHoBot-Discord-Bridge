package com.flavor.huhobot;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

/** 工具执行上下文。模型传入的 guild/channel 一律不采信，只使用这里的值。 */
public record ToolContext(FlavorDiscordBridge plugin,
                          Guild guild,
                          Member member,
                          String channelId,
                          String actorId,
                          String actorName) {
}
