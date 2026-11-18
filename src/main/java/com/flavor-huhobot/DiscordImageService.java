package com.flavor.huhobot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 生成 Discord 图片所需的 HTML 数据与上传字节。 */
public final class DiscordImageService {

    private static final int WHITELIST_PAGE_SIZE = 20;
    private static final int ONLINE_PLAYER_LIMIT = 24;

    private final FlavorDiscordBridge plugin;
    private final HtmlImageRenderer renderer;
    private final AvatarService avatars;
    private final OnlineChartRenderer charts = new OnlineChartRenderer();

    public DiscordImageService(FlavorDiscordBridge plugin) {
        this.plugin = plugin;
        this.renderer = plugin.imageRenderer();
        this.avatars = plugin.avatarService();
    }

    public byte[] motd(MotdClient.Result result, String displayAddress) throws IOException {
        String html = renderer.template("motd.html");
        String icon = result.icon() != null && result.icon().startsWith("data:image/")
                ? result.icon()
                : renderer.defaultServerIcon();
        String motd;
        if (result.motdComponent() != null && !result.motdComponent().isBlank()) {
            motd = MotdComponent.toHtml(result.motdComponent());
        } else if (result.motd() == null || result.motd().isBlank()) {
            motd = message("motd-empty");
        } else {
            motd = MotdHtml.toHtml(Text.truncate(result.motd(), 360));
        }
        String players = result.maxPlayers() > 0
                ? result.onlinePlayers() + " / " + result.maxPlayers()
                : String.valueOf(result.onlinePlayers());
        String delay = result.delay() > 0 ? result.delay() + " ms" : "-";
        String version = result.version().isBlank() ? "-" : escape(result.version());

        html = html
                .replace("{{ICON}}", icon)
                .replace("{{ADDRESS}}", escape(displayAddress))
                .replace("{{MOTD}}", motd)
                .replace("{{VERSION_LABEL}}", message("image-motd-version-label"))
                .replace("{{PLAYERS_LABEL}}", message("image-motd-players-label"))
                .replace("{{DELAY_LABEL}}", message("image-motd-delay-label"))
                .replace("{{VERSION}}", version)
                .replace("{{PLAYERS}}", players)
                .replace("{{DELAY}}", delay)
                .replace("{{STATUS}}", message(result.online() ? "image-motd-online" : "image-motd-offline"));
        return renderer.render(html, 900, 320);
    }

    public byte[] online(String serverName, int totalPlayers, int maxPlayers,
                         String serverIcon,
                         List<AvatarService.Request> players,
                         List<OnlineHistory.Sample> history) throws IOException {
        String html = renderer.template("online.html");
        List<AvatarService.Request> shown = players.size() > ONLINE_PLAYER_LIMIT
                ? players.subList(0, ONLINE_PLAYER_LIMIT)
                : players;
        Map<String, String> heads = avatars.resolveAll(shown).join();

        byte[] chart = charts.render(history);
        String chartHtml = chart == null
                ? ""
                : "<div class=\"chart\"><img src=\""
                + HtmlImageRenderer.pngDataUri(chart) + "\" width=\"820\" height=\"190\" alt=\"\"/></div>";

        String playersHtml = playerCards(shown, heads);
        String empty = shown.isEmpty()
                ? "<div class=\"empty\">" + message("image-online-empty") + "</div>"
                : "";
        String footer = message("image-online-footer");
        if (players.size() > ONLINE_PLAYER_LIMIT) {
            footer += " · " + plugin.messages().get("image-online-more",
                    Map.of("count", String.valueOf(players.size() - ONLINE_PLAYER_LIMIT)));
        }

        String iconSource = serverIcon;
        if (iconSource != null && !iconSource.isBlank() && !iconSource.startsWith("data:image/")) {
            iconSource = "data:image/png;base64," + iconSource;
        }
        html = html
                .replace("{{ICON}}", iconSource == null || iconSource.isBlank()
                        ? renderer.defaultServerIcon() : iconSource)
                .replace("{{SERVER_NAME}}", escape(serverName))
                .replace("{{ONLINE_LABEL}}", message("image-online-label"))
                .replace("{{COUNT}}", String.valueOf(totalPlayers))
                .replace("{{MAX}}", String.valueOf(Math.max(0, maxPlayers)))
                .replace("{{CHART}}", chartHtml)
                .replace("{{PLAYERS}}", playersHtml)
                .replace("{{EMPTY}}", empty)
                .replace("{{FOOTER}}", escape(footer));

        int rows = Math.max(1, (shown.size() + 3) / 4);
        int height = 150 + (chart == null ? 0 : 200) + rows * 48 + 50;
        return renderer.render(html, 900, height);
    }

    public List<byte[]> whitelist(List<AvatarService.Request> players) throws IOException {
        List<AvatarService.Request> all = new ArrayList<>(players);
        all.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()));
        if (all.isEmpty()) {
            return List.of(renderWhitelistPage(List.of(), Map.of(), 1, 1, 0));
        }
        int pages = (all.size() + WHITELIST_PAGE_SIZE - 1) / WHITELIST_PAGE_SIZE;
        List<byte[]> result = new ArrayList<>();
        for (int page = 0; page < pages; page++) {
            int from = page * WHITELIST_PAGE_SIZE;
            int to = Math.min(all.size(), from + WHITELIST_PAGE_SIZE);
            List<AvatarService.Request> pagePlayers = all.subList(from, to);
            Map<String, String> heads = avatars.resolveAll(pagePlayers).join();
            result.add(renderWhitelistPage(pagePlayers, heads, page + 1, pages, all.size()));
        }
        return result;
    }

    public byte[] error(String detail) throws IOException {
        String html = renderer.template("error.html")
                .replace("{{TITLE}}", message("image-error-title"))
                .replace("{{MESSAGE}}", escape(detail == null || detail.isBlank()
                        ? message("image-error-message") : detail));
        return renderer.render(html, 640, 180);
    }

    private byte[] renderWhitelistPage(List<AvatarService.Request> players, Map<String, String> heads,
                                       int page, int pages, int total) throws IOException {
        String html = renderer.template("whitelist.html")
                .replace("{{TITLE}}", message("image-whitelist-title"))
                .replace("{{COUNT}}", escape(plugin.messages().get("image-whitelist-count",
                        Map.of("count", String.valueOf(total)))))
                .replace("{{PAGE}}", pages <= 1 ? "" : escape(plugin.messages().get("image-whitelist-page",
                        Map.of("page", String.valueOf(page), "total", String.valueOf(pages)))))
                .replace("{{PLAYERS}}", playerCards(players, heads))
                .replace("{{EMPTY}}", players.isEmpty()
                        ? "<div class=\"empty\">" + message("image-whitelist-empty") + "</div>" : "")
                .replace("{{FOOTER}}", escape(plugin.messages().get("image-whitelist-title")));
        return renderer.render(html, 900, 520);
    }

    private String playerCards(List<AvatarService.Request> players, Map<String, String> heads) {
        if (players.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<table class=\"players\">");
        for (int i = 0; i < players.size(); i++) {
            if (i % 4 == 0) {
                html.append("<tr>");
            }
            AvatarService.Request player = players.get(i);
            String head = heads.getOrDefault(player.key(), renderer.steveHead());
            html.append("<td class=\"player\"><table><tr><td>")
                    .append("<img class=\"head\" src=\"").append(head)
                    .append("\" width=\"32\" height=\"32\" alt=\"\"/></td><td class=\"name\">")
                    .append(escape(player.name())).append("</td></tr></table></td>");
            if (i % 4 == 3 || i == players.size() - 1) {
                html.append("</tr>");
            }
        }
        return html.append("</table>").toString();
    }

    private String message(String key) {
        return escape(Text.stripColorCodes(plugin.messages().get(key)));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
