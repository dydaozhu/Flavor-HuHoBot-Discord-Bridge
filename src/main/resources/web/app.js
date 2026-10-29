const $ = (selector) => document.querySelector(selector);

let token = sessionStorage.getItem("fhd_token") || "";

const FIELDS = [
    ["discord.guild-id", "Discord 服务器 ID", "text"],
    ["discord.channels.game-to-discord", "游戏 → Discord 频道 ID", "text"],
    ["discord.channels.discord-to-game", "Discord → 游戏频道 ID", "text"],
    ["discord.channels.agent", "Agent 回复频道 ID", "text"],
    ["discord.channels.audit", "审计频道 ID", "text"],
    ["bridge.enabled", "启用聊天桥接", "bool"],
    ["bridge.from-game", "游戏 → Discord 格式", "text"],
    ["bridge.from-discord", "Discord → 游戏格式", "text"],
    ["bridge.start-with", "仅转发此前缀开头的游戏消息", "text"],
    ["bridge.link-required", "要求绑定后才能聊天", "bool"],
    ["link.whitelist-on-link", "绑定时自动加白名单", "bool"],
    ["agent.enabled", "启用 AI Agent", "bool"],
    ["agent.base-url", "AI 接口地址", "text"],
    ["agent.api-key", "AI API Key", "password"],
    ["agent.model", "模型", "text"],
    ["agent.mode", "模式（manual / auto）", "text"],
    ["agent.max-steps", "最大步数", "number"],
    ["agent.daily-token-budget", "每日 token 预算", "number"],
    ["security.command-mode", "命令策略（allowlist / blacklist）", "text"]
];

function getPath(object, path) {
    return path.split(".").reduce((value, key) => value == null ? undefined : value[key], object);
}

async function api(path, options = {}) {
    const headers = Object.assign({ "Content-Type": "application/json" }, options.headers || {});
    if (token) {
        headers["Authorization"] = "Bearer " + token;
    }
    const response = await fetch(path, Object.assign({}, options, { headers }));
    const text = await response.text();
    let data = {};
    try {
        data = text ? JSON.parse(text) : {};
    } catch (error) {
        data = { error: text };
    }
    if (!response.ok) {
        throw new Error(data.error || response.statusText);
    }
    return data;
}

async function login() {
    const value = $("#token").value.trim();
    if (!value) {
        return;
    }
    try {
        await api("/api/login", { method: "POST", body: JSON.stringify({ token: value }) });
        token = value;
        sessionStorage.setItem("fhd_token", token);
        showApp();
        await loadAll();
    } catch (error) {
        $("#login-error").textContent = error.message;
    }
}

function showApp() {
    $("#login").classList.add("hidden");
    $("#app").classList.remove("hidden");
}

function showLogin() {
    $("#app").classList.add("hidden");
    $("#login").classList.remove("hidden");
    $("#login-error").textContent = "";
}

function card(label, value, className) {
    const element = document.createElement("div");
    element.className = "card";
    const title = document.createElement("div");
    title.className = "card-label";
    title.textContent = label;
    const body = document.createElement("div");
    body.className = "card-value " + (className || "");
    body.textContent = value;
    element.append(title, body);
    return element;
}

async function loadStatus() {
    const data = await api("/api/status");
    const container = $("#status");
    container.replaceChildren(
        card("Discord", data.discord ? "已连接" : "未连接", data.discord ? "ok" : "bad"),
        card("服务器", data.guild || "-"),
        card("在线玩家", String(data.online)),
        card("绑定账号", String(data.links)),
        card("AI Agent", data.agent ? ("开启 / " + data.mode) : "关闭", data.agent ? "ok" : ""),
        card("待审批", String(data.pendingApprovals)),
        card("今日 token", String(data.tokensToday)),
        card("版本", data.version)
    );
}

function buildForm(config) {
    const container = $("#config");
    container.replaceChildren();
    FIELDS.forEach(([path, label, type], index) => {
        const row = document.createElement("div");
        row.className = "field";
        const labelElement = document.createElement("label");
        labelElement.textContent = label;
        labelElement.htmlFor = "cfg-" + index;
        let input;
        if (type === "bool") {
            input = document.createElement("input");
            input.type = "checkbox";
            input.checked = Boolean(getPath(config, path));
        } else {
            input = document.createElement("input");
            input.type = type === "number" ? "number" : (type === "password" ? "password" : "text");
            input.value = getPath(config, path) == null ? "" : String(getPath(config, path));
        }
        input.id = "cfg-" + index;
        input.dataset.path = path;
        input.dataset.type = type;
        row.append(labelElement, input);
        container.append(row);
    });
}

async function loadConfig() {
    const data = await api("/api/config");
    buildForm(data);
}

async function saveConfig() {
    const changes = {};
    FIELDS.forEach(([path, , type], index) => {
        const input = document.getElementById("cfg-" + index);
        if (!input) {
            return;
        }
        let value;
        if (type === "bool") {
            value = input.checked;
        } else if (type === "number") {
            value = Number(input.value || 0);
        } else {
            value = input.value;
        }
        if (type === "password" && (String(value).trim() === "" || String(value).includes("•"))) {
            return;
        }
        changes[path] = value;
    });
    try {
        const result = await api("/api/config", { method: "POST", body: JSON.stringify({ changes }) });
        $("#save-msg").textContent = "已保存 " + result.applied + " 项";
        await loadStatus();
    } catch (error) {
        $("#save-msg").textContent = "保存失败：" + error.message;
    }
}

function formatTime(value) {
    if (!value) {
        return "";
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

async function loadAudit() {
    const rows = await api("/api/audit?limit=100");
    const container = $("#audit");
    container.replaceChildren();
    if (!Array.isArray(rows) || rows.length === 0) {
        const empty = document.createElement("p");
        empty.className = "muted";
        empty.textContent = "暂无记录。";
        container.append(empty);
        return;
    }
    const table = document.createElement("table");
    const head = document.createElement("tr");
    ["时间", "操作者", "动作", "详情", "结果"].forEach((title) => {
        const th = document.createElement("th");
        th.textContent = title;
        head.append(th);
    });
    table.append(head);
    rows.forEach((row) => {
        const tr = document.createElement("tr");
        [formatTime(row.time), row.actorName || row.actorId, row.action, row.detail, row.result].forEach((value) => {
            const td = document.createElement("td");
            td.textContent = value == null ? "" : String(value);
            tr.append(td);
        });
        table.append(tr);
    });
    container.append(table);
}

async function loadAll() {
    try {
        await Promise.all([loadStatus(), loadConfig(), loadAudit()]);
    } catch (error) {
        if (String(error.message).includes("未授权")) {
            sessionStorage.removeItem("fhd_token");
            token = "";
            showLogin();
        } else {
            $("#save-msg").textContent = error.message;
        }
    }
}

async function reloadPlugin() {
    try {
        await api("/api/reload", { method: "POST" });
        $("#save-msg").textContent = "已重载";
        await loadAll();
    } catch (error) {
        $("#save-msg").textContent = "重载失败：" + error.message;
    }
}

$("#login-btn").addEventListener("click", login);
$("#token").addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
        login();
    }
});
$("#logout").addEventListener("click", () => {
    sessionStorage.removeItem("fhd_token");
    token = "";
    showLogin();
});
$("#refresh").addEventListener("click", loadAll);
$("#save").addEventListener("click", saveConfig);
$("#reload").addEventListener("click", reloadPlugin);

if (token) {
    showApp();
    loadAll();
}
