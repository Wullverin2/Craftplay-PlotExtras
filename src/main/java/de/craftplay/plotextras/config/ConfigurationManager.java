package de.craftplay.plotextras.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public final class ConfigurationManager {

    private static final List<String> MANAGED_FILES = Arrays.asList(
            "config.yml",
            "language/de.yml",
            "language/en.yml",
            "gui/de/main.yml",
            "gui/en/main.yml",
            "gui/de/bedrock.yml",
            "gui/en/bedrock.yml",
            "gui/de/myplots.yml",
            "gui/en/myplots.yml",
            "gui/de/bedrock-myplots.yml",
            "gui/en/bedrock-myplots.yml",
            "gui/de/create.yml",
            "gui/en/create.yml",
            "gui/de/members.yml",
            "gui/en/members.yml",
            "gui/de/search.yml",
            "gui/en/search.yml",
            "gui/de/community.yml",
            "gui/en/community.yml",
            "gui/de/reports.yml",
            "gui/en/reports.yml",
            "gui/de/history.yml",
            "gui/en/history.yml",
            "gui/de/danger.yml",
            "gui/en/danger.yml",
            "gui/de/help.yml",
            "gui/en/help.yml",
            "gui/de/flags.yml",
            "gui/en/flags.yml",
            "gui/de/settings.yml",
            "gui/en/settings.yml",
            "gui/de/team.yml",
            "gui/en/team.yml"
    );
    private static final DateTimeFormatter BACKUP_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final JavaPlugin plugin;

    public ConfigurationManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void installOrUpdateDefaults() {
        for (final String resourcePath : MANAGED_FILES) {
            installOrUpdate(resourcePath);
        }
    }

    private void installOrUpdate(final String resourcePath) {
        if (plugin.getResource(resourcePath) == null) {
            plugin.getLogger().warning("Standarddatei fehlt im Plugin: " + resourcePath);
            return;
        }

        final File target = new File(plugin.getDataFolder(), resourcePath);
        if (!target.exists()) {
            plugin.saveResource(resourcePath, false);
            return;
        }

        final YamlConfiguration defaults = loadDefaults(resourcePath);
        final YamlConfiguration existing = YamlConfiguration.loadConfiguration(target);
        boolean changed = false;

        for (final String key : defaults.getKeys(true)) {
            if ("file-version".equals(key)) {
                continue;
            }
            if (existing.contains(key)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                existing.createSection(key);
            } else {
                existing.set(key, defaults.get(key));
            }
            changed = true;
        }

        final int defaultVersion = defaults.getInt("file-version", 1);
        final int existingVersion = existing.getInt("file-version", 0);
        if ("config.yml".equals(resourcePath) && existingVersion < 6) {
            final List<String> hiddenButtons = existing.getStringList("gui.hidden-main-buttons");
            if (hiddenButtons.removeIf(hiddenButton -> "help".equalsIgnoreCase(hiddenButton))) {
                existing.set("gui.hidden-main-buttons", hiddenButtons);
                changed = true;
            }
        }
        if (resourcePath.replace('\\', '/').endsWith("/main.yml") && existingVersion < 10) {
            if (existing.contains("bedrock-form")) {
                existing.set("bedrock-form", null);
                changed = true;
            }
            final org.bukkit.configuration.ConfigurationSection buttons = existing.getConfigurationSection("buttons");
            if (buttons != null) {
                for (final String buttonId : buttons.getKeys(false)) {
                    final String bedrockLabelPath = "buttons." + buttonId + ".bedrock-label";
                    if (existing.contains(bedrockLabelPath)) {
                        existing.set(bedrockLabelPath, null);
                        changed = true;
                    }
                }
            }
        }
        if (resourcePath.replace('\\', '/').endsWith("/main.yml") && existingVersion < 11) {
            existing.set("buttons.my-plots.commands", Arrays.asList("open-menu:myplots"));
            existing.set("buttons.my-plots.close", false);
            existing.set("buttons.favorites.commands", Arrays.asList("open-menu:myplots:1:name:favorites"));
            existing.set("buttons.favorites.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/bedrock.yml") && existingVersion < 2) {
            existing.set("buttons.my-plots.commands", Arrays.asList("open-menu:myplots"));
            existing.set("buttons.my-plots.close", false);
            existing.set("buttons.favorites.commands", Arrays.asList("open-menu:myplots:1:name:favorites"));
            existing.set("buttons.favorites.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/main.yml") && existingVersion < 12) {
            existing.set("buttons.search.commands", Arrays.asList("open-menu:search"));
            existing.set("buttons.search.close", false);
            existing.set("buttons.create.commands", Arrays.asList("open-menu:create:types"));
            existing.set("buttons.create.close", false);
            existing.set("buttons.community.commands", Arrays.asList("open-menu:community"));
            existing.set("buttons.community.close", false);
            existing.set("buttons.members.commands", Arrays.asList("open-menu:members:overview"));
            existing.set("buttons.members.close", false);
            existing.set("buttons.roles.commands", Arrays.asList("open-menu:members:roles"));
            existing.set("buttons.roles.close", false);
            existing.set("buttons.reports.commands", Arrays.asList("open-menu:reports"));
            existing.set("buttons.reports.close", false);
            existing.set("buttons.history.commands", Arrays.asList("open-menu:history"));
            existing.set("buttons.history.close", false);
            existing.set("buttons.help.commands", Arrays.asList("open-menu:help"));
            existing.set("buttons.help.close", false);
            changed = true;
        }
        if (resourcePath.replace('\\', '/').endsWith("/bedrock.yml") && existingVersion < 3) {
            existing.set("buttons.search.commands", Arrays.asList("open-menu:search"));
            existing.set("buttons.search.close", false);
            existing.set("buttons.create.commands", Arrays.asList("open-menu:create:types"));
            existing.set("buttons.create.close", false);
            existing.set("buttons.community.commands", Arrays.asList("open-menu:community"));
            existing.set("buttons.community.close", false);
            existing.set("buttons.members.commands", Arrays.asList("open-menu:members:overview"));
            existing.set("buttons.members.close", false);
            existing.set("buttons.roles.commands", Arrays.asList("open-menu:members:roles"));
            existing.set("buttons.roles.close", false);
            existing.set("buttons.reports.commands", Arrays.asList("open-menu:reports"));
            existing.set("buttons.reports.close", false);
            existing.set("buttons.history.commands", Arrays.asList("open-menu:history"));
            existing.set("buttons.history.close", false);
            existing.set("buttons.help.commands", Arrays.asList("open-menu:help"));
            existing.set("buttons.help.close", false);
            changed = true;
        }
        if (existingVersion != defaultVersion) {
            existing.set("file-version", defaultVersion);
            changed = true;
        }

        if (!changed) {
            return;
        }

        if (!backup(target, resourcePath)) {
            plugin.getLogger().warning("Konfiguration wird nicht verändert, weil kein Backup erstellt werden konnte: " + resourcePath);
            return;
        }
        try {
            existing.save(target);
            plugin.getLogger().info("Konfiguration ergänzt: " + resourcePath);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Konfiguration konnte nicht gespeichert werden: " + resourcePath, exception);
        }
    }

    private YamlConfiguration loadDefaults(final String resourcePath) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Standarddatei konnte nicht gelesen werden: " + resourcePath, exception);
            return new YamlConfiguration();
        }
    }

    private boolean backup(final File source, final String resourcePath) {
        final File backupFolder = new File(plugin.getDataFolder(), "backup");
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            plugin.getLogger().warning("Backup-Ordner konnte nicht erstellt werden: " + backupFolder.getPath());
            return false;
        }

        final String timestamp = BACKUP_TIME_FORMAT.format(LocalDateTime.now());
        final String safeName = resourcePath.replace('/', '_').replace('\\', '_').replace(".yml", "");
        File backupFile = new File(backupFolder, safeName + "-" + timestamp + ".yml");
        int counter = 2;
        while (backupFile.exists()) {
            backupFile = new File(backupFolder, safeName + "-" + timestamp + "-" + counter + ".yml");
            counter++;
        }
        try {
            Files.copy(source.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Backup konnte nicht erstellt werden: " + backupFile.getPath(), exception);
            return false;
        }
    }
}
