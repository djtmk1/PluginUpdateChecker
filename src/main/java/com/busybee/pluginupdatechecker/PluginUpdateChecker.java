package com.busybee.pluginupdatechecker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PluginUpdateChecker extends JavaPlugin {

    private PluginUpdater pluginUpdater;

    @Override
    public void onEnable() {
        getLogger().info("PluginUpdateChecker has been enabled.");
        this.pluginUpdater = new PluginUpdater(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("checkupdates")) {
            if (!sender.hasPermission("pluginupdater.check")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }

            sender.sendMessage(Component.text("Starting plugin update check...", NamedTextColor.YELLOW));

            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    File logFile = new File("logs/latest.log");
                    if (!logFile.exists()) {
                        sender.sendMessage(Component.text("Could not find latest.log file.", NamedTextColor.RED));
                        return;
                    }

                    Map<String, String> updates = new HashMap<>();
                    Pattern urlPattern = Pattern.compile("(https?://\\S+)");

                    for (String line : Files.readAllLines(logFile.toPath())) {
                        String lowerCaseLine = line.toLowerCase();
                        if (lowerCaseLine.contains("update available") || lowerCaseLine.contains("new version") || lowerCaseLine.contains("is outdated") || lowerCaseLine.contains("newer version") || lowerCaseLine.contains("a new update")) {
                            String pluginName = "Unknown";
                            String foundPluginName = null;

                            Pattern bracketPattern = Pattern.compile("\\[([^\\]]+)\\]");
                            Matcher bracketMatcher = bracketPattern.matcher(line);
                            while (bracketMatcher.find()) {
                                String content = bracketMatcher.group(1);
                                String potentialPluginName = content;

                                if (content.contains("/")) {
                                    potentialPluginName = content.split("/")[0].trim();
                                }

                                Plugin plugin = getServer().getPluginManager().getPlugin(potentialPluginName);
                                if (plugin != null) {
                                    foundPluginName = plugin.getName();
                                    break;
                                }
                            }

                            if (foundPluginName != null) {
                                pluginName = foundPluginName;
                            } else {
                                for (Plugin plugin : getServer().getPluginManager().getPlugins()) {
                                    Pattern pluginNameWordPattern = Pattern.compile("\\b" + Pattern.quote(plugin.getName()) + "\\b", Pattern.CASE_INSENSITIVE);
                                    if (pluginNameWordPattern.matcher(line).find()) {
                                        pluginName = plugin.getName();
                                        break;
                                    }
                                }
                            }

                            if (!"Unknown".equals(pluginName)) {
                                Matcher urlMatcher = urlPattern.matcher(line);
                                String url;
                                if (urlMatcher.find()) {
                                    url = urlMatcher.group(1);
                                } else {
                                    url = "https://www.google.com/search?q=spigot " + pluginName + " plugin";
                                }
                                updates.put(pluginName, url);
                            }
                        }
                    }

                    if (updates.isEmpty()) {
                        sender.sendMessage(Component.text("Scan complete. No update URLs found in the latest log file.", NamedTextColor.GREEN));
                        return;
                    }

                    File updatesFile = new File(getDataFolder(), "updates.yml");
                    if (!getDataFolder().exists()) {
                        getDataFolder().mkdirs();
                    }

                    FileConfiguration updatesConfig = YamlConfiguration.loadConfiguration(updatesFile);
                    updatesConfig.set("updates", null);
                    updatesConfig.createSection("updates", updates);
                    updatesConfig.options().header("This file was generated by PluginUpdateChecker.\\nIt contains links to plugins that have reported available updates.\\nIf a direct link was not found, a search link is provided.");
                    updatesConfig.save(updatesFile);

                    sender.sendMessage(Component.text("Scan complete! Found " + updates.size() + " updates. Check the updates.yml file for details.", NamedTextColor.GREEN));

                } catch (IOException e) {
                    getLogger().severe("An error occurred while checking for plugin updates: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            return true;
        } else if (command.getName().equalsIgnoreCase("autoupdate")) {
            if (!sender.hasPermission("pluginupdater.autoupdate")) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text("Starting auto-update process...", NamedTextColor.YELLOW));
            pluginUpdater.performUpdates(sender);
            return true;
        }
        return false;
    }
}
