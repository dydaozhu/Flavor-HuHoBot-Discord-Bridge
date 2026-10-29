package com.flavor.huhobot;


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 待审批请求的生命周期。 */
public final class ApprovalService {

    private final FlavorDiscordBridge plugin;
    private final AgentService agent;
    private final Map<String, Approval> pending = new ConcurrentHashMap<>();

    public ApprovalService(FlavorDiscordBridge plugin, AgentService agent) {
        this.plugin = plugin;
        this.agent = agent;
    }

    public Approval create(AgentSession session, ToolCall call, String argsJson) {
        long now = System.currentTimeMillis();
        Approval approval = new Approval(
                UUID.randomUUID().toString(),
                session.id(),
                session.guildId(),
                session.channelId(),
                session.ownerId(),
                call.name(),
                argsJson,
                call.id(),
                now,
                now + plugin.config().approvalTimeoutSeconds() * 1000L
        );
        pending.put(approval.id(), approval);
        return approval;
    }

    public Approval get(String id) {
        return pending.get(id);
    }

    public void decide(String id, String userId, boolean approved) {
        Approval approval = pending.remove(id);
        if (approval != null) {
            agent.onDecision(approval, approved, userId);
        }
    }

    public void expire() {
        long now = System.currentTimeMillis();
        for (Approval approval : pending.values()) {
            if (approval.expiresAt() <= now && pending.remove(approval.id(), approval)) {
                agent.onExpired(approval);
            }
        }
    }

    public void removeForSession(String sessionId) {
        pending.values().removeIf(approval -> approval.sessionId().equals(sessionId));
    }

    public int size() {
        return pending.size();
    }
}
