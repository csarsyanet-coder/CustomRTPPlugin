package me.arsyadev.customrtp;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CustomRTPPlugin extends JavaPlugin implements TabExecutor, Listener {

    private Economy economy;
    private final DecimalFormat moneyFormat = new DecimalFormat("#,###.##");
    private NamespacedKey voucherKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        voucherKey = new NamespacedKey(this, "resource_voucher");

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

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new CustomRTPExpansion(this).register();
            getLogger().info("PlaceholderAPI hook aktif.");
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

        if (args.length >= 1 && args[0].equalsIgnoreCase("givevoucher")) {
            if (!sender.hasPermission("customrtp.givevoucher")) {
                sender.sendMessage(color("&cKamu tidak punya permission."));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(color("&cGunakan: /customrtp givevoucher <player> [amount]"));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(color("&cPlayer tidak ditemukan."));
                return true;
            }

            int amount = 1;
            if (args.length >= 3) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    sender.sendMessage(color("&cAmount harus angka."));
                    return true;
                }
            }

            ItemStack voucher = createVoucherItem(amount);
            target.getInventory().addItem(voucher);

            sender.sendMessage(color(
                    getConfig().getString(
                                    "messages.voucher-given-sender",
                                    "&aBerhasil memberi &e%amount%x &6Voucher Resource &ake &f%player%"
                            )
                            .replace("%amount%", String.valueOf(amount))
                            .replace("%player%", target.getName())
            ));

            target.sendMessage(color(
                    getConfig().getString(
                                    "messages.voucher-given-target",
                                    "&aKamu menerima &e%amount%x &6Voucher Resource"
                            )
                            .replace("%amount%", String.valueOf(amount))
            ));
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
        ConfigurationSection worldSection = getWorldSection(worldKey);
        if (worldSection == null) {
            player.sendMessage(color("&cTujuan RTP tidak ditemukan di config."));
            return true;
        }

        if (!worldSection.getBoolean("enabled", true)) {
            player.sendMessage(color(getConfig().getString("messages.disabled", "&cRTP ini sedang dinonaktifkan.")));
            return true;
        }

        if (!canEnterResourceWorld(player, worldKey, worldSection, true)) {
            return true;
        }

        String rtpCommand = worldSection.getString("rtp-command", "rtp world world_resources");
        if (rtpCommand.startsWith("/")) {
            rtpCommand = rtpCommand.substring(1);
        }

        boolean success = player.performCommand(rtpCommand);
        if (!success) {
            player.sendMessage(color(getConfig().getString("messages.command-failed", "&cGagal menjalankan command RTP.")));
            return true;
        }

        player.sendMessage(color(getConfig().getString(
                "messages.processing",
                "&eRTP sedang diproses. Biaya akan dipotong saat kamu benar-benar masuk world resource."
        )));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleportCheck(PlayerTeleportEvent event) {
        ResourceWorldMatch match = getTeleportTargetResourceWorld(event);
        if (match == null) {
            return;
        }

        if (!canEnterResourceWorld(event.getPlayer(), match.worldKey, match.section, false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleportCharge(PlayerTeleportEvent event) {
        ResourceWorldMatch match = getTeleportTargetResourceWorld(event);
        if (match == null) {
            return;
        }

        Player player = event.getPlayer();

        if (player.hasPermission("customrtp.bypasscost")) {
            return;
        }

        double balance = economy.getBalance(player);
        double minimumBalance = match.section.getDouble("minimum-balance", 1000.0D);
        double percent = match.section.getDouble("percent", 10.0D);
        double maxCost = match.section.getDouble("max-cost", 50000.0D);

        if (consumeVoucher(player)) {
            String voucherMessage = getConfig().getString(
                    "messages.voucher-used",
                    "&aVoucher Resource digunakan. Masuk gratis ke &e(%world%)&a."
            );

            player.sendMessage(applyPlaceholders(
                    voucherMessage,
                    match.worldKey,
                    balance,
                    0.0D,
                    minimumBalance,
                    percent,
                    maxCost
            ));
            return;
        }

        double cost = calculateCost(balance, percent, maxCost);
        if (cost <= 0.0D) {
            return;
        }

        EconomyResponse withdrawResponse = economy.withdrawPlayer(player, cost);
        if (!withdrawResponse.transactionSuccess()) {
            String error = withdrawResponse.errorMessage == null || withdrawResponse.errorMessage.isBlank()
                    ? "unknown error"
                    : withdrawResponse.errorMessage;

            player.sendMessage(color(
                    getConfig().getString(
                                    "messages.charge-failed-after-entry",
                                    "&cKamu sudah masuk resource world, tapi pemotongan saldo gagal: %error%"
                            )
                            .replace("%error%", error)
            ));
            return;
        }

        String paidMessage = getConfig().getString(
                "messages.enter-paid",
                "&aSaldo dipotong &e$%cost% &auntuk masuk ke world resource &f(%world%)"
        );

        player.sendMessage(applyPlaceholders(
                paidMessage,
                match.worldKey,
                balance,
                cost,
                minimumBalance,
                percent,
                maxCost
        ));
    }

    private ResourceWorldMatch getTeleportTargetResourceWorld(PlayerTeleportEvent event) {
        if (event.getFrom() == null
                || event.getTo() == null
                || event.getFrom().getWorld() == null
                || event.getTo().getWorld() == null) {
            return null;
        }

        String fromWorld = event.getFrom().getWorld().getName();
        String toWorld = event.getTo().getWorld().getName();

        if (fromWorld.equalsIgnoreCase(toWorld)) {
            return null;
        }

        return findResourceWorldByTarget(toWorld);
    }

    private boolean canEnterResourceWorld(Player player, String worldKey, ConfigurationSection worldSection, boolean fromCommand) {
        String permission = worldSection.getString("permission", "");
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            player.sendMessage(color(getConfig().getString("messages.no-permission", "&cKamu tidak punya permission.")));
            return false;
        }

        if (player.hasPermission("customrtp.bypasscost")) {
            return true;
        }

        if (hasVoucher(player)) {
            return true;
        }

        double balance = economy.getBalance(player);
        double minimumBalance = worldSection.getDouble("minimum-balance", 1000.0D);
        double percent = worldSection.getDouble("percent", 10.0D);
        double maxCost = worldSection.getDouble("max-cost", 50000.0D);

        if (balance < minimumBalance) {
            String message = fromCommand
                    ? getConfig().getString(
                    "messages.not-enough-balance",
                    "&cSaldo minimal untuk RTP ini adalah &a$%minimum_balance%&f."
            )
                    : getConfig().getString(
                    "messages.entry-denied",
                    "&cKamu butuh minimal &a$%minimum_balance% &catau 1 &6Voucher Resource &cuntuk masuk ke &e(%world%)"
            );

            player.sendMessage(applyPlaceholders(
                    message,
                    worldKey,
                    balance,
                    0.0D,
                    minimumBalance,
                    percent,
                    maxCost
            ));
            return false;
        }

        return true;
    }

    private double calculateCost(double balance, double percent, double maxCost) {
        double rawCost = balance * percent / 100.0D;
        double cost = Math.floor(Math.min(rawCost, maxCost));
        return Math.max(cost, 0.0D);
    }

    private ConfigurationSection getWorldSection(String worldKey) {
        ConfigurationSection worldsSection = getConfig().getConfigurationSection("worlds");
        if (worldsSection == null) {
            return null;
        }
        return worldsSection.getConfigurationSection(worldKey);
    }

    private ResourceWorldMatch findResourceWorldByTarget(String targetWorld) {
        ConfigurationSection worldsSection = getConfig().getConfigurationSection("worlds");
        if (worldsSection == null) {
            return null;
        }

        for (String worldKey : worldsSection.getKeys(false)) {
            ConfigurationSection section = worldsSection.getConfigurationSection(worldKey);
            if (section == null || !section.getBoolean("enabled", true)) {
                continue;
            }

            String configuredTarget = section.getString("target-world", "");
            if (!configuredTarget.isBlank() && configuredTarget.equalsIgnoreCase(targetWorld)) {
                return new ResourceWorldMatch(worldKey, section);
            }
        }

        return null;
    }

    private ItemStack createVoucherItem(int amount) {
        String materialName = getConfig().getString("voucher.material", "PAPER");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.PAPER;
        }

        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color(getConfig().getString("voucher.name", "&6Voucher Resource")));

            List<String> lore = getConfig().getStringList("voucher.lore");
            if (!lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(color(line));
                }
                meta.setLore(coloredLore);
            }

            meta.getPersistentDataContainer().set(voucherKey, PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }

        return item;
    }

    private boolean hasVoucher(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isVoucher(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean consumeVoucher(Player player) {
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isVoucher(item)) {
                continue;
            }

            if (item.getAmount() <= 1) {
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - 1);
            }

            player.updateInventory();
            return true;
        }

        return false;
    }

    private boolean isVoucher(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        Integer value = meta.getPersistentDataContainer().get(voucherKey, PersistentDataType.INTEGER);
        return value != null && value == 1;
    }

    public String getPlaceholderValue(OfflinePlayer offlinePlayer, String worldKey, String valueKey) {
        if (economy == null || offlinePlayer == null) {
            return "";
        }

        ConfigurationSection worldSection = getWorldSection(worldKey);
        if (worldSection == null) {
            return "";
        }

        double balance = economy.getBalance(offlinePlayer);
        double minimumBalance = worldSection.getDouble("minimum-balance", 1000.0D);
        double percent = worldSection.getDouble("percent", 10.0D);
        double maxCost = worldSection.getDouble("max-cost", 50000.0D);
        double cost = balance >= minimumBalance ? calculateCost(balance, percent, maxCost) : 0.0D;

        Player onlinePlayer = offlinePlayer.getPlayer();
        boolean hasVoucher = onlinePlayer != null && hasVoucher(onlinePlayer);

        return switch (valueKey) {
            case "balance" -> formatMoney(balance);
            case "cost" -> formatMoney(cost);
            case "percent" -> formatSimple(percent);
            case "minimum_balance" -> formatMoney(minimumBalance);
            case "max_cost" -> formatMoney(maxCost);
            case "has_voucher" -> hasVoucher ? "Ada" : "Tidak ada";
            case "status" -> hasVoucher ? "Gratis dengan Voucher" : "Potong $" + formatMoney(cost);
            default -> "";
        };
    }

    private String formatMoney(double amount) {
        return moneyFormat.format(amount);
    }

    private String formatSimple(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
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

            if (sender.hasPermission("customrtp.givevoucher")) {
                suggestions.add("givevoucher");
            }

            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("givevoucher")) {
            List<String> players = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                players.add(online.getName());
            }
            return players;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("givevoucher")) {
            List<String> amounts = new ArrayList<>();
            amounts.add("1");
            amounts.add("5");
            amounts.add("10");
            amounts.add("64");
            return amounts;
        }

        return Collections.emptyList();
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static final class ResourceWorldMatch {
        private final String worldKey;
        private final ConfigurationSection section;

        private ResourceWorldMatch(String worldKey, ConfigurationSection section) {
            this.worldKey = worldKey;
            this.section = section;
        }
    }
}
