package com.lunatgbot.plugin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandHandler implements CommandExecutor {

    private final LunaTGBot plugin;

    public CommandHandler(LunaTGBot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();

        switch (name) {
            case "tglink":        return cmdLink(sender); 
            case "tgunlink":      return cmdUnlink(sender);
            case "tgreload":      return cmdReload(sender);
            case "tggive":        return cmdGive(sender, args);
            case "tgcheck":       return cmdCheck(sender, args);
            case "tgunlinkadmin": return cmdUnlinkAdmin(sender, args);
        }
        return false;
    }

    // ── /tglink ───────────────────────────────────────────────────────────
    private boolean cmdLink(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.msg("player-only")); return true; }
        Player p = (Player) sender;
        LinkManager lm = plugin.getLinkManager();

        if (lm.isLinked(p.getUniqueId())) {
            p.sendMessage(plugin.msg("already-linked"));
            return true;
        }

        String code    = lm.generateCode(p.getUniqueId());
        String botName = plugin.getConfig().getString("bot-username", "your_bot");

        p.sendMessage(plugin.msg("link-code").replace("{code}", code));
        p.sendMessage(plugin.msg("link-instruction").replace("{bot}", botName));
        return true;
    }

    // ── /tgunlink ─────────────────────────────────────────────────────────
    private boolean cmdUnlink(CommandSender sender) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.msg("player-only")); return true; }
        Player p = (Player) sender;

        if (!plugin.getLinkManager().isLinked(p.getUniqueId())) {
            p.sendMessage(plugin.msg("not-linked"));
            return true;
        }
        plugin.getLinkManager().unlink(p.getUniqueId());
        p.sendMessage(plugin.msg("unlinked"));
        return true;
    }

    // ── /tgreload ─────────────────────────────────────────────────────────
    private boolean cmdReload(CommandSender sender) {
        if (!sender.hasPermission("lunatgbot.admin")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
        plugin.reload();
        sender.sendMessage(plugin.msg("reload"));
        return true;
    }

    // ── /tggive <ник> <сумма> ─────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private boolean cmdGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lunatgbot.admin")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /tggive <ник> <сумма>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.msg("player-not-found").replace("{player}", args[0]));
            return true;
        }

        double amount;
        try { amount = Double.parseDouble(args[1]); }
        catch (NumberFormatException e) {
            sender.sendMessage("§cНекорректная сумма: " + args[1]);
            return true;
        }

        plugin.getEconomy().depositPlayer(target, amount);
        String currency = plugin.getConfig().getString("settings.currency-name", "монет");
        String fmt = String.format("%.0f", amount);

        sender.sendMessage(plugin.msg("give-success")
            .replace("{amount}", fmt)
            .replace("{currency}", currency)
            .replace("{player}", args[0]));

        if (target.isOnline()) {
            ((Player) target.getPlayer()).sendMessage(plugin.msg("give-received")
                .replace("{amount}", fmt)
                .replace("{currency}", currency));
        }
        return true;
    }

    // ── /tgcheck <ник> ────────────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private boolean cmdCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lunatgbot.admin")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
        if (args.length < 1) { sender.sendMessage("§cИспользование: /tgcheck <ник>"); return true; }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        Long tgId = plugin.getLinkManager().getTelegramId(target.getUniqueId());
        if (tgId == null) {
            sender.sendMessage("§e" + args[0] + " §cне привязан к Telegram.");
        } else {
            sender.sendMessage("§e" + args[0] + " §aпривязан. Telegram ID: §f" + tgId);
        }
        return true;
    }

    // ── /tgunlinkadmin <ник> ──────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private boolean cmdUnlinkAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lunatgbot.admin")) { sender.sendMessage(plugin.msg("no-permission")); return true; }
        if (args.length < 1) { sender.sendMessage("§cИспользование: /tgunlinkadmin <ник>"); return true; }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!plugin.getLinkManager().isLinked(target.getUniqueId())) {
            sender.sendMessage("§e" + args[0] + " §cне привязан.");
            return true;
        }
        plugin.getLinkManager().unlink(target.getUniqueId());
        sender.sendMessage("§aАккаунт §e" + args[0] + " §aуспешно отвязан.");
        return true;
    }
}
