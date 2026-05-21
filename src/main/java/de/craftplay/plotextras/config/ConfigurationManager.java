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
