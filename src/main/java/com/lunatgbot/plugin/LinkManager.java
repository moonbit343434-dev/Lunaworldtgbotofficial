package com.lunatgbot.plugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LinkManager {

    private final LunaTGBot plugin;
    private final File dataFile;

    private final Map<UUID, Long>   linkedAccounts = new HashMap<>();
    private final Map<Long, UUID>   telegramToUuid = new HashMap<>();
    private final Map<String, UUID> pendingCodes   = new HashMap<>();
    private final Map<String, Long> codeExpiry     = new HashMap<>();

    private static final long CODE_TTL = 10 * 60 * 1000L;

    public LinkManager(LunaTGBot plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "links.yml");
        load();
    }

    // ── Коды ─────────────────────────────────────────────────────────────

    public String generateCode(UUID uuid) {
        pendingCodes.entrySet().removeIf(e -> e.getValue().equals(uuid));
        codeExpiry.entrySet().removeIf(e -> {
            String k = e.getKey();
            return pendingCodes.containsKey(k) && pendingCodes.get(k).equals(uuid);
        });
        String code = String.format("%06d", new Random().nextInt(1000000));
        pendingCodes.put(code, uuid);
        codeExpiry.put(code, System.currentTimeMillis() + CODE_TTL);
        return code;
    }

    public boolean isCodeValid(String code) {
        Long exp = codeExpiry.get(code);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            pendingCodes.remove(code);
            codeExpiry.remove(code);
            return false;
        }
        return pendingCodes.containsKey(code);
    }

    public UUID consumeCode(String code) {
        codeExpiry.remove(code);
        return pendingCodes.remove(code);
    }

    // ── Привязки ──────────────────────────────────────────────────────────

    public void link(UUID uuid, long tgId) {
        UUID old = telegramToUuid.get(tgId);
        if (old != null) linkedAccounts.remove(old);
        linkedAccounts.put(uuid, tgId);
        telegramToUuid.put(tgId, uuid);
        save();
    }

    public void unlink(UUID uuid) {
        Long tgId = linkedAccounts.remove(uuid);
        if (tgId != null) telegramToUuid.remove(tgId);
        save();
    }

    public boolean isLinked(UUID uuid)          { return linkedAccounts.containsKey(uuid); }
    public Long    getTelegramId(UUID uuid)      { return linkedAccounts.get(uuid); }
    public UUID    getUuidByTelegram(long tgId)  { return telegramToUuid.get(tgId); }

    // ── Сохранение ────────────────────────────────────────────────────────

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : linkedAccounts.entrySet())
            cfg.set("links." + e.getKey().toString(), e.getValue());
        try { cfg.save(dataFile); }
        catch (IOException ex) { plugin.getLogger().warning("Ошибка сохранения links.yml: " + ex.getMessage()); }
    }

    private void load() {
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.isConfigurationSection("links")) return;
        for (String key : cfg.getConfigurationSection("links").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long tgId = cfg.getLong("links." + key);
                linkedAccounts.put(uuid, tgId);
                telegramToUuid.put(tgId, uuid);
            } catch (Exception ignored) {}
        }
        plugin.getLogger().info("Загружено привязок: " + linkedAccounts.size());
    }
}
