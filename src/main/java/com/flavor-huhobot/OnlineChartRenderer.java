package com.flavor.huhobot;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 在线人数折线图；两段数据之间的空档用红色表示。 */
public final class OnlineChartRenderer {

    private static final int WIDTH = 820;
    private static final int HEIGHT = 190;
    private static final long GAP_MILLIS = OnlineHistory.SAMPLE_INTERVAL_MILLIS * 2;

    public byte[] render(List<OnlineHistory.Sample> input) {
        if (input == null || input.size() < 2) {
            return null;
        }
        List<OnlineHistory.Sample> samples = new ArrayList<>(input);
        samples.sort((a, b) -> Long.compare(a.time(), b.time()));

        long start = samples.get(0).time();
        long end = samples.get(samples.size() - 1).time();
        if (end <= start) {
            return null;
        }
        int max = 1;
        for (OnlineHistory.Sample sample : samples) {
            max = Math.max(max, sample.count());
        }
        max = Math.max(1, (int) Math.ceil(max * 1.15));

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0x23, 0x24, 0x28));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            int left = 42;
            int right = WIDTH - 14;
            int top = 12;
            int bottom = HEIGHT - 26;
            int plotWidth = right - left;
            int plotHeight = bottom - top;

            g.setColor(new Color(0x3f, 0x41, 0x47));
            g.setStroke(new BasicStroke(1f));
            for (int i = 0; i <= 4; i++) {
                int y = top + plotHeight * i / 4;
                g.drawLine(left, y, right, y);
            }

            for (int i = 0; i < samples.size() - 1; i++) {
                OnlineHistory.Sample a = samples.get(i);
                OnlineHistory.Sample b = samples.get(i + 1);
                if (b.time() - a.time() > GAP_MILLIS) {
                    int x1 = x(a.time(), start, end, left, plotWidth);
                    int x2 = x(b.time(), start, end, left, plotWidth);
                    g.setColor(new Color(0x8b, 0x2f, 0x36, 180));
                    g.fillRect(x1, top, Math.max(2, x2 - x1), plotHeight);
                }
            }

            g.setFont(new Font("Noto Sans SC", Font.PLAIN, 11));
            g.setColor(new Color(0x94, 0x9b, 0xa4));
            g.drawString(String.valueOf(max), 8, top + 10);
            g.drawString("0", 20, bottom);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.systemDefault());
            for (int i = 0; i <= 4; i++) {
                long t = start + (end - start) * i / 4;
                int x = x(t, start, end, left, plotWidth);
                String label = formatter.format(Instant.ofEpochMilli(t));
                int width = g.getFontMetrics().stringWidth(label);
                g.drawString(label, Math.min(right - width, Math.max(left, x - width / 2)), HEIGHT - 8);
            }

            g.setColor(new Color(0x57, 0xf2, 0x87));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < samples.size() - 1; i++) {
                OnlineHistory.Sample a = samples.get(i);
                OnlineHistory.Sample b = samples.get(i + 1);
                if (b.time() - a.time() > GAP_MILLIS) {
                    continue;
                }
                int x1 = x(a.time(), start, end, left, plotWidth);
                int x2 = x(b.time(), start, end, left, plotWidth);
                int y1 = y(a.count(), max, top, plotHeight);
                int y2 = y(b.count(), max, top, plotHeight);
                g.drawLine(x1, y1, x2, y2);
            }
            g.setColor(new Color(0x57, 0xf2, 0x87));
            for (OnlineHistory.Sample sample : samples) {
                int px = x(sample.time(), start, end, left, plotWidth);
                int py = y(sample.count(), max, top, plotHeight);
                g.fillOval(px - 2, py - 2, 4, 4);
            }
        } finally {
            g.dispose();
        }

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int x(long time, long start, long end, int left, int width) {
        return left + (int) Math.round((double) (time - start) / (end - start) * width);
    }

    private static int y(int count, int max, int top, int height) {
        return top + height - (int) Math.round((double) count / max * height);
    }
}
