package com.lunatgbot.plugin;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;

public class LunaTGBot extends JavaPlugin {

    private static LunaTGBot instance;
    private Economy economy;
    private TelegramBot telegramBot;
    private LinkManager linkManager;
    private RewardManager rewardManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault не найден! Установи Vault + Economy плагин.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        linkManager   = new LinkManager(this);
        rewardManager = new RewardManager(this);

        String token = getConfig().getString("bot-token", "");
        if (token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            getLogger().severe("Укажи токен бота в config.yml (bot-token)!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        telegramBot = new TelegramBot(this, token);
        telegramBot.start();

        // Команды
        CommandHandler handler = new CommandHandler(this);
        getCommand("tglink").setExecutor(handler);
        getCommand("tgunlink").setExecutor(handler);
        getCommand("tgreload").setExecutor(handler);
        getCommand("tggive").setExecutor(handler);
        getCommand("tgcheck").setExecutor(handler);
        getCommand("tgunlinkadmin").setExecutor(handler);

        getLogger().info("LunaTGBot v" + getDescription().getVersion() + " запущен!");
    }

    @Override
    public void onDisable() {
        if (telegramBot != null) telegramBot.stop();
        if (linkManager != null) linkManager.save();
        getLogger().info("LunaTGBot остановлен.");
    }

    public void reload() {
        reloadConfig();
        rewardManager = new RewardManager(this);
        if (telegramBot != null) {
            telegramBot.stop();
            String token = getConfig().getString("bot-token", "");
            telegramBot = new TelegramBot(this, token);
            telegramBot.start();
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
            getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    // ── Хелпер для цветных сообщений ─────────────────────────────────────
    public String msg(String key) {
        String prefix = getConfig().getString("messages.prefix", "&8[&bLuna&8] &r")
            .replace("&", "§");
        String text = getConfig().getString("messages." + key, key)
            .replace("&", "§");
        return prefix + text;
    }

    public String msgRaw(String key) {
        return getConfig().getString("messages." + key, key).replace("&", "§");
    }

    public static LunaTGBot getInstance() { return instance; }
    public Economy getEconomy()           { return economy; }
    public TelegramBot getTelegramBot()   { return telegramBot; }
    public LinkManager getLinkManager()   { return linkManager; }
    public RewardManager getRewardManager() { return rewardManager; }
}
