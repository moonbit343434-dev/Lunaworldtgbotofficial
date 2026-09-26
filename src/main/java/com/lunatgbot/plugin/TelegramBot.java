package com.lunatgbot.plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.UUID;

public class TelegramBot {

    private final LunaTGBot plugin;
    private final String apiBase;
    private volatile boolean running = false;
    private Thread pollThread;
    private long lastUpdateId = 0;

    public TelegramBot(LunaTGBot plugin, String token) {
        this.plugin  = plugin;
        this.apiBase = "https://api.telegram.org/bot" + token;
    }

    public void start() {
        running    = true;
        pollThread = new Thread(this::pollLoop, "LunaTGBot-Poll");
        pollThread.setDaemon(true);
        pollThread.start();
        plugin.getLogger().info("Telegram бот запущен.");
    }

    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
    }

    // ── Long Polling ─────────────────────────────────────────────────────

    private void pollLoop() {
        while (running) {
            try {
                String resp = get(apiBase + "/getUpdates?timeout=30&offset=" + (lastUpdateId + 1));
                if (resp != null) parseUpdates(resp);
            } catch (Exception e) {
                if (running) plugin.getLogger().warning("Polling ошибка: " + e.getMessage());
                sleep(3000);
            }
        }
    }

    private void parseUpdates(String json) {
        int pos = 0;
        while (true) {
            int idx = json.indexOf("\"update_id\"", pos);
            if (idx == -1) break;

            long uid = parseLong(json, idx + 12);
            if (uid > lastUpdateId) lastUpdateId = uid;

            int next = json.indexOf("\"update_id\"", idx + 1);

            // text
            int textIdx = json.indexOf("\"text\"", idx);
            if (textIdx == -1 || (next != -1 && textIdx > next)) { pos = idx + 1; continue; }
            String text = parseString(json, textIdx + 7);

            // chat.id
            long chatId = 0;
            int chatSec = json.indexOf("\"chat\"", idx);
            if (chatSec != -1 && (next == -1 || chatSec < next)) {
                int cid = json.indexOf("\"id\"", chatSec);
                if (cid != -1) chatId = parseLong(json, cid + 5);
            }

            // first_name
            String firstName = "";
            int fnIdx = json.indexOf("\"first_name\"", idx);
            if (fnIdx != -1 && (next == -1 || fnIdx < next)) {
                firstName = parseString(json, fnIdx + 13);
            }

            if (text != null && chatId != 0) handleMessage(chatId, firstName, text.trim());
            pos = idx + 1;
        }
    }

    // ── Обработка сообщений ───────────────────────────────────────────────

    private void handleMessage(long chatId, String firstName, String text) {
        LinkManager lm = plugin.getLinkManager();

        if (text.equals("/start")) {
            sendMessage(chatId, plugin.getConfig().getString("messages.tg-start",
                "Привет! Напиши /tglink на сервере и отправь мне код.")
                .replace("{name}", firstName));
            return;
        }

        if (text.equals("/info") || text.equals("/check")) {
            UUID uuid = lm.getUuidByTelegram(chatId);
            if (uuid == null) {
                sendMessage(chatId, plugin.getConfig().getString("messages.tg-info-not-linked", "Не привязан."));
            } else {
                String pName = Bukkit.getOfflinePlayer(uuid).getName();
                sendMessage(chatId, plugin.getConfig().getString("messages.tg-info-linked", "Привязан: {player}")
                    .replace("{player}", pName != null ? pName : "Unknown"));
            }
            return;
        }

        // 6-значный код
        if (text.matches("\\d{6}")) {
            if (!lm.isCodeValid(text)) {
                sendMessage(chatId, plugin.getConfig().getString("messages.tg-invalid-code",
                    "Код недействителен. Получи новый через /tglink."));
                return;
            }

            UUID playerUuid = lm.consumeCode(text);
            if (playerUuid == null) {
                sendMessage(chatId, "Ошибка. Попробуй снова.");
                return;
            }

            // Уже привязан этот TG?
            UUID existing = lm.getUuidByTelegram(chatId);
            if (existing != null) {
                String existName = Bukkit.getOfflinePlayer(existing).getName();
                sendMessage(chatId, plugin.getConfig().getString("messages.tg-already-linked",
                    "Уже привязан к {player}.")
                    .replace("{player}", existName != null ? existName : "Unknown"));
                return;
            }

            lm.link(playerUuid, chatId);

            double reward = plugin.getRewardManager().rollReward();
            int total     = plugin.getRewardManager().getTotalVariants();
            String currency = plugin.getConfig().getString("settings.currency-name", "монет");
            String fmt    = String.format("%.0f", reward);

            final double  fReward   = reward;
            final UUID    fUuid     = playerUuid;
            final long    fChatId   = chatId;
            final String  fCurrency = currency;
            final String  fFmt      = fmt;
            final int     fTotal    = total;

            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(fUuid), fReward);

                String pName = Bukkit.getOfflinePlayer(fUuid).getName();
                if (pName == null) pName = "Unknown";

                // Сообщение в игре
                Player online = Bukkit.getPlayer(fUuid);
                if (online != null) {
                    online.sendMessage(plugin.msg("linked-ingame")
                        .replace("{amount}", fFmt)
                        .replace("{currency}", fCurrency)
                        .replace("{total}", String.valueOf(fTotal)));
                }

                // Сообщение в Telegram
                sendMessage(fChatId, plugin.getConfig().getString("messages.tg-linked",
                    "Привязан! Награда: {amount} {currency}")
                    .replace("{player}", pName)
                    .replace("{amount}", fFmt)
                    .replace("{currency}", fCurrency));

                plugin.getLogger().info("Привязка: " + pName + " -> TG:" + fChatId + " | +" + fFmt + " " + fCurrency);
            });
            return;
        }

        sendMessage(chatId, plugin.getConfig().getString("messages.tg-unknown",
            "Отправь 6-значный код для привязки."));
    }

    // ── HTTP ─────────────────────────────────────────────────────────────

    public void sendMessage(long chatId, String text) {
        try {
            String enc = URLEncoder.encode(text, "UTF-8");
            get(apiBase + "/sendMessage?chat_id=" + chatId + "&text=" + enc);
        } catch (Exception e) {
            plugin.getLogger().warning("sendMessage error: " + e.getMessage());
        }
    }

    private String get(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(35000);
        c.setReadTimeout(35000);
        int code = c.getResponseCode();
        InputStream is = code == 200 ? c.getInputStream() : c.getErrorStream();
        if (is == null) return null;
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        c.disconnect();
        return sb.toString();
    }

    // ── JSON helpers ──────────────────────────────────────────────────────

    private long parseLong(String json, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (Character.isDigit(ch) || (ch == '-' && sb.length() == 0)) sb.append(ch);
            else if (sb.length() > 0) break;
        }
        try { return Long.parseLong(sb.toString()); } catch (Exception e) { return 0; }
    }

    private String parseString(String json, int from) {
        int s = json.indexOf('"', from);
        if (s == -1) return null;
        s++;
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int i = s; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (esc) { sb.append(ch); esc = false; }
            else if (ch == '\\') esc = true;
            else if (ch == '"') break;
            else sb.append(ch);
        }
        return sb.toString();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
