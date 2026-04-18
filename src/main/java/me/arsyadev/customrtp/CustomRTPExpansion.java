package me.arsyadev.customrtp;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

public final class CustomRTPExpansion extends PlaceholderExpansion {

    private final CustomRTPPlugin plugin;

    public CustomRTPExpansion(CustomRTPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "customrtp";
    }

    @Override
    public String getAuthor() {
        return "ArsyaDev";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || params == null || params.isBlank()) {
            return "";
        }

        String[] parts = params.toLowerCase(Locale.ROOT).split("_", 2);
        if (parts.length != 2) {
            return "";
        }

        String worldKey = parts[0];
        String valueKey = parts[1];

        return plugin.getPlaceholderValue(player, worldKey, valueKey);
    }
}
