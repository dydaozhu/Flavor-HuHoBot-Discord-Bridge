package com.flavor.huhobot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPolicyTest {

    private final CommandPolicy policy = new CommandPolicy(
            "allowlist",
            List.of("give", "say", "whitelist", "execute"),
            List.of("op", "deop", "stop", "reload", "whitelist off"),
            256
    );

    @Test
    void allowsConfiguredCommand() {
        assertTrue(policy.check("give Steve diamond 1").allowed());
    }

    @Test
    void stripsLeadingSlash() {
        assertTrue(policy.check("/give Steve diamond").allowed());
    }

    @Test
    void blocksUnknownCommand() {
        assertFalse(policy.check("rm -rf /").allowed());
    }

    @Test
    void blocksExecuteOpBypass() {
        assertFalse(policy.check("execute as @a run op Steve").allowed());
    }

    @Test
    void blocksWhitelistOff() {
        assertFalse(policy.check("whitelist off").allowed());
    }

    @Test
    void blocksNewlineInjection() {
        assertFalse(policy.check("say hi\nop Steve").allowed());
    }

    @Test
    void blocksTooLongCommand() {
        assertFalse(policy.check("say " + "a".repeat(300)).allowed());
    }

    @Test
    void doesNotBlockOrdinaryWordInArguments() {
        assertTrue(policy.check("say op is here").allowed());
    }

    @Test
    void allowsNamespacedAllowlistedCommand() {
        assertTrue(policy.check("minecraft:give Steve diamond 1").allowed());
    }

    @Test
    void blocksNamespacedDangerousCommandInBlacklist() {
        CommandPolicy blacklist = new CommandPolicy(
                "blacklist", List.of(), List.of("op", "stop"), 256);
        assertFalse(blacklist.check("minecraft:op Steve").allowed());
        assertFalse(blacklist.check("minecraft:stop").allowed());
    }

    @Test
    void blacklistAllowsUnlistedCommand() {
        CommandPolicy blacklist = new CommandPolicy(
                "blacklist", List.of(), List.of("op"), 256);
        assertTrue(blacklist.check("give Steve diamond 1").allowed());
    }
}
