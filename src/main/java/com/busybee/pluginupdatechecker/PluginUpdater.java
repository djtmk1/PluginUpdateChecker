package com.busybee.pluginupdatechecker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

public class PluginUpdater {

    private final JavaPlugin plugin;

    public PluginUpdater(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void performUpdates(CommandSender sender) {
        File updatesFile = new File(plugin.getDataFolder(), "updates.yml");
        if (!updatesFile.exists()) {
            sender.sendMessage(Component.text("updates.yml not found. Run /checkupdates first.", NamedTextColor.RED));
            return;
        }

        FileConfiguration updatesConfig = YamlConfiguration.loadConfiguration(updatesFile);
        if (!updatesConfig.isConfigurationSection("updates")) {
            sender.sendMessage(Component.text("No updates found in updates.yml.", NamedTextColor.YELLOW));
            return;
        }

        File updateFolder = new File(plugin.getDataFolder(), "updates");
        if (!updateFolder.exists()) {
            updateFolder.mkdirs();
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int total = updatesConfig.getConfigurationSection("updates").getKeys(false).size();
            AtomicInteger downloaded = new AtomicInteger(0);
            AtomicInteger skipped = new AtomicInteger(0);

            for (String pluginName : updatesConfig.getConfigurationSection("updates").getKeys(false)) {
                String urlString = updatesConfig.getString("updates." + pluginName);

                if (urlString != null && urlString.toLowerCase().endsWith(".jar")) {
                    try {
                        sender.sendMessage(Component.text("Downloading update for " + pluginName + "...", NamedTextColor.GRAY));
                        URL url = new URL(urlString);
                        String fileName = pluginName + ".jar";
                        File destination = new File(updateFolder, fileName);

                        try (InputStream in = url.openStream()) {
                            Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            plugin.getLogger().info("Downloaded update for " + pluginName + " to " + destination.getPath());
                            downloaded.getAndIncrement();
                        }
                    } catch (IOException e) {
                        plugin.getLogger().severe("Failed to download update for " + pluginName + ": " + e.getMessage());
                        sender.sendMessage(Component.text("Failed to download " + pluginName + ". Check console for errors.", NamedTextColor.RED));
                    }
                } else {
                    plugin.getLogger().warning("Skipping update for " + pluginName + ": URL is not a direct JAR link.");
                    sender.sendMessage(Component.text("Skipping " + pluginName + ": No direct .jar link found.", NamedTextColor.YELLOW));
                    skipped.getAndIncrement();
                }
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(Component.text("Auto-update process complete!", NamedTextColor.GREEN)
                        .append(Component.text(" Downloaded: " + downloaded.get(), NamedTextColor.WHITE))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Skipped: " + skipped.get(), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text("Downloaded files are in the 'plugins/PluginUpdateChecker/updates' folder.", NamedTextColor.AQUA));
            });
        });
    }
}
