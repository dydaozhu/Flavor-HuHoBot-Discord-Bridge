package com.flavor.huhobot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineHistoryTest {

    @TempDir
    Path directory;

    @Test
    void persistsSamples() {
        Path file = directory.resolve("online-history.json");
        OnlineHistory history = new OnlineHistory(file.toFile());
        history.record(3);
        history.record(7);

        OnlineHistory reloaded = new OnlineHistory(file.toFile());
        reloaded.load();

        assertEquals(2, reloaded.snapshot().size());
        assertEquals(7, reloaded.snapshot().get(1).count());
    }

    @Test
    void chartNeedsAtLeastTwoSamples() {
        OnlineChartRenderer renderer = new OnlineChartRenderer();
        assertTrue(renderer.render(java.util.List.of()) == null);
        assertTrue(renderer.render(java.util.List.of(new OnlineHistory.Sample(1, 2))) == null);
    }
}
