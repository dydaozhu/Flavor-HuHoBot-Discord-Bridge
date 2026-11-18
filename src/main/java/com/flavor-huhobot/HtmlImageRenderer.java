package com.flavor.huhobot;

import org.xhtmlrenderer.resource.ImageResource;
import org.xhtmlrenderer.resource.XMLResource;
import org.xhtmlrenderer.simple.XHTMLPanel;
import org.xhtmlrenderer.swing.NaiveUserAgent;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 将本地 HTML 模板渲染为 PNG，不依赖浏览器。 */
public final class HtmlImageRenderer {

    private static final int SCALE = 2;
    private static final Map<String, String> TEMPLATE_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean fontRegistered;

    @FunctionalInterface
    interface ResourceLoader {
        InputStream open(String path) throws IOException;
    }

    private final ResourceLoader resources;
    private volatile String steveHead;

    public HtmlImageRenderer(FlavorDiscordBridge plugin) {
        this(path -> plugin.getResource(path));
    }

    HtmlImageRenderer(ResourceLoader resources) {
        this.resources = resources;
        registerBundledFont();
    }

    public String template(String name) throws IOException {
        String key = name;
        String cached = TEMPLATE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = resources.open("html/" + name)) {
            if (stream == null) {
                throw new IOException("缺少 HTML 模板: html/" + name);
            }
            String value = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            TEMPLATE_CACHE.put(key, value);
            return value;
        }
    }

    public byte[] render(String html, int width, int height) throws IOException {
        try {
            XHTMLPanel panel = new XHTMLPanel();
            panel.getSharedContext().setUserAgentCallback(new DataUriUserAgent());
            panel.setDocument(XMLResource.load(new StringReader(html)).getDocument());
            panel.setSize(width, height);
            panel.doLayout();

            BufferedImage image = new BufferedImage(width * SCALE, height * SCALE,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setColor(new Color(0x31, 0x33, 0x38));
                graphics.fillRect(0, 0, width * SCALE, height * SCALE);
                graphics.scale(SCALE, SCALE);
                panel.paint(graphics);
            } finally {
                graphics.dispose();
            }

            int contentBottom = findContentBottom(image);
            int trimmedHeight = Math.min(image.getHeight(), contentBottom + 10 * SCALE);
            if (trimmedHeight > 0 && trimmedHeight < image.getHeight()) {
                image = image.getSubimage(0, 0, image.getWidth(), trimmedHeight);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception error) {
            throw new IOException("HTML 渲染失败: " + error.getMessage(), error);
        }
    }

    /** 从下往上找到最后一条非背景像素，裁掉图片底部的大块空置区域。 */
    private static int findContentBottom(BufferedImage image) {
        int background = 0x313338;
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != background) {
                    return y + 1;
                }
            }
        }
        return image.getHeight();
    }

    public String bundledPng(String name) throws IOException {
        try (InputStream stream = resources.open("html/" + name)) {
            if (stream == null) {
                throw new IOException("缺少内置图片: html/" + name);
            }
            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(stream.readAllBytes());
        }
    }

    public String steveHead() {
        String cached = steveHead;
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = resources.open("html/steve.png")) {
            if (stream == null) {
                return "";
            }
            String value = pngDataUri(AvatarService.cropHead(stream.readAllBytes()));
            steveHead = value;
            return value;
        } catch (Exception ignored) {
            return "";
        }
    }

    public String defaultServerIcon() {
        try {
            return bundledPng("server-icon.png");
        } catch (IOException ignored) {
            return "";
        }
    }

    public static String pngDataUri(byte[] png) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }

    private static void registerBundledFont() {
        if (fontRegistered) {
            return;
        }
        synchronized (HtmlImageRenderer.class) {
            if (fontRegistered) {
                return;
            }
            try (InputStream stream = HtmlImageRenderer.class
                    .getResourceAsStream("/html/fonts/NotoSansSC-Regular.otf")) {
                if (stream != null) {
                    Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                }
            } catch (Exception ignored) {
                // 字体注册失败时退回系统字体，图片仍可生成。
            }
            fontRegistered = true;
        }
    }

    private static final class DataUriUserAgent extends NaiveUserAgent {
        @Override
        public ImageResource getImageResource(String uri) {
            if (uri != null && uri.startsWith("data:image/")) {
                try {
                    int comma = uri.indexOf(',');
                    if (comma > 0) {
                        byte[] bytes = Base64.getMimeDecoder().decode(uri.substring(comma + 1));
                        Image image = ImageIO.read(new ByteArrayInputStream(bytes));
                        if (image != null) {
                            return createImageResource(uri, image);
                        }
                    }
                } catch (Exception ignored) {
                    // 交给默认实现处理，避免单张图片导致整页失败。
                }
            }
            return super.getImageResource(uri);
        }
    }
}
