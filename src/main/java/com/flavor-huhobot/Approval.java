package com.flavor.huhobot;

/** 一次等待管理员审批的工具调用。 */
public final class Approval {

    private final String id;
    private final String sessionId;
    private final String guildId;
    private final String channelId;
    private final String actorId;
    private final String toolName;
    private final String argsJson;
    private final String toolCallId;
    private final long createdAt;
    private final long expiresAt;

    private volatile String messageId;

    public Approval(String id,
                    String sessionId,
                    String guildId,
                    String channelId,
                    String actorId,
                    String toolName,
                    String argsJson,
                    String toolCallId,
                    long createdAt,
                    long expiresAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.guildId = guildId;
        this.channelId = channelId;
        this.actorId = actorId;
        this.toolName = toolName;
        this.argsJson = argsJson;
        this.toolCallId = toolCallId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String id() {
        return id;
    }

    public String sessionId() {
        return sessionId;
    }

    public String guildId() {
        return guildId;
    }

    public String channelId() {
        return channelId;
    }

    public String actorId() {
        return actorId;
    }

    public String toolName() {
        return toolName;
    }

    public String argsJson() {
        return argsJson;
    }

    public String toolCallId() {
        return toolCallId;
    }

    public long createdAt() {
        return createdAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public String messageId() {
        return messageId;
    }

    public void messageId(String messageId) {
        this.messageId = messageId;
    }
}
