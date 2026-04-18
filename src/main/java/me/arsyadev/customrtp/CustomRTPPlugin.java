package me.arsyadev.customrtp;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CustomRTPPlugin extends JavaPlugin implements TabExecutor, Listener {

    private Economy economy;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,###.##");
    private final Map<UUID, PendingRtp> pendingRtps = new HashMap<>();

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

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ArsyaDev CustomRTP berhasil diaktifkan.");
    }

    @Override
    public void onDisable() {
        for (PendingRtp pending : pendingRtps.values()) {
            if (pending.timeoutTask != null) {
                pending.timeoutTask.cancel();
            }
        }
        pendingRtps.clear();
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

        if (pendingRtps.containsKey(player.getUniqueId())) {
            player.sendMessage(color(getConfig().getString(
                    "messages.pending",
                    "&eRTP sebelumnya masih diproses. Tunggu sebentar."
            )));
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

        double balance = economy.getBalance(player);
        double minimumBalance = worldSection.getDouble("minimum-balance", 1000.0D);
        double percent = worldSection.getDouble("percent", 10.0D);
        double maxCost = worldSection.getDouble("max-cost", 50000.0D);
        boolean bypassCost = player.hasPermission("customrtp.bypasscost");

        if (!bypassCost && balance < minimumBalance) {
            String message = config.getString(
                    "messages.not-enough-balance",
                    "&cSaldo minimal untuk RTP ini adalah &a$%minimum_balance%&f."
            );
            player.sendMessage(applyPlaceholders(
                    message, worldKey, balance, 0.0D, minimumBalance, percent, maxCost
            ));
            return true;
        }

        double rawCost = balance * percent / 100.0D;
        double cost = Math.floor(Math.min(rawCost, maxCost));
        if (cost < 0.0D) {
            cost = 0.0D;
        }

        String rtpCommand = worldSection.getString("rtp-command", "rtp world world_resources");
        if (rtpCommand.startsWith("/")) {
            rtpCommand = rtpCommand.substring(1);
        }

        PendingRtp pending = null;
        if (!bypassCost && cost > 0.0D) {
            String targetWorld = worldSection.getString("target-world", "").trim();
            pending = new PendingRtp(
                    worldKey,
                    targetWorld,
                    balance,
                    cost,
                    minimumBalance,
                    percent,
                    maxCost
            );

            pendingRtps.put(player.getUniqueId(), pending);

            long timeoutSeconds = Math.max(3L, config.getLong("teleport-confirm-timeout-seconds", 20L));
            pending.timeoutTask = Bukkit.getScheduler().runTaskLater(
                    this,
                    () -> handlePendingTimeout(player.getUniqueId()),
                    timeoutSeconds * 20L
            );
        }

        boolean success = player.performCommand(rtpCommand);

        if (!success) {
            clearPending(player.getUniqueId());
            player.sendMessage(color(config.getString(
                    "messages.command-failed",
                    "&cGagal menjalankan command RTP."
            )));
            return true;
        }

        if (pending != null && !pending.completed) {
            String processingMessage = config.getString(
                    "messages.processing",
                    "&eRTP sedang diproses. Biaya akan dipotong setelah teleport berhasil."
            );
            player.sendMessage(applyPlaceholders(
                    processingMessage, worldKey, balance, cost, minimumBalance, percent, maxCost
            ));
        }

        if (pending == null) {
            String processingMessage = config.getString(
                    "messages.processing-no-cost",
                    "&eRTP sedang diproses."
            );
            player.sendMessage(color(processingMessage));
        }

        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        PendingRtp pending = pendingRtps.get(player.getUniqueId());

        if (pending == null || pending.completed) {
            return;
        }

        if (!isSupportedTeleportCause(event.getCause())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (!isSuccessfulRtpTeleport(pending, from, to)) {
            return;
        }

        pending.completed = true;
        pendingRtps.remove(player.getUniqueId());

        if (pending.timeoutTask != null) {
            pending.timeoutTask.cancel();
        }

        Bukkit.getScheduler().runTask(this, () -> chargeSuccessfulTeleport(player, pending));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPending(event.getPlayer().getUniqueId());
    }

    private boolean isSupportedTeleportCause(PlayerTeleportEvent.TeleportCause cause) {
        return cause == PlayerTeleportEvent.TeleportCause.PLUGIN
                || cause == PlayerTeleportEvent.TeleportCause.COMMAND
                || cause == PlayerTeleportEvent.TeleportCause.UNKNOWN;
    }

    private boolean isSuccessfulRtpTeleport(PendingRtp pending, Location from, Location to) {
        if (to == null || to.getWorld() == null) {
            return false;
        }

        if (!pending.targetWorld.isBlank()) {
            return to.getWorld().getName().equalsIgnoreCase(pending.targetWorld);
        }

        if (from.getWorld() != null
                && !from.getWorld().getName().equalsIgnoreCase(to.getWorld().getName())) {
            return true;
        }

        double minDistance = Math.max(8.0D, getConfig().getDouble("teleport-confirm-distance", 32.0D));
        return from.distanceSquared(to) >= (minDistance * minDistance);
    }

    private void chargeSuccessfulTeleport(Player player, PendingRtp pending) {
        if (!player.isOnline()) {
            return;
        }

        EconomyResponse withdrawResponse = economy.withdrawPlayer(player, pending.cost);
        if (!withdrawResponse.transactionSuccess()) {
            String message = getConfig().getString(
                    "messages.charge-failed-after-teleport",
                    "&eRTP berhasil, tapi biaya tidak bisa dipotong: %error%"
            );

            String error = withdrawResponse.errorMessage == null || withdrawResponse.errorMessage.isBlank()
                    ? "unknown error"
                    : withdrawResponse.errorMessage;

            player.sendMessage(color(message.replace("%error%", error)));
            return;
        }

        String successMessage = getConfig().getString(
                "messages.success",
                "&aSaldo dipotong &e$%cost% &auntuk RTP &f(%world%)"
        );

        player.sendMessage(applyPlaceholders(
                successMessage,
                pending.worldKey,
                pending.requestBalance,
                pending.cost,
                pending.minimumBalance,
                pending.percent,
                pending.maxCost
        ));
    }

    private void handlePendingTimeout(UUID playerId) {
        PendingRtp pending = pendingRtps.remove(playerId);
        if (pending == null || pending.completed) {
            return;
        }

        pending.completed = true;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            String message = getConfig().getString(
                    "messages.not-confirmed",
                    "&eRTP gagal, cooldown, atau tidak terkonfirmasi. Saldo tidak dipotong."
            );

            player.sendMessage(applyPlaceholders(
                    message,
                    pending.worldKey,
                    pending.requestBalance,
                    pending.cost,
                    pending.minimumBalance,
                    pending.percent,
                    pending.maxCost
            ));
        }
    }

    private void clearPending(UUID playerId) {
        PendingRtp pending = pendingRtps.remove(playerId);
        if (pending == null) {
            return;
        }

        pending.completed = true;
        if (pending.timeoutTask != null) {
            pending.timeoutTask.cancel();
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            ConfigurationSection worldsSection = getConfig().getConfigurationSection("worlds");

            if (worldsSection != null) {
                suggestions.addAll(worldsSection.getKeys(false));
            }

            if (sender.hasPermission("customrtp.admin")) {
                suggestions.add("reload");
            }

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

    private static final class PendingRtp {
        private final String worldKey;
        private final String targetWorld;
        private final double requestBalance;
        private final double cost;
        private final double minimumBalance;
        private final double percent;
        private final double maxCost;

        private BukkitTask timeoutTask;
        private boolean completed;

        private PendingRtp(
                String worldKey,
                String targetWorld,
                double requestBalance,
                double cost,
                double minimumBalance,
                double percent,
                double maxCost
        ) {
            this.worldKey = worldKey;
            this.targetWorld = targetWorld == null ? "" : targetWorld;
            this.requestBalance = requestBalance;
            this.cost = cost;
            this.minimumBalance = minimumBalance;
            this.percent = percent;
            this.maxCost = maxCost;
            this.completed = false;
        }
    }
}
