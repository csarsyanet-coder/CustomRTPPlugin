package me.arsyadev.customrtp;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CustomRTPPlugin extends JavaPlugin implements TabExecutor {

    private Economy economy;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,###");

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vault economy tidak ditemukan. Plugin akan dinonaktifkan.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PluginCommand command = getCommand("customrtp");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        getLogger().info("ArsyaDev CustomRTP berhasil diaktifkan.");
    }

    private boolean setupEconomy() {
        Server server = getServer();
        Plugin vault = server.getPluginManager().getPlugin("Vault");
        if (vault == null) {
            return false;
        }

        ServicesManager servicesManager = server.getServicesManager();
        RegisteredServiceProvider<Economy> registration = servicesManager.getRegistration(Economy.class);
        if (registration == null) {
            return false;
        }

        this.economy = registration.getProvider();
        return this.economy != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("customrtp.admin")) {
                sender.sendMessage(color("&cKamu tidak punya permission."));
                return true;
            }

            reloadConfig();
            sender.sendMessage(color(getConfig().getString("messages.reload", "&aConfig berhasil direload.")));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya bisa dipakai player.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(color("&cGunakan: /customrtp <overworld|nether|end>"));
            return true;
        }

        String worldKey = args[0].toLowerCase(Locale.ROOT);

        FileConfiguration config = getConfig();
        ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
        if (worldsSection == null || !worldsSection.isConfigurationSection(worldKey)) {
            player.sendMessage(color("&cTujuan RTP tidak ditemukan di config."));
            return true;
        }

        ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldKey);
        if (worldSection == null) {
            player.sendMessage(color("&cKonfigurasi world tidak valid."));
            return true;
        }

        if (!worldSection.getBoolean("enabled", true)) {
            player.sendMessage(color(config.getString("messages.disabled", "&cRTP ini sedang dinonaktifkan.")));
            return true;
        }

        String permission = worldSection.getString("permission", "");
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            player.sendMessage(color(config.getString("messages.no-permission", "&cKamu tidak punya permission.")));
            return true;
        }

        double balance = economy.getBalance((OfflinePlayer) player);
        double minimumBalance = worldSection.getDouble("minimum-balance", 1000.0D);
        double percent = worldSection.getDouble("percent", 10.0D);
        double maxCost = worldSection.getDouble("max-cost", 50000.0D);

        boolean bypassCost = player.hasPermission("customrtp.bypasscost");

        if (!bypassCost && balance < minimumBalance) {
            String message = config.getString(
                    "messages.not-enough-balance",
                    "&cSaldo minimal untuk RTP ini adalah &a$%minimum_balance%&f."
            );
            player.sendMessage(applyPlaceholders(message, worldKey, balance, 0.0D, minimumBalance, percent, maxCost));
            return true;
        }

        double rawCost = balance * percent / 100.0D;
        double cost = Math.floor(Math.min(rawCost, maxCost));
        if (cost < 0.0D) {
            cost = 0.0D;
        }

        if (!bypassCost && cost > 0.0D) {
            EconomyResponse withdrawResponse = economy.withdrawPlayer((OfflinePlayer) player, cost);
            if (!withdrawResponse.transactionSuccess()) {
                player.sendMessage(color("&cGagal memotong saldo: " + withdrawResponse.errorMessage));
                return true;
            }
        }

        String rtpCommand = worldSection.getString("rtp-command", "rtp world world_resources");
        if (rtpCommand.startsWith("/")) {
            rtpCommand = rtpCommand.substring(1);
        }

        boolean success = player.performCommand(rtpCommand);
        if (!success) {
            if (!bypassCost && cost > 0.0D) {
                economy.depositPlayer((OfflinePlayer) player, cost);
            }

            player.sendMessage(color(config.getString("messages.command-failed", "&cGagal menjalankan command RTP.")));
            return true;
        }

        String successMessage = config.getString(
                "messages.success",
                "&aSaldo dipotong &e$%cost% &auntuk RTP &f(%world%)"
        );
        player.sendMessage(applyPlaceholders(successMessage, worldKey, balance, cost, minimumBalance, percent, maxCost));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();

            ConfigurationSection worldsSection = getConfig().getConfigurationSection("worlds");
            if (worldsSection != null) {
                suggestions.addAll(worldsSection.getKeys(false));
            }

            suggestions.add("reload");
            return suggestions;
        }

        return Collections.emptyList();
    }

    private String applyPlaceholders(
            String message,
            String world,
            double balance,
            double cost,
            double minimumBalance,
            double percent,
            double maxCost
    ) {
        if (message == null) {
            return "";
        }

        return color(
                message
                        .replace("%world%", world)
                        .replace("%balance%", formatMoney(balance))
                        .replace("%cost%", formatMoney(cost))
                        .replace("%minimum_balance%", formatMoney(minimumBalance))
                        .replace("%percent%", formatMoney(percent))
                        .replace("%max_cost%", formatMoney(maxCost))
        );
    }

    private String formatMoney(double amount) {
        return moneyFormat.format(amount);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
