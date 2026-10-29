package com.flavor.huhobot;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一次 AI 任务（对应一个 Discord 线程）。 */
public final class AgentSession {

    private final String id;
    private final String guildId;
    private final String channelId;
    private final String ownerId;
    private final String ownerName;
    private final long createdAt = System.currentTimeMillis();
    private volatile long lastActiveAt = System.currentTimeMillis();
    private final List<ObjectNode> messages = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile boolean stopped;
    private volatile Approval pending;
    private volatile ArrayDeque<ToolCall> batch;
    private int steps;

    public AgentSession(String id, String guildId, String channelId, String ownerId, String ownerName) {
        this.id = id;
        this.guildId = guildId;
        this.channelId = channelId;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
    }

    public String id() {
        return id;
    }

    public String guildId() {
        return guildId;
    }

    public String channelId() {
        return channelId;
    }

    public String ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public long createdAt() {
        return createdAt;
    }

    public long lastActiveAt() {
        return lastActiveAt;
    }

    public void touch() {
        lastActiveAt = System.currentTimeMillis();
    }

    public List<ObjectNode> messages() {
        return messages;
    }

    public AtomicBoolean running() {
        return running;
    }

    public boolean stopped() {
        return stopped;
    }

    public void stopped(boolean stopped) {
        this.stopped = stopped;
    }

    public Approval pending() {
        return pending;
    }

    public void pending(Approval pending) {
        this.pending = pending;
    }

    public ArrayDeque<ToolCall> batch() {
        return batch;
    }

    public void batch(ArrayDeque<ToolCall> batch) {
        this.batch = batch;
    }

    public int steps() {
        return steps;
    }

    public void resetSteps() {
        steps = 0;
    }

    public int nextStep() {
        return ++steps;
    }
}
