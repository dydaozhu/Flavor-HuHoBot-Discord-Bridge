package com.flavor.huhobot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkStoreTest {

    @TempDir
    Path directory;

    @Test
    void persistsAndReloadsLinks() {
        Path file = directory.resolve("binds.json");
        LinkStore store = new LinkStore(file.toFile());
        store.put(new LinkStore.Link("111", "uuid-1", "Steve", 1000L));

        LinkStore reloaded = new LinkStore(file.toFile());
        reloaded.load();

        assertEquals(1, reloaded.size());
        assertEquals("Steve", reloaded.byDiscord("111").mcName());
        assertEquals("Steve", reloaded.byName("steve").mcName());
        assertEquals("Steve", reloaded.byUuid("uuid-1").mcName());
    }

    @Test
    void allowsMultipleLinksForSameDiscordUser() {
        LinkStore store = new LinkStore(directory.resolve("binds.json").toFile());
        store.put(new LinkStore.Link("111", "uuid-1", "Steve", 1000L));
        store.put(new LinkStore.Link("111", "uuid-2", "Alex", 2000L));

        assertEquals(2, store.size());
        assertEquals(2, store.countByDiscord("111"));
        assertNotNull(store.byName("Steve"));
        assertNotNull(store.byName("Alex"));
    }

    @Test
    void removesSpecificLinkAndKeepsOthers() {
        LinkStore store = new LinkStore(directory.resolve("binds.json").toFile());
        store.put(new LinkStore.Link("111", "uuid-1", "Steve", 1000L));
        store.put(new LinkStore.Link("111", "uuid-2", "Alex", 2000L));

        assertTrue(store.removeLink("111", "uuid-1"));
        assertEquals(1, store.size());
        assertNull(store.byUuid("uuid-1"));
        assertNotNull(store.byUuid("uuid-2"));
    }

    @Test
    void persistsLockedState() {
        Path file = directory.resolve("binds.json");
        LinkStore store = new LinkStore(file.toFile());
        store.put(new LinkStore.Link("111", "uuid-1", "Steve", 1000L));
        assertTrue(store.setLocked("111", "uuid-1", true));

        LinkStore reloaded = new LinkStore(file.toFile());
        reloaded.load();
        assertTrue(reloaded.byUuid("uuid-1").locked());
    }

    @Test
    void removesAllLinksOfDiscordUser() {
        LinkStore store = new LinkStore(directory.resolve("binds.json").toFile());
        store.put(new LinkStore.Link("111", "uuid-1", "Steve", 1000L));
        store.put(new LinkStore.Link("111", "uuid-2", "Alex", 2000L));
        assertTrue(store.removeByDiscord("111"));
        assertEquals(0, store.size());
        assertNull(store.byUuid("uuid-1"));
        assertNull(store.byUuid("uuid-2"));
    }
}

