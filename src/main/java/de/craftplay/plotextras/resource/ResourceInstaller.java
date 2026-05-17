package de.craftplay.plotextras.resource;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.logging.Level;

public final class ResourceInstaller {

    private static final List<String> DEFAULT_RESOURCES = List.of(
            "config.yml",
            "features.yml",
            "wall.yml",
            "border.yml",
            "plot-settings.yml",
            "limits.yml",
            "language/de.yml",
            "language/en.yml",
            "gui/de/main.yml",
            "gui/de/plot-dashboard.yml",
            "gui/de/plot-tools.yml",
            "gui/de/plot-warps.yml",
            "gui/de/plot-search.yml",
            "gui/de/guestbook.yml",
            "gui/de/requests.yml",
            "gui/de/team-requests.yml",
            "gui/de/reports.yml",
            "gui/de/build-tasks.yml",
            "gui/de/config-issues.yml",
            "gui/de/statistics.yml",
            "gui/de/feature-toggles.yml",
            "gui/de/flag-presets.yml",
            "gui/de/flags.yml",
            "gui/de/decor.yml",
            "gui/de/decor-border.yml",
            "gui/de/decor-wall-stone.yml",
            "gui/de/decor-wall-wood.yml",
            "gui/de/decor-wall-nether.yml",
            "gui/de/decor-wall-precious.yml",
            "gui/de/decor-wall-slabs.yml",
            "gui/de/decor-border-natural.yml",
            "gui/de/decor-border-path.yml",
            "gui/de/decor-border-stone.yml",
            "gui/de/decor-border-color.yml",
            "gui/de/decor-border-slabs.yml",
            "gui/de/decor-border-special.yml",
            "gui/de/members.yml",
            "gui/de/roles.yml",
            "gui/de/role-edit.yml",
            "gui/de/member-roles.yml",
            "gui/de/member-remove-confirm.yml",
            "gui/de/backups.yml",
            "gui/de/backup-restore-confirm.yml",
            "gui/de/team-inspector.yml",
            "gui/de/team-tools.yml",
            "gui/de/audit-log.yml",
            "gui/de/redstone-alerts.yml",
            "gui/de/settings.yml",
            "gui/de/settings-weather.yml",
            "gui/de/settings-time.yml",
            "gui/de/settings-biome.yml",
            "gui/de/entity-limits.yml",
            "gui/de/language.yml",
            "gui/de-bedrock/audit-log.yml",
            "gui/de-bedrock/backup-restore-confirm.yml",
            "gui/de-bedrock/backups.yml",
            "gui/de-bedrock/build-tasks.yml",
            "gui/de-bedrock/config-issues.yml",
            "gui/de-bedrock/decor.yml",
            "gui/de-bedrock/decor-border.yml",
            "gui/de-bedrock/decor-border-color.yml",
            "gui/de-bedrock/decor-border-natural.yml",
            "gui/de-bedrock/decor-border-path.yml",
            "gui/de-bedrock/decor-border-slabs.yml",
            "gui/de-bedrock/decor-border-special.yml",
            "gui/de-bedrock/decor-border-stone.yml",
            "gui/de-bedrock/decor-wall-nether.yml",
            "gui/de-bedrock/decor-wall-precious.yml",
            "gui/de-bedrock/decor-wall-slabs.yml",
            "gui/de-bedrock/decor-wall-stone.yml",
            "gui/de-bedrock/decor-wall-wood.yml",
            "gui/de-bedrock/entity-limits.yml",
            "gui/de-bedrock/feature-toggles.yml",
            "gui/de-bedrock/flag-presets.yml",
            "gui/de-bedrock/flags.yml",
            "gui/de-bedrock/guestbook.yml",
            "gui/de-bedrock/language.yml",
            "gui/de-bedrock/main.yml",
            "gui/de-bedrock/member-remove-confirm.yml",
            "gui/de-bedrock/member-roles.yml",
            "gui/de-bedrock/members.yml",
            "gui/de-bedrock/plot-dashboard.yml",
            "gui/de-bedrock/plot-search.yml",
            "gui/de-bedrock/plot-tools.yml",
            "gui/de-bedrock/plot-warps.yml",
            "gui/de-bedrock/redstone-alerts.yml",
            "gui/de-bedrock/reports.yml",
            "gui/de-bedrock/requests.yml",
            "gui/de-bedrock/role-edit.yml",
            "gui/de-bedrock/roles.yml",
            "gui/de-bedrock/settings.yml",
            "gui/de-bedrock/settings-biome.yml",
            "gui/de-bedrock/settings-time.yml",
            "gui/de-bedrock/settings-weather.yml",
            "gui/de-bedrock/statistics.yml",
            "gui/de-bedrock/team-inspector.yml",
            "gui/de-bedrock/team-requests.yml",
            "gui/de-bedrock/team-tools.yml",
            "gui/en/main.yml",
            "gui/en/plot-dashboard.yml",
            "gui/en/plot-tools.yml",
            "gui/en/plot-warps.yml",
            "gui/en/plot-search.yml",
            "gui/en/guestbook.yml",
            "gui/en/requests.yml",
            "gui/en/team-requests.yml",
            "gui/en/reports.yml",
            "gui/en/build-tasks.yml",
            "gui/en/config-issues.yml",
            "gui/en/statistics.yml",
            "gui/en/feature-toggles.yml",
            "gui/en/flag-presets.yml",
            "gui/en/flags.yml",
            "gui/en/decor.yml",
            "gui/en/decor-border.yml",
            "gui/en/decor-wall-stone.yml",
            "gui/en/decor-wall-wood.yml",
            "gui/en/decor-wall-nether.yml",
            "gui/en/decor-wall-precious.yml",
            "gui/en/decor-wall-slabs.yml",
            "gui/en/decor-border-natural.yml",
            "gui/en/decor-border-path.yml",
            "gui/en/decor-border-stone.yml",
            "gui/en/decor-border-color.yml",
            "gui/en/decor-border-slabs.yml",
            "gui/en/decor-border-special.yml",
            "gui/en/members.yml",
            "gui/en/roles.yml",
            "gui/en/role-edit.yml",
            "gui/en/member-roles.yml",
            "gui/en/member-remove-confirm.yml",
            "gui/en/backups.yml",
            "gui/en/backup-restore-confirm.yml",
            "gui/en/team-inspector.yml",
            "gui/en/team-tools.yml",
            "gui/en/audit-log.yml",
            "gui/en/redstone-alerts.yml",
            "gui/en/settings.yml",
            "gui/en/settings-weather.yml",
            "gui/en/settings-time.yml",
            "gui/en/settings-biome.yml",
            "gui/en/entity-limits.yml",
            "gui/en/language.yml",
            "gui/en-bedrock/audit-log.yml",
            "gui/en-bedrock/backup-restore-confirm.yml",
            "gui/en-bedrock/backups.yml",
            "gui/en-bedrock/build-tasks.yml",
            "gui/en-bedrock/config-issues.yml",
            "gui/en-bedrock/decor.yml",
            "gui/en-bedrock/decor-border.yml",
            "gui/en-bedrock/decor-border-color.yml",
            "gui/en-bedrock/decor-border-natural.yml",
            "gui/en-bedrock/decor-border-path.yml",
            "gui/en-bedrock/decor-border-slabs.yml",
            "gui/en-bedrock/decor-border-special.yml",
            "gui/en-bedrock/decor-border-stone.yml",
            "gui/en-bedrock/decor-wall-nether.yml",
            "gui/en-bedrock/decor-wall-precious.yml",
            "gui/en-bedrock/decor-wall-slabs.yml",
            "gui/en-bedrock/decor-wall-stone.yml",
            "gui/en-bedrock/decor-wall-wood.yml",
            "gui/en-bedrock/entity-limits.yml",
            "gui/en-bedrock/feature-toggles.yml",
            "gui/en-bedrock/flag-presets.yml",
            "gui/en-bedrock/flags.yml",
            "gui/en-bedrock/guestbook.yml",
            "gui/en-bedrock/language.yml",
            "gui/en-bedrock/main.yml",
            "gui/en-bedrock/member-remove-confirm.yml",
            "gui/en-bedrock/member-roles.yml",
            "gui/en-bedrock/members.yml",
            "gui/en-bedrock/plot-dashboard.yml",
            "gui/en-bedrock/plot-search.yml",
            "gui/en-bedrock/plot-tools.yml",
            "gui/en-bedrock/plot-warps.yml",
            "gui/en-bedrock/redstone-alerts.yml",
            "gui/en-bedrock/reports.yml",
            "gui/en-bedrock/requests.yml",
            "gui/en-bedrock/role-edit.yml",
            "gui/en-bedrock/roles.yml",
            "gui/en-bedrock/settings.yml",
            "gui/en-bedrock/settings-biome.yml",
            "gui/en-bedrock/settings-time.yml",
            "gui/en-bedrock/settings-weather.yml",
            "gui/en-bedrock/statistics.yml",
            "gui/en-bedrock/team-inspector.yml",
            "gui/en-bedrock/team-requests.yml",
            "gui/en-bedrock/team-tools.yml"
    );

    private ResourceInstaller() {
    }

    public static void installDefaults(final JavaPlugin plugin) {
        final String currentVersion = plugin.getDescription().getVersion();
        final File versionFile = new File(plugin.getDataFolder(), "version.yml");
        final YamlConfiguration versionConfig = YamlConfiguration.loadConfiguration(versionFile);
        final String previousVersion = versionConfig.getString("version", "");
        final boolean versionUpgrade = !previousVersion.isBlank() && !previousVersion.equalsIgnoreCase(currentVersion);
        final File backupFolder = versionUpgrade ? new File(plugin.getDataFolder(), "backups/" + previousVersion) : null;

        for (final String resourcePath : DEFAULT_RESOURCES) {
            final File target = new File(plugin.getDataFolder(), resourcePath);
            if (!target.exists()) {
                plugin.saveResource(resourcePath, false);
                continue;
            }

            if (versionUpgrade) {
                backup(plugin, target, new File(backupFolder, resourcePath));
            }
            mergeMissingDefaults(plugin, resourcePath, target);
        }

        migratePlotSettings(plugin);
        migrateLegacyComponentConfig(plugin);

        versionConfig.set("version", currentVersion);
        try {
            versionConfig.save(versionFile);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not write version.yml.", exception);
        }
    }

    private static void backup(final JavaPlugin plugin, final File source, final File target) {
        try {
            final File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create backup folder: " + parent.getPath());
                return;
            }
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not backup config file: " + source.getPath(), exception);
        }
    }

    private static void mergeMissingDefaults(final JavaPlugin plugin, final String resourcePath, final File target) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return;
            }

            final YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            final YamlConfiguration existing = YamlConfiguration.loadConfiguration(target);
            boolean changed = false;
            for (final String key : defaults.getKeys(true)) {
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

            if (changed) {
                existing.save(target);
            }
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not merge defaults into " + target.getPath(), exception);
        }
    }

    private static void migratePlotSettings(final JavaPlugin plugin) {
        final File configFile = new File(plugin.getDataFolder(), "config.yml");
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        final ConfigurationSection legacySection = config.getConfigurationSection("plot-settings");
        if (legacySection == null) {
            return;
        }

        final File settingsFile = new File(plugin.getDataFolder(), "plot-settings.yml");
        final YamlConfiguration settingsConfig = YamlConfiguration.loadConfiguration(settingsFile);
        copySection(legacySection, settingsConfig, "");
        config.set("plot-settings", null);

        try {
            settingsConfig.save(settingsFile);
            config.save(configFile);
            plugin.getLogger().info("Moved legacy plot-settings from config.yml to plot-settings.yml.");
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not migrate plot-settings.yml.", exception);
        }
    }

    private static void migrateLegacyComponentConfig(final JavaPlugin plugin) {
        final File configFile = new File(plugin.getDataFolder(), "config.yml");
        final YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        final ConfigurationSection componentsSection = config.getConfigurationSection("plot-components");
        if (componentsSection == null) {
            return;
        }

        boolean migrated = false;
        for (final String component : List.of("wall", "border")) {
            final ConfigurationSection legacySection = componentsSection.getConfigurationSection(component);
            if (legacySection == null) {
                continue;
            }
            final File componentFile = new File(plugin.getDataFolder(), component + ".yml");
            final YamlConfiguration componentConfig = YamlConfiguration.loadConfiguration(componentFile);
            copySection(legacySection, componentConfig, "");
            try {
                componentConfig.save(componentFile);
                migrated = true;
            } catch (final IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not migrate " + component + ".yml.", exception);
            }
        }

        if (!migrated) {
            return;
        }
        config.set("plot-components", null);
        try {
            config.save(configFile);
            plugin.getLogger().info("Moved legacy plot-components from config.yml to wall.yml and border.yml.");
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not remove legacy plot-components from config.yml.", exception);
        }
    }

    private static void copySection(final ConfigurationSection source, final YamlConfiguration target, final String pathPrefix) {
        for (final String key : source.getKeys(false)) {
            final String path = pathPrefix.isBlank() ? key : pathPrefix + "." + key;
            final ConfigurationSection child = source.getConfigurationSection(key);
            if (child != null) {
                copySection(child, target, path);
            } else {
                target.set(path, source.get(key));
            }
        }
    }
}
